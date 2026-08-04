package com.dermoai.core.domain.rules

import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.SkinCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedFilterEngineTest {

    private val engine = RuleBasedFilterEngine()

    private fun condition(
        label: String = "Nevus/Mole",
        code: String = "NEV",
        confidence: Float = 0.6f,
        severity: ConditionSeverity = ConditionSeverity.LOW,
    ) = SkinCondition(label, code, confidence, severity)

    private fun result(
        top: SkinCondition = condition(),
        rest: List<SkinCondition> = emptyList(),
    ) = InferenceResult(
        topPrediction = top,
        allPredictions = listOf(top) + rest,
        heatmap = null,
        severityEstimate = when (top.severity) {
            ConditionSeverity.LOW -> 2
            ConditionSeverity.MEDIUM -> 5
            ConditionSeverity.HIGH -> 8
            ConditionSeverity.CRITICAL -> 10
        },
    )

    @Test
    fun `empty profile leaves result untouched`() {
        val r = result()
        val out = engine.apply(r, SkinProfile())
        assertEquals(r, out.result)
        assertTrue(out.adjustments.isEmpty())
        assertFalse(out.referralFlagged)
    }

    @Test
    fun `age over 50 with melanoma top escalates one tier and flags referral`() {
        val r = result(top = condition(label = "Melanoma", code = "MEL", severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(age = 55))
        assertEquals(ConditionSeverity.MEDIUM, out.result.topPrediction.severity)
        assertTrue(out.referralFlagged)
        assertEquals(listOf("age_50_melanoma"), out.adjustments.map { it.ruleId })
    }

    @Test
    fun `age over 50 with melanoma as runner-up also escalates`() {
        val r = result(
            top = condition(severity = ConditionSeverity.LOW),
            rest = listOf(condition(label = "Melanoma", code = "MEL", confidence = 0.3f)),
        )
        val out = engine.apply(r, SkinProfile(age = 60))
        assertEquals(ConditionSeverity.MEDIUM, out.result.topPrediction.severity)
        assertTrue(out.referralFlagged)
    }

    @Test
    fun `escalation caps at critical`() {
        val r = result(top = condition(label = "Melanoma", code = "MEL", severity = ConditionSeverity.CRITICAL))
        val out = engine.apply(r, SkinProfile(age = 70, sunExposure = "Outdoor worker"))
        assertEquals(ConditionSeverity.CRITICAL, out.result.topPrediction.severity)
        assertEquals(10, out.result.severityEstimate)
    }

    @Test
    fun `heavy sun exposure with UV lesion escalates`() {
        val r = result(top = condition(label = "Actinic Keratosis", code = "AK", severity = ConditionSeverity.MEDIUM))
        val out = engine.apply(r, SkinProfile(sunExposure = "Outdoor worker"))
        assertEquals(ConditionSeverity.HIGH, out.result.topPrediction.severity)
        assertTrue(out.referralFlagged)
    }

    @Test
    fun `heavy sun exposure without UV lesion does nothing`() {
        val r = result(top = condition(label = "Acne", code = "Acne", severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(sunExposure = "Outdoor worker"))
        assertEquals(r, out.result)
        assertTrue(out.adjustments.isEmpty())
    }

    @Test
    fun `young acne gets a note and no escalation`() {
        val r = result(top = condition(label = "Acne", code = "Acne", severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(age = 17))
        assertEquals(ConditionSeverity.LOW, out.result.topPrediction.severity)
        assertEquals(listOf("young_acne"), out.adjustments.map { it.ruleId })
        assertFalse(out.referralFlagged)
    }

    @Test
    fun `oily skin with acne gets a note`() {
        val r = result(top = condition(label = "Acne", code = "Acne", severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(skinType = "Oily"))
        assertEquals(listOf("oily_acne"), out.adjustments.map { it.ruleId })
    }

    @Test
    fun `dark skin with BCC in top two gets a monitoring note`() {
        val r = result(
            top = condition(),
            rest = listOf(condition(label = "BCC", code = "BCC", confidence = 0.25f, severity = ConditionSeverity.HIGH)),
        )
        val out = engine.apply(r, SkinProfile(skinTone = "Dark Brown"))
        assertEquals(listOf("dark_skin_uv_lesion"), out.adjustments.map { it.ruleId })
        assertFalse(out.referralFlagged)
    }

    @Test
    fun `male hair loss gets a note`() {
        val r = result(top = condition(label = "Hair Loss", code = "HairLoss", severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(gender = "Male"))
        assertEquals(listOf("male_hair_loss"), out.adjustments.map { it.ruleId })
    }

    @Test
    fun `young person with mole gets a note`() {
        val r = result(top = condition(severity = ConditionSeverity.LOW))
        val out = engine.apply(r, SkinProfile(age = 22))
        assertEquals(listOf("young_mole"), out.adjustments.map { it.ruleId })
    }

    @Test
    fun `elderly medium concern escalates`() {
        val r = result(top = condition(label = "Seborrheic Keratosis", code = "SEK", severity = ConditionSeverity.MEDIUM))
        val out = engine.apply(r, SkinProfile(age = 70))
        assertEquals(ConditionSeverity.HIGH, out.result.topPrediction.severity)
        assertTrue(out.referralFlagged)
        assertEquals(listOf("elderly_prompt_review"), out.adjustments.map { it.ruleId })
    }

    @Test
    fun `high severity without profile still flags referral`() {
        val r = result(top = condition(label = "SCC", code = "SCC", severity = ConditionSeverity.HIGH))
        val out = engine.apply(r, SkinProfile())
        assertTrue(out.referralFlagged)
        assertEquals(r, out.result)
    }

    @Test
    fun `all predictions except top keep original severity`() {
        val runnerUp = condition(label = "BCC", code = "BCC", confidence = 0.3f, severity = ConditionSeverity.HIGH)
        val r = result(top = condition(label = "Melanoma", code = "MEL", severity = ConditionSeverity.LOW), rest = listOf(runnerUp))
        val out = engine.apply(r, SkinProfile(age = 55))
        assertEquals(ConditionSeverity.MEDIUM, out.result.topPrediction.severity)
        assertEquals(ConditionSeverity.HIGH, out.result.allPredictions[1].severity)
    }
}
