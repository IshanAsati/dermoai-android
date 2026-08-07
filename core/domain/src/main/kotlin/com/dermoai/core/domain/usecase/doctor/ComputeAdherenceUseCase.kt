package com.dermoai.core.domain.usecase.doctor

import com.dermoai.core.domain.model.PatientAdherence
import javax.inject.Inject
import kotlin.math.max

/**
 * Turns a patient's raw scan timestamps into the adherence figures the doctor
 * dashboard renders.
 *
 * Pure arithmetic on Longs with the clock injected as a parameter — no
 * repository, no coroutine. Adherence is entirely a function of (timestamps,
 * cadence, now), and keeping it that way is what makes every boundary below
 * testable without a database or a fake clock class.
 */
class ComputeAdherenceUseCase @Inject constructor() {

    /**
     * @param scanTimestamps epoch millis, in any order. Duplicates are counted as
     *   given; de-duplication belongs to whoever owns the scan table.
     * @param expectedCadenceDays how often this patient is meant to scan. Coerced
     *   to at least 1 — a zero cadence would divide by zero and a negative one is
     *   meaningless, and neither should crash a dashboard.
     * @param now injected so tests and any future server-clock source can drive it.
     */
    operator fun invoke(
        patientUserId: String,
        scanTimestamps: List<Long>,
        expectedCadenceDays: Int,
        now: Long = System.currentTimeMillis(),
    ): PatientAdherence {
        val cadence = expectedCadenceDays.coerceAtLeast(1)
        if (scanTimestamps.isEmpty()) {
            return PatientAdherence.none(patientUserId, cadence)
        }

        // Future-dated scans are dropped rather than trusted: a device with a
        // skewed clock would otherwise inflate the window count and hide a
        // patient who has actually stopped scanning.
        val sorted = scanTimestamps.filter { it <= now }.sorted()
        if (sorted.isEmpty()) {
            return PatientAdherence.none(patientUserId, cadence)
        }

        val windowStart = now - PatientAdherence.WINDOW_DAYS * DAY_MS
        val inWindow = sorted.count { it >= windowStart }

        // Expected scans over the window. Fractional on purpose: a 5-day cadence
        // over 14 days is 2.8, and rounding it to 3 would make a perfectly
        // compliant patient look 93% adherent forever.
        val expectedInWindow = PatientAdherence.WINDOW_DAYS.toFloat() / cadence.toFloat()
        val ratio = (inWindow / max(expectedInWindow, MIN_EXPECTED)).coerceIn(0f, 1f)

        return PatientAdherence(
            patientUserId = patientUserId,
            scansLast14Days = inWindow,
            expectedCadenceDays = cadence,
            streakDays = streakDays(sorted, cadence, now),
            lastScanAt = sorted.last(),
            adherenceRatio = ratio,
        )
    }

    /**
     * Days spanned by the current unbroken run of on-cadence scans.
     *
     * "Unbroken" allows [GRACE_DAYS] on top of the cadence, because a patient who
     * scans on day 8 of a weekly cadence has not lapsed, they were busy on
     * Tuesday — and a streak that snaps on a few hours of slippage is a streak
     * nobody can keep, which makes it useless as an engagement signal.
     *
     * Returns 0 when the newest scan is itself already past due: the run has to
     * reach the present to count.
     */
    private fun streakDays(sortedAscending: List<Long>, cadence: Int, now: Long): Int {
        val allowedGap = (cadence + GRACE_DAYS) * DAY_MS
        val newest = sortedAscending.last()
        if (now - newest > allowedGap) return 0

        var runStart = newest
        for (i in sortedAscending.lastIndex downTo 1) {
            val later = sortedAscending[i]
            val earlier = sortedAscending[i - 1]
            if (later - earlier > allowedGap) break
            runStart = earlier
        }
        // +1 so a single on-time scan reads as a 1-day streak rather than 0,
        // which would be indistinguishable from having lapsed.
        return ((newest - runStart) / DAY_MS).toInt() + 1
    }

    private companion object {
        const val DAY_MS: Long = 24L * 60 * 60 * 1000

        /** Slack allowed on top of the cadence before a streak is considered broken. */
        const val GRACE_DAYS: Int = 1

        /**
         * Floor on the divisor. A cadence longer than the window (say monthly)
         * yields an expectation below 1, and dividing by it would let a single
         * scan report a ratio far above 1 before clamping — this keeps the
         * underlying number honest instead of relying on the clamp.
         */
        const val MIN_EXPECTED: Float = 1f
    }
}
