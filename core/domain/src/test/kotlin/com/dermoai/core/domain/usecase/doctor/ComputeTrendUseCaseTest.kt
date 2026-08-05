package com.dermoai.core.domain.usecase.doctor

import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This use case decides whether a doctor sees "WORSENING" beside a patient's
 * name, so the failures worth guarding are the ones that overclaim: a direction
 * asserted from two photos, lighting noise dressed up as a trend, a NaN from
 * zero-confidence scans slipping through as a comparison, and any result that
 * loses the not-a-diagnosis wording on its way to the screen.
 */
class ComputeTrendUseCaseTest {

    private val useCase = ComputeTrendUseCase()
    private val patient = "patient-1"
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    /** Severity ordinals follow ConditionSeverity: LOW 0, MEDIUM 1, HIGH 2, CRITICAL 3. */
    private fun sample(dayIndex: Int, severity: Int, confidence: Float = 1f) =
        ScanSeveritySample(t0 + dayIndex * day, severity, confidence)

    private fun compute(samples: List<ScanSeveritySample>) = useCase(patient, samples)

    // ── not enough history ──────────────────────────────────────────────────

    @Test
    fun `no scans yields stable with zero basis`() {
        val result = compute(emptyList())
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(0, result.basisScans)
        assertEquals(0f, result.severityDelta, 0f)
    }

    @Test
    fun `one scan cannot claim a direction`() {
        val result = compute(listOf(sample(0, 3)))
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(1, result.basisScans)
        assertEquals(0f, result.severityDelta, 0f)
    }

    @Test
    fun `two scans cannot claim a direction however extreme`() {
        // The core overclaim guard: LOW then CRITICAL is a line through two points
        // and would flip on a single change of lighting. It must not read WORSENING.
        val result = compute(listOf(sample(0, 0), sample(7, 3)))
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(0f, result.severityDelta, 0f)
        assertFalse("two scans is not a confident basis", result.isConfident)
    }

    @Test
    fun `the insufficient-data explanation says so in words`() {
        // A bare STABLE chip reads as reassurance; the UI needs to be able to say
        // "not enough data" instead, which means the string has to carry it.
        val result = compute(listOf(sample(0, 1), sample(3, 1)))
        assertTrue(
            "explanation must state the shortfall: ${result.explanation}",
            result.explanation.contains("at least 3"),
        )
        assertTrue(result.explanation.contains(PatientTrend.DISCLAIMER))
    }

    @Test
    fun `explanation reads naturally at each small count`() {
        assertTrue(compute(emptyList()).explanation.startsWith("No scans on record"))
        assertTrue(compute(listOf(sample(0, 1))).explanation.startsWith("Only 1 scan on record"))
        assertTrue(
            compute(listOf(sample(0, 1), sample(1, 1))).explanation
                .startsWith("Only 2 scans on record"),
        )
    }

    @Test
    fun `exactly three scans is the first confident basis`() {
        // The documented threshold. Three is where a middle observation can
        // contradict the endpoints, so it is where a direction becomes allowable.
        val result = compute(listOf(sample(0, 0), sample(3, 1), sample(6, 3)))
        assertEquals(3, result.basisScans)
        assertTrue(result.isConfident)
        assertEquals(TrendDirection.WORSENING, result.direction)
    }

    // ── direction ───────────────────────────────────────────────────────────

    @Test
    fun `rising severity is worsening with a positive delta`() {
        val result = compute(
            listOf(sample(0, 0), sample(2, 0), sample(4, 1), sample(6, 2), sample(8, 3)),
        )
        assertEquals(TrendDirection.WORSENING, result.direction)
        assertTrue("delta must be positive when severity rises", result.severityDelta > 0f)
    }

    @Test
    fun `falling severity is improving with a negative delta`() {
        // The sign convention matters: a UI that colours on the sign would show
        // green for deterioration if this were inverted.
        val result = compute(
            listOf(sample(0, 3), sample(2, 3), sample(4, 2), sample(6, 1), sample(8, 0)),
        )
        assertEquals(TrendDirection.IMPROVING, result.direction)
        assertTrue("delta must be negative when severity falls", result.severityDelta < 0f)
    }

