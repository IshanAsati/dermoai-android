package com.dermoai.core.ml.config

import android.content.Context
import com.dermoai.core.domain.model.HealthyGate
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads assets/ml/healthy_gate.json, written by tools/ml/train_healthy_gate.py.
 */
@Singleton
class HealthyGateLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns null when the asset is absent, leaving the 12-class head unmodified. */
    fun load(): HealthyGate? = runCatching {
        val json = context.assets.open(GATE_PATH).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("weights")
        HealthyGate(
            weights = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() },
            bias = obj.getDouble("bias").toFloat(),
            threshold = obj.getDouble("threshold").toFloat(),
        )
    }.getOrNull()

    companion object {
        const val GATE_PATH = "ml/healthy_gate.json"
    }
}
