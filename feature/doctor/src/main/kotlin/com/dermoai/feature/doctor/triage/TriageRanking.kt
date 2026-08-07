package com.dermoai.feature.doctor.triage

import com.dermoai.core.domain.model.AdherenceBand
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.PatientAdherence
import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.model.TrendDirection

/**
 * One patient as the doctor's inbox needs them: identity, newest finding, and
 * the two derived signals.
 *
 * Built from domain types only — no Room entities — so the ordering below is a
 * pure function testable on the JVM without a database, which is the point of
 * splitting it out of the ViewModel at all.
 */
data class TriageRow(
    val patientUserId: String,
    val displayName: String,
    /** [com.dermoai.core.domain.model.PatientLink.id], carried so a revoke needs no second lookup. */
    val linkId: String,
    /** Severity of the *most recent* scan's top prediction. Null when there are no scans. */
    val latestSeverity: ConditionSeverity?,
    /** Human label of that top prediction, e.g. "Eczema". Null when there are no scans. */
    val latestFinding: String?,
    val lastScanAt: Long?,
    val adherence: PatientAdherence,
    val trend: PatientTrend,
)

/**
 * The order the doctor dashboard lists patients in.
 *
 * Alphabetical is the wrong default for an inbox whose job is "who needs me
 * first". A list sorted by name buries a CRITICAL finding under whoever is
 * called Adams, and the doctor learns to scroll rather than to trust the top of
 * the list. So the ordering is explicitly clinical, in this priority:
 *
 *  1. **Severity of the newest scan**, descending. A finding the model called
 *     CRITICAL outranks everything else — it is the only signal here derived
 *     from an actual image rather than from counting dates.
 *  2. **Trend**, WORSENING before STABLE before IMPROVING. Direction of travel
 *     beats consistency: someone getting worse at a low severity is a more
 *     useful call than someone stable at the same severity.
 *  3. **Adherence**, INACTIVE before SLIPPING before GOOD. Last, because "has
 *     stopped scanning" is an engagement problem, not a clinical one — it must
 *     never push a patient above someone with a real finding, but among
 *     otherwise-equal patients the silent one is the one worth chasing.
 *  4. Most recent scan first, then name, purely so the list is stable across
 *     recompositions rather than shuffling on every recompute.
 *
 * A patient with no scans at all sorts to the bottom of tier 1 and to the top
 * of tier 3 — low clinical urgency, high "chase them" urgency — which is the
 * honest reading of an empty record.
 */
object TriageRanking {

    /**
     * Higher is more urgent. Null (no scans) is 0 rather than
     * [ConditionSeverity.LOW]'s ordinal, so "no evidence" ranks below "evidence
     * of something mild" instead of tying with it.
     */
    fun severityWeight(severity: ConditionSeverity?): Int =
        severity?.let { it.ordinal + 1 } ?: 0

    /**
     * Higher is more urgent.
     *
     * An unconfident trend is forced to the STABLE weight regardless of its
     * direction: [PatientTrend.isConfident] is false when fewer than three
     * scans went into it, and letting a two-photo "WORSENING" outrank a
     * genuinely worsening patient would be ranking on noise.
     */
    fun trendWeight(trend: PatientTrend): Int =
        if (!trend.isConfident) STABLE_WEIGHT else when (trend.direction) {
            TrendDirection.WORSENING -> 2
            TrendDirection.STABLE -> STABLE_WEIGHT
            TrendDirection.IMPROVING -> 0
        }

    /** Higher is more urgent — silence ranks above slipping ranks above on-track. */
    fun adherenceWeight(band: AdherenceBand): Int = when (band) {
        AdherenceBand.INACTIVE -> 2
        AdherenceBand.SLIPPING -> 1
        AdherenceBand.GOOD -> 0
    }

    /**
     * Exposed rather than inlined into [rank] so tests can assert the pairwise
     * decision directly, which is where the interesting failures are.
     */
    val comparator: Comparator<TriageRow> =
        compareByDescending<TriageRow> { severityWeight(it.latestSeverity) }
            .thenByDescending { trendWeight(it.trend) }
            .thenByDescending { adherenceWeight(it.adherence.band) }
            // Freshest evidence first: among equals, the finding the doctor can
            // still act on today beats one from three weeks ago.
            .thenByDescending { it.lastScanAt ?: Long.MIN_VALUE }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.patientUserId }

    fun rank(rows: List<TriageRow>): List<TriageRow> = rows.sortedWith(comparator)

    private const val STABLE_WEIGHT = 1
}
