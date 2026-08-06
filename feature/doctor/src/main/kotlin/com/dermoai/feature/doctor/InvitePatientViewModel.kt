package com.dermoai.feature.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DoctorInviteDao
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.domain.model.DoctorInvite
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.feature.doctor.data.toDomain
import com.dermoai.feature.doctor.invite.InviteCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.asKotlinRandom
import com.dermoai.core.data.sync.DoctorSyncRepository

sealed interface InviteUiState {
    data object Loading : InviteUiState

    /** No clinician profile on this account, so there is nothing to issue codes against. */
    data object NoProfile : InviteUiState

    data class Ready(
        val profile: DoctorProfile,
        /** Newest first, as the DAO returns them. Includes spent and expired codes. */
        val invites: List<DoctorInvite>,
    ) : InviteUiState
}

/**
 * Issues and manages invite codes.
 *
 * Codes are drawn from a [SecureRandom], not [kotlin.random.Random.Default].
 * The code is the credential that grants a clinician sight of someone's medical
 * photos; eight characters over a 31-symbol alphabet is only ~40 bits, and a
 * predictable generator turns that into no bits at all. Expiry and a use cap
 * are the other half of that defence and are always set.
 *
 * Generation retries on collision rather than trusting the generator: the
 * `code` column carries a unique index, so a repeat would otherwise surface as
 * an insert failure mid-consultation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvitePatientViewModel @Inject constructor(
    private val doctorProfileDao: DoctorProfileDao,
    private val doctorInviteDao: DoctorInviteDao,
    private val sync: DoctorSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    /** The invite whose QR is on screen. Defaults to the newest usable one. */
    private val _selectedInviteId = MutableStateFlow<String?>(null)
    val selectedInviteId: StateFlow<String?> = _selectedInviteId.asStateFlow()

    private val _expiryDays = MutableStateFlow(DEFAULT_EXPIRY_DAYS)
    val expiryDays: StateFlow<Int> = _expiryDays.asStateFlow()

    private val _maxUses = MutableStateFlow(DEFAULT_MAX_USES)
    val maxUses: StateFlow<Int> = _maxUses.asStateFlow()

    /** Set when generation gave up; cleared on the next attempt. */
    private val _generationFailed = MutableStateFlow(false)
    val generationFailed: StateFlow<Boolean> = _generationFailed.asStateFlow()

    private var loadJob: Job? = null
    private var profile: DoctorProfile? = null

    private val random = SecureRandom().asKotlinRandom()

    /** @param userId the signed-in doctor's `AuthUser.id`. */
    fun load(userId: String) {
        // Room flows never complete — drop the previous collector on revisit.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            doctorProfileDao.observeByUserId(userId)
                .flatMapLatest { entity ->
                    val doctor = entity?.toDomain()
                    profile = doctor
                    if (doctor == null) {
                        flowOf(InviteUiState.NoProfile)
                    } else {
                        doctorInviteDao.observeByDoctorId(doctor.id).map { rows ->
                            InviteUiState.Ready(doctor, rows.map { it.toDomain() })
                        }
                    }
                }
                .collect { next ->
                    _state.value = next
                    if (next is InviteUiState.Ready && _selectedInviteId.value == null) {
                        val now = System.currentTimeMillis()
                        _selectedInviteId.value = next.invites.firstOrNull { it.isUsable(now) }?.id
                    }
                }
        }
    }

    fun setExpiryDays(days: Int) {
        _expiryDays.value = days.coerceAtLeast(1)
    }

    fun setMaxUses(uses: Int) {
        _maxUses.value = uses.coerceAtLeast(1)
    }

    fun selectInvite(inviteId: String) {
        _selectedInviteId.value = inviteId
    }

    /**
     * Creates a code with the currently configured expiry and use cap, and
     * selects it so its QR is what the patient is shown.
     */
    fun createInvite() {
        val doctor = profile ?: return
        viewModelScope.launch {
            _generationFailed.value = false
            val code = generateUniqueCode()
            if (code == null) {
                _generationFailed.value = true
                return@launch
            }
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val entity = DoctorInviteEntity(
                id = id,
                userId = doctor.userId,
                doctorId = doctor.id,
                code = code,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + _expiryDays.value * DAY_MS,
                maxUses = _maxUses.value,
            )
            val result = runCatching { doctorInviteDao.upsert(entity) }
            if (result.isSuccess) {
                _selectedInviteId.value = id
                // Publish so the patient's phone can find this code. A code that
                // only exists in this device's Room is unredeemable anywhere
                // else, which is precisely the bug this fixes.
                //
                // Deliberately after the local write and deliberately not
                // failing the invite: the code is valid locally regardless, and
                // a doctor with no signal should still get a code they can read
                // out. The push returns a degraded success when offline.
                sync.ensureSession()
                sync.pushDoctorInvite(entity, doctor.userId)
            } else {
                _generationFailed.value = true
            }
        }
    }

    /**
     * Cancels a code. The row stays — a doctor's list of outstanding codes has
     * to be able to show that a code was cancelled rather than silently losing
     * the one a patient is still holding.
     */
    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            doctorInviteDao.revoke(inviteId, System.currentTimeMillis())
        }
    }

    /**
     * @return a code no existing row holds, or null after [MAX_CODE_ATTEMPTS]
     *   collisions — which at this alphabet size means something is wrong with
     *   the generator, not that the doctor was unlucky.
     */
    private suspend fun generateUniqueCode(): String? {
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = InviteCodes.generate(random)
            if (doctorInviteDao.getByCode(candidate) == null) return candidate
        }
        return null
    }

    companion object {
        const val DEFAULT_EXPIRY_DAYS: Int = 7
        const val DEFAULT_MAX_USES: Int = 1

        /** Offered on the screen; a code that never expires is not offered at all. */
        val EXPIRY_OPTIONS: List<Int> = listOf(1, 7, 30)
        val USES_OPTIONS: List<Int> = listOf(1, 5, 20)

        private const val MAX_CODE_ATTEMPTS = 8
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
