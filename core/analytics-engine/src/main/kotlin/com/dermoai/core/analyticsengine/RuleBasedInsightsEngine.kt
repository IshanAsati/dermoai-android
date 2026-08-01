package com.dermoai.core.analyticsengine

import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.domain.insights.InsightType
import com.dermoai.core.domain.insights.InsightsEngine
import com.dermoai.core.domain.insights.SkinInsight
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedInsightsEngine @Inject constructor(
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val checkInDao: DailyCheckInDao,
) : InsightsEngine {

    override suspend fun generateInsights(userId: String, windowDays: Int): List<SkinInsight> {
        val scans = skinScanDao.observeByUserId(userId).first()
        val checkIns = checkInDao.observeByUserId(userId).first()
        val results = mutableListOf<SkinInsight>()

        val cutoff = System.currentTimeMillis() - windowDays * 24 * 60 * 60 * 1000L
        val recentScans = scans.filter { it.capturedAt >= cutoff }
        val recentCheckIns = checkIns.filter { it.createdAt >= cutoff }

        // Rule 1: Minimum data requirement
        if (recentScans.size < 2 && recentCheckIns.size < 3) {
            results.add(
                SkinInsight(
                    id = "insufficient_${userId}",
                    type = InsightType.NO_PATTERN,
                    message = "Not enough data yet. Complete daily SkinMind check-ins and scans to unlock insights.",
                    confidence = 1f,
                )
            )
            return results
        }

        // Rule 2: Scan frequency
        if (recentScans.size >= 5) {
            val scanRate = recentScans.size.toFloat() / windowDays.coerceAtLeast(1)
            if (scanRate >= 0.2f) {
                results.add(
                    SkinInsight(
                        id = "scan_freq_${userId}",
                        type = InsightType.NO_PATTERN,
                        message = "You've scanned ${recentScans.size} times in $windowDays days. Consistent tracking leads to better insights.",
                        confidence = 0.7f,
                        evidenceMetrics = mapOf("scanCount" to recentScans.size.toFloat()),
                    )
                )
            }
        }

        // Rule 3: Concern band trends — CRITICAL/HIGH scans
        val criticalScans = recentScans.filter { scan ->
            predictionDao.topPrediction(scan.id)?.concernBand in setOf("CRITICAL", "HIGH")
        }
        if (criticalScans.size >= 2) {
            results.add(
                SkinInsight(
                    id = "elevated_${userId}",
                    type = InsightType.SYMPTOM_IMPROVING,
                    message = "${criticalScans.size} scans with elevated concern in the last $windowDays days. Consider sharing your report with a dermatologist.",
                    confidence = 0.8f,
                    evidenceMetrics = mapOf("criticalScanCount" to criticalScans.size.toFloat()),
                )
            )
        }

        // Rule 4: Sleep correlation with itch
        if (recentCheckIns.size >= 5) {
            val lowSleepDays = recentCheckIns.filter { it.sleepQuality <= 2 }
            val highItchDays = recentCheckIns.filter { it.itchDiscomfort >= 5 }
            val correlation = lowSleepDays.intersect(highItchDays.toSet()).size
            if (correlation >= 3) {
                results.add(
                    SkinInsight(
                        id = "sleep_itch_${userId}",
                        type = InsightType.SLEEP_CORRELATION,
                        message = "Itch scores were higher on days you logged poor sleep (last $windowDays days).",
                        confidence = 0.65f,
                        evidenceMetrics = mapOf("correlationDays" to correlation.toFloat()),
                    )
                )
            }
        }

        // Rule 5: Stress correlation
        if (recentCheckIns.size >= 5) {
            val highStress = recentCheckIns.filter { it.stressLevel >= 4 }
            if (highStress.size >= 3) {
                results.add(
                    SkinInsight(
                        id = "stress_${userId}",
                        type = InsightType.STRESS_CORRELATION,
                        message = "You logged high stress on ${highStress.size} days in the last $windowDays days. Stress can affect skin health.",
                        confidence = 0.6f,
                        evidenceMetrics = mapOf("highStressDays" to highStress.size.toFloat()),
                    )
                )
            }
        }

        // Rule 6: Product tracking
        val newProductDays = recentCheckIns.filter { it.newProductUsed }
        if (newProductDays.size >= 3) {
            results.add(
                SkinInsight(
                    id = "product_${userId}",
                    type = InsightType.ADHERENCE_IMPROVEMENT,
                    message = "You've tried ${newProductDays.size} new products recently. Consistency with one routine often yields clearer patterns.",
                    confidence = 0.5f,
                    evidenceMetrics = mapOf("newProductDays" to newProductDays.size.toFloat()),
                )
            )
        }

        if (results.isEmpty()) {
            results.add(
                SkinInsight(
                    id = "fallback_${userId}",
                    type = InsightType.NO_PATTERN,
                    message = "No obvious pattern detected yet. Complete daily check-ins to unlock insights.",
                    confidence = 1f,
                )
            )
        }

        return results
    }
}
