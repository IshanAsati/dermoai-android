package com.dermoai.core.domain.model

/**
 * How consistently a patient has been scanning. Derived from scan timestamps on
 * every read — deliberately **not** a table.
 *
 * Persisting this would mean it goes stale the moment the clock moves: a patient
 * who was GOOD yesterday and has not scanned since is still GOOD in the row but
 * INACTIVE in reality, and a doctor's dashboard would show the wrong thing until
 * something happened to recompute it. Recomputing from the scan list is cheap
 * (a count and a couple of subtractions) and can never be stale.
 *
 * Produced by `usecase.doctor.ComputeAdherenceUseCase`.
 */
data class PatientAdherence(
    val patientUserId: String,
    val scansLast14Days: Int,
    /** The cadence the patient is being measured against, in days. Always >= 1. */
    val expectedCadenceDays: Int,
    /**
     * Length of the current unbroken run of on-cadence scans, in days. Zero when
     * the most recent scan is already overdue — a streak you have broken is not
     * a streak.
     */
    val streakDays: Int,
    val lastScanAt: Long? = null,
    /**
     * Scans done over scans expected in the 14-day window, clamped to 0..1.
     *
     * Clamped because the UI draws this as a progress bar, and because scanning
     * five times a day is anxiety, not adherence — it should not read as 400%
     * compliant.
     */
    val adherenceRatio: Float = 0f,
) {
    /**
     * The coarse bucket the dashboard actually renders. Three bands, not a
     * number, because a doctor scanning a list needs "who has stopped" at a
     * glance and a percentage invites false precision about a wellness metric.
     */
    val band: AdherenceBand
        get() = when {
            scansLast14Days == 0 -> AdherenceBand.INACTIVE
            adherenceRatio >= GOOD_RATIO -> AdherenceBand.GOOD
            else -> AdherenceBand.SLIPPING
        }

    companion object {
        /** The window every dashboard figure is computed over. */
        const val WINDOW_DAYS: Int = 14

        /**
         * Not 1.0. Requiring a perfect record to show GOOD would paint almost
         * everyone amber and train doctors to ignore the colour entirely.
         */
        const val GOOD_RATIO: Float = 0.75f

        /** An empty-history patient, so callers never build a half-filled one by hand. */
        fun none(patientUserId: String, expectedCadenceDays: Int): PatientAdherence =
            PatientAdherence(
                patientUserId = patientUserId,
                scansLast14Days = 0,
                expectedCadenceDays = expectedCadenceDays.coerceAtLeast(1),
                streakDays = 0,
                lastScanAt = null,
                adherenceRatio = 0f,
            )
    }
}

enum class AdherenceBand {
    GOOD,
    SLIPPING,
    INACTIVE,
}
