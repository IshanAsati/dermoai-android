package com.dermoai.feature.settings.demo

import com.dermoai.core.domain.model.ConditionSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure data-shape checks for [DemoDataPlan] — no Room, no Hilt, no Android.
 *
 * These exist to catch the two ways a demo-data tweak could quietly ruin the
 * walkthrough: too few patients/scans to look "populated", or a day range
 * that puts something in the future relative to "now" (which
 * `ComputeAdherenceUseCase` silently drops, producing a dashboard that looks
 * emptier than the data actually seeded).
 */
class DemoDataPlanTest {

    private val now = 1_754_524_800_000L // fixed instant; the plan doesn't read wall-clock time today

    @Test
    fun `doctor roster has enough patients to look populated but not overwhelming`() {
        val patients = DemoDataPlan.doctorPatients(now)
        assertTrue("expected 3-4 demo patients, got ${patients.size}", patients.size in 3..4)
    }

    @Test
    fun `doctor patients have distinct names and at least one scan each`() {
        val patients = DemoDataPlan.doctorPatients(now)
        assertEquals(patients.size, patients.map { it.displayName }.distinct().size)
        patients.forEach { patient ->
            assertTrue("${patient.displayName} has no scans", patient.scans.isNotEmpty())
        }
    }

    @Test
    fun `doctor roster is differentiated, not four identical rows`() {
        val patients = DemoDataPlan.doctorPatients(now)
        val latestSeverities = patients.map { it.scans.minBy { s -> s.daysAgo }.predictions.first().severity }
        assertTrue(
            "expected more than one distinct latest severity across the roster, got $latestSeverities",
            latestSeverities.distinct().size > 1,
        )
    }

    @Test
    fun `at least one doctor patient has enough scans for a confident trend`() {
        // ComputeTrendUseCase.MIN_SCANS_FOR_DIRECTION is 3; without at least one
        // patient meeting that, the triage demo never shows a WORSENING/IMPROVING
        // chip at all.
        val patients = DemoDataPlan.doctorPatients(now)
        assertTrue(patients.any { it.scans.size >= 3 })
    }

    @Test
    fun `no scan is captured in the future`() {
        val patients = DemoDataPlan.doctorPatients(now)
        val allScans = patients.flatMap { it.scans } + DemoDataPlan.patientTimeline(now)
        allScans.forEach { scan ->
            assertTrue("scan ${scan.bodyArea} has a negative daysAgo (${scan.daysAgo})", scan.daysAgo >= 0)
        }
    }

    @Test
    fun `patient timeline is rich and spread over roughly two to three months`() {
        val timeline = DemoDataPlan.patientTimeline(now)
        assertTrue("expected at least 15 scans for a rich timeline, got ${timeline.size}", timeline.size >= 15)
        val spanDays = timeline.maxOf { it.daysAgo } - timeline.minOf { it.daysAgo }
        assertTrue("expected the timeline to span 60-100 days, spanned $spanDays", spanDays in 60..100)
    }

    @Test
    fun `patient timeline is mostly benign with a small number flagged higher`() {
        val timeline = DemoDataPlan.patientTimeline(now)
        val topSeverities = timeline.map { it.predictions.first().severity }
        val flagged = topSeverities.count { it >= ConditionSeverity.MEDIUM }
        assertTrue("expected 1-6 elevated findings out of ${topSeverities.size}, got $flagged", flagged in 1..6)
        assertTrue(
            "expected the majority of scans to be LOW severity",
            topSeverities.count { it == ConditionSeverity.LOW } > timeline.size / 2,
        )
    }

    @Test
    fun `every scan has at least one prediction and predictions are rank-ordered by confidence`() {
        val patients = DemoDataPlan.doctorPatients(now)
        val allScans = patients.flatMap { it.scans } + DemoDataPlan.patientTimeline(now)
        allScans.forEach { scan ->
            assertTrue(scan.predictions.isNotEmpty())
            val confidences = scan.predictions.map { it.confidence }
            assertEquals(confidences.sortedDescending(), confidences)
        }
    }
}
