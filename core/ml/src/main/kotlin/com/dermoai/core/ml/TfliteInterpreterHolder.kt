package com.dermoai.core.ml

import android.content.Context
import android.graphics.Bitmap
import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.ModelConfig
import com.dermoai.core.domain.model.SkinCondition
import com.dermoai.core.ml.preprocessing.ImagePreprocessor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TfliteInterpreterHolder @Inject constructor(
    private val preprocessor: ImagePreprocessor,
) {
    private var interpreter: Interpreter? = null

    val isLoaded: Boolean get() = interpreter != null

    fun load(context: Context, config: ModelConfig) {
        release()
        val modelBuffer = loadModelFile(context, MODEL_ASSET_PATH)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelBuffer, options)
    }

    fun runInference(
        bitmap: Bitmap,
        config: ModelConfig,
        labels: List<String>,
    ): InferenceResult {
        val interpreter = interpreter ?: error("Interpreter not loaded")
        val input = preprocessor.prepareInput(bitmap, config)
        val output = Array(1) { FloatArray(config.outputClasses) }
        interpreter.run(input, output)
        val probabilities = softmax(output[0], config.melanomaIndex, config.melanomaLogitBias)
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val predictions = ranked.map { index ->
            SkinCondition(
                label = labels.getOrElse(index) { "Unknown" },
                code = LABEL_CODES.getOrElse(index) { "UNK" },
                confidence = probabilities[index],
                severity = severityFor(index),
            )
        }
        val top = predictions.first()
        return InferenceResult(
            topPrediction = top,
            allPredictions = predictions,
            heatmap = null,
            severityEstimate = severityToScore(top.severity),
        )
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength,
        )
    }

    private fun softmax(logits: FloatArray, melanomaIndex: Int, bias: Float): FloatArray {
        val adjusted = logits.copyOf()
        if (bias != 0f && melanomaIndex in adjusted.indices) {
            adjusted[melanomaIndex] += bias
        }
        val max = adjusted.max()
        val exp = adjusted.map { kotlin.math.exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }

    private fun severityFor(classIndex: Int): ConditionSeverity = when (classIndex) {
        5 -> ConditionSeverity.CRITICAL
        0, 4 -> ConditionSeverity.HIGH
        1, 3 -> ConditionSeverity.MEDIUM
        else -> ConditionSeverity.LOW
    }

    private fun severityToScore(severity: ConditionSeverity): Int = when (severity) {
        ConditionSeverity.LOW -> 2
        ConditionSeverity.MEDIUM -> 5
        ConditionSeverity.HIGH -> 8
        ConditionSeverity.CRITICAL -> 10
    }

    companion object {
        const val MODEL_ASSET_PATH = "ml/skin_model.tflite"

        val LABEL_CODES = listOf(
            "BCC", "ACK", "NEV", "SEK", "SCC", "MEL",
            "Acne", "HairLoss", "NailFungus", "Fungal", "Vascular", "Healthy",
        )
    }
}