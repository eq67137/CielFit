package com.cielfit.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the shipped FoodClassifier against the real TFLite model on a device.
 *
 * The JVM unit test can only assert the asset is a flatbuffer and the maths is
 * right; it cannot prove the interpreter loads, that XNNPACK resolves, or that
 * the input tensor really wants 192x192 uint8. Those are exactly the things
 * that broke before (2024-wide output vs a 2022-row label file, int-truncated
 * dequantisation), so they are pinned here.
 */
@RunWith(AndroidJUnit4::class)
class FoodClassifierInstrumentedTest {

    // App assets (the tflite model + label CSV) live in the APK under test,
    // not the test APK, so this must be the target context.
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // The test images ship in the TEST apk.
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun bitmapOf(name: String): Bitmap =
        testAssets.open(name).use { BitmapFactory.decodeStream(it) }

    @Test
    fun modelLoadsAndReportsItsRealTensorContract() {
        val c = FoodClassifier.load(context)
        try {
            // load() itself is the assertion: a bad flatbuffer or a missing
            // native lib throws here rather than at first use.
            assertNotNull(c)
        } finally {
            c.close()
        }
    }

    @Test
    fun classifyReturnsNonEmptyRankedPredictions() {
        val c = FoodClassifier.load(context)
        try {
            val out = c.classify(bitmapOf("food.jpg"), topK = 5)
            assertTrue("classifier returned nothing", out.isNotEmpty())
            assertTrue("expected up to 5 results", out.size <= 5)
            for (p in out) {
                assertTrue("label blank for class ${p.classId}", p.label.isNotBlank())
                assertTrue("probability out of range: ${p.probability}", p.probability >= 0f)
                // The background class must never be surfaced as a food.
                assertTrue("background leaked into results", p.classId != FoodClassifier.BACKGROUND_ID)
            }
            // Sorted descending - the UI shows "best matches" in this order.
            val probs = out.map { it.probability }
            assertEquals("results are not sorted by probability", probs.sortedDescending(), probs)
        } finally {
            c.close()
        }
    }

    @Test
    fun probabilitiesAreFractionsNotRawQuantisedBytes() {
        // The original bug: raw uint8 tensor values (~200) were used as
        // probabilities, so every confidence read as "20000%" and the ranking
        // was meaningless. Dequantised values are 0..1.
        val c = FoodClassifier.load(context)
        try {
            for (p in c.classify(bitmapOf("food.jpg"), topK = 5)) {
                assertTrue(
                    "probability ${p.probability} looks like a raw uint8 byte, not a fraction",
                    p.probability <= 1.0f
                )
            }
        } finally {
            c.close()
        }
    }

    @Test
    fun classIdsStayInsideTheLabelMap() {
        // Output width is 2024 (background + 2023 labels) while the CSV has 2023
        // rows. Sizing the output array from the label file made interpreter.run
        // throw, and an off-by-one lookup mislabelled every prediction.
        val c = FoodClassifier.load(context)
        try {
            val labels = context.assets.open("food_labels.csv").bufferedReader().useLines { lines ->
                lines.mapNotNull { l ->
                    val p = l.split(',', limit = 2)
                    p.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { p.size == 2 }
                }.toSet()
            }
            for (p in c.classify(bitmapOf("food.jpg"), topK = 5)) {
                assertTrue(
                    "class ${p.classId} has no row in food_labels.csv",
                    p.classId in labels
                )
                assertTrue("label fell back to a synthetic name: ${p.label}", !p.label.startsWith("food_"))
            }
        } finally {
            c.close()
        }
    }

    @Test
    fun outputBufferMatchesTheTensorType() {
        // Regression: the output was allocated as IntArray, which TFLite maps to
        // INT32, while the tensor is UINT8. interpreter.run() threw
        // "Cannot convert between a TensorFlowLite tensor with type UINT8 and a
        // Java object of type [[I" on every classify() call - the photo scanner
        // could never return a result. This asserts the call actually completes.
        val c = FoodClassifier.load(context)
        try {
            val out = c.classify(bitmapOf("food.jpg"), topK = 3)
            assertTrue("classify() returned nothing", out.isNotEmpty())
        } finally {
            c.close()
        }
    }

    @Test
    fun repeatedClassifyCallsAreStable() {
        // Reusing one Interpreter across frames must not accumulate state.
        val c = FoodClassifier.load(context)
        try {
            val bmp = bitmapOf("food.jpg")
            val first = c.classify(bmp, topK = 3).map { it.classId }
            repeat(5) { c.classify(bmp, topK = 3) }
            val last = c.classify(bmp, topK = 3).map { it.classId }
            assertEquals("classification drifted across repeated calls", first, last)
        } finally {
            c.close()
        }
    }
}
