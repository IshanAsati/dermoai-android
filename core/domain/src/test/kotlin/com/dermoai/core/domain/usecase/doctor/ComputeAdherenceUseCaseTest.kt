package com.dermoai.core.domain.usecase.doctor

import com.dermoai.core.domain.model.AdherenceBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adherence is the number a doctor uses to decide who to chase, so the failures
 * that matter are the ones that make a patient look fine when they have stopped
 * scanning — an empty history that lands anywhere but INACTIVE, a stale streak
 * that never breaks, or a future-dated scan from a skewed device clock padding
 * the window count.
 */
class ComputeAdherenceUseCaseTest {

    private val useCase = ComputeAdherenceUseCase()
    private val patient = "patient-1"

    /** Fixed "now" so no test depends on the wall clock. */
    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun daysAgo(n: Int): Long = now - n * day

    private fun compute(
        timestamps: List<Long>,
        cadence: Int = 7,
        at: Long = now,
    ) = useCase(patient, timestamps, cadence, at)

    // ── empty and degenerate input ───────────────────────────────────────────

    @Test
    fun `no scans is inactive with a zero ratio`() {
        // The case a dashboard must never render as healthy.
        val result = compute(emptyList())
        assertEquals(0, result.scansLast14Days)
        assertEquals(0f, result.adherenceRatio, 0f)
        assertEquals(0, result.streakDays)
        assertNull(result.lastScanAt)
        assertEquals(AdherenceBand.INACTIVE, result.band)
    }

    @Test
    fun `no scans still echoes the patient and cadence back`() {
        // The empty path is a separate return; it used to be easy to lose the
        // caller's cadence there and report a default the UI then labelled wrongly.
        val result = compute(emptyList(), cadence = 3)
        assertEquals(patient, result.patientUserId)
        assertEquals(3, result.expectedCadenceDays)
    }

    @Test
    fun `zero cadence does not divide by zero`() {
        // A cadence of 0 arriving from a bad config would otherwise be Infinity
        // or NaN and poison every comparison downstream.
        val result = compute(listOf(daysAgo(1)), cadence = 0)
        assertEquals(1, result.expectedCadenceDays)
        assertTrue(result.adherenceRatio.isFinite())
    }

    @Test
    fun `negative cadence is coerced rather than inverting the ratio`() {
        val result = compute(listOf(daysAgo(1)), cadence = -5)
        assertEquals(1, result.expectedCadenceDays)
        assertTrue(result.adherenceRatio in 0f..1f)
    }

    // ── the 14-day window ───────────────────────────────────────────────────

    @Test
    fun `only scans inside the window are counted`() {
        val result = compute(listOf(daysAgo(1), daysAgo(13), daysAgo(20), daysAgo(60)))
        assertEquals(2, result.scansLast14Days)
    }

    @Test
    fun `a scan exactly 14 days old is inside the window`() {
        // Off-by-one at the boundary silently drops a scan and nudges someone
        // from GOOD to SLIPPING for no reason.
        val result = compute(listOf(now - 14 * day))
        assertEquals(1, result.scansLast14Days)
    }

    @Test
    fun `lastScanAt is the newest scan regardless of input order`() {
        val result = compute(listOf(daysAgo(30), daysAgo(2), daysAgo(9)))
        assertEquals(daysAgo(2), result.lastScanAt)
    }

    @Test
    fun `scans outside the window still count as history`() {
        // Nothing in the window, but the patient did scan once — the ratio is 0
        // and the band INACTIVE, yet lastScanAt must still be reported so the UI
        // can say "last scan 20 days ago" instead of "never".
        val result = compute(listOf(daysAgo(20)))
        assertEquals(0, result.scansLast14Days)
        assertEquals(AdherenceBand.INACTIVE, result.band)
        assertEquals(daysAgo(20), result.lastScanAt)
    }

    @Test
    fun `future timestamps are ignored`() {
        // A device with a clock set forward would otherwise report a full window
        // of adherence for a patient who has scanned once.
        val result = compute(listOf(now + 5 * day, now + 40 * day, daysAgo(1)))
        assertEquals(1, result.scansLast14Days)
        assertEquals(daysAgo(1), result.lastScanAt)
    }

    @Test
    fun `only future timestamps behaves like no scans`() {
        val result = compute(listOf(now + day, now + 2 * day))
        assertEquals(AdherenceBand.INACTIVE, result.band)
        assertNull(result.lastScanAt)
    }

    // ── the ratio ───────────────────────────────────────────────────────────

    @Test
    fun `meeting a weekly cadence reads as fully adherent`() {
        // 14 days / 7 = 2 expected, 2 done.
        val result = compute(listOf(daysAgo(1), daysAgo(8)), cadence = 7)
        assertEquals(1f, result.adherenceRatio, 1e-6f)
        assertEquals(AdherenceBand.GOOD, result.band)
    }

    @Test
    fun `half a weekly cadence reads as half adherent`() {
        val result = compute(listOf(daysAgo(1)), cadence = 7)
        assertEquals(0.5f, result.adherenceRatio, 1e-6f)
        assertEquals(AdherenceBand.SLIPPING, result.band)
    }

    @Test
    fun `over-scanning is clamped to one`() {
        // Ten scans in a fortnight on a weekly cadence is 500%, which would draw a
        // progress bar off the end of the screen and read as five times healthier
        // than a compliant patient.
        val result = compute((1..10).map { daysAgo(it) }, cadence = 7)
        assertEquals(1f, result.adherenceRatio, 1e-6f)
        assertEquals(AdherenceBand.GOOD, result.band)
    }

