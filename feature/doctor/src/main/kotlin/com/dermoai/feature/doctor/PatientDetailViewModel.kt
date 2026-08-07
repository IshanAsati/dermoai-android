package com.dermoai.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.domain.model.AuditAction
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.LinkStatus
import com.dermoai.core.domain.model.PatientAdherence
import com.dermoai.core.domain.model.PatientLink
import com.dermoai.core.domain.model.PatientTrend
import com.dermoai.core.domain.usecase.doctor.ComputeAdherenceUseCase
import com.dermoai.core.domain.usecase.doctor.ComputeTrendUseCase
import com.dermoai.core.domain.usecase.doctor.ScanSeveritySample
import com.dermoai.feature.doctor.data.AuditLogger
import com.dermoai.feature.doctor.data.concernBandToSeverity
import com.dermoai.feature.doctor.data.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of the patient's scans, reduced to what this screen draws. */
data class PatientScanRow(
    val scanId: String,
    val capturedAt: Long,
    val imagePath: String,
    val bodyArea: String,
    val note: String,
    val finding: String?,
    val confidence: Float?,
    val severity: ConditionSeverity?,
)

sealed interface PatientDetailUiState {
    data object Loading : PatientDetailUiState

    /**
     * No link, or a link that does not [PatientLink.grantsAccess]. Reached by
     * following a stale back-stack entry after the patient revoked, so it must
     * read as a normal outcome rather than an error.
     */
    data object NotLinked : PatientDetailUiState

    data class Ready(
        val link: PatientLink,
        val scans: List<PatientScanRow>,
        val adherence: PatientAdherence,
        val trend: PatientTrend,
    ) : PatientDetailUiState

    /** Terminal after the doctor revokes their own access from this screen. */
    data object Revoked : PatientDetailUiState
}

/**
 * Backs one patient's record as seen by their doctor.
 *
 * The access check is done here against [PatientLink.grantsAccess] — the link,
 * not the account's role — because being a doctor grants a different UI while
 * only a consented, active link grants sight of a specific person's photos.
 *
 * Opening this screen writes an [AuditAction.VIEWED_PATIENT] row. That write is
 * the consideration the patient gets for handing over their photos, so it
 * happens on entry and before any scan is drawn, not on a "log this" toggle.
 */
@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    private val doctorProfileDao: DoctorProfileDao,
    private val patientLinkDao: PatientLinkDao,
    private val skinScanDao: SkinScanDao,
    private val scanPredictionDao: ScanPredictionDao,
    private val auditLogger: AuditLogger,
    private val computeAdherence: ComputeAdherenceUseCase,
    private val computeTrend: ComputeTrendUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<PatientDetailUiState>(PatientDetailUiState.Loading)
    val state: StateFlow<PatientDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var link: PatientLink? = null
    private var doctorUserId: String? = null

    /**
     * @param doctorUserId the signed-in doctor's `AuthUser.id`.
     * @param patientUserId the patient's `AuthUser.id`.
     */
    fun load(doctorUserId: String, patientUserId: String) {
        // Room flows never complete; without this a revisit stacks collectors.
        loadJob?.cancel()
        this.doctorUserId = doctorUserId
        loadJob = viewModelScope.launch {
            _state.value = PatientDetailUiState.Loading

            val profile = doctorProfileDao.getByUserId(doctorUserId)?.toDomain()
            if (profile == null || !profile.isVerified) {
                _state.value = PatientDetailUiState.NotLinked
                return@launch
            }
            val current = patientLinkDao.getByPatientAndDoctor(patientUserId, profile.id)?.toDomain()
            if (current == null || !current.grantsAccess) {
                _state.value = PatientDetailUiState.NotLinked
                return@launch
            }
            link = current

            // Logged before the record is rendered: the patient's guarantee is
            // that a view is recorded, not that a view that finished loading is.
            auditLogger.record(
                doctorUserId = doctorUserId,
                patientUserId = patientUserId,
                action = AuditAction.VIEWED_PATIENT,
                detail = current.id,
            )

            skinScanDao.observeByUserId(patientUserId)
                .map { scans ->
                    val rows = scans.map { scan ->
                        val prediction = scanPredictionDao.topPrediction(scan.id)
                        PatientScanRow(
                            scanId = scan.id,
                            capturedAt = scan.capturedAt,
                            imagePath = scan.imagePath,
                            bodyArea = scan.bodyArea,
                            note = scan.note,
                            finding = prediction?.label,
                            confidence = prediction?.confidence,
                            severity = concernBandToSeverity(prediction?.concernBand),
                        )
                    }
                    PatientDetailUiState.Ready(
                        link = current,
                        scans = rows,
                        adherence = computeAdherence(
                            patientUserId = patientUserId,
                            scanTimestamps = scans.map { it.capturedAt },
                            expectedCadenceDays = DoctorDashboardViewModel.DEFAULT_CADENCE_DAYS,
                        ),
                        trend = computeTrend(
                            patientUserId,
                            rows.mapNotNull { row ->
                                val severity = row.severity ?: return@mapNotNull null
                                ScanSeveritySample(
                                    timestamp = row.capturedAt,
                                    severityOrdinal = severity.ordinal,
                                    confidence = row.confidence ?: 0f,
                                )
                            },
                        ),
                    )
                }
                .collect { _state.value = it }
        }
    }

    /**
     * Ends this doctor's own access.
     *
     * Uses the targeted status UPDATE rather than a whole-row write so a revoke
     * racing an unrelated name refresh cannot be undone by it, and keeps
     * `consentGrantedAt` intact — the record that consent *was* given is what
     * lets the audit trail say when access existed and when it stopped.
     */
    fun revokeAccess() {
        val current = link ?: return
        val actor = doctorUserId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            patientLinkDao.updateStatus(
                linkId = current.id,
                status = LinkStatus.REVOKED.name,
                consentGrantedAt = current.consentGrantedAt,
                updatedAt = now,
            )
            auditLogger.record(
                doctorUserId = actor,
                patientUserId = current.patientUserId,
                action = AuditAction.REVOKED_LINK,
                detail = DETAIL_REVOKED_BY_DOCTOR,
                now = now,
            )
            // Stop the scan collector first: it would otherwise re-emit Ready
            // over the terminal state and put the record back on screen.
            loadJob?.cancel()
            _state.value = PatientDetailUiState.Revoked
        }
    }

    private companion object {
        /** Who initiated, in the audit row. Never clinical content. */
        const val DETAIL_REVOKED_BY_DOCTOR = "revoked by doctor"
    }
}
