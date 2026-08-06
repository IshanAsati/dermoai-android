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
        if (gateScore < threshold) return ranked
        val healthy = SkinCondition(
            label = healthyLabel,
            code = healthyCode,
            confidence = gateScore,
            severity = ConditionSeverity.LOW,
        )
        val remainder = 1f - gateScore
        return listOf(healthy) + ranked
            .filterNot { it.code == healthyCode }
            .map { it.copy(confidence = it.confidence * remainder) }
    }

    // FloatArray uses reference equality; compare contents so two gates loaded
    // from the same asset are equal.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HealthyGate) return false
        return weights.contentEquals(other.weights) &&
            bias == other.bias &&
            threshold == other.threshold
    }

    override fun hashCode(): Int =
        (weights.contentHashCode() * 31 + bias.hashCode()) * 31 + threshold.hashCode()
}
