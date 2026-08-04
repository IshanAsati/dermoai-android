package com.dermoai.core.domain.rules

import com.dermoai.core.domain.ml.InferenceResult
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.SkinCondition
import javax.inject.Inject

/**
 * Demographic / lifestyle inputs the rule layer uses to refine estimates.
 * Fields come from the user's onboarding / Settings skin profile; empty
 * values mean "unknown" and disable the rules that depend on them.
 */
data class SkinProfile(
    val age: Int = 0,
    val gender: String = "",
    val skinType: String = "",
    val skinTone: String = "",
    val sunExposure: String = "",
) {
    val isPopulated: Boolean
        get() = age > 0 || gender.isNotBlank() || skinType.isNotBlank() ||
            skinTone.isNotBlank() || sunExposure.isNotBlank()
}

/** One applied rule, carrying the user-facing explanation ("transparent filtering"). */
data class RuleAdjustment(
    val ruleId: String,
    val description: String,
)

/** Output of the rule layer: the adjusted result plus what changed and why. */
data class FilteredResult(
    val result: InferenceResult,
    val adjustments: List<RuleAdjustment>,
    /** True when the app should suggest visiting a dermatologist. */
    val referralFlagged: Boolean,
)

/**
 * Rule-based filter layered on top of the model output (age, skin, gender,
 * sun exposure). Design rules:
 *  - Rules may only ESCALATE concern or annotate — never downgrade it
 *    (safety-first for an educational tool).
 *  - Every applied rule is reported back to the user via [RuleAdjustment].
 *  - Unknown demographics simply skip their rules.
 */
class RuleBasedFilterEngine @Inject constructor() {

    fun apply(result: InferenceResult, profile: SkinProfile): FilteredResult {
        val top = result.topPrediction
        val topTwoCodes = result.allPredictions.take(2).map { it.code }.toSet()
        val adjustments = mutableListOf<RuleAdjustment>()
        var severity = top.severity
        var referral = severity >= ConditionSeverity.HIGH

        fun escalate() {
            severity = when (severity) {
                ConditionSeverity.LOW -> ConditionSeverity.MEDIUM
                ConditionSeverity.MEDIUM -> ConditionSeverity.HIGH
                ConditionSeverity.HIGH -> ConditionSeverity.CRITICAL
                ConditionSeverity.CRITICAL -> ConditionSeverity.CRITICAL
            }
            if (severity >= ConditionSeverity.HIGH) referral = true
        }

        // Rule 1: Melanoma risk rises steeply after 50.
        if (profile.age >= 50 && (top.code == CODE_MELANOMA || CODE_MELANOMA in topTwoCodes)) {
            escalate()
            referral = true
            adjustments += RuleAdjustment(
                "age_50_melanoma",
                "Age over 50 raises melanoma risk — concern level adjusted.",
            )
        }

        // Rule 2: Skin concerns in older adults deserve earlier review.
        if (profile.age >= 65 && severity == ConditionSeverity.MEDIUM) {
            escalate()
            adjustments += RuleAdjustment(
                "elderly_prompt_review",
                "Skin concerns in older adults deserve prompt review — concern level adjusted.",
            )
        }

        // Rule 3: Heavy sun exposure + sun-related lesion in the top two.
        if (profile.sunExposure in SUN_HEAVY && topTwoCodes.any { it in UV_RELATED_CODES }) {
            escalate()
            adjustments += RuleAdjustment(
                "sun_exposure_uv",
                "Frequent sun exposure raises the risk of sun-related lesions — concern level adjusted.",
            )
        }

        // Rule 4: Acne is very common in teens and young adults — expected, not alarming.
        if (profile.age in 13..25 && top.code == CODE_ACNE) {
            adjustments += RuleAdjustment(
                "young_acne",
                "Acne is very common at your age — this estimate is expected.",
            )
        }

        // Rule 5: Oily skin commonly accompanies acne.
        if (profile.skinType.equals("Oily", ignoreCase = true) && top.code == CODE_ACNE) {
            adjustments += RuleAdjustment(
                "oily_acne",
                "Oily skin commonly accompanies acne — consistent with your profile.",
            )
        }

        // Rule 6: UV-linked cancers are rarer in dark skin but can be more aggressive.
        if (profile.skinTone in DARK_SKIN_TONES && topTwoCodes.any { it in listOf(CODE_BCC, CODE_SCC) }) {
            adjustments += RuleAdjustment(
                "dark_skin_uv_lesion",
                "These lesions are less common in darker skin tones but can be more aggressive — keep monitoring.",
            )
        }

        // Rule 7: Hair loss is common in men — contextual note only.
        if (profile.gender.equals("Male", ignoreCase = true) && top.code == CODE_HAIR_LOSS) {
            adjustments += RuleAdjustment(
                "male_hair_loss",
                "Hair loss is common in men — consistent with your profile.",
            )
        }

        // Rule 8: Moles in younger people are usually harmless — contextual note only.
        if (profile.age in 1..30 && top.code == CODE_NEVUS) {
            adjustments += RuleAdjustment(
                "young_mole",
                "Moles are common at your age; most are harmless — track any changes.",
            )
        }

        val adjustedTop = if (severity == top.severity) top else top.copy(severity = severity)
        val adjustedResult = if (severity == top.severity) {
            // Unchanged — keep the original result byte-identical (incl. severityEstimate).
            result.copy(
                topPrediction = adjustedTop,
                allPredictions = result.allPredictions.map { if (it === top) adjustedTop else it },
            )
        } else {
            result.copy(
                topPrediction = adjustedTop,
                allPredictions = result.allPredictions.map { if (it === top) adjustedTop else it },
                severityEstimate = SEVERITY_SCORES.getValue(adjustedTop.severity),
            )
        }
        return FilteredResult(
            result = adjustedResult,
            adjustments = adjustments,
            referralFlagged = referral,
        )
    }

    companion object {
        const val CODE_MELANOMA = "MEL"
        const val CODE_BCC = "BCC"
        const val CODE_SCC = "SCC"
        const val CODE_ACNE = "Acne"
        const val CODE_HAIR_LOSS = "HairLoss"
        const val CODE_NEVUS = "NEV"

        val UV_RELATED_CODES = setOf("AK", "SEK", CODE_BCC, CODE_SCC)
        val SUN_HEAVY = setOf("1-2 hours daily", "Outdoor worker")
        val DARK_SKIN_TONES = setOf("Brown", "Dark Brown")

        val SEVERITY_SCORES = mapOf(
            ConditionSeverity.LOW to 2,
            ConditionSeverity.MEDIUM to 5,
            ConditionSeverity.HIGH to 8,
            ConditionSeverity.CRITICAL to 10,
        )
    }
}
