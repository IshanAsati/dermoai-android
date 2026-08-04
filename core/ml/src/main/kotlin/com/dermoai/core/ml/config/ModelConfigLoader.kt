package com.dermoai.core.ml.config

import android.content.Context
import com.dermoai.core.domain.model.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelConfigLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(): ModelConfig {
        val json = context.assets.open(CONFIG_PATH).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val mean = obj.getJSONArray("normalizeMean").let { arr ->
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
        val std = obj.getJSONArray("normalizeStd").let { arr ->
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        }
        return ModelConfig(
            inputWidth = obj.getInt("inputWidth"),
            inputHeight = obj.getInt("inputHeight"),
            inputChannels = obj.getInt("inputChannels"),
            normalizeMean = mean,
            normalizeStd = std,
            outputClasses = obj.getInt("outputClasses"),
            supportsHeatmap = obj.getBoolean("supportsHeatmap"),
            melanomaIndex = obj.optInt("melanomaIndex", 5),
            melanomaLogitBias = obj.optDouble("melanomaLogitBias", 0.0).toFloat(),
            useTestTimeAugmentation = obj.optBoolean("useTestTimeAugmentation", true),
            healthyIndex = obj.optInt("healthyIndex", 11),
        )
    }

    companion object {
        const val CONFIG_PATH = "ml/model_config.json"
    }
}