package com.dermoai.core.domain.model

/**
 * NEV-vs-confusable-class gate over the model's 1024-d penultimate features.
 *
 * The 12-class head sometimes calls a real mole ACK or SEK instead of NEV --
 * measured directly on held-out data and on a real mole photo scoring ACK
 * above NEV. Unlike [HealthyGate], this gate never touches a malignant call:
 * it only ever promotes NEV when the head's own top call is already one of
 * [overridableCodes], a set fixed at training time to exclude BCC/SCC/MEL by
 * construction (see tools/ml/train_mole_gate.py, which refuses to write the
 * asset if any malignant sample could actually clear the threshold under
 * that condition). A linear probe cannot safely separate "mole" from
 * "melanoma" outright -- the two populations overlap in these features far
 * more than healthy-vs-lesion ever did -- so this gate is deliberately
 * narrower than the healthy gate: it corrects a specific, verified confusion
 * rather than claiming a general mole detector.
 *
 * Values come from assets/ml/mole_gate.json, produced by
 * tools/ml/train_mole_gate.py.
 */
data class MoleGate(
    val weights: FloatArray,
    val bias: Float,
    val threshold: Float,
    /**
     * The gate only fires when the head's own top call is in this set.
     * Fixed at training time to the classes NEV is actually confused with
     * (ACK, SEK) -- never a malignant class, so this gate has no path to
     * relabelling a cancer call as a benign mole.
     */
    val overridableCodes: Set<String>,
) {
    /** Probability that [features] show a mole rather than [overridableCodes]'s condition. */
    fun score(features: FloatArray): Float {
        require(features.size == weights.size) {
            "feature length ${features.size} != gate length ${weights.size}"
        }
        var z = bias
        for (i in weights.indices) z += weights[i] * features[i]
        return 1f / (1f + kotlin.math.exp(-z))
    }

    /**
     * Promotes NEV to the top of [ranked] when the head's own top call is in
     * [overridableCodes] and the gate is confident, rescaling the rest the
     * same way [HealthyGate.applyTo] does. Returns [ranked] unchanged
     * otherwise -- in particular, whenever the head's top call isn't in
     * [overridableCodes], which already excludes every malignant class.
     */
    fun applyTo(
        ranked: List<SkinCondition>,
        features: FloatArray,
        moleLabel: String,
        moleCode: String,
    ): List<SkinCondition> {
        val top = ranked.firstOrNull() ?: return ranked
        if (top.code !in overridableCodes) return ranked
        val moleScore = score(features)
        if (moleScore < threshold) return ranked
        val mole = SkinCondition(
            label = moleLabel,
            code = moleCode,
            confidence = moleScore,
            severity = ConditionSeverity.LOW,
        )
        // The head's 12-way softmax already assigns NEV its own (lower) probability,
        // so it's normally already in `ranked` -- rescaling by a flat `remainder`
        // factor would double-count that mass and the total would exceed/undershoot
        // 1. Renormalizing the remainder's own sum to 1 first keeps the total exact
        // regardless of whether NEV was already present.
        val remainder = 1f - moleScore
        val remaining = ranked.filterNot { it.code == moleCode }
        val remainingSum = remaining.sumOf { it.confidence.toDouble() }.toFloat()
        val rescaled = if (remainingSum > 0f) {
            remaining.map { it.copy(confidence = it.confidence / remainingSum * remainder) }
        } else {
            remaining
        }
        return listOf(mole) + rescaled
    }

    // FloatArray uses reference equality; compare contents so two gates loaded
    // from the same asset are equal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoleGate) return false
        return weights.contentEquals(other.weights) &&
            bias == other.bias &&
            threshold == other.threshold &&
            overridableCodes == other.overridableCodes
    }

    override fun hashCode(): Int =
        ((weights.contentHashCode() * 31 + bias.hashCode()) * 31 + threshold.hashCode()) * 31 +
            overridableCodes.hashCode()
}
