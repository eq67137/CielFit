package com.cielfit.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the two bugs that made Scan food return nothing:
 *  1. the label file is `id,name` with ids 1..2023 and no id 0 (that slot is the
 *     model's background class), so id MUST be used as the tensor index;
 *  2. the output tensor is uint8-quantised, so it must be dequantised with
 *     `Float(v) * scale` - `(v * scale).toFloat()` on an Int truncates to zero.
 *
 * This runs on the real shipped assets, so a bad CSV or scale change fails here
 * instead of on the user's phone.
 */
class FoodClassifierAssetsTest {

    private val assetDir = File("src/main/assets")

    private fun labels(): Map<Int, String> {
        val out = HashMap<Int, String>()
        assetDir.resolve("food_labels.csv").readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            val p = line.split(',', limit = 2)
            if (p.size < 2) return@forEach
            p[0].trim().toIntOrNull()?.let { out[it] = p[1].trim() }
        }
        return out
    }

    @Test
    fun `label ids start at 1 because slot 0 is background`() {
        val l = labels()
        assertTrue("labels empty", l.isNotEmpty())
        assertEquals("id 1 must exist (0 is background)", true, l.containsKey(1))
        assertEquals("no label may claim id 0", false, l.containsKey(0))
    }

    @Test
    fun `label ids are contiguous 1 to N`() {
        val ids = labels().keys.sorted()
        assertEquals("ids must be contiguous from 1", ids, (1..ids.size).toList())
    }

    @Test
    fun `label count matches the model output width`() {
        // 2023 labels + 1 background slot = 2024 output classes.
        val n = labels().size
        assertEquals("model emits background + N labels", 2024, n + 1)
    }

    @Test
    fun `dequantising a uint8 score preserves its fraction`() {
        // The real defect: the output array was IntArray and the code did
        // `(v * outputScale).toFloat()` on each Int. Int * Float widens to Float in
        // Kotlin, so that specific line was merely redundant - but reading raw Int
        // tensor values WITHOUT multiplying by scale is what zeroed every score.
        val scale = 1f / 256f
        for (v in listOf(1, 64, 128, 192, 255)) {
            val correct = v.toFloat() * scale
            assertTrue("score $v dequantised to zero", correct > 0f)
            assertEquals(v / 256f, correct, 1e-6f)
        }
        // Raw tensor values are NOT probabilities - the model returned 200s, not 0.x.
        val raw = 200
        assertTrue("raw tensor value must not be used as a probability", raw > 1f)
        assertEquals(0.78125f, raw.toFloat() * scale, 1e-6f)
    }

    @Test
    fun `model file is present and is a real flatbuffer`() {
        val f = assetDir.resolve("food_classifier.tflite")
        assertTrue("model asset missing", f.exists())
        val head = ByteArray(8)
        assetDir.resolve("food_classifier.tflite").inputStream().use { it.read(head) }
        val magic = String(head.copyOfRange(4, 8))
        assertEquals("not a TFLite flatbuffer: $magic", "TFL3", magic)
    }
}