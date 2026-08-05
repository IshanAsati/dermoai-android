package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.domain.model.UserRole
import com.dermoai.core.domain.model.VerificationStatus
import com.dermoai.core.domain.usecase.auth.ObserveAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Answers the two questions navigation needs before choosing a surface: is this
 * account a doctor, and has that claim actually been verified?
 *
 * Kept out of [SessionViewModel] on purpose. That one gates the splash screen
 * for every account in the app, and folding two more Room observers into it
 * would make every patient launch wait on doctor tables it will never read, and
 * would couple the "am I signed in" decision to the "am I credentialed" one.
 * They also have different lifetimes: session state matters from cold start,
 * doctor state only once a session exists. Navigation can hold both at the root
 * with two `hiltViewModel()` calls; nothing here duplicates SessionViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DoctorSessionViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val userProfileDao: UserProfileDao,
    private val doctorProfileDao: DoctorProfileDao,
) : ViewModel() {

    /**
     * Doctor status for the signed-in account, re-derived whenever the session
     * or either row changes — so a verification decision written by a sync lands
     * on screen without the user restarting the app.
     */
    val doctorSession: StateFlow<DoctorSessionState> = observeAuthState()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(DoctorSessionState(isLoading = false))
            } else {
                combine(
                    userProfileDao.observeById(user.id),
                    doctorProfileDao.observeByUserId(user.id),
                ) { profileRow, doctorRow ->
                    // The persisted row wins over AuthUser.role: in Firebase mode
                    // the auth user object carries no role claim at all, so
                    // trusting it would read every doctor as a patient. Fall back
                    // to the session's role only while the row is still absent.
                    val role = profileRow?.role?.let(UserRole::fromStorage) ?: user.role
                    DoctorSessionState(
                        isLoading = false,
                        userId = user.id,
                        role = role,
                        profile = doctorRow?.toDoctorProfile(),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DoctorSessionState(),
        )
}

/**
 * What navigation is allowed to branch on.
 *
 * Note what this does *not* expose: any way to change the status. Verification
 * is a manual review, so this type is read-only by construction.
 */
data class DoctorSessionState(
    /** True until the first emission; routing must wait rather than guess. */
    val isLoading: Boolean = true,
    val userId: String? = null,
    val role: UserRole = UserRole.PATIENT,
    /** The credential claim, or null when the account never made one. */
    val profile: DoctorProfile? = null,
) {
    /**
     * Whether to show doctor surfaces at all. Driven by the role column, not by
     * the presence of a profile row, so a half-written sign-up leaves the user
     * on the patient surface — the safe side of that failure.
     */
    val isDoctor: Boolean get() = role == UserRole.DOCTOR

    /**
     * UNVERIFIED when a doctor account somehow has no claim on file, so callers
     * never have to decide what a null means.
     */
    val verificationStatus: VerificationStatus
        get() = profile?.verificationStatus ?: VerificationStatus.UNVERIFIED

    /**
     * The only flag that may unlock patient data. Both halves are required: a
     * role without a verified claim is just an unreviewed assertion, and a
     * verified claim on a patient account is a stale row.
     */
    val isVerifiedDoctor: Boolean get() = isDoctor && profile?.isVerified == true

    /**
     * A doctor who is not through review yet — route these to
     * [DoctorStatusScreen] rather than the dashboard.
     */
    val needsVerification: Boolean get() = isDoctor && !isVerifiedDoctor
}

/**
 * Room row to domain model.
 *
 * Lives here rather than in `:core:database` because the entity is storage's
 * shape and the mapping is a consumer's concern; the status is parsed leniently
 * for the same reason [UserRole.fromStorage] is — an unreadable value must
 * downgrade the claim, never crash the session or silently promote it.
 */
internal fun DoctorProfileEntity.toDoctorProfile(): DoctorProfile = DoctorProfile(
    id = id,
    userId = userId,
    fullName = fullName,
    qualifications = DoctorProfileEntity.decodeQualifications(qualifications),
    registrationNumber = registrationNumber,
    specialty = specialty,
    institution = institution,
    yearsExperience = yearsExperience,
    verificationStatus = VerificationStatus.entries
        .firstOrNull { it.name == verificationStatus }
        ?: VerificationStatus.UNVERIFIED,
    verifiedAt = verifiedAt,
    bio = bio,
)
