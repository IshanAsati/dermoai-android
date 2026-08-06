package com.dermoai.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DoctorInviteDao
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.core.domain.model.AuditAction
import com.dermoai.core.domain.model.DoctorInvite
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.domain.model.LinkStatus
import com.dermoai.feature.doctor.data.AuditLogger
import com.dermoai.feature.doctor.data.toDomain
import com.dermoai.feature.doctor.invite.InviteCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.dermoai.core.data.sync.toEntity
import com.dermoai.core.data.sync.DoctorSyncRepository
import com.dermoai.core.common.result.AppResult

/**
 * Why a redemption did not happen.
 *
 * [Unusable] carries the domain's own wording from
 * [DoctorInvite.unusableReason] rather than a re-worded local string: that
 * method already distinguishes cancelled from expired from spent, and each of
 * those tells the patient a different thing to do next.
 */
sealed interface RedeemRejection {
    data object NotFound : RedeemRejection
    data class Unusable(val reason: String) : RedeemRejection

    /**
     * The code was usable when checked and gone by the time consent was given —
     * somebody else took the last use in between. Shown as exactly that rather
     * than as a generic failure, because the patient did nothing wrong and the
     * fix ("ask for another code") is specific.
     */
    data object LostRace : RedeemRejection
    data object Failed : RedeemRejection

    /**
     * The server was never reached — offline, not signed in, or no backend
     * configured — as opposed to [NotFound], where the server answered and
     * said no such code exists. Collapsing the two used to be this flow's
     * biggest diagnostic gap: a patient staring at "no invite matches that
     * code" while the real problem was venue wifi has no way to know to try
     * again versus ask for a fresh code.
     */
    data object Offline : RedeemRejection
}

/** Outcome of looking a code up on the backend. See [RedeemInviteViewModel.fetchRemoteInvite]. */
private sealed interface RemoteInviteLookup {
    data class Found(val invite: DoctorInvite) : RemoteInviteLookup

    /** The server was reached and answered: no invite has this code. */
    data object NotFound : RemoteInviteLookup

    /** The server was not reached at all — offline, no session, not configured. */
    data object Unreachable : RemoteInviteLookup
}

sealed interface RedeemUiState {
    /** Typing. [rejection] is the outcome of the previous attempt, if any. */
    data class Entry(val rejection: RedeemRejection? = null) : RedeemUiState

    data object Checking : RedeemUiState

    /**
     * The code is valid and the patient is being told, in full, what they are
     * about to hand over. Nothing is written to the database in this state.
     */
    data class Consent(val invite: DoctorInvite, val doctor: DoctorProfile) : RedeemUiState

    data object Linking : RedeemUiState

    data class Linked(
        val doctorName: String,
        /** True when the link already existed, so no use was consumed. */
        val alreadyHadAccess: Boolean,
    ) : RedeemUiState
}

/**
 * The patient's side of the invite flow.
 *
 * The shape of this state machine is the consent design. Looking a code up and
 * granting access are two separate, patient-initiated steps with a full
 * disclosure between them — [RedeemUiState.Consent] writes nothing. A flow that
 * linked on a valid code would be collecting a keystroke, not consent, for a
 * decision that hands a stranger someone's medical photographs.
 *
 * The write order matters too: [DoctorInviteDao.incrementUse] runs *before* the
 * link is created, and its return value is checked. It increments and re-tests
 * the cap in one SQL statement precisely so two patients redeeming a single-use
 * code at the same moment cannot both be admitted; the loser gets
 * [RedeemRejection.LostRace] instead of a silently over-used code.
 */
