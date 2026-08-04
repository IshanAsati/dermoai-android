package com.dermoai.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.dao.TreatmentRoutineDao
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import com.dermoai.core.domain.insights.InsightsEngine
import com.dermoai.core.domain.insights.SkinInsight
import com.dermoai.core.environment.EnvironmentAlert
import com.dermoai.core.environment.EnvironmentAlertEvaluator
import com.dermoai.core.environment.EnvironmentRepository
import com.dermoai.core.environment.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val checkInDao: DailyCheckInDao,
    private val insightsEngine: InsightsEngine,
    private val envRepository: EnvironmentRepository,
    private val locationProvider: LocationProvider,
    private val routineDao: TreatmentRoutineDao,
    private val skinScanDao: SkinScanDao,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _latestScan = MutableStateFlow<SkinScanEntity?>(null)
    val latestScan: StateFlow<SkinScanEntity?> = _latestScan.asStateFlow()
    private val _skinMindCompleted = MutableStateFlow(false)
    val skinMindCompleted: StateFlow<Boolean> = _skinMindCompleted.asStateFlow()

    private val _skinMindStreak = MutableStateFlow(0)
    val skinMindStreak: StateFlow<Int> = _skinMindStreak.asStateFlow()

    private val _insights = MutableStateFlow<List<SkinInsight>>(emptyList())
    val insights: StateFlow<List<SkinInsight>> = _insights.asStateFlow()

    private val _currentAlert = MutableStateFlow<EnvironmentAlert?>(null)
    val currentAlert: StateFlow<EnvironmentAlert?> = _currentAlert.asStateFlow()

    private val _cachedEnvLabel = MutableStateFlow("")
    val cachedEnvLabel: StateFlow<String> = _cachedEnvLabel.asStateFlow()

    private val _treatmentRoutines = MutableStateFlow<List<TreatmentRoutineEntity>>(emptyList())
    val treatmentRoutines: StateFlow<List<TreatmentRoutineEntity>> = _treatmentRoutines.asStateFlow()

    private var refreshJob: Job? = null
    private var routineJob: Job? = null

    fun refresh(userId: String = "") {
        // Cancel any in-flight refresh so concurrent fetches can't race on the
        // shared EnvironmentRepository cache and apply out-of-order results.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val checkIns = checkInDao.observeByUserId(userId).first()
            _skinMindCompleted.value = checkIns.any { it.dateKey == today }
            _skinMindStreak.value = computeStreak(checkIns)
            _insights.value = insightsEngine.generateInsights(userId)

            if (userId.isNotEmpty()) {
                // Rebind the (infinite) routine flow on refresh instead of leaking a
                // new collector on every visit — cancel the previous one first.
                routineJob?.cancel()
                routineJob = viewModelScope.launch {
                    routineDao.observeByUserId(userId).collect { _treatmentRoutines.value = it }
                }
            }
            _latestScan.value = skinScanDao.getLatestByUserId(userId)
            _isLoading.value = false

            val cached = envRepository.cachedConditions()
            if (cached != null) {
                _currentAlert.value = EnvironmentAlertEvaluator.evaluate(cached)
                _cachedEnvLabel.value =
                    if (cached.locationLabel.isNotEmpty()) cached.locationLabel else "Updated ${android.text.format.DateFormat.format("MMM d, h:mm a", cached.fetchedAt)}"
            }
            if (locationProvider.hasCoarsePermission()) {
                val loc = locationProvider.lastCoarseLocation()
                if (loc != null) {
                    // Use the fetch result directly instead of re-reading the shared
                    // cache: a failed fetch would otherwise re-display stale data as
                    // fresh, and a concurrent fetch could have overwritten the cache.
                    when (val result = envRepository.fetchCurrent(loc.first, loc.second)) {
                        is AppResult.Success -> {
                            _currentAlert.value = EnvironmentAlertEvaluator.evaluate(result.data)
                            _cachedEnvLabel.value =
                                if (result.data.locationLabel.isNotEmpty()) result.data.locationLabel
                                else "Updated ${android.text.format.DateFormat.format("MMM d, h:mm a", result.data.fetchedAt)}"
                        }
                        is AppResult.Error -> {
                            // Fetch failed — keep last known conditions as-is.
                        }
                        is AppResult.Loading -> {
                            // Not expected from a completed suspend call; no-op.
                        }
                    }
                }
            }
        }
    }

    private fun computeStreak(checkIns: List<com.dermoai.core.database.entity.DailyCheckInEntity>): Int {
        val dates = checkIns.map { it.dateKey }.sortedDescending().distinct()
        if (dates.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        val today = sdf.format(cal.time)
        val anchor = dates.first()
        // A streak counts as long as the most recent check-in is today or yesterday.
        if (anchor != today) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (anchor != sdf.format(cal.time)) return 0
        }
        // The anchor day itself always counts (fixes yesterday-anchored streaks showing N-1).
        var streak = 1
        cal.time = sdf.parse(anchor)!!
        for (i in 1 until dates.size) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (sdf.format(cal.time) == dates[i]) streak++ else break
        }
        return streak
    }
}
