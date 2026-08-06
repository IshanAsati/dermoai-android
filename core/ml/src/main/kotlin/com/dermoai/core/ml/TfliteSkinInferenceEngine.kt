package com.dermoai.core.ml

import android.content.Context
import android.graphics.Bitmap
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.ml.SkinInferenceEngine
import com.dermoai.core.domain.model.ModelConfig
import com.dermoai.core.ml.config.HealthyGateLoader
import com.dermoai.core.ml.config.ModelConfigLoader
import com.dermoai.core.ml.config.ModelLabelsLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite-backed inference engine. Loads model assets from app/src/main/assets/ml/.
 * The PyTorch checkpoint from dermoai-final is converted to TFLite in Phase 6.
 */
@Singleton
class TfliteSkinInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configLoader: ModelConfigLoader,
    private val labelsLoader: ModelLabelsLoader,
    private val healthyGateLoader: HealthyGateLoader,
    private val interpreterHolder: TfliteInterpreterHolder,
) : SkinInferenceEngine {

    private var config: ModelConfig? = null
    private var labels: List<String> = emptyList()

    override val isReady: Boolean
        get() = interpreterHolder.isLoaded && labels.isNotEmpty()

    override suspend fun initialize(): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            config = configLoader.load()
            labels = labelsLoader.load()
            interpreterHolder.load(context, config!!, healthyGateLoader.load())
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Error(it, it.message) },
        )
    }

    override suspend fun predict(bitmap: Bitmap): AppResult<InferenceResult> = withContext(Dispatchers.Default) {
        val currentConfig = config ?: return@withContext AppResult.Error(
            IllegalStateException("Model not initialized"),
            "Model not initialized",
        )
        if (!isReady) {
            return@withContext AppResult.Error(
                IllegalStateException("TFLite model not loaded"),
                "On-device model is not available yet",
            )
        }
        runCatching {
            interpreterHolder.runInference(bitmap, currentConfig, labels)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, it.message) },
        )
    }

    override fun release() {
        interpreterHolder.release()
    }
}