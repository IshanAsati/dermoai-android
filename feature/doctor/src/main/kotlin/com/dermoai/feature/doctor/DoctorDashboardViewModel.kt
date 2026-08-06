package com.dermoai.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.domain.model.PatientLink
import com.dermoai.core.domain.usecase.doctor.ComputeAdherenceUseCase
import com.dermoai.core.domain.usecase.doctor.ComputeTrendUseCase
import com.dermoai.core.domain.usecase.doctor.ScanSeveritySample
import com.dermoai.feature.doctor.data.concernBandToSeverity
import com.dermoai.feature.doctor.data.toDomain
import com.dermoai.feature.doctor.triage.TriageRanking
import com.dermoai.feature.doctor.triage.TriageRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dermoai.core.data.sync.toEntity
import com.dermoai.core.data.sync.DoctorSyncRepository
import com.dermoai.core.common.result.AppResult

/**
 * What the triage inbox can be in.
 *
 * [NoProfile] and [Locked] are separate states rather than one "can't show you
 * anything" because the actions differ: the first needs the clinician to fill
 * in their credentials, the second needs them to wait for a human. Collapsing
 * them would produce a screen that tells a doctor to do nothing while nothing
 * is happening.
 */
sealed interface DoctorDashboardUiState {
    data object Loading : DoctorDashboardUiState

    /** Signed in, but this account has never claimed to be a clinician. */
    data object NoProfile : DoctorDashboardUiState

    /**
     * A profile exists but is not [DoctorProfile.isVerified]. No patient data is
     * loaded in this state — not hidden behind a flag in the row, not fetched
     * and then not drawn: the query is never run.
     */
    data class Locked(val profile: DoctorProfile) : DoctorDashboardUiState

    data class Ready(
        val profile: DoctorProfile,
        /** Already ordered by [TriageRanking]. The screen must not re-sort. */
        val rows: List<TriageRow>,
    ) : DoctorDashboardUiState
}

