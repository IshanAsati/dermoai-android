package com.dermoai.core.ml

import android.content.Context
import android.graphics.Bitmap
import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.HealthyGate
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
    private var healthyGate: HealthyGate? = null

    /** Output tensor indices, resolved by width since TFLite does not preserve order. */
    private var logitsOutput = 0
    private var featuresOutput = -1

    val isLoaded: Boolean get() = interpreter != null

    fun load(context: Context, config: ModelConfig, gate: HealthyGate? = null) {
        release()
        val modelBuffer = loadModelFile(context, MODEL_ASSET_PATH)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        val created = Interpreter(modelBuffer, options)
        resolveOutputs(created, config)
        interpreter = created
        // A gate is useless without the feature output to score, and older
        // single-output models predate it.
        healthyGate = gate.takeIf { featuresOutput >= 0 }
    }

    private fun resolveOutputs(interpreter: Interpreter, config: ModelConfig) {
        logitsOutput = 0
        featuresOutput = -1
        for (i in 0 until interpreter.outputTensorCount) {
            when (interpreter.getOutputTensor(i).shape().last()) {
                config.outputClasses -> logitsOutput = i
                FEATURE_DIM -> featuresOutput = i
            }
        }
    }

    fun runInference(
        bitmap: Bitmap,
        config: ModelConfig,
        labels: List<String>,
    ): InferenceResult {
        val interpreter = interpreter ?: error("Interpreter not loaded")
        val input = preprocessor.prepareInput(bitmap, config)

        val logits = Array(1) { FloatArray(config.outputClasses) }
        val outputs = mutableMapOf<Int, Any>(logitsOutput to logits)
        val features = if (featuresOutput >= 0) {
            Array(1) { FloatArray(FEATURE_DIM) }.also { outputs[featuresOutput] = it }
        } else {
            null
        }
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)

        val probabilities = softmax(logits[0], config.melanomaIndex, config.melanomaLogitBias)
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        var predictions = ranked.map { index ->
            SkinCondition(
                label = labels.getOrElse(index) { "Unknown" },
                code = LABEL_CODES.getOrElse(index) { "UNK" },
                confidence = probabilities[index],
                severity = severityFor(index),
            )
        }

        // The 12-class head never predicts normal skin on real photos, so when the
        // feature-space gate says healthy it takes precedence. See HealthyGate.applyTo.
        val gate = healthyGate
        val featureVector = features?.first()
        if (gate != null && featureVector != null) {
            predictions = gate.applyTo(
                ranked = predictions,
                features = featureVector,
                healthyLabel = labels.getOrElse(config.healthyIndex) { "Healthy / Normal Skin" },
                healthyCode = LABEL_CODES.getOrElse(config.healthyIndex) { "Healthy" },
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
        healthyGate = null
        featuresOutput = -1
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

        /** Width of the penultimate feature vector the healthy gate scores. */
        const val FEATURE_DIM = 1024

        val LABEL_CODES = listOf(
            "BCC", "ACK", "NEV", "SEK", "SCC", "MEL",
            "Acne", "HairLoss", "NailFungus", "Fungal", "Vascular", "Healthy",
        )
    }
}