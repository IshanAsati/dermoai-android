package com.dermoai.core.domain.model

/**
 * Linear healthy-vs-lesion gate over the model's 1024-d penultimate features.
 *
 * The 12-class head cannot recognise normal skin: class 11 scores 99.6% recall on
 * the checkpoint's own validation split but 0/9 on real skin, because it learned a
 * cue specific to its training images. The backbone's features do still separate
 * the two, so this gate scores them directly and overrides the head when it fires.
 *
 * Values come from assets/ml/healthy_gate.json, produced by
 * tools/ml/train_healthy_gate.py.
 */
data class HealthyGate(
    val weights: FloatArray,
    val bias: Float,
    /**
     * Deliberately not 0.5. Calling a lesion "healthy" could tell someone their
     * melanoma is nothing, so the operating point is picked for a low
     * lesion-false-positive rate rather than for balanced accuracy.
     */
    val threshold: Float,
    /**
     * Below this, the gate isn't just failing to confirm "Healthy" -- it actively
     * disagrees. The head's own class-11 shortcut (see class doc) sometimes fires
     * on a real lesion photo with real confidence, and [threshold] alone can't
     * catch that: it only ever pushes a result *toward* Healthy, never away from
     * one the head invented on its own. When the head's top call is Healthy but
     * the gate's independent read is this low, the call is dropped rather than
     * shown as a reassurance it doesn't support. 0f (the default for gates
     * predating this field) never vetoes, matching the old override-only behavior.
     */
    val vetoThreshold: Float = 0f,
) {
    /** Probability that [features] show normal skin rather than a lesion. */
    fun score(features: FloatArray): Float {
        require(features.size == weights.size) {
            "feature length ${features.size} != gate length ${weights.size}"
        }
        var z = bias
        for (i in weights.indices) z += weights[i] * features[i]
        return 1f / (1f + kotlin.math.exp(-z))
    }

    fun isHealthy(features: FloatArray): Boolean = score(features) >= threshold

    /**
     * Puts Healthy at the top of [ranked] and rescales the head's own numbers into
     * the probability the gate left over.
     *
     * Without the rescale the entries below Healthy keep their original softmax
     * values, so a 97% Healthy result can be followed by a 99% Nevus and the list
     * contradicts itself. Returns [ranked] unchanged when the gate has not fired.
     */
    fun applyTo(
        ranked: List<SkinCondition>,
        features: FloatArray,
        healthyLabel: String,
        healthyCode: String,
    ): List<SkinCondition> {
        val gateScore = score(features)
        if (gateScore >= threshold) {
            val healthy = SkinCondition(
                label = healthyLabel,
                code = healthyCode,
                confidence = gateScore,
                severity = ConditionSeverity.LOW,
            )
            // The head's 12-way softmax already assigns Healthy its own (lower)
            // probability, so it's normally already in `ranked` -- rescaling by a
            // flat `remainder` factor would double-count that mass. Renormalizing
            // the remainder's own sum to 1 first keeps the total exact regardless
            // of whether Healthy was already present.
            val remainder = 1f - gateScore
            val remaining = ranked.filterNot { it.code == healthyCode }
            val remainingSum = remaining.sumOf { it.confidence.toDouble() }.toFloat()
            val rescaled = if (remainingSum > 0f) {
                remaining.map { it.copy(confidence = it.confidence / remainingSum * remainder) }
            } else {
                remaining
            }
            return listOf(healthy) + rescaled
        }
        // The gate isn't confident enough to override, but if it actively disagrees
        // with a Healthy call the head made on its own, that call is unsupported --
        // drop it so a lesion photo isn't shown back as a reassurance.
        if (gateScore < vetoThreshold && ranked.firstOrNull()?.code == healthyCode) {
            return ranked.filterNot { it.code == healthyCode }
        }
        return ranked
    }

    // FloatArray uses reference equality; compare contents so two gates loaded
    // from the same asset are equal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HealthyGate) return false
        return weights.contentEquals(other.weights) &&
            bias == other.bias &&
            threshold == other.threshold &&
            vetoThreshold == other.vetoThreshold
    }

    override fun hashCode(): Int =
        ((weights.contentHashCode() * 31 + bias.hashCode()) * 31 + threshold.hashCode()) * 31 +
            vetoThreshold.hashCode()
}