    @Test
    fun `identical severities are stable with a zero delta`() {
        val result = compute(List(6) { sample(it, 2) })
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(0f, result.severityDelta, 1e-6f)
    }

    @Test
    fun `a tie between the halves is stable`() {
        // Earlier mean 1.5, recent mean 1.5 by different routes. Any tie-break
        // that leaned one way would invent a direction out of symmetry.
        val result = compute(
            listOf(sample(0, 1), sample(1, 2), sample(2, 2), sample(3, 1)),
        )
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(0f, result.severityDelta, 1e-6f)
    }

    @Test
    fun `movement below the meaningful threshold stays stable`() {
        // Earlier mean 1.0, recent mean 1.33: a 0.33 drift, which is the sort of
        // wobble two photos of the same lesion produce under different light.
        val result = compute(
            listOf(
                sample(0, 1), sample(1, 1), sample(2, 1),
                sample(3, 1), sample(4, 1), sample(5, 2),
            ),
        )
        assertTrue("delta should be small: ${result.severityDelta}", result.severityDelta < 0.5f)
        assertEquals(TrendDirection.STABLE, result.direction)
    }

    @Test
    fun `movement at the meaningful threshold is reported`() {
        // Earlier mean 1.0, recent mean 1.5 — exactly MEANINGFUL_DELTA, which must
        // count. A strict comparison here would swallow half-band drifts forever.
        val result = compute(
            listOf(sample(0, 1), sample(1, 1), sample(2, 1), sample(3, 2)),
        )
        assertEquals(0.5f, result.severityDelta, 1e-6f)
        assertEquals(TrendDirection.WORSENING, result.direction)
    }

    // ── windowing ───────────────────────────────────────────────────────────

    @Test
    fun `unsorted input is compared chronologically`() {
        // Feeding the list in DESC order (which is how every DAO here returns it)
        // must not reverse the verdict.
        val ascending = listOf(sample(0, 0), sample(1, 0), sample(2, 3), sample(3, 3))
        val descending = ascending.reversed()
        assertEquals(compute(ascending), compute(descending))
        assertEquals(TrendDirection.WORSENING, compute(descending).direction)
    }

    @Test
    fun `the middle scan of an odd run belongs to neither half`() {
        // With 5 scans the halves are the first 2 and the last 2. The middle value
        // is deliberately excluded, so changing it alone must not move the result.
        val low = compute(
            listOf(sample(0, 0), sample(1, 0), sample(2, 0), sample(3, 3), sample(4, 3)),
        )
        val high = compute(
            listOf(sample(0, 0), sample(1, 0), sample(2, 3), sample(3, 3), sample(4, 3)),
        )
        assertEquals(low.severityDelta, high.severityDelta, 1e-6f)
        assertEquals(low.direction, high.direction)
    }

    @Test
    fun `basisScans reports every scan supplied not just the compared ones`() {
        // The reader discounts the trend by this number, so it must describe the
        // history they have, not the internal window size.
        val result = compute(List(7) { sample(it, it / 3) })
        assertEquals(7, result.basisScans)
    }

    @Test
    fun `old history does not drown out a recent change`() {
        // Halves, not a global regression: twenty stable scans followed by twenty
        // severe ones must still read WORSENING.
        val samples = List(20) { sample(it, 0) } + List(20) { sample(20 + it, 3) }
        assertEquals(TrendDirection.WORSENING, compute(samples).direction)
    }

    // ── confidence weighting ────────────────────────────────────────────────

    @Test
    fun `low-confidence scans count for less`() {
        // A blurry 3 should not carry the same weight as a clear 3. Same ordinals,
        // different confidences, must produce different magnitudes.
        val confident = compute(
            listOf(sample(0, 0, 1f), sample(1, 0, 1f), sample(2, 3, 1f), sample(3, 3, 1f)),
        )
        val hedged = compute(
            listOf(sample(0, 0, 1f), sample(1, 0, 1f), sample(2, 3, 0.1f), sample(3, 0, 1f)),
        )
        assertTrue(confident.severityDelta > hedged.severityDelta)
    }

