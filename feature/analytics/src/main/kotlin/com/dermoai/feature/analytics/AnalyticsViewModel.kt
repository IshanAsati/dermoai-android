package com.dermoai.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.domain.insights.InsightsEngine
import com.dermoai.core.domain.insights.SkinInsight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeekBucket(val weekStart: String, val count: Int)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val checkInDao: DailyCheckInDao,
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val insightsEngine: InsightsEngine,
) : ViewModel() {

    private val _itchOverTime = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val itchOverTime: StateFlow<List<Pair<String, Float>>> = _itchOverTime.asStateFlow()

    private val _stressOverTime = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val stressOverTime: StateFlow<List<Pair<String, Float>>> = _stressOverTime.asStateFlow()

    private val _scansByWeek = MutableStateFlow<List<WeekBucket>>(emptyList())
    val scansByWeek: StateFlow<List<WeekBucket>> = _scansByWeek.asStateFlow()

    private val _concernDistribution = MutableStateFlow<Map<String, Int>>(emptyMap())
    val concernDistribution: StateFlow<Map<String, Int>> = _concernDistribution.asStateFlow()

    private val _insights = MutableStateFlow<List<SkinInsight>>(emptyList())
    val insights: StateFlow<List<SkinInsight>> = _insights.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refresh(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val checkIns = checkInDao.observeByUserId(userId).first()
            val scans = skinScanDao.observeByUserId(userId).first()

            _itchOverTime.value = checkIns.reversed().map { it.dateKey to it.itchDiscomfort.toFloat() }
            _stressOverTime.value = checkIns.reversed().map { it.dateKey to it.stressLevel.toFloat() }

            _scansByWeek.value = scans.groupBy {
                it.capturedAt.toString().substring(0, 10) // simplified grouping
            }.entries.map { WeekBucket(it.key, it.value.size) }.sortedBy { it.weekStart }

            val bands = mutableMapOf<String, Int>()
            for (s in scans) {
                val p = predictionDao.topPrediction(s.id)
                val band = p?.concernBand ?: "UNKNOWN"
                bands[band] = (bands[band] ?: 0) + 1
            }
            _concernDistribution.value = bands

            _insights.value = insightsEngine.generateInsights(userId)
            _isLoading.value = false
        }
    }
}
