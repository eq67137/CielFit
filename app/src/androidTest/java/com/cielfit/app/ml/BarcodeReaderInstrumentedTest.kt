package com.cielfit.app.ml

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * Runs the shipped BarcodeReader against real barcode images on a device.
 *
 * This is the test that proves the scanner actually works: the unit tests can
 * only check assets and maths, but decoding depends on the ZXing format hints,
 * the luminance extraction, and the stride handling, none of which are
 * observable without the real library and a real image.
 *
 * The images are generated barcodes for real product codes (Nutella, Coca-Cola,
 * Milka) plus a noise negative control. Luminance is taken with the same
 * Rec.601 weights a camera Y plane uses, so the input matches what CameraX
 * hands the analyzer in production.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeReaderInstrumentedTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    /** Loads a test image as a packed Y-plane byte array, as CameraX would supply. */
    private fun luminanceOf(name: String): Triple<ByteArray, Int, Int> {
        val bmp = assets.open(name).use { BitmapFactory.decodeStream(it) }
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val y = ByteArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec.601 luma, the same conversion a camera YUV plane carries.
            y[i] = ((66 * r + 129 * g + 25 * b + 128 shr 8) + 16).toByte()
        }
        return Triple(y, w, h)
    }

    private fun decode(name: String): String? {
        val (y, w, h) = luminanceOf(name)
        return BarcodeReader().decodeLuminance(y, w, h)
    }

    @Test
    fun decodesEan13Nutella() {
        assertEquals("3017620422003", decode("ean13_3017620422003.png"))
    }

    @Test
    fun decodesEan13CocaCola() {
        assertEquals("5449000000996", decode("ean13_5449000000996.png"))
    }

    @Test
    fun decodesEan13Milka() {
        assertEquals("7622210449283", decode("ean13_7622210449283.png"))
    }

    @Test
    fun decodesEan8() {
        assertEquals("96385074", decode("ean8_96385074.png"))
    }

    @Test
    fun decodesCode128() {
        assertEquals("CIELFIT123", decode("code128_CIELFIT123.png"))
    }

    @Test
    fun noiseDecodesToNullRatherThanGarbage() {
        // A false positive here would log a phantom food on every camera frame.
        assertNull("noise must not decode", decode("noise.png"))
    }

    @Test
    fun readerIsReusableAfterAFailedDecode() {
        // MultiFormatReader holds per-decode state. Without the reset() in the
        // finally, a null result leaves the reader primed and the next real
        // barcode silently fails - the classic "scanner works once" bug.
        val reader = BarcodeReader()
        val (noise, nw, nh) = luminanceOf("noise.png")
        assertNull(reader.decodeLuminance(noise, nw, nh))

        val (y, w, h) = luminanceOf("ean13_3017620422003.png")
        assertEquals(
            "reader did not recover after a null decode",
            "3017620422003",
            reader.decodeLuminance(y, w, h)
        )
    }

    @Test
    fun paddedRowStrideIsUnsheared() {
        // CameraX pads rows to rowStride > width. Reading straight through
        // shears the image and no barcode is found, so this guards the
        // row-by-row copy in yPlaneBytes.
        val (packed, w, h) = luminanceOf("ean13_3017620422003.png")
        val stride = w + 37 // deliberately not a multiple of anything
        val padded = ByteBuffer.allocateDirect(stride * h)
        for (y in 0 until h) {
            padded.put(packed, y * w, w)
            repeat(stride - w) { padded.put(0x7F) } // filler the camera would leave
        }
        padded.rewind()

        val extracted = BarcodeReader().yPlaneBytes(padded, w, h, stride)
        assertEquals("extracted length must be width*height", w * h, extracted.size)
        assertTrue("extraction did not strip row padding", extracted.contentEquals(packed))

        assertEquals(
            "padded-stride frame must still decode",
            "3017620422003",
            BarcodeReader().decodeLuminance(extracted, w, h)
        )
    }

    @Test
    fun emptyFrameDecodesToNull() {
        assertNull(BarcodeReader().decodeLuminance(ByteArray(640 * 480), 640, 480))
    }
}
