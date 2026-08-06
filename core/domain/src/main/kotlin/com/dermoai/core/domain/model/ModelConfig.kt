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
    /**
     * Keep at 0. Measured on 99 real ISIC clinical photos, a -1.2 bias halved
     * melanoma recall (40% -> 20%) in exchange for a little overall accuracy.
     */
    val melanomaLogitBias: Float = 0f,
    val useTestTimeAugmentation: Boolean = true,
    val healthyIndex: Int = 11,
)