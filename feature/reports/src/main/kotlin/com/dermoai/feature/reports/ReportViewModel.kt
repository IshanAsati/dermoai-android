package com.dermoai.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.reports.DoctorReportGenerator
import com.dermoai.core.reports.ReportInput
import com.dermoai.core.reports.ScanWithPredictions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface PdfUiState {
    data object Idle : PdfUiState
    data object Generating : PdfUiState
    data class Ready(val file: File) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val scanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val checkInDao: DailyCheckInDao,
    private val generator: DoctorReportGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    var includeImages = MutableStateFlow(true)
    var includePredictions = MutableStateFlow(true)
    var includeSkinMind = MutableStateFlow(true)
    var selectedRangeDays = MutableStateFlow(30)

    fun generate(userId: String, displayName: String) {
        _state.value = PdfUiState.Generating
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - selectedRangeDays.value * 24L * 60 * 60 * 1000
            val scans = scanDao.observeByUserId(userId).first().filter { it.capturedAt >= cutoff }
            val scanEntries = scans.map { scan ->
                val predictions = predictionDao.observeByScanId(scan.id).first()
                ScanWithPredictions(scan, predictions)
            }
            val checkIns = checkInDao.observeByUserId(userId).first().filter { it.createdAt >= cutoff }
            val rangeLabel = "Last ${selectedRangeDays.value} days"

            val input = ReportInput(
                patientName = displayName,
                dateRangeLabel = rangeLabel,
                includeImages = includeImages.value,
                includePredictions = includePredictions.value,
                includeSkinMind = includeSkinMind.value,
                scans = scanEntries,
                checkIns = checkIns,
            )
            when (val result = generator.generate(input)) {
                is AppResult.Success -> _state.value = PdfUiState.Ready(result.data)
                is AppResult.Error -> _state.value = PdfUiState.Error(result.message ?: "Generation failed")
                else -> _state.value = PdfUiState.Error("Unknown error")
            }
        }
    }

    fun reset() { _state.value = PdfUiState.Idle }
}
