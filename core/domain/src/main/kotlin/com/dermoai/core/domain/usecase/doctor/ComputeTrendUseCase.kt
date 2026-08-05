package com.dermoai.core.domain.usecase.doctor

import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.model.TrendDirection
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.round

/**
 * One scan, reduced to the three numbers a trend can legitimately be built from.
 *
 * [confidence] is carried rather than dropped because a 34%-confidence
 * "carcinoma" and a 96%-confidence one are not the same evidence, and averaging
 * them as equals is how a blurry photo turns into a doctor's alert.
 */
data class ScanSeveritySample(
    val timestamp: Long,
    /** Ordinal on the `ConditionSeverity` scale — LOW 0 … CRITICAL 3. */
    val severityOrdinal: Int,
    /** Model confidence in that severity, 0..1. Values outside are clamped. */
    val confidence: Float,
)

/**
 * Compares recent scan severities against earlier ones and says which way things
 * are moving — conservatively.
 *
 * Three deliberate refusals to overclaim, because this output sits next to a
 * patient's name on a clinician's screen and will be read as more authoritative
 * than it is:
 *
 *  1. Under [PatientTrend.MIN_SCANS_FOR_DIRECTION] scans it returns STABLE and
 *     says outright that there is not enough history. It never guesses from two
 *     points.
 *  2. Movement below [PatientTrend.MEANINGFUL_DELTA] is STABLE. Phone photos
 *     under different lighting produce drift on their own.
 *  3. Every result carries [PatientTrend.DISCLAIMER]. There is no caller for
 *     whom presenting this as a diagnosis is correct.
 *
 * Pure function of its inputs — no clock, no repository — so each of those
 * boundaries is directly testable.
 */
class ComputeTrendUseCase @Inject constructor() {

    /**
     * @param samples in any order; sorted internally by timestamp.
     */
    operator fun invoke(
        patientUserId: String,
        samples: List<ScanSeveritySample>,
    ): PatientTrend {
        val sorted = samples.sortedBy { it.timestamp }
        val n = sorted.size

        if (n < PatientTrend.MIN_SCANS_FOR_DIRECTION) {
            return PatientTrend(
                patientUserId = patientUserId,
                direction = TrendDirection.STABLE,
                severityDelta = 0f,
                basisScans = n,
                explanation = notEnoughDataExplanation(n),
            )
        }

        // Split into an earlier and a recent half. With an odd count the middle
        // scan is excluded from both sides rather than assigned to one, so the
        // comparison stays symmetric and the direction cannot depend on which
        // half an arbitrary tie-breaker put it in.
        val half = n / 2
        val earlier = sorted.take(half)
        val recent = sorted.takeLast(half)

        val earlierMean = weightedMean(earlier)
        val recentMean = weightedMean(recent)

        // Null means every scan on one side had zero confidence — no usable
        // evidence, so no direction rather than a delta computed from nothing.
        if (earlierMean == null || recentMean == null) {
            return PatientTrend(
                patientUserId = patientUserId,
                direction = TrendDirection.STABLE,
                severityDelta = 0f,
                basisScans = n,
                explanation = "Recent scans were too low-confidence to compare. " +
                    PatientTrend.DISCLAIMER,
            )
        }

        val delta = recentMean - earlierMean
        val direction = when {
            abs(delta) < PatientTrend.MEANINGFUL_DELTA -> TrendDirection.STABLE
            delta > 0f -> TrendDirection.WORSENING
            else -> TrendDirection.IMPROVING
        }

        return PatientTrend(
            patientUserId = patientUserId,
            direction = direction,
            severityDelta = delta,
            basisScans = n,
            explanation = explain(direction, delta, half, n),
        )
    }

    /**
     * Confidence-weighted mean severity. Returns null when the weights sum to
     * zero, which is the one case a plain division would turn into NaN and quietly
     * propagate into a direction.
     */
    private fun weightedMean(samples: List<ScanSeveritySample>): Float? {
        var weighted = 0f
        var weight = 0f
        for (s in samples) {
            val w = s.confidence.coerceIn(0f, 1f)
            weighted += s.severityOrdinal * w
            weight += w
        }
        return if (weight <= 0f) null else weighted / weight
    }

    private fun notEnoughDataExplanation(count: Int): String {
        val have = when (count) {
            0 -> "No scans on record"
            1 -> "Only 1 scan on record"
            else -> "Only $count scans on record"
        }
        return "$have; at least ${PatientTrend.MIN_SCANS_FOR_DIRECTION} are needed " +
            "before a direction is shown. ${PatientTrend.DISCLAIMER}"
    }

    private fun explain(
        direction: TrendDirection,
        delta: Float,
        halfSize: Int,
        total: Int,
    ): String {
        val magnitude = oneDecimal(abs(delta))
        val window = "the latest $halfSize of $total scans against the earliest $halfSize"
        val body = when (direction) {
            TrendDirection.WORSENING ->
                "Severity ratings rose by $magnitude comparing $window."
            TrendDirection.IMPROVING ->
                "Severity ratings fell by $magnitude comparing $window."
            TrendDirection.STABLE ->
                "Severity ratings moved by $magnitude comparing $window, " +
                    "which is within normal variation between photos."
        }
        return "$body ${PatientTrend.DISCLAIMER}"
    }

    /** Avoids String.format so this stays free of locale-dependent decimal separators. */
    private fun oneDecimal(value: Float): String = (round(value * 10f) / 10f).toString()
}
