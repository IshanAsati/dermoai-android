package com.dermoai.feature.doctor.triage

import com.dermoai.core.domain.model.AdherenceBand
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.PatientAdherence
import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.model.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ordering is the most valuable thing in this feature to test: it decides
 * which patient a clinician looks at first, and every failure mode is silent —
 * a wrong order still renders a perfectly plausible list.
 *
 * The failures these guard against, specifically: an engagement signal
 * (INACTIVE) outranking a clinical one (CRITICAL); a two-photo "worsening"
 * built on noise jumping the queue ahead of a real one; and a comparator that
 * reshuffles equal patients on every recomposition so the row a doctor was
 * about to tap moves.
 */
class TriageRankingTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun adherenceOf(band: AdherenceBand): PatientAdherence = when (band) {
        AdherenceBand.GOOD -> PatientAdherence("p", 2, 7, 14, now, 1f)
        AdherenceBand.SLIPPING -> PatientAdherence("p", 1, 7, 0, now - 9 * day, 0.5f)
        AdherenceBand.INACTIVE -> PatientAdherence("p", 0, 7, 0, null, 0f)
    }

    private fun trendOf(
        direction: TrendDirection,
        basisScans: Int = PatientTrend.MIN_SCANS_FOR_DIRECTION + 3,
    ) = PatientTrend("p", direction, 0f, basisScans, "explanation")

    private fun row(
        name: String,
        severity: ConditionSeverity? = null,
        direction: TrendDirection = TrendDirection.STABLE,
        basisScans: Int = PatientTrend.MIN_SCANS_FOR_DIRECTION + 3,
        band: AdherenceBand = AdherenceBand.GOOD,
        lastScanAt: Long? = now,
    ) = TriageRow(
        patientUserId = "user-$name",
        displayName = name,
        linkId = "link-$name",
        latestSeverity = severity,
        latestFinding = severity?.name,
        lastScanAt = lastScanAt,
        adherence = adherenceOf(band),
        trend = trendOf(direction, basisScans),
    )

    private fun names(vararg rows: TriageRow): List<String> =
        TriageRanking.rank(rows.toList()).map { it.displayName }

    // ── tier 1: severity beats everything ────────────────────────────────────

    @Test
    fun `a critical finding outranks a worsening trend`() {
        // The headline failure: if trend or adherence could outrank severity,
        // the one patient with a CRITICAL image sinks below someone whose
        // severities merely drifted upward.
        val critical = row("Critical", ConditionSeverity.CRITICAL, TrendDirection.IMPROVING)
        val worsening = row("Worsening", ConditionSeverity.LOW, TrendDirection.WORSENING)
        assertEquals(listOf("Critical", "Worsening"), names(worsening, critical))
    }

    @Test
    fun `a critical finding outranks an inactive patient`() {
        // Engagement must never outrank clinical evidence. An INACTIVE patient
        // is someone to chase, not someone to treat first.
        val critical = row("Critical", ConditionSeverity.CRITICAL, band = AdherenceBand.GOOD)
        val inactive = row("Inactive", ConditionSeverity.LOW, band = AdherenceBand.INACTIVE)
        assertEquals(listOf("Critical", "Inactive"), names(inactive, critical))
    }

    @Test
    fun `a patient with no scans sorts below one with even a low finding`() {
        // Null severity must weigh less than LOW, not tie with it — "no
        // evidence" is not the same claim as "evidence of something mild".
        val noScans = row("NoScans", severity = null, lastScanAt = null, band = AdherenceBand.INACTIVE)
        val low = row("Low", ConditionSeverity.LOW)
        assertEquals(listOf("Low", "NoScans"), names(noScans, low))
    }

    // ── tier 2: trend ────────────────────────────────────────────────────────

    @Test
    fun `at equal severity a worsening patient outranks a stable one`() {
        val worsening = row("Worsening", ConditionSeverity.MEDIUM, TrendDirection.WORSENING)
        val stable = row("Stable", ConditionSeverity.MEDIUM, TrendDirection.STABLE)
        assertEquals(listOf("Worsening", "Stable"), names(stable, worsening))
    }

    @Test
    fun `an unconfident worsening trend does not outrank a confident one`() {
        // Below MIN_SCANS_FOR_DIRECTION the direction is noise. Ranking on it
        // would put a patient with two lighting-varied photos above a patient
        // with a real upward run.
        val noisy = row(
            "Noisy",
            ConditionSeverity.MEDIUM,
            TrendDirection.WORSENING,
            basisScans = PatientTrend.MIN_SCANS_FOR_DIRECTION - 1,
        )
        val real = row("Real", ConditionSeverity.MEDIUM, TrendDirection.WORSENING)
        assertEquals(listOf("Real", "Noisy"), names(noisy, real))
    }

    @Test
    fun `an improving patient sorts below a stable one at equal severity`() {
        val improving = row("Improving", ConditionSeverity.HIGH, TrendDirection.IMPROVING)
        val stable = row("Stable", ConditionSeverity.HIGH, TrendDirection.STABLE)
        assertEquals(listOf("Stable", "Improving"), names(improving, stable))
    }

    // ── tier 3: adherence ────────────────────────────────────────────────────

    @Test
    fun `an inactive patient rises only among patients equal on severity and trend`() {
        // INACTIVE must rise here and nowhere else. If this passes but the
        // severity tests fail, the tiers are in the wrong order.
        val inactive = row("Inactive", ConditionSeverity.MEDIUM, band = AdherenceBand.INACTIVE)
        val slipping = row("Slipping", ConditionSeverity.MEDIUM, band = AdherenceBand.SLIPPING)
        val good = row("Good", ConditionSeverity.MEDIUM, band = AdherenceBand.GOOD)
        assertEquals(
            listOf("Inactive", "Slipping", "Good"),
            names(good, slipping, inactive),
        )
    }

    // ── determinism ──────────────────────────────────────────────────────────

    @Test
    fun `patients equal on every signal are ordered by recency then name`() {
        // A comparator that leaves equals unordered reshuffles the list on every
        // recompute, and the row a doctor was reaching for moves under their
        // finger.
        val older = row("Zoe", ConditionSeverity.LOW, lastScanAt = now - 5 * day)
        val newerA = row("Anna", ConditionSeverity.LOW, lastScanAt = now)
        val newerZ = row("Zach", ConditionSeverity.LOW, lastScanAt = now)
        assertEquals(
            listOf("Anna", "Zach", "Zoe"),
            names(older, newerZ, newerA),
        )
        // And the same input in a different order produces the same output.
        assertEquals(
            listOf("Anna", "Zach", "Zoe"),
            names(newerA, older, newerZ),
        )
    }

    @Test
    fun `a full mixed inbox comes out in the documented tier order`() {
        // End-to-end check of all three tiers interacting, which is the thing a
        // per-tier test cannot catch on its own.
        val ranked = names(
            row("OnTrack", ConditionSeverity.LOW, band = AdherenceBand.GOOD),
            row("Silent", ConditionSeverity.LOW, band = AdherenceBand.INACTIVE),
            row("Drifting", ConditionSeverity.LOW, TrendDirection.WORSENING),
            row("Severe", ConditionSeverity.CRITICAL, TrendDirection.IMPROVING, band = AdherenceBand.GOOD),
            row("Watch", ConditionSeverity.HIGH),
            row("Never", severity = null, lastScanAt = null, band = AdherenceBand.INACTIVE),
        )
        assertEquals(
            listOf("Severe", "Watch", "Drifting", "Silent", "OnTrack", "Never"),
            ranked,
        )
    }
}
