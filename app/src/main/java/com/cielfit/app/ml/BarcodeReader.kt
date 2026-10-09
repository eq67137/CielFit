package com.cielfit.app.ml

import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * Barcode decoding with ZXing core (no Play Services, no dynamite module).
 * API surface verified against zxing core 3.5.3 with javap.
 *
 * The ZXing work is split from the CameraX plumbing on purpose: [decodeLuminance]
 * and [yPlaneBytes] take plain arrays and are exercised by
 * BarcodeReaderInstrumentedTest against real generated barcodes, so a broken
 * format hint or stride bug fails in a test instead of on the user's phone.
 */
class BarcodeReader {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODE_128
                ),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    /** Returns the decoded text, or null if this frame holds no readable barcode. */
    fun decode(proxy: ImageProxy): String? {
        val image = proxy.image ?: return null
        // ponytail: only the Y (luminance) plane is needed for barcodes, so no colour copy.
        val plane = image.planes[0]
        val data = yPlaneBytes(plane.buffer, image.width, image.height, plane.rowStride)
        return decodeLuminance(data, image.width, image.height)
    }

    /**
     * Copies the Y plane out of [buffer] into a tightly packed width*height array.
     *
     * CameraX pads each row to [rowStride], which is usually wider than the image.
     * Reading straight through therefore shears the image diagonally and no barcode
     * is ever found, so the padded case is copied row by row.
     */
    internal fun yPlaneBytes(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int
    ): ByteArray {
        val data = ByteArray(width * height)
        buffer.rewind()
        if (rowStride == width) {
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                val toRead = minOf(rowStride, buffer.remaining())
                if (toRead <= 0) break
                buffer.get(row, 0, toRead)
                System.arraycopy(row, 0, data, y * width, minOf(width, toRead))
            }
        }
        return data
    }

    /**
     * Decodes a packed 8-bit luminance buffer. Returns the barcode text or null.
     *
     * The reader is reset in a `finally`: MultiFormatReader keeps per-decode state,
     * and a throw between decodeWithState and reset would corrupt the next frame.
     */
    internal fun decodeLuminance(data: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            try { reader.reset() } catch (_: Exception) {}
        }
    }
}