package com.dermoai.core.domain.severity

import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import kotlin.math.roundToInt

/** What the app tells the user to do next, derived from the severity tier. */
enum class SeverityGuidance {
    /** LOW — educational information, no action required. */
    EDUCATIONAL,

    /** MEDIUM — common condition, keep monitoring. */
    MONITOR,

    /** HIGH — consider consulting a dermatologist. */
    CONSULT_DERMATOLOGIST,

    /** CRITICAL — consult a clinician promptly. */
    URGENT_CARE,
}

/**
 * Structured, severity-aware result message ("a line depending on how serious
 * the condition is and the probabilities"). The engine stays pure and
 * testable; the UI localizes the sentence templates from this payload.
 */
data class SeverityMessage(
    val tier: ConditionSeverity,
    val guidance: SeverityGuidance,
    val conditionLabel: String,
    /** Top prediction confidence rounded to a whole percentage (0-100). */
    val confidencePercent: Int,
    /** Second condition worth mentioning, if confidence is high enough. */
    val runnerUpLabel: String?,
    /** True when the tier recommends seeing a doctor (HIGH / CRITICAL). */
    val considerDoctor: Boolean,
)

/**
 * Builds the message payload from model inference output.
 */
class SeverityMessageEngine {

    fun build(result: InferenceResult): SeverityMessage {
        val top = result.topPrediction
        val runnerUp = result.allPredictions
            .drop(1)
            .firstOrNull { it.confidence >= RUNNER_UP_MIN_CONFIDENCE && it.code != top.code }
        return SeverityMessage(
            tier = top.severity,
            guidance = top.severity.toGuidance(),
            conditionLabel = top.label,
            confidencePercent = (top.confidence * 100).roundToInt(),
            runnerUpLabel = runnerUp?.label,
            considerDoctor = top.severity >= ConditionSeverity.HIGH,
        )
    }

    private fun ConditionSeverity.toGuidance(): SeverityGuidance = when (this) {
        ConditionSeverity.LOW -> SeverityGuidance.EDUCATIONAL
        ConditionSeverity.MEDIUM -> SeverityGuidance.MONITOR
        ConditionSeverity.HIGH -> SeverityGuidance.CONSULT_DERMATOLOGIST
        ConditionSeverity.CRITICAL -> SeverityGuidance.URGENT_CARE
    }

    companion object {
        /** Runner-up conditions below this confidence are not worth mentioning. */
        const val RUNNER_UP_MIN_CONFIDENCE = 0.15f
    }
}