/**
 * Backs the doctor's triage inbox.
 *
 * Two things here are load-bearing rather than incidental:
 *
 *  1. **Verification gates the query, not the render.** The patient-link flow is
 *     only subscribed to inside the VERIFIED branch of [flatMapLatest], so an
 *     unverified doctor's device never reads another person's scans into memory
 *     at all. A gate applied in the composable would be one `if` away from
 *     leaking.
 *  2. **Everything is derived per read.** Adherence and trend are recomputed
 *     from timestamps and predictions on every emission rather than stored,
 *     because a persisted "GOOD" goes stale the moment the clock moves and a
 *     dashboard showing yesterday's adherence is worse than one showing none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoctorDashboardViewModel @Inject constructor(
    private val doctorProfileDao: DoctorProfileDao,
    private val patientLinkDao: PatientLinkDao,
    private val skinScanDao: SkinScanDao,
    private val scanPredictionDao: ScanPredictionDao,
    private val computeAdherence: ComputeAdherenceUseCase,
    private val computeTrend: ComputeTrendUseCase,
    private val sync: DoctorSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DoctorDashboardUiState>(DoctorDashboardUiState.Loading)
    val state: StateFlow<DoctorDashboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * @param userId the signed-in account's `AuthUser.id` — not a
     *   `DoctorProfile.id`. The profile is resolved from it so navigation never
     *   has to know which of the two identifiers a screen wants.
     */
    fun load(userId: String) {
        // Room flows never complete, so a re-entry into this tab would otherwise
        // leave the previous collector alive and stack one more on every visit.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Patients consent on their own device, so the link is created over
            // there. Pull before observing, otherwise the doctor's list shows
            // only patients who happened to link on this phone — which is no
            // list at all in a real consultation.
            //
            // Fire-and-forget on purpose: the Room flow below is the source of
            // truth for the UI, so a failed pull just means the screen shows
            // what this device already knows, which is the offline behaviour.
            refreshFromBackend(userId)

            doctorProfileDao.observeByUserId(userId)
                .flatMapLatest { entity ->
                    val profile = entity?.toDomain()
                    when {
                        profile == null -> flowOf(DoctorDashboardUiState.NoProfile)
                        !profile.isVerified -> flowOf(DoctorDashboardUiState.Locked(profile))
                        else -> triageFlow(profile).map { rows ->
                            DoctorDashboardUiState.Ready(profile, rows)
                        }
                    }
                }
                .collect { _state.value = it }
        }
    }

    /**
     * Pulls links this doctor's patients created elsewhere into local Room.
     *
     * Every failure is swallowed: no backend configured, no session, no signal.
     * The dashboard is driven by the Room flow either way, so the worst case is
     * a list that is merely stale rather than a screen that errors.
     */
    private suspend fun refreshFromBackend(userId: String) {
        val profile = doctorProfileDao.getByUserId(userId) ?: return
        sync.ensureSession()
        val remote = (sync.pullPatientLinksForDoctor(profile.id) as? AppResult.Success)
            ?.data?.value.orEmpty()
        if (remote.isEmpty()) return
        val now = System.currentTimeMillis()
        runCatching {
            // Owner is the doctor's account, matching the table convention the
            // local write path uses — otherwise the same link would arrive with
            // a different userId than the one written on this device and show up
            // twice.
            patientLinkDao.upsertAll(remote.map { it.toEntity(userId, now) })
        }
    }

    /**
     * Active, consented links → one ranked row each.
     *
     * `observeActiveByDoctorId` filters on ACTIVE *and* a non-null consent
     * timestamp in SQL, so a row force-set to ACTIVE without the patient ever
     * accepting cannot reach this list.
     */
    private fun triageFlow(profile: DoctorProfile): Flow<List<TriageRow>> =
        patientLinkDao.observeActiveByDoctorId(profile.id)
            .flatMapLatest { linkEntities ->
                val links = linkEntities.map { it.toDomain() }
                if (links.isEmpty()) {
                    // `combine` over an empty list never emits, which would leave
                    // the screen on its skeletons forever for a doctor with no
                    // patients — exactly the case the empty state is for.
                    flowOf(emptyList())
                } else {
                    combine(links.map { rowFlow(it) }) { rows ->
                        TriageRanking.rank(rows.toList())
                    }
                }
            }

    private fun rowFlow(link: PatientLink): Flow<TriageRow> =
        skinScanDao.observeByUserId(link.patientUserId)
            .map { scans -> buildRow(link, scans) }

    private suspend fun buildRow(link: PatientLink, scans: List<SkinScanEntity>): TriageRow {
        // `observeByUserId` orders by capturedAt DESC, so the head is the newest.
        val newest = scans.firstOrNull()
        val newestPrediction = newest?.let { scanPredictionDao.topPrediction(it.id) }

        // Only scans that actually produced a prediction can contribute to a
        // trend; a photo the model never scored is not evidence of stability.
        val samples = scans.mapNotNull { scan ->
            val prediction = scanPredictionDao.topPrediction(scan.id) ?: return@mapNotNull null
            val severity = concernBandToSeverity(prediction.concernBand) ?: return@mapNotNull null
            ScanSeveritySample(
                timestamp = scan.capturedAt,
                severityOrdinal = severity.ordinal,
                confidence = prediction.confidence,
            )
        }

        return TriageRow(
            patientUserId = link.patientUserId,
            displayName = link.patientDisplayName,
            linkId = link.id,
            latestSeverity = concernBandToSeverity(newestPrediction?.concernBand),
            latestFinding = newestPrediction?.label,
            lastScanAt = newest?.capturedAt,
            adherence = computeAdherence(
                patientUserId = link.patientUserId,
                scanTimestamps = scans.map { it.capturedAt },
                expectedCadenceDays = DEFAULT_CADENCE_DAYS,
            ),
            trend = computeTrend(link.patientUserId, samples),
        )
    }

    companion object {
        /**
         * Per-patient cadence is not modelled anywhere yet, so every patient is
         * measured against weekly. Kept as a named constant rather than a literal
         * so the one place to change when cadence becomes a prescribed field is
         * obvious.
         */
        const val DEFAULT_CADENCE_DAYS: Int = 7
    }
}
