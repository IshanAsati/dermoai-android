package com.dermoai.core.domain.model

/**
 * Configuration for the on-device TFLite skin model.
 * Values are sourced from assets/ml/model_config.json.
 */
data class ModelConfig(
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val normalizeMean: FloatArray,
    val normalizeStd: FloatArray,
    val outputClasses: Int,
    val supportsHeatmap: Boolean,
    val melanomaIndex: Int = 5,
    val melanomaLogitBias: Float = 0f,
    val useTestTimeAugmentation: Boolean = true,
)