    @Test
    fun `a cadence longer than the window expects one scan not a fraction`() {
        // Monthly cadence means 0.47 expected scans over 14 days; dividing by that
        // would make a single scan read as 213% before clamping. The floor keeps
        // the underlying number meaningful rather than leaning on the clamp.
        val result = compute(listOf(daysAgo(3)), cadence = 30)
        assertEquals(1f, result.adherenceRatio, 1e-6f)
    }

    @Test
    fun `fractional expectations are not rounded against the patient`() {
        // 14 / 5 = 2.8 expected. Three scans is compliant; rounding 2.8 up to 3
        // and then comparing would leave a perfect patient permanently short.
        val result = compute(listOf(daysAgo(1), daysAgo(6), daysAgo(11)), cadence = 5)
        assertEquals(1f, result.adherenceRatio, 1e-6f)
    }

    // ── bands ───────────────────────────────────────────────────────────────

    @Test
    fun `band boundary at three quarters is inclusive`() {
        // 14 / 7 = 2 expected; 1.5 scans is impossible, so use a 2-day cadence:
        // 7 expected, 6 done => 0.857 (GOOD). 5 done => 0.714 (SLIPPING).
        val good = compute((1..6).map { daysAgo(it * 2) }, cadence = 2)
        assertEquals(AdherenceBand.GOOD, good.band)

        val slipping = compute((1..5).map { daysAgo(it * 2) }, cadence = 2)
        assertEquals(AdherenceBand.SLIPPING, slipping.band)
    }

    @Test
    fun `any scan in the window rules out inactive`() {
        // INACTIVE must mean "nothing at all", never merely "very low" — the two
        // prompt different clinical follow-ups.
        val result = compute((1..1).map { daysAgo(13) }, cadence = 1)
        assertTrue(result.adherenceRatio < 0.1f)
        assertEquals(AdherenceBand.SLIPPING, result.band)
    }

    // ── streaks ─────────────────────────────────────────────────────────────

    @Test
    fun `a single on-time scan is a one day streak`() {
        // Zero here would be indistinguishable from having lapsed.
        assertEquals(1, compute(listOf(daysAgo(1)), cadence = 7).streakDays)
    }

    @Test
    fun `an overdue newest scan breaks the streak`() {
        // The run has to reach the present. Without this a patient who scanned
        // religiously for a year and then stopped keeps a 365-day streak forever.
        val timestamps = (3..10).map { daysAgo(it * 7) }
        assertEquals(0, compute(timestamps, cadence = 7).streakDays)
    }

    @Test
    fun `consecutive on-cadence scans accumulate`() {
        // Weekly scans at 1, 8, 15, 22 days ago: an unbroken run spanning 21 days.
        val result = compute(listOf(daysAgo(1), daysAgo(8), daysAgo(15), daysAgo(22)), cadence = 7)
        assertEquals(22, result.streakDays)
    }

    @Test
    fun `a gap beyond the grace period ends the run`() {
        // 1, 8 then a jump to 40: only the recent pair counts.
        val result = compute(listOf(daysAgo(1), daysAgo(8), daysAgo(40)), cadence = 7)
        assertEquals(8, result.streakDays)
    }

    @Test
    fun `one day of slippage does not break the streak`() {
        // A weekly patient scanning on day 8 was busy on Tuesday, not lapsed. A
        // streak that snaps on a few hours is one nobody can hold, which makes it
        // useless as a signal.
        val result = compute(listOf(daysAgo(1), daysAgo(9)), cadence = 7)
        assertEquals(9, result.streakDays)
    }

    @Test
    fun `two days of slippage does break the streak`() {
        val result = compute(listOf(daysAgo(1), daysAgo(11)), cadence = 7)
        assertEquals(1, result.streakDays)
    }

    @Test
    fun `unsorted input produces the same streak as sorted input`() {
        val ordered = listOf(daysAgo(22), daysAgo(15), daysAgo(8), daysAgo(1))
        val shuffled = listOf(daysAgo(8), daysAgo(1), daysAgo(22), daysAgo(15))
        assertEquals(
            compute(ordered, cadence = 7).streakDays,
            compute(shuffled, cadence = 7).streakDays,
        )
    }

    @Test
    fun `duplicate timestamps do not break the streak walk`() {
        // Equal adjacent values give a zero gap, which must be treated as within
        // cadence rather than tripping any strict comparison.
        val result = compute(listOf(daysAgo(1), daysAgo(1), daysAgo(8)), cadence = 7)
        assertEquals(8, result.streakDays)
        assertEquals(3, result.scansLast14Days)
    }

    @Test
    fun `streak counts days spanned not scans taken`() {
        // Three scans in three days is a 3-day streak, not a 3-scan one; the two
        // diverge as soon as a patient scans twice in a day.
        val result = compute(listOf(daysAgo(0), daysAgo(1), daysAgo(1), daysAgo(2)), cadence = 1)
        assertEquals(3, result.streakDays)
    }

    // ── determinism ─────────────────────────────────────────────────────────

    @Test
    fun `the same inputs always produce the same result`() {
        // The clock is a parameter; nothing may reach for System.currentTimeMillis.
        val timestamps = listOf(daysAgo(1), daysAgo(5), daysAgo(12))
        assertEquals(compute(timestamps), compute(timestamps))
    }
}
