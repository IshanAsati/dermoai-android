package com.dermoai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The healthy/lesion gate is the only thing standing between a photo of normal
 * skin and the 12-class head confidently calling it carcinoma, and it is plain
 * arithmetic that no other test touches. These cover the two failures that
 * actually happened while building it: a silently wrong dot product, and a
 * ranking that showed entries above the top result.
 */
class HealthyGateTest {

    private fun gate(
        weights: FloatArray = floatArrayOf(1f, 0f, 0f),
        bias: Float = 0f,
        threshold: Float = 0.65f,
    ) = HealthyGate(weights, bias, threshold)

    private fun condition(code: String, confidence: Float) =
        SkinCondition(label = code, code = code, confidence = confidence,
            severity = ConditionSeverity.LOW)

    // ── score ────────────────────────────────────────────────────────────────

    @Test
    fun `zero logit scores one half`() {
        assertEquals(0.5f, gate().score(floatArrayOf(0f, 0f, 0f)), 1e-6f)
    }

    @Test
    fun `bias alone shifts the score`() {
        // sigmoid(2) = 0.880797...
        assertEquals(0.880797f, gate(bias = 2f).score(floatArrayOf(0f, 0f, 0f)), 1e-5f)
    }

    @Test
    fun `score is the dot product through a sigmoid`() {
        // (0.5*2) + (-1*3) + (2*1) + bias 0.5 = 0.5 -> sigmoid(0.5) = 0.622459
        val g = gate(weights = floatArrayOf(0.5f, -1f, 2f), bias = 0.5f)
        assertEquals(0.622459f, g.score(floatArrayOf(2f, 3f, 1f)), 1e-5f)
    }

    @Test
    fun `every weight contributes`() {
        // A dot product that skipped an element would still pass a test whose
        // features are all equal, so make each position distinguishable.
        val g = gate(weights = floatArrayOf(1f, 10f, 100f))
        val only3rd = g.score(floatArrayOf(0f, 0f, 1f))
        val only2nd = g.score(floatArrayOf(0f, 1f, 0f))
        val only1st = g.score(floatArrayOf(1f, 0f, 0f))
        assertTrue("third weight must dominate", only3rd > only2nd && only2nd > only1st)
    }

    @Test
    fun `score stays in range for extreme inputs`() {
        val g = gate(weights = floatArrayOf(1000f, 0f, 0f))
        val high = g.score(floatArrayOf(1000f, 0f, 0f))
        val low = g.score(floatArrayOf(-1000f, 0f, 0f))
        assertTrue("saturates to 1 without overflow", high in 0f..1f && high > 0.99f)
        assertTrue("saturates to 0 without NaN", low in 0f..1f && low < 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `feature length mismatch is rejected`() {
        // A silently truncated vector would quietly change every decision.
        gate().score(floatArrayOf(1f, 2f))
    }

    // ── threshold ────────────────────────────────────────────────────────────

    @Test
    fun `threshold is inclusive at the boundary`() {
        val g = gate(threshold = 0.5f)
        assertTrue(g.isHealthy(floatArrayOf(0f, 0f, 0f)))   // exactly 0.5
    }

    @Test
    fun `below threshold is not healthy`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        assertFalse(g.isHealthy(floatArrayOf(-1f, 0f, 0f)))  // sigmoid(-1) = 0.269
    }

    // ── applyTo: the override and rescale ────────────────────────────────────

    private val ranked = listOf(
        condition("NEV", 0.90f), condition("SEK", 0.07f), condition("MEL", 0.03f))

    @Test
    fun `gate below threshold leaves the ranking untouched`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val out = g.applyTo(ranked, floatArrayOf(-5f, 0f, 0f), "Healthy Skin", "Healthy")
        assertEquals(ranked, out)
    }

    @Test
    fun `gate above threshold puts healthy first`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val out = g.applyTo(ranked, floatArrayOf(5f, 0f, 0f), "Healthy Skin", "Healthy")
        assertEquals("Healthy", out.first().code)
        assertEquals(ConditionSeverity.LOW, out.first().severity)
    }

    @Test
    fun `no entry outranks the healthy result`() {
        // The bug this exists to catch: prepending Healthy at 97% while leaving a
        // 99% Nevus below it, so the list contradicts itself on screen.
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val out = g.applyTo(ranked, floatArrayOf(1f, 0f, 0f), "Healthy Skin", "Healthy")
        val top = out.first().confidence
        assertTrue("top must not be outranked",
            out.drop(1).all { it.confidence <= top + 1e-6f })
    }

    @Test
    fun `confidences still sum to one after the override`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val out = g.applyTo(ranked, floatArrayOf(1f, 0f, 0f), "Healthy Skin", "Healthy")
        assertEquals(1f, out.sumOf { it.confidence.toDouble() }.toFloat(), 1e-5f)
    }

    @Test
    fun `relative order of the head's classes is preserved`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val out = g.applyTo(ranked, floatArrayOf(1f, 0f, 0f), "Healthy Skin", "Healthy")
        assertEquals(listOf("Healthy", "NEV", "SEK", "MEL"), out.map { it.code })
    }

    @Test
    fun `an existing healthy entry is not duplicated`() {
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.65f)
        val withHealthy = ranked + condition("Healthy", 0.001f)
        val out = g.applyTo(withHealthy, floatArrayOf(1f, 0f, 0f), "Healthy Skin", "Healthy")
        assertEquals(1, out.count { it.code == "Healthy" })
        assertEquals(withHealthy.size, out.size)
    }

    // ── equality ─────────────────────────────────────────────────────────────

    @Test
    fun `gates with equal contents are equal`() {
        // FloatArray uses reference equality by default, so two gates loaded from
        // the same asset would otherwise compare unequal.
        val a = HealthyGate(floatArrayOf(1f, 2f), 0.5f, 0.65f)
        val b = HealthyGate(floatArrayOf(1f, 2f), 0.5f, 0.65f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `gates with different weights are not equal`() {
        assertFalse(HealthyGate(floatArrayOf(1f, 2f), 0f, 0.65f) ==
            HealthyGate(floatArrayOf(1f, 3f), 0f, 0.65f))
    }

    // ── the shipped gate ─────────────────────────────────────────────────────

    @Test
    fun `realistic 1024-wide gate behaves sanely`() {
        val rnd = java.util.Random(0)
        val w = FloatArray(1024) { (rnd.nextGaussian() * 0.05).toFloat() }
        val g = HealthyGate(w, -0.5f, 0.65f)
        val f = FloatArray(1024) { (rnd.nextGaussian()).toFloat() }
        val s = g.score(f)
        assertTrue("score must be a probability", s in 0f..1f && !s.isNaN())
        assertEquals("score must be deterministic", s, g.score(f), 0f)
        assertEquals("isHealthy must agree with score", s >= 0.65f, g.isHealthy(f))
    }

    @Test
    fun `threshold ordering is monotonic in the score`() {
        val w = floatArrayOf(1f, 0f, 0f)
        val strict = HealthyGate(w, 0f, 0.9f)
        val loose = HealthyGate(w, 0f, 0.6f)
        val f = floatArrayOf(1f, 0f, 0f)   // sigmoid(1) = 0.731
        assertFalse("0.731 < 0.9", strict.isHealthy(f))
        assertTrue("0.731 >= 0.6", loose.isHealthy(f))
        assertTrue(abs(strict.score(f) - loose.score(f)) < 1e-9f)
    }
}
