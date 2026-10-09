package com.cielfit.app.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Prediction(val classId: Int, val label: String, val probability: Float)

/**
 * TFLite wrapper for google/aiy/vision/classifier/food_V1.
 *
 * Tensor contract verified against the real file (see docs/specs): input is
 * [1,192,192,3] uint8 quantized (scale 1/128, zp 128) and output is [1,2024]
 * uint8 (scale 1/256). Background class is index 0 and is dropped.
 */
class FoodClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: Map<Int, String>
) {
    private val inputSize: Int
    private val inputScale: Float
    private val inputZeroPoint: Int
    private val outputSize: Int
    private val outputScale: Float
    private val outputIsFloat: Boolean

    init {
        val inD = interpreter.getInputTensor(0)
        inputSize = inD.shape()[2]
        inputScale = inD.quantizationParams().scale
        inputZeroPoint = inD.quantizationParams().zeroPoint
        val outD = interpreter.getOutputTensor(0)
        // The output width comes from the TENSOR, never from the label file. This
        // model emits 2024 classes (index 0 = background) while the label CSV has
        // 2022 rows, so sizing by labels makes interpreter.run() throw.
        outputSize = outD.shape()[outD.shape().size - 1]
        outputIsFloat = outD.dataType() == org.tensorflow.lite.DataType.FLOAT32
        outputScale = if (outputIsFloat) 1f else outD.quantizationParams().scale
    }

    private val bufferSize: Int
        get() = inputSize * inputSize * 3

    /** Classify a bitmap; returns up to [topK] predictions sorted by probability desc. */
    fun classify(bitmap: Bitmap, topK: Int = 5): List<Prediction> {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer
            .allocateDirect(bufferSize)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.put(((p shr 16) and 0xFF).toByte()) // R
            buffer.put(((p shr 8) and 0xFF).toByte())  // G
            buffer.put((p and 0xFF).toByte())           // B
        }
        buffer.rewind()

        // Size the output array from the tensor's real shape.
        // TFLite maps ByteArray -> UINT8, IntArray -> INT32, FloatArray -> FLOAT32.
        // This model's output is UINT8, so an IntArray here makes run() throw
        // "Cannot convert between a TensorFlowLite tensor with type UINT8 and a
        // Java object of type [[I" on every single classify() call.
        val output: Any = if (outputIsFloat) {
            Array(1) { FloatArray(outputSize) }
        } else {
            Array(1) { ByteArray(outputSize) }
        }
        interpreter.run(buffer, output)

        // Dequantize into Float BEFORE scaling. `(v * scale).toFloat()` on an Int
        // yields the integer part only, so every probability collapsed to 0 and the
        // ranking was meaningless.
        val scores: List<Float> = if (outputIsFloat) {
            (output as Array<FloatArray>)[0].toList()
        } else {
            (output as Array<ByteArray>)[0].map { (it.toInt() and 0xFF).toFloat() * outputScale }
        }

        return scores
            .mapIndexed { i, v -> v to i }
            .filter { it.second != BACKGROUND_ID }
            .sortedByDescending { it.first }
            .take(topK)
            .map { (prob, id) ->
                Prediction(id, labels[id] ?: "food_$id", prob)
            }
    }

    fun close() = interpreter.close()

    companion object {
        const val BACKGROUND_ID = 0
        private const val MODEL_ASSET = "food_classifier.tflite"
        private const val LABEL_ASSET = "food_labels.csv"

        fun load(context: Context): FoodClassifier {
            // The CSV is `id,name` with ids 1..2023 and NO id 0 row - id 0 is the
            // model's background class. So the file's own id IS the tensor index.
            // Dropping the header and shifting by list position moved every label
            // down one, mislabelling every prediction.
            val labels = context.assets.open(LABEL_ASSET).bufferedReader().useLines { lines ->
                val out = HashMap<Int, String>()
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split(',', limit = 2)
                    if (parts.size < 2) return@forEach
                    val id = parts[0].trim().toIntOrNull() ?: return@forEach
                    out[id] = parts[1].trim()
                }
                out
            }
            val model = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val modelBuffer = ByteBuffer
                .allocateDirect(model.size)
                .order(ByteOrder.nativeOrder())
            modelBuffer.put(model)
            modelBuffer.rewind()
            val interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                numThreads = 2
                useXNNPACK = true
            })
            return FoodClassifier(interpreter, labels)
        }
    }
}