    @Test
    fun `zero confidence throughout does not produce NaN`() {
        // Weighted mean with zero weights is 0/0. Left unchecked it becomes NaN,
        // and NaN comparisons are all false, so it would silently fall through to
        // IMPROVING and show green.
        val result = compute(List(4) { sample(it, 3, 0f) })
        assertFalse("delta must not be NaN", result.severityDelta.isNaN())
        assertEquals(TrendDirection.STABLE, result.direction)
        assertTrue(result.explanation.contains("low-confidence"))
    }

    @Test
    fun `zero confidence on one half alone still refuses a direction`() {
        val result = compute(
            listOf(sample(0, 0, 0f), sample(1, 0, 0f), sample(2, 3, 1f), sample(3, 3, 1f)),
        )
        assertEquals(TrendDirection.STABLE, result.direction)
        assertEquals(0f, result.severityDelta, 0f)
    }

    @Test
    fun `out-of-range confidences are clamped instead of scaling the result`() {
        // A confidence of 5 would multiply one scan's influence fivefold and could
        // manufacture a delta larger than the severity scale itself.
        val result = compute(
            listOf(sample(0, 0, 1f), sample(1, 0, 1f), sample(2, 3, 5f), sample(3, 3, 5f)),
        )
        assertEquals(3f, result.severityDelta, 1e-6f)
    }

    @Test
    fun `negative confidence cannot invert a scan's contribution`() {
        val result = compute(
            listOf(sample(0, 0, 1f), sample(1, 0, 1f), sample(2, 3, -1f), sample(3, 3, 1f)),
        )
        assertTrue("delta stays within the severity scale", result.severityDelta in 0f..3f)
    }

    // ── the explanation ─────────────────────────────────────────────────────

    @Test
    fun `every result carries the not-a-diagnosis disclaimer`() {
        // The one invariant with no exceptions: there is no caller for whom
        // presenting this as clinical fact is correct.
        val cases = listOf(
            emptyList(),
            listOf(sample(0, 1)),
            listOf(sample(0, 0), sample(1, 1), sample(2, 3)),
            listOf(sample(0, 3), sample(1, 2), sample(2, 0)),
            List(4) { sample(it, 2, 0f) },
        )
        for (case in cases) {
            val result = compute(case)
            assertTrue(
                "missing disclaimer for ${case.size} scans: ${result.explanation}",
                result.explanation.contains(PatientTrend.DISCLAIMER),
            )
        }
    }

    @Test
    fun `the explanation names the direction it is justifying`() {
        assertTrue(
            compute(listOf(sample(0, 0), sample(1, 3), sample(2, 3), sample(3, 3)))
                .explanation.contains("rose"),
        )
        assertTrue(
            compute(listOf(sample(0, 3), sample(1, 3), sample(2, 0), sample(3, 0)))
                .explanation.contains("fell"),
        )
        assertTrue(
            compute(List(4) { sample(it, 1) }).explanation.contains("within normal variation"),
        )
    }

    @Test
    fun `the explanation states how many scans were compared`() {
        val result = compute(List(6) { sample(it, if (it < 3) 0 else 3) })
        assertTrue(
            "explanation should show the window: ${result.explanation}",
            result.explanation.contains("latest 3 of 6"),
        )
    }

    @Test
    fun `the magnitude is rendered to one decimal without locale drift`() {
        // String.format would emit "1,5" under a comma-decimal locale and make the
        // sentence unparseable to anyone reading it.
        val result = compute(listOf(sample(0, 0), sample(1, 0), sample(2, 3), sample(3, 3)))
        assertTrue(
            "expected a dot-decimal magnitude: ${result.explanation}",
            result.explanation.contains("3.0"),
        )
    }

    @Test
    fun `the explanation is never empty`() {
        assertTrue(compute(emptyList()).explanation.isNotBlank())
    }

    // ── determinism ─────────────────────────────────────────────────────────

    @Test
    fun `the same samples always produce the same trend`() {
        val samples = listOf(sample(0, 1, 0.8f), sample(2, 2, 0.6f), sample(4, 2, 0.9f))
        assertEquals(compute(samples), compute(samples))
    }
}
