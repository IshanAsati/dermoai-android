package com.dermoai.core.ml.config

import android.content.Context
import com.dermoai.core.domain.model.MoleGate
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads assets/ml/mole_gate.json, written by tools/ml/train_mole_gate.py.
 */
@Singleton
class MoleGateLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns null when the asset is absent, leaving the 12-class head unmodified. */
    fun load(): MoleGate? = runCatching {
        val json = context.assets.open(GATE_PATH).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("weights")
        val codes = obj.getJSONArray("overridableCodes")
        MoleGate(
            weights = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() },
            bias = obj.getDouble("bias").toFloat(),
            threshold = obj.getDouble("threshold").toFloat(),
            overridableCodes = (0 until codes.length()).map { codes.getString(it) }.toSet(),
        )
    }.getOrNull()

    companion object {
        const val GATE_PATH = "ml/mole_gate.json"
    }
}
