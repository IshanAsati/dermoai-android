package com.dermoai.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.AuditEntryDao
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.domain.model.AuditAction
import com.dermoai.core.domain.model.LinkStatus
import com.dermoai.core.domain.model.PatientLink
import com.dermoai.feature.doctor.data.AuditLogger
import com.dermoai.feature.doctor.data.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One doctor who has, or has had, access to this patient. */
data class LinkedDoctorRow(
    val linkId: String,
    val doctorId: String,
    /** The doctor's `AuthUser.id`, needed to attribute a revocation in the log. */
    val doctorUserId: String,
    val displayName: String,
    val subtitle: String,
    val status: LinkStatus,
    val consentGrantedAt: Long?,
    /** Most recent time this doctor touched the record. Null if never. */
    val lastAccessAt: Long?,
)

/** One line of the access log, already resolved to a readable name. */
data class AccessLogRow(
    val id: String,
    val doctorName: String?,
    val action: AuditAction,
    val at: Long,
)

sealed interface PatientPrivacyUiState {
    data object Loading : PatientPrivacyUiState
    data class Ready(
        val doctors: List<LinkedDoctorRow>,
        val log: List<AccessLogRow>,
    ) : PatientPrivacyUiState
}

/**
 * The patient-facing audit trail — what makes the consent real rather than
 * decorative.
 *
 * Two things have to be true for a consent model to mean anything: the patient
 * can see who used the access, and the patient can end it without asking the
 * holder. This screen is both, which is why it reads from
 * [AuditEntryDao.observeBySubject] (an append-only log the doctor cannot edit)
 * rather than from anything the doctor's device writes on its own terms.
 *
 * Revoked links are listed, not hidden. "Dr X had access from March to June" is
 * information the patient is entitled to keep, and deleting the row on revoke
 * would erase it.
 */
@HiltViewModel
class PatientPrivacyViewModel @Inject constructor(
    private val patientLinkDao: PatientLinkDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val auditEntryDao: AuditEntryDao,
    private val auditLogger: AuditLogger,
) : ViewModel() {

    private val _state = MutableStateFlow<PatientPrivacyUiState>(PatientPrivacyUiState.Loading)
    val state: StateFlow<PatientPrivacyUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** @param patientUserId the signed-in patient's own `AuthUser.id`. */
    fun load(patientUserId: String) {
        // Room flows never complete — drop the previous collector on revisit.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            combine(
                patientLinkDao.observeByPatientUserId(patientUserId),
                auditEntryDao.observeBySubject(patientUserId),
            ) { linkEntities, auditEntities ->
                val links = linkEntities.map { it.toDomain() }
                val entries = auditEntities.map { it.toDomain() }

                // Names are resolved once per distinct id rather than per row:
                // the log is unbounded and never pruned, so a lookup per entry
                // would grow linearly with the patient's whole history.
                val namesByUserId = entries.map { it.actorUserId }.distinct()
                    .associateWith { doctorProfileDao.getByUserId(it)?.fullName }

                PatientPrivacyUiState.Ready(
                    doctors = links.map { link -> toDoctorRow(link, entries) },
                    log = entries.map { entry ->
                        AccessLogRow(
                            id = entry.id,
                            doctorName = namesByUserId[entry.actorUserId],
                            action = entry.action,
                            at = entry.at,
                        )
                    },
                )
            }.collect { _state.value = it }
        }
    }

    private suspend fun toDoctorRow(
        link: PatientLink,
        entries: List<com.dermoai.core.domain.model.AuditEntry>,
    ): LinkedDoctorRow {
        val profile = doctorProfileDao.getById(link.doctorId)?.toDomain()
        return LinkedDoctorRow(
            linkId = link.id,
            doctorId = link.doctorId,
            doctorUserId = profile?.userId.orEmpty(),
            displayName = profile?.fullName.orEmpty(),
            subtitle = listOfNotNull(profile?.specialty, profile?.institution)
                .filter { it.isNotEmpty() }
                .joinToString(" · "),
            status = link.status,
            consentGrantedAt = link.consentGrantedAt,
            // `observeBySubject` orders by `at` DESC, so the first match is newest.
            lastAccessAt = entries.firstOrNull {
                it.actorUserId == profile?.userId && it.action == AuditAction.VIEWED_PATIENT
            }?.at,
        )
    }

    /**
     * Ends a doctor's access at the patient's own request.
     *
     * Uses the targeted status UPDATE and keeps `consentGrantedAt`, so the row
     * still records that consent was given and when it ended — the delete path
     * exists for "erase this entirely" and is deliberately not what a revoke
     * does.
     */
    fun revokeDoctor(row: LinkedDoctorRow, patientUserId: String) {
        viewModelScope.launch {
            val existing = patientLinkDao.getById(row.linkId)?.toDomain() ?: return@launch
            val now = System.currentTimeMillis()
            patientLinkDao.updateStatus(
                linkId = existing.id,
                status = LinkStatus.REVOKED.name,
                consentGrantedAt = existing.consentGrantedAt,
                updatedAt = now,
            )
            if (row.doctorUserId.isNotEmpty()) {
                auditLogger.record(
                    doctorUserId = row.doctorUserId,
                    patientUserId = patientUserId,
                    action = AuditAction.REVOKED_LINK,
                    detail = DETAIL_REVOKED_BY_PATIENT,
                    now = now,
                )
            }
        }
    }

    private companion object {
        /** Who initiated, in the audit row. Never clinical content. */
        const val DETAIL_REVOKED_BY_PATIENT = "revoked by patient"
    }
}
