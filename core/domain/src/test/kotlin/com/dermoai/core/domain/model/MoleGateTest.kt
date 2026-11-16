package com.dermoai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mole gate's entire safety story is that it can only ever fire when the
 * head's own top call is already in [overridableCodes] -- a set fixed at
 * training time to exclude every malignant class. These cover that guard
 * directly, since a bug here would be the one that actually matters: letting
 * the gate relabel something the head already called malignant.
 */
class MoleGateTest {

    private fun gate(
        weights: FloatArray = floatArrayOf(1f, 0f, 0f),
        bias: Float = 0f,
        threshold: Float = 0.95f,
        overridableCodes: Set<String> = setOf("ACK", "SEK"),
    ) = MoleGate(weights, bias, threshold, overridableCodes)

    private fun condition(code: String, confidence: Float) =
        SkinCondition(label = code, code = code, confidence = confidence,
            severity = ConditionSeverity.LOW)

    // ── score ────────────────────────────────────────────────────────────────

    @Test
    fun `zero logit scores one half`() {
        assertEquals(0.5f, gate().score(floatArrayOf(0f, 0f, 0f)), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `feature length mismatch is rejected`() {
        gate().score(floatArrayOf(1f, 2f))
    }

    // ── applyTo: the guard that makes this safe ─────────────────────────────

    @Test
    fun `never fires when the head's top call is malignant`() {
        // A high score should be irrelevant here -- BCC is not in overridableCodes,
        // by construction, regardless of what the gate itself thinks.
        val ranked = listOf(condition("BCC", 0.80f), condition("NEV", 0.15f))
        val g = gate(weights = floatArrayOf(1000f, 0f, 0f))  // saturates near 1.0
        val out = g.applyTo(ranked, floatArrayOf(1000f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(ranked, out)
    }

    @Test
    fun `never fires when the head's top call is already healthy`() {
        val ranked = listOf(condition("Healthy", 0.90f), condition("NEV", 0.05f))
        val g = gate(weights = floatArrayOf(1000f, 0f, 0f))
        val out = g.applyTo(ranked, floatArrayOf(1000f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(ranked, out)
    }

    @Test
    fun `promotes NEV when top is an overridable code and the gate is confident`() {
        val ranked = listOf(condition("ACK", 0.49f), condition("NEV", 0.14f), condition("SEK", 0.09f))
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.95f)
        val out = g.applyTo(ranked, floatArrayOf(5f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals("NEV", out.first().code)
    }

    @Test
    fun `leaves the ranking untouched below threshold`() {
        val ranked = listOf(condition("ACK", 0.49f), condition("NEV", 0.14f))
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.95f)
        val out = g.applyTo(ranked, floatArrayOf(-5f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(ranked, out)
    }

    @Test
    fun `leaves the ranking untouched when top is not overridable`() {
        // Confident gate score, but SEK/ACK aren't the head's top call here.
        val ranked = listOf(condition("NEV", 0.60f), condition("ACK", 0.30f))
        val g = gate(weights = floatArrayOf(1000f, 0f, 0f))
        val out = g.applyTo(ranked, floatArrayOf(1000f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(ranked, out)
    }

    @Test
    fun `no entry outranks the promoted mole result`() {
        val ranked = listOf(condition("ACK", 0.60f), condition("NEV", 0.30f), condition("SEK", 0.10f))
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.95f)
        val out = g.applyTo(ranked, floatArrayOf(5f, 0f, 0f), "Nevus / Mole", "NEV")
        val top = out.first().confidence
        assertTrue(out.drop(1).all { it.confidence <= top + 1e-6f })
    }

    @Test
    fun `confidences still sum to one after the promotion`() {
        val ranked = listOf(condition("ACK", 0.60f), condition("NEV", 0.30f), condition("SEK", 0.10f))
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.95f)
        val out = g.applyTo(ranked, floatArrayOf(5f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(1f, out.sumOf { it.confidence.toDouble() }.toFloat(), 1e-5f)
    }

    @Test
    fun `an existing NEV entry is not duplicated`() {
        val ranked = listOf(condition("ACK", 0.60f), condition("NEV", 0.001f), condition("SEK", 0.10f))
        val g = gate(weights = floatArrayOf(1f, 0f, 0f), threshold = 0.95f)
        val out = g.applyTo(ranked, floatArrayOf(5f, 0f, 0f), "Nevus / Mole", "NEV")
        assertEquals(1, out.count { it.code == "NEV" })
        assertEquals(ranked.size, out.size)
    }

    @Test
    fun `empty ranking is returned unchanged`() {
        val g = gate()
        assertEquals(emptyList<SkinCondition>(), g.applyTo(emptyList(), floatArrayOf(0f, 0f, 0f), "Nevus / Mole", "NEV"))
    }

    // ── equality ─────────────────────────────────────────────────────────────

    @Test
    fun `gates with equal contents are equal`() {
        val a = MoleGate(floatArrayOf(1f, 2f), 0.5f, 0.95f, setOf("ACK", "SEK"))
        val b = MoleGate(floatArrayOf(1f, 2f), 0.5f, 0.95f, setOf("ACK", "SEK"))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `gates with different overridable codes are not equal`() {
        val a = MoleGate(floatArrayOf(1f, 2f), 0f, 0.95f, setOf("ACK", "SEK"))
        val b = MoleGate(floatArrayOf(1f, 2f), 0f, 0.95f, setOf("ACK"))
        assertFalse(a == b)
    }
}
