package com.dermoai.core.domain.model

/**
 * Which way a patient's scan severities have been moving. Derived, never stored,
 * for the same staleness reason as [PatientAdherence].
 *
 * The [explanation] is not decoration. This number comes from a classifier
 * operating on phone photos under uncontrolled lighting, so a bare "WORSENING"
 * chip next to a patient's name would be read as a clinical finding it is not
 * entitled to be. Every instance carries, in words, what it was computed from
 * and what it is not.
 *
 * Produced by `usecase.doctor.ComputeTrendUseCase`.
 */
data class PatientTrend(
    val patientUserId: String,
    val direction: TrendDirection,
    /**
     * Change in confidence-weighted severity ordinal, recent window minus earlier
     * window. Positive means severities went up. Roughly "steps on the
     * [ConditionSeverity] scale", so 1.0 is a whole band — but it is a weighted
     * mean, so treat it as a magnitude hint, not a measurement.
     */
    val severityDelta: Float,
    /** How many scans went into the comparison. Shown so the reader can discount it. */
    val basisScans: Int,
    /** Plain-language, non-diagnostic account of the above. Always non-empty. */
    val explanation: String,
) {
    /**
     * Whether there was enough history to say anything directional at all.
     * The UI should render an unconfident trend as "not enough data" rather than
     * as a reassuring STABLE.
     */
    val isConfident: Boolean
        get() = basisScans >= MIN_SCANS_FOR_DIRECTION

    companion object {
        /**
         * Two scans is a line through two points and would flip direction on any
         * lighting change. Three is still weak, but it is the first count where a
         * middle observation can contradict the endpoints.
         */
        const val MIN_SCANS_FOR_DIRECTION: Int = 3

        /**
         * Severity movement smaller than this reads as STABLE. Set below a full
         * band because a consistent half-band drift across several scans is worth
         * surfacing, and above zero because float noise is not a trend.
         */
        const val MEANINGFUL_DELTA: Float = 0.5f

        /**
         * Appended to every explanation. Non-optional: there is no caller for whom
         * it is correct to present this as a diagnosis.
         */
        const val DISCLAIMER: String =
            "This is a tracking signal from photos, not a diagnosis."
    }
}

enum class TrendDirection {
    IMPROVING,
    STABLE,
    WORSENING,
}
