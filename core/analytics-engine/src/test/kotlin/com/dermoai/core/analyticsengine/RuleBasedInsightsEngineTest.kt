package com.dermoai.core.analyticsengine

import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.entity.DailyCheckInEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.domain.insights.InsightType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fake DAOs backed by in-memory lists. */
private class FakeSkinScanDao(private val items: List<SkinScanEntity>) : SkinScanDao {
    override fun observeByUserId(userId: String) = MutableStateFlow(items)
    override suspend fun getLatestByUserId(userId: String) = items.firstOrNull()
    override suspend fun getById(scanId: String) = items.find { it.id == scanId }
    override suspend fun upsert(scan: SkinScanEntity) {}
    override suspend fun deleteById(scanId: String) {}
    override suspend fun updateVoiceNote(scanId: String, path: String?) {}
}

private class FakeScanPredictionDao(private val items: List<ScanPredictionEntity>) : ScanPredictionDao {
    override fun observeByScanId(scanId: String) = MutableStateFlow(items.filter { it.scanId == scanId })
    override suspend fun topPrediction(scanId: String) = items.find { it.scanId == scanId }
    override suspend fun upsert(prediction: ScanPredictionEntity) {}
    override suspend fun upsertAll(predictions: List<ScanPredictionEntity>) {}
    override suspend fun deleteByScanId(scanId: String) {}
}

private class FakeDailyCheckInDao(private val items: List<DailyCheckInEntity>) : DailyCheckInDao {
    override suspend fun getByDate(userId: String, dateKey: String) = items.find { it.dateKey == dateKey }
    override fun observeByUserId(userId: String) = MutableStateFlow(items)
    override fun observeTotalDays(userId: String) = MutableStateFlow(items.size)
    override suspend fun upsert(checkIn: DailyCheckInEntity) {}
    override suspend fun deleteById(id: String) {}
}

class RuleBasedInsightsEngineTest {

    private lateinit var engine: RuleBasedInsightsEngine

    @Before
    fun setUp() {
        engine = RuleBasedInsightsEngine(
            skinScanDao = FakeSkinScanDao(emptyList()),
            predictionDao = FakeScanPredictionDao(emptyList()),
            checkInDao = FakeDailyCheckInDao(emptyList()),
        )
    }

    @Test
    fun insufficientData_returnsNoPattern() = runTest {
        val insights = engine.generateInsights("test")
        assertEquals(1, insights.size)
        assertEquals(InsightType.NO_PATTERN, insights.first().type)
    }

    @Test
    fun sufficientCheckIns_returnsStressInsight() = runTest {
        val now = System.currentTimeMillis()
        val checkIns = List(5) { i ->
            DailyCheckInEntity(
                id = "ci_$i", userId = "test", dateKey = "2025-01-${10 + i}",
                stressLevel = 5, skinFeel = 3, itchDiscomfort = 3, sleepQuality = 3,
                createdAt = now,
            )
        }
        val engineWith = RuleBasedInsightsEngine(
            skinScanDao = FakeSkinScanDao(listOf(createScan("s1", now), createScan("s2", now))),
            predictionDao = FakeScanPredictionDao(listOf(createPrediction("s1", "LOW", 0.8f), createPrediction("s2", "LOW", 0.7f))),
            checkInDao = FakeDailyCheckInDao(checkIns),
        )
        val insights = engineWith.generateInsights("test")
        assertTrue(insights.any { it.type == InsightType.STRESS_CORRELATION })
    }

    private fun createScan(id: String, time: Long) = SkinScanEntity(
        id = id, userId = "test", imagePath = "/tmp/$id", thumbnailPath = "/tmp/${id}_thumb",
        capturedAt = time, createdAt = time, updatedAt = time,
    )

    private fun createPrediction(scanId: String, band: String, conf: Float) = ScanPredictionEntity(
        id = "pred_$scanId", scanId = scanId, label = "Test", labelCode = "TST",
        confidence = conf, rank = 1, concernBand = band, createdAt = 0L,
    )
}