@HiltViewModel
class RedeemInviteViewModel @Inject constructor(
    private val doctorInviteDao: DoctorInviteDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val patientLinkDao: PatientLinkDao,
    private val auditLogger: AuditLogger,
    private val sync: DoctorSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RedeemUiState>(RedeemUiState.Entry())
    val state: StateFlow<RedeemUiState> = _state.asStateFlow()

    /** Always normalised — see [InviteCodes.normalise]. */
    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    /**
     * Normalises on every keystroke rather than on submit, so the field shows
     * the patient the same characters the lookup will use. Typing over a
     * previous rejection clears it — leaving the old error under a new code
     * reads as though the new code failed too.
     */
    fun onCodeChanged(raw: String) {
        _code.value = InviteCodes.normalise(raw)
        val current = _state.value
        if (current is RedeemUiState.Entry && current.rejection != null) {
            _state.value = RedeemUiState.Entry()
        }
    }

    val isCodeComplete: Boolean get() = InviteCodes.isComplete(_code.value)

    /** Looks the code up and, if it is live, moves to the consent disclosure. */
    fun checkCode() {
        val candidate = _code.value
        if (!InviteCodes.isComplete(candidate)) return
        viewModelScope.launch {
            _state.value = RedeemUiState.Checking
            val now = System.currentTimeMillis()
            // Local first: the doctor and patient may be the same device, and a
            // hit here needs no network at all.
            var invite = doctorInviteDao.getByCode(candidate)?.toDomain()
            if (invite == null) {
                // The ordinary case for a real consultation — the code was
                // generated on the clinician's phone and has never existed in
                // this device's database. Fetch it, and the issuing profile,
                // then cache both so the rest of the flow is offline-identical
                // to a locally-issued code.
                when (val remote = fetchRemoteInvite(candidate)) {
                    is RemoteInviteLookup.Found -> invite = remote.invite
                    is RemoteInviteLookup.NotFound -> {
                        _state.value = RedeemUiState.Entry(RedeemRejection.NotFound)
                        return@launch
                    }
                    is RemoteInviteLookup.Unreachable -> {
                        _state.value = RedeemUiState.Entry(RedeemRejection.Offline)
                        return@launch
                    }
                }
            }
            val resolvedInvite = invite ?: run {
                _state.value = RedeemUiState.Entry(RedeemRejection.NotFound)
                return@launch
            }
            val reason = resolvedInvite.unusableReason(now)
            if (reason != null) {
                _state.value = RedeemUiState.Entry(RedeemRejection.Unusable(reason))
                return@launch
            }
            val doctor = doctorProfileDao.getById(resolvedInvite.doctorId)?.toDomain()
            if (doctor == null) {
                // A live code whose issuing profile is missing on this device.
                // Not the patient's problem to interpret, and not something to
                // paper over by linking to an unknown clinician. Most commonly
                // this now means the doctor's device has not yet pushed its
                // profile — see InvitePatientViewModel.pushProfileIfChanged.
                _state.value = RedeemUiState.Entry(RedeemRejection.Failed)
                return@launch
            }
            _state.value = RedeemUiState.Consent(resolvedInvite, doctor)
        }
    }

    /**
     * Looks the code up on the backend and caches what it finds locally.
     *
     * Distinguishes "the server answered and there is no such code"
     * ([RemoteInviteLookup.NotFound]) from "the server was never reached"
     * ([RemoteInviteLookup.Unreachable]) by reading [PullOutcome.fromServer]
     * rather than just the nullable value — a skipped/offline pull returns an
     * empty value that looks identical to a genuine miss unless this is
     * checked first. Collapsing the two was the flow's biggest diagnostic gap:
     * a patient on bad wifi and a patient with a typo saw the exact same
     * message.
     */
    private suspend fun fetchRemoteInvite(code: String): RemoteInviteLookup {
        val now = System.currentTimeMillis()
        sync.ensureSession()
        val outcome = (sync.findInviteByCode(code) as? AppResult.Success)?.data
            ?: return RemoteInviteLookup.Unreachable
        if (!outcome.fromServer) return RemoteInviteLookup.Unreachable
        val remote = outcome.value ?: return RemoteInviteLookup.NotFound

        // The profile has to come across too: the consent screen names the
        // clinician, and linking to an unnamed doctor is exactly what the
        // existing null-profile branch below refuses to do.
        val profile = (sync.pullDoctorProfile(remote.doctorUserId) as? AppResult.Success)
            ?.data?.value
        if (profile != null) {
            runCatching { doctorProfileDao.upsert(profile.toEntity(now)) }
        }
        runCatching { doctorInviteDao.upsert(remote.toEntity(now)) }
        val cached = doctorInviteDao.getByCode(code)?.toDomain()
            ?: return RemoteInviteLookup.NotFound
        return RemoteInviteLookup.Found(cached)
    }

    /** Back out of the disclosure without granting anything. */
    fun declineConsent() {
        _state.value = RedeemUiState.Entry()
    }

    /**
     * The affirmative action. Only this method writes.
     *
     * @param patientUserId the patient's own `AuthUser.id`.
     * @param patientDisplayName snapshotted onto the link so the doctor's list
     *   renders before the patient's profile has synced to their device.
     */
    fun grantConsent(patientUserId: String, patientDisplayName: String) {
        val consent = _state.value as? RedeemUiState.Consent ?: return
        val invite = consent.invite
        val doctor = consent.doctor
        viewModelScope.launch {
            _state.value = RedeemUiState.Linking
            val now = System.currentTimeMillis()

            val outcome = runCatching {
                val existing = patientLinkDao
                    .getByPatientAndDoctor(patientUserId, doctor.id)
                    ?.toDomain()

                // Already linked: return the same answer without burning a use.
                // Re-consenting to access you already granted should not cost
                // the doctor one of a single-use code's uses.
                if (existing != null && existing.grantsAccess) {
                    return@runCatching RedeemUiState.Linked(doctor.fullName, alreadyHadAccess = true)
                }

                // Claim the use first. A zero here means the row moved between
                // the check above and now — the only safe reading is that this
                // patient did not get it.
                val claimed = doctorInviteDao.incrementUse(
                    inviteId = invite.id,
                    now = now,
                    updatedAt = now,
                )
                if (claimed == 0) {
                    return@runCatching RedeemUiState.Entry(RedeemRejection.LostRace)
                }

                // Reuse the existing row's id when re-linking after a revoke:
                // `(doctorId, patientUserId)` is unique, so a fresh id would be
                // a conflict rather than a second link.
                val linkId = existing?.id ?: UUID.randomUUID().toString()
                val link = PatientLinkEntity(
                    id = linkId,
                    // Owning account is the doctor's, per the table convention.
                    userId = doctor.userId,
                    doctorId = doctor.id,
                    patientUserId = patientUserId,
                    patientDisplayName = patientDisplayName,
                    linkedAt = now,
                    createdAt = now,
                    updatedAt = now,
                    status = LinkStatus.ACTIVE.name,
                    consentGrantedAt = now,
                )
                patientLinkDao.upsert(link)
                auditLogger.record(
                    doctorUserId = doctor.userId,
                    patientUserId = patientUserId,
                    action = AuditAction.LINKED_PATIENT,
                    detail = DETAIL_GRANTED_BY_PATIENT,
                    now = now,
                )
                // Publish the link so it reaches the doctor's device. Without
                // this the patient believes they are connected and the doctor's
                // list stays empty — the consent would be real locally and
                // invisible to the person it was granted to.
                sync.pushPatientLink(link, doctor.userId)
                RedeemUiState.Linked(doctor.fullName, alreadyHadAccess = false)
            }

            _state.value = outcome.getOrElse { RedeemUiState.Entry(RedeemRejection.Failed) }
        }
    }

    private companion object {
        /** Who initiated, in the audit row. Never clinical content. */
        const val DETAIL_GRANTED_BY_PATIENT = "granted by patient"
    }
}
