package com.dermoai.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanWithPrediction(
    val scan: SkinScanEntity,
    val topPrediction: ScanPredictionEntity?,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
) : ViewModel() {

    private val _scans = MutableStateFlow<List<ScanWithPrediction>>(emptyList())
    val scans: StateFlow<List<ScanWithPrediction>> = _scans

    private val _predictions = MutableStateFlow<List<ScanPredictionEntity>>(emptyList())
    val predictions: StateFlow<List<ScanPredictionEntity>> = _predictions

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _scan = MutableStateFlow<SkinScanEntity?>(null)
    val scan: StateFlow<SkinScanEntity?> = _scan.asStateFlow()

    private val _scanLoading = MutableStateFlow(true)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()

    fun loadTimeline(userId: String) {
        viewModelScope.launch {
            skinScanDao.observeByUserId(userId).collect { entities ->
                val enriched = entities.map { scan ->
                    val top = predictionDao.topPrediction(scan.id)
                    ScanWithPrediction(scan, top)
                }
                _scans.value = enriched
                _isLoading.value = false
            }
        }
    }

    fun loadPredictions(scanId: String) {
        viewModelScope.launch {
            predictionDao.observeByScanId(scanId).collect {
                _predictions.value = it
            }
        }
    }

    fun loadScan(scanId: String) {
        viewModelScope.launch {
            _scanLoading.value = true
            _scan.value = skinScanDao.getById(scanId)
            _scanLoading.value = false
        }
    }

    suspend fun deleteScan(scanId: String): Boolean = runCatching {
        skinScanDao.deleteById(scanId)
        predictionDao.deleteByScanId(scanId)
    }.isSuccess
}
