package com.dermoai.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dermoai.core.database.DermoDatabase
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ScanWithPrediction(
    val scan: SkinScanEntity,
    val topPrediction: ScanPredictionEntity?,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val db: DermoDatabase,
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

    private var timelineJob: Job? = null

    fun loadTimeline(userId: String) {
        // The collector below never completes — cancel the previous one so
        // revisiting the tab doesn't stack an unbounded number of Room flows.
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            skinScanDao.observeByUserId(userId).collect { entities ->
                val enriched = entities.map { scan ->
                    ScanWithPrediction(scan, predictionDao.topPrediction(scan.id))
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

    /** Persists the recorded voice-note path back to the scan row. */
    fun saveVoiceNote(scanId: String, path: String?) {
        viewModelScope.launch {
            runCatching { skinScanDao.updateVoiceNote(scanId, path) }
        }
    }

    /** Deletes a scan atomically (scan + predictions in one transaction) and removes its files. */
    suspend fun deleteScan(scanId: String): Boolean = runCatching {
        val scan = skinScanDao.getById(scanId)
        db.withTransaction {
            predictionDao.deleteByScanId(scanId)
            skinScanDao.deleteById(scanId)
        }
        scan?.let { s ->
            withContext(Dispatchers.IO) {
                runCatching { File(s.imagePath).delete() }
                runCatching { File(s.thumbnailPath).delete() }
                s.voiceNotePath?.let { runCatching { File(it).delete() } }
            }
        }
    }.isSuccess
}
