package com.dermoai.core.domain.ml

import android.graphics.Bitmap
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.model.SkinCondition

/**
 * Abstraction for on-device skin condition inference.
 * Implementations may use TFLite; the interface allows future engine swaps.
 */
interface SkinInferenceEngine {
    suspend fun initialize(): AppResult<Unit>
    suspend fun predict(bitmap: Bitmap): AppResult<InferenceResult>
    fun release()
    val isReady: Boolean
}

data class InferenceResult(
    val topPrediction: SkinCondition,
    val allPredictions: List<SkinCondition>,
    val heatmap: Bitmap?,
    val severityEstimate: Int,
)