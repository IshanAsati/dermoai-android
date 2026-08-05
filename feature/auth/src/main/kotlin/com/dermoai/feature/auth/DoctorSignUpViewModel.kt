package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.common.ui.UiState
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.UserProfileEntity
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.model.VerificationStatus
import com.dermoai.core.domain.repository.AuthRepository
import com.dermoai.core.domain.usecase.auth.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Registration for a healthcare professional.
 *
 * Separate from [AuthViewModel] rather than another mode on it because the two
 * flows only *look* alike: this one collects nine credential fields, validates
 * them per-field, and — the part that matters — writes two extra rows after the
 * account exists. Folding that into the patient form would make every patient
 * sign-up carry doctor state it never uses.
 *
 * What this ViewModel deliberately does **not** do is verify anything. It writes
 * [VerificationStatus.PENDING] and stops. Verification is a manual, out-of-band
 * review; there is no code path here — and there must never be one — that lets a
 * user move their own claim to VERIFIED.
 */
@HiltViewModel
class DoctorSignUpViewModel @Inject constructor(
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val doctorProfileDao: DoctorProfileDao,
    private val userProfileDao: UserProfileDao,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(DoctorSignUpFormState())
    val formState: StateFlow<DoctorSignUpFormState> = _formState.asStateFlow()

    private val _signUpResult = MutableStateFlow<UiState<AuthUser>>(UiState.Idle)
    val signUpResult: StateFlow<UiState<AuthUser>> = _signUpResult.asStateFlow()

    val isLocalAuthMode: Boolean = authRepository.isLocalAuthMode()

    // ── Field edits ─────────────────────────────────────────────────────────
    // Every edit marks its field "touched" so an error only appears once the
    // user has actually been in the field. A form that shows six red messages
    // before a single keystroke reads as broken, not as helpful.

    fun onFullNameChange(value: String) = edit(DoctorSignUpField.FULL_NAME) { it.copy(fullName = value) }

    fun onEmailChange(value: String) = edit(DoctorSignUpField.EMAIL) { it.copy(email = value) }

    fun onPasswordChange(value: String) = edit(DoctorSignUpField.PASSWORD) { it.copy(password = value) }

    fun onRegistrationNumberChange(value: String) =
        edit(DoctorSignUpField.REGISTRATION_NUMBER) { it.copy(registrationNumber = value) }

    fun onSpecialtyChange(value: String) = edit(null) { it.copy(specialty = value) }

    fun onInstitutionChange(value: String) = edit(null) { it.copy(institution = value) }

    fun onYearsExperienceChange(value: String) =
        edit(DoctorSignUpField.YEARS_EXPERIENCE) { it.copy(yearsExperience = value) }

    fun onBioChange(value: String) = edit(null) { it.copy(bio = value) }

    fun onQualificationDraftChange(value: String) = edit(null) { it.copy(qualificationDraft = value) }

    /**
     * Commits the draft text as a qualification chip. Blank and duplicate values
     * are dropped silently — the alternative is an error message for a mistake
     * the user can see they made.
     */
    fun addQualification() {
        val draft = _formState.value.qualificationDraft.trim()
        if (draft.isEmpty()) return
        _formState.update { state ->
            val existing = state.qualifications
            if (existing.any { it.equals(draft, ignoreCase = true) }) {
                state.copy(qualificationDraft = "")
            } else {
                state.copy(
                    qualifications = existing + draft,
                    qualificationDraft = "",
                    touched = state.touched + DoctorSignUpField.QUALIFICATIONS,
                    submitError = null,
                )
            }
        }
    }

    fun removeQualification(value: String) {
        _formState.update { state ->
            state.copy(
                qualifications = state.qualifications.filterNot { it == value },
                touched = state.touched + DoctorSignUpField.QUALIFICATIONS,
                submitError = null,
            )
        }
    }

    /**
     * Creates the account, then records the credential claim.
     *
     * Guarded by [DoctorSignUpFormState.isValid] as well as the disabled button,
     * because a hardware keyboard's IME action can fire submit on a form the
     * button would have refused.
     */
    fun submit() {
        val form = _formState.value
        if (!form.isValid) {
            // Reveal everything: the user asked to submit, so hiding the reason
            // behind "you haven't touched that field yet" is now unhelpful.
            _formState.update { it.copy(touched = DoctorSignUpField.entries.toSet()) }
            return
        }
        viewModelScope.launch {
            _signUpResult.value = UiState.Loading
            val result = signUpWithEmail(
                email = form.email.trim(),
                password = form.password,
                displayName = form.fullName.trim(),
            )
            when (result) {
                is AppResult.Success -> onAccountCreated(result.data, form)
                is AppResult.Error -> {
                    val message = result.message ?: SIGN_UP_FAILED
                    _signUpResult.value = UiState.Error(message)
                    _formState.update { it.copy(submitError = message) }
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun clearResult() {
        _signUpResult.value = UiState.Idle
    }

    private suspend fun onAccountCreated(user: AuthUser, form: DoctorSignUpFormState) {
        val persisted = runCatching { persistDoctorClaim(user, form) }
        if (persisted.isSuccess) {
            _signUpResult.value = UiState.Success(user)
        } else {
            // The account exists but the credentials did not land, so reporting
            // success would drop the user into a doctor surface with no claim on
            // file and nothing for a reviewer to look at. Say so instead.
            _signUpResult.value = UiState.Error(PROFILE_SAVE_FAILED)
            _formState.update { it.copy(submitError = PROFILE_SAVE_FAILED) }
        }
    }

    /**
     * Writes the credential claim, then flips the account's role.
     *
     * Order is load-bearing. The role column is what routes the account to the
     * doctor surface, so it moves last: if the profile write fails, the user is
     * still a patient and can retry, whereas the reverse ordering would strand
     * them on a doctor surface with no credentials behind it.
     */
    private suspend fun persistDoctorClaim(user: AuthUser, form: DoctorSignUpFormState) {
        val now = System.currentTimeMillis()
        val existing = doctorProfileDao.getByUserId(user.id)
        doctorProfileDao.upsert(
            DoctorProfileEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = user.id,
                fullName = form.fullName.trim(),
                qualifications = DoctorProfileEntity.encodeQualifications(form.qualifications),
                registrationNumber = form.registrationNumber.trim(),
                specialty = form.specialty.trim(),
                institution = form.institution.trim(),
                yearsExperience = form.yearsExperienceValue ?: 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                // PENDING, never VERIFIED. A human decides the rest.
                verificationStatus = VerificationStatus.PENDING.name,
                verifiedAt = null,
                bio = form.bio.trim(),
            ),
        )

        val profile = userProfileDao.getById(user.id)
        userProfileDao.upsert(
            profile?.copy(
                role = UserProfileEntity.ROLE_DOCTOR,
                updatedAt = now,
                syncStatus = UserProfileEntity.SYNC_PENDING,
            ) ?: UserProfileEntity(
                // The auth repository normally writes this row during sign-up;
                // this branch only covers an implementation that doesn't, so the
                // role still lands rather than being silently lost.
                id = user.id,
                email = user.email,
                displayName = form.fullName.trim(),
                createdAt = now,
                updatedAt = now,
                role = UserProfileEntity.ROLE_DOCTOR,
            ),
        )
    }

    private fun edit(
        field: DoctorSignUpField?,
        transform: (DoctorSignUpFormState) -> DoctorSignUpFormState,
    ) {
        _formState.update { state ->
            transform(state).copy(
                touched = if (field == null) state.touched else state.touched + field,
                submitError = null,
            )
        }
    }

    private companion object {
        const val SIGN_UP_FAILED = "Sign-up failed"
        const val PROFILE_SAVE_FAILED =
            "Your account was created but your credentials could not be saved. Sign in and try again."
    }
}

/**
 * The fields that can carry a validation error.
 *
 * Specialty, institution and bio are absent on purpose: a locum with no fixed
 * institution and a generalist with no sub-specialty are both real, and blocking
 * them buys nothing a reviewer cannot resolve.
 */
enum class DoctorSignUpField {
    FULL_NAME,
    EMAIL,
    PASSWORD,
    REGISTRATION_NUMBER,
    YEARS_EXPERIENCE,
    QUALIFICATIONS,
}

/**
 * Validation outcomes, as an enum rather than a message string.
 *
 * The ViewModel has no access to localised resources, and the codebase requires
 * user-visible text to come from `strings.xml`. Returning a reason lets the
 * screen pick the translated sentence and lets tests assert on the reason
 * instead of on English prose that a translator may later reword.
 */
enum class DoctorSignUpError {
    REQUIRED,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    NO_QUALIFICATIONS,
    YEARS_NOT_A_NUMBER,
    YEARS_OUT_OF_RANGE,
}

/**
 * Controlled fields for [DoctorSignUpScreen], plus the derived validation state.
 *
 * Errors are computed from the state rather than stored alongside it so the two
 * can never disagree — a stored copy has to be recomputed on every edit, and the
 * one edit that forgets leaves a stale error next to a valid field.
 */
data class DoctorSignUpFormState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val registrationNumber: String = "",
    val specialty: String = "",
    val institution: String = "",
    val yearsExperience: String = "",
    /** Text in the "add a qualification" field, not yet committed to the list. */
    val qualificationDraft: String = "",
    val qualifications: List<String> = emptyList(),
    val bio: String = "",
    /** Fields the user has edited; controls whether an error is shown yet. */
    val touched: Set<DoctorSignUpField> = emptySet(),
    /** Failure from the sign-up call itself, not from field validation. */
    val submitError: String? = null,
) {

    /** Parsed years of experience, or null when the text is not a whole number. */
    val yearsExperienceValue: Int? get() = yearsExperience.trim().toIntOrNull()

    val errors: Map<DoctorSignUpField, DoctorSignUpError> get() = buildMap {
        if (fullName.isBlank()) put(DoctorSignUpField.FULL_NAME, DoctorSignUpError.REQUIRED)

        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isBlank() -> put(DoctorSignUpField.EMAIL, DoctorSignUpError.REQUIRED)
            !isPlausibleEmail(trimmedEmail) ->
                put(DoctorSignUpField.EMAIL, DoctorSignUpError.INVALID_EMAIL)
        }

        when {
            password.isEmpty() -> put(DoctorSignUpField.PASSWORD, DoctorSignUpError.REQUIRED)
            // Mirrors SignUpWithEmailUseCase. Diverging would either reject
            // passwords the backend accepts or let the backend reject ours.
            password.length < MIN_PASSWORD_LENGTH ->
                put(DoctorSignUpField.PASSWORD, DoctorSignUpError.PASSWORD_TOO_SHORT)
        }

        if (registrationNumber.isBlank()) {
            put(DoctorSignUpField.REGISTRATION_NUMBER, DoctorSignUpError.REQUIRED)
        }

        if (qualifications.isEmpty()) {
            put(DoctorSignUpField.QUALIFICATIONS, DoctorSignUpError.NO_QUALIFICATIONS)
        }

        val years = yearsExperienceValue
        when {
            yearsExperience.isBlank() -> put(DoctorSignUpField.YEARS_EXPERIENCE, DoctorSignUpError.REQUIRED)
            years == null -> put(DoctorSignUpField.YEARS_EXPERIENCE, DoctorSignUpError.YEARS_NOT_A_NUMBER)
            years !in MIN_YEARS..MAX_YEARS ->
                put(DoctorSignUpField.YEARS_EXPERIENCE, DoctorSignUpError.YEARS_OUT_OF_RANGE)
        }
    }

    val isValid: Boolean get() = errors.isEmpty()

    /**
     * The error to render for [field] right now — null until the user has been
     * in the field or has attempted to submit.
     */
    fun visibleError(field: DoctorSignUpField): DoctorSignUpError? =
        errors[field]?.takeIf { field in touched }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        const val MIN_YEARS = 0

        /**
         * A career longer than this is a typo (or a swapped-in birth year) far
         * more often than it is a real practitioner.
         */
        const val MAX_YEARS = 70

        /**
         * Same shape check the auth repository uses. Deliberately loose: the only
         * proof an address works is mail arriving at it, and a strict regex here
         * mostly rejects valid unusual addresses.
         */
        fun isPlausibleEmail(value: String): Boolean =
            !value.contains(' ') &&
                value.count { it == '@' } == 1 &&
                value.substringBefore('@').isNotEmpty() &&
                value.substringAfter('@').let { domain ->
                    domain.contains('.') &&
                        !domain.startsWith('.') &&
                        !domain.endsWith('.') &&
                        domain.substringAfterLast('.').length >= 2
                }
    }
}
