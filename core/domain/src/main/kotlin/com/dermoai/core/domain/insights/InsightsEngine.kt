package com.dermoai.core.domain.insights

/**
 * Generates lifestyle-skin correlation insights from user history.
 */
interface InsightsEngine {
    suspend fun generateInsights(userId: String, windowDays: Int = 30): List<SkinInsight>
}

data class SkinInsight(
    val id: String,
    val type: InsightType,
    val message: String,
    val confidence: Float,
    val evidenceMetrics: Map<String, Float> = emptyMap(),
    val generatedAt: Long = System.currentTimeMillis(),
)

enum class InsightType {
    SLEEP_CORRELATION,
    STRESS_CORRELATION,
    ADHERENCE_IMPROVEMENT,
    SYMPTOM_IMPROVING,
    NO_PATTERN,
}