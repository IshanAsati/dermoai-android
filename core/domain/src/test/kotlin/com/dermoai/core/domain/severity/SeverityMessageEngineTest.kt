package com.dermoai.core.domain.severity

import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.SkinCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeverityMessageEngineTest {

    private val engine = SeverityMessageEngine()

    private fun condition(
        label: String = "Nevus/Mole",
        code: String = "NEV",
        confidence: Float = 0.87f,
        severity: ConditionSeverity = ConditionSeverity.LOW,
    ) = SkinCondition(label, code, confidence, severity)

    private fun result(
        top: SkinCondition,
        rest: List<SkinCondition> = emptyList(),
    ) = InferenceResult(topPrediction = top, allPredictions = listOf(top) + rest, heatmap = null, severityEstimate = 2)

    @Test
    fun `low severity maps to educational guidance and no doctor visit`() {
        val message = engine.build(result(condition(severity = ConditionSeverity.LOW)))
        assertEquals(SeverityGuidance.EDUCATIONAL, message.guidance)
        assertEquals(ConditionSeverity.LOW, message.tier)
        assertFalse(message.considerDoctor)
    }

    @Test
    fun `medium severity maps to monitor guidance`() {
        val message = engine.build(result(condition(severity = ConditionSeverity.MEDIUM)))
        assertEquals(SeverityGuidance.MONITOR, message.guidance)
        assertFalse(message.considerDoctor)
    }

    @Test
    fun `high severity maps to consult guidance and flags doctor visit`() {
        val message = engine.build(result(condition(severity = ConditionSeverity.HIGH)))
        assertEquals(SeverityGuidance.CONSULT_DERMATOLOGIST, message.guidance)
        assertTrue(message.considerDoctor)
    }

    @Test
    fun `critical severity maps to urgent care guidance and flags doctor visit`() {
        val message = engine.build(result(condition(severity = ConditionSeverity.CRITICAL)))
        assertEquals(SeverityGuidance.URGENT_CARE, message.guidance)
        assertTrue(message.considerDoctor)
    }

    @Test
    fun `confidence is rounded to a whole percent`() {
        assertEquals(87, engine.build(result(condition(confidence = 0.8743f))).confidencePercent)
        assertEquals(99, engine.build(result(condition(confidence = 0.9876f))).confidencePercent)
        assertEquals(50, engine.build(result(condition(confidence = 0.5f))).confidencePercent)
    }

    @Test
    fun `top label is carried through`() {
        val message = engine.build(result(condition(label = "Melanoma", code = "MEL", severity = ConditionSeverity.CRITICAL)))
        assertEquals("Melanoma", message.conditionLabel)
    }

    @Test
    fun `runner-up below threshold is not mentioned`() {
        val message = engine.build(
            result(
                top = condition(),
                rest = listOf(condition(label = "Acne", code = "Acne", confidence = 0.10f)),
            )
        )
        assertNull(message.runnerUpLabel)
    }

    @Test
    fun `runner-up above threshold is mentioned`() {
        val message = engine.build(
            result(
                top = condition(),
                rest = listOf(condition(label = "Seborrheic Keratosis", code = "SEK", confidence = 0.22f)),
            )
        )
        assertEquals("Seborrheic Keratosis", message.runnerUpLabel)
    }

    @Test
    fun `same-condition duplicate is never picked as runner-up`() {
        val message = engine.build(
            result(
                top = condition(label = "Acne", code = "Acne", confidence = 0.6f),
                rest = listOf(condition(label = "Acne", code = "Acne", confidence = 0.3f)),
            )
        )
        assertNull(message.runnerUpLabel)
    }

    @Test
    fun `boundary confidence of 15 percent is mentioned`() {
        val message = engine.build(
            result(
                top = condition(),
                rest = listOf(condition(label = "BCC", code = "BCC", confidence = 0.15f)),
            )
        )
        assertEquals("BCC", message.runnerUpLabel)
    }
}
