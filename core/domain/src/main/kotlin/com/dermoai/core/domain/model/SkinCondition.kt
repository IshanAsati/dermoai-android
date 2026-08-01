package com.dermoai.core.domain.model

/**
 * Domain representation of a possible skin condition from model inference.
 */
data class SkinCondition(
    val label: String,
    val code: String,
    val confidence: Float,
    val severity: ConditionSeverity,
)

enum class ConditionSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}