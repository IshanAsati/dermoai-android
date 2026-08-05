package com.dermoai.feature.auth

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validation is the only thing standing between a made-up registration number
 * and a row that a reviewer will later be asked to trust, and the submit path is
 * the only place in the app that writes a credential claim. These cover the two
 * kinds of failure that matter: a form that accepts something it should not, and
 * a submit that records something other than PENDING.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoctorSignUpViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private class FakeAuthRepository(
        private val failWith: String? = null,
    ) : AuthRepository {
        var signUpCalls = 0
        var lastEmail: String? = null
        var lastDisplayName: String? = null

        override fun observeAuthState(): Flow<AuthUser?> = flowOf(null)

        override suspend fun getCurrentUser(): AuthUser? = null

        override suspend fun signInWithEmail(email: String, password: String): AppResult<AuthUser> =
            AppResult.Error(UnsupportedOperationException(), "not used")

        override suspend fun signUpWithEmail(
            email: String,
            password: String,
            displayName: String,
        ): AppResult<AuthUser> {
            signUpCalls++
            lastEmail = email
            lastDisplayName = displayName
            failWith?.let { return AppResult.Error(IllegalStateException(it), it) }
            return AppResult.Success(
                AuthUser(id = USER_ID, email = email, displayName = displayName),
            )
        }

        override suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser> =
            AppResult.Error(UnsupportedOperationException(), "not used")

        override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

        override fun isLocalAuthMode(): Boolean = true
    }

    private class FakeDoctorProfileDao : DoctorProfileDao {
        val rows = mutableMapOf<String, DoctorProfileEntity>()

        override fun observeByUserId(userId: String): Flow<DoctorProfileEntity?> =
            MutableStateFlow(rows.values.firstOrNull { it.userId == userId })

        override suspend fun getByUserId(userId: String): DoctorProfileEntity? =
            rows.values.firstOrNull { it.userId == userId }

        override suspend fun getById(doctorId: String): DoctorProfileEntity? = rows[doctorId]

        override suspend fun upsert(profile: DoctorProfileEntity) {
            rows[profile.id] = profile
        }

        override suspend fun updateVerification(
            doctorId: String,
            status: String,
            verifiedAt: Long?,
            updatedAt: Long,
        ) {
            rows[doctorId]?.let {
                rows[doctorId] = it.copy(
                    verificationStatus = status,
                    verifiedAt = verifiedAt,
                    updatedAt = updatedAt,
                )
            }
        }

        override suspend fun deleteById(doctorId: String) {
            rows.remove(doctorId)
        }
    }

    private class FakeUserProfileDao(seed: UserProfileEntity? = null) : UserProfileDao {
        val rows = mutableMapOf<String, UserProfileEntity>()

        init {
            seed?.let { rows[it.id] = it }
        }

        override fun observeById(userId: String): Flow<UserProfileEntity?> =
            MutableStateFlow(rows[userId])

        override suspend fun getById(userId: String): UserProfileEntity? = rows[userId]

        override suspend fun upsert(profile: UserProfileEntity) {
            rows[profile.id] = profile
        }

        override suspend fun deleteById(userId: String) {
            rows.remove(userId)
        }
    }

    private fun viewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        doctorDao: FakeDoctorProfileDao = FakeDoctorProfileDao(),
        userDao: FakeUserProfileDao = FakeUserProfileDao(seededProfile()),
    ) = DoctorSignUpViewModel(
        signUpWithEmail = SignUpWithEmailUseCase(auth),
        doctorProfileDao = doctorDao,
        userProfileDao = userDao,
        authRepository = auth,
    )

    /** Fills every field with something acceptable; individual tests break one. */
    private fun DoctorSignUpViewModel.fillValidForm() {
        onFullNameChange("Dr Asha Rao")
        onEmailChange("asha@clinic.example")
        onPasswordChange("s3cret!")
        onRegistrationNumberChange("MCI-889231")
        onSpecialtyChange("Dermatology")
        onInstitutionChange("City Skin Clinic")
        onYearsExperienceChange("12")
        onQualificationDraftChange("MBBS")
        addQualification()
        onBioChange("Clinical dermatology, 12 years.")
    }

    private fun errorFor(vm: DoctorSignUpViewModel, field: DoctorSignUpField) =
        vm.formState.value.errors[field]

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    fun `an untouched form is invalid`() {
        // Catches a submit button that starts enabled — the fastest way to write
        // an empty credential claim.
        val vm = viewModel()
        assertFalse(vm.formState.value.isValid)
    }

    @Test
    fun `an untouched form flags every required field`() {
        // Catches validation that only ever looks at one field.
        val vm = viewModel()
        val errors = vm.formState.value.errors
        assertEquals(DoctorSignUpError.REQUIRED, errors[DoctorSignUpField.FULL_NAME])
        assertEquals(DoctorSignUpError.REQUIRED, errors[DoctorSignUpField.EMAIL])
        assertEquals(DoctorSignUpError.REQUIRED, errors[DoctorSignUpField.PASSWORD])
        assertEquals(DoctorSignUpError.REQUIRED, errors[DoctorSignUpField.REGISTRATION_NUMBER])
        assertEquals(DoctorSignUpError.REQUIRED, errors[DoctorSignUpField.YEARS_EXPERIENCE])
        assertEquals(
            DoctorSignUpError.NO_QUALIFICATIONS,
            errors[DoctorSignUpField.QUALIFICATIONS],
        )
    }

    @Test
    fun `a fully populated form is valid`() {
        // The counterpart to every rejection test: catches validation so strict
        // that nothing legitimate can be submitted.
        val vm = viewModel()
        vm.fillValidForm()
        assertTrue(vm.formState.value.errors.toString(), vm.formState.value.isValid)
    }

    @Test
    fun `an address with no at sign is not an email`() {
        val vm = viewModel()
        vm.fillValidForm()
        vm.onEmailChange("asha.clinic.example")
        assertEquals(DoctorSignUpError.INVALID_EMAIL, errorFor(vm, DoctorSignUpField.EMAIL))
        assertFalse(vm.formState.value.isValid)
    }

    @Test
    fun `an address with no dot in the domain is not an email`() {
        // Catches a check that stops at "contains @" — the most common shortcut.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onEmailChange("asha@clinic")
        assertEquals(DoctorSignUpError.INVALID_EMAIL, errorFor(vm, DoctorSignUpField.EMAIL))
    }

    @Test
    fun `an address with a space is not an email`() {
        val vm = viewModel()
        vm.fillValidForm()
        vm.onEmailChange("asha rao@clinic.example")
        assertEquals(DoctorSignUpError.INVALID_EMAIL, errorFor(vm, DoctorSignUpField.EMAIL))
    }

    @Test
    fun `surrounding whitespace does not invalidate an email`() {
        // Catches validation that runs before trimming while submit trims after,
        // which produces an error the user cannot see the cause of.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onEmailChange("  asha@clinic.example  ")
        assertNull(errorFor(vm, DoctorSignUpField.EMAIL))
    }

    @Test
    fun `a five character password is rejected`() {
        // Catches drift from SignUpWithEmailUseCase's own six-character floor,
        // which would surface as a backend rejection on an apparently valid form.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onPasswordChange("12345")
        assertEquals(
            DoctorSignUpError.PASSWORD_TOO_SHORT,
            errorFor(vm, DoctorSignUpField.PASSWORD),
        )
    }

    @Test
    fun `a six character password is accepted`() {
        // The boundary itself: an off-by-one here rejects passwords the use case
        // would have taken.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onPasswordChange("123456")
        assertNull(errorFor(vm, DoctorSignUpField.PASSWORD))
        assertTrue(vm.formState.value.isValid)
    }

    @Test
    fun `a blank registration number is rejected`() {
        // This field is the whole basis of the review; a blank one gives the
        // reviewer nothing to check against the register.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onRegistrationNumberChange("")
        assertEquals(
            DoctorSignUpError.REQUIRED,
            errorFor(vm, DoctorSignUpField.REGISTRATION_NUMBER),
        )
    }

    @Test
    fun `a whitespace-only registration number is rejected`() {
        // Catches an isEmpty check where isBlank was meant — a space passes the
        // first and is just as useless to a reviewer.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onRegistrationNumberChange("   ")
        assertEquals(
            DoctorSignUpError.REQUIRED,
            errorFor(vm, DoctorSignUpField.REGISTRATION_NUMBER),
        )
    }

    @Test
    fun `a whitespace-only full name is rejected`() {
        val vm = viewModel()
        vm.fillValidForm()
        vm.onFullNameChange("  ")
        assertEquals(DoctorSignUpError.REQUIRED, errorFor(vm, DoctorSignUpField.FULL_NAME))
    }

    @Test
    fun `a form with no qualifications is invalid`() {
        val vm = viewModel()
        vm.fillValidForm()
        vm.removeQualification("MBBS")
        assertEquals(
            DoctorSignUpError.NO_QUALIFICATIONS,
            errorFor(vm, DoctorSignUpField.QUALIFICATIONS),
        )
        assertFalse(vm.formState.value.isValid)
    }

    @Test
    fun `typing a qualification is not the same as adding it`() {
        // Catches validation that reads the draft field: text left uncommitted in
        // the box is not in the list and must not satisfy the requirement.
        val vm = viewModel()
        vm.fillValidForm()
        vm.removeQualification("MBBS")
        vm.onQualificationDraftChange("MD Dermatology")
        assertEquals(
            DoctorSignUpError.NO_QUALIFICATIONS,
            errorFor(vm, DoctorSignUpField.QUALIFICATIONS),
        )
    }

    @Test
    fun `adding a qualification clears the draft and satisfies the requirement`() {
        val vm = viewModel()
        vm.onQualificationDraftChange("  MBBS  ")
        vm.addQualification()
        assertEquals(listOf("MBBS"), vm.formState.value.qualifications)
        assertEquals("", vm.formState.value.qualificationDraft)
        assertNull(errorFor(vm, DoctorSignUpField.QUALIFICATIONS))
    }

    @Test
    fun `a blank qualification is not added`() {
        val vm = viewModel()
        vm.onQualificationDraftChange("   ")
        vm.addQualification()
        assertTrue(vm.formState.value.qualifications.isEmpty())
    }

    @Test
    fun `the same qualification is not added twice`() {
        // A duplicate reads to a reviewer as two separate claims to the same
        // degree, which is noise at best and padding at worst.
        val vm = viewModel()
        vm.onQualificationDraftChange("MBBS")
        vm.addQualification()
        vm.onQualificationDraftChange("mbbs")
        vm.addQualification()
        assertEquals(listOf("MBBS"), vm.formState.value.qualifications)
    }

    @Test
    fun `zero years of experience is accepted`() {
        // The lower bound is a real case: a freshly registered doctor.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("0")
        assertNull(errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE))
        assertTrue(vm.formState.value.isValid)
    }

    @Test
    fun `seventy years of experience is accepted`() {
        // The upper bound is inclusive; an exclusive comparison would reject it.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("70")
        assertNull(errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE))
    }

    @Test
    fun `seventy one years of experience is rejected`() {
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("71")
        assertEquals(
            DoctorSignUpError.YEARS_OUT_OF_RANGE,
            errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE),
        )
    }

    @Test
    fun `negative years of experience is rejected`() {
        // Catches a range check written as "years > MAX" only.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("-1")
        assertEquals(
            DoctorSignUpError.YEARS_OUT_OF_RANGE,
            errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE),
        )
    }

    @Test
    fun `non numeric years of experience is rejected as unparseable`() {
        // Distinct from out-of-range so the message can be distinct: "enter a
        // number" and "enter a smaller number" are different instructions.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("twelve")
        assertEquals(
            DoctorSignUpError.YEARS_NOT_A_NUMBER,
            errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE),
        )
    }

    @Test
    fun `a years value too large for an Int is rejected rather than crashing`() {
        // toIntOrNull returns null on overflow; a toInt() here would throw while
        // the user was still typing.
        val vm = viewModel()
        vm.fillValidForm()
        vm.onYearsExperienceChange("99999999999")
        assertEquals(
            DoctorSignUpError.YEARS_NOT_A_NUMBER,
            errorFor(vm, DoctorSignUpField.YEARS_EXPERIENCE),
        )
    }

    // ── Error visibility ────────────────────────────────────────────────────

    @Test
    fun `errors stay hidden until a field is touched`() {
        // Catches a form that opens covered in red before a single keystroke.
        val vm = viewModel()
        assertNotNull(errorFor(vm, DoctorSignUpField.EMAIL))
        assertNull(vm.formState.value.visibleError(DoctorSignUpField.EMAIL))
    }

    @Test
    fun `an error becomes visible once its field is edited`() {
        val vm = viewModel()
        vm.onEmailChange("nope")
        assertEquals(
            DoctorSignUpError.INVALID_EMAIL,
            vm.formState.value.visibleError(DoctorSignUpField.EMAIL),
        )
    }

    @Test
    fun `submitting an invalid form reveals every error`() {
        // The IME "done" action can reach submit even while the button is
        // disabled; silently doing nothing would look like a broken button.
        val vm = viewModel()
        vm.onEmailChange("nope")
        vm.submit()
        assertNotNull(vm.formState.value.visibleError(DoctorSignUpField.REGISTRATION_NUMBER))
        assertNotNull(vm.formState.value.visibleError(DoctorSignUpField.QUALIFICATIONS))
    }

    @Test
    fun `submitting an invalid form does not create an account`() = runTest(dispatcher.scheduler) {
        val auth = FakeAuthRepository()
        val vm = viewModel(auth = auth)
        vm.onEmailChange("nope")
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, auth.signUpCalls)
    }

    // ── Submit ──────────────────────────────────────────────────────────────

    @Test
    fun `a successful submit records the claim as pending and unverified`() =
        runTest(dispatcher.scheduler) {
            // The single most important assertion in this file: signing up must
            // never produce a verified doctor. Verification is a manual review.
            val doctorDao = FakeDoctorProfileDao()
            val vm = viewModel(doctorDao = doctorDao)
            vm.fillValidForm()
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            val row = doctorDao.getByUserId(USER_ID)
            assertNotNull(row)
            assertEquals(VerificationStatus.PENDING.name, row!!.verificationStatus)
            assertNull(row.verifiedAt)
        }

    @Test
    fun `a successful submit stores the typed credentials`() = runTest(dispatcher.scheduler) {
        // Catches fields dropped or crossed over on the way into the entity — a
        // reviewer checking the wrong number cannot spot that from the app.
        val doctorDao = FakeDoctorProfileDao()
        val vm = viewModel(doctorDao = doctorDao)
        vm.fillValidForm()
        vm.onQualificationDraftChange("MD Dermatology")
        vm.addQualification()
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        val row = doctorDao.getByUserId(USER_ID)!!
        assertEquals("Dr Asha Rao", row.fullName)
        assertEquals("MCI-889231", row.registrationNumber)
        assertEquals("Dermatology", row.specialty)
        assertEquals("City Skin Clinic", row.institution)
        assertEquals(12, row.yearsExperience)
        assertEquals(
            listOf("MBBS", "MD Dermatology"),
            DoctorProfileEntity.decodeQualifications(row.qualifications),
        )
    }

    @Test
    fun `a successful submit promotes the account to the doctor role`() =
        runTest(dispatcher.scheduler) {
            // Without this the account signs up as a doctor and then lands on the
            // patient surface with no way back to its own verification screen.
            val userDao = FakeUserProfileDao(seededProfile())
            val vm = viewModel(userDao = userDao)
            vm.fillValidForm()
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UserProfileEntity.ROLE_DOCTOR, userDao.getById(USER_ID)?.role)
        }

    @Test
    fun `a successful submit keeps the existing profile row's creation time`() =
        runTest(dispatcher.scheduler) {
            // Catches a whole-row rewrite that resets createdAt, which would make
            // the account look newly created every time the role is touched.
            val seeded = seededProfile().copy(createdAt = 1_000L)
            val userDao = FakeUserProfileDao(seeded)
            val vm = viewModel(userDao = userDao)
            vm.fillValidForm()
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1_000L, userDao.getById(USER_ID)?.createdAt)
        }

    @Test
    fun `a successful submit reports success`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        vm.fillValidForm()
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.signUpResult.value is UiState.Success)
    }

    @Test
    fun `a failed sign-up writes no credential claim and no role change`() =
        runTest(dispatcher.scheduler) {
            // If the account was never created, a doctor row would be orphaned and
            // a role flip would apply to somebody else's row.
            val auth = FakeAuthRepository(failWith = "Email already in use")
            val doctorDao = FakeDoctorProfileDao()
            val userDao = FakeUserProfileDao(seededProfile())
            val vm = viewModel(auth = auth, doctorDao = doctorDao, userDao = userDao)
            vm.fillValidForm()
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(doctorDao.rows.isEmpty())
            assertEquals(UserProfileEntity.ROLE_PATIENT, userDao.getById(USER_ID)?.role)
            assertTrue(vm.signUpResult.value is UiState.Error)
            assertEquals("Email already in use", vm.formState.value.submitError)
        }

    @Test
    fun `submit trims the email and name before creating the account`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository()
            val vm = viewModel(auth = auth)
            vm.fillValidForm()
            vm.onEmailChange("  asha@clinic.example ")
            vm.onFullNameChange("  Dr Asha Rao  ")
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("asha@clinic.example", auth.lastEmail)
            assertEquals("Dr Asha Rao", auth.lastDisplayName)
        }

    @Test
    fun `resubmitting reuses the existing claim row rather than adding a second`() =
        runTest(dispatcher.scheduler) {
            // The table allows one profile per account; a second insert with a new
            // id would violate the unique index at the Room level.
            val doctorDao = FakeDoctorProfileDao()
            val vm = viewModel(doctorDao = doctorDao)
            vm.fillValidForm()
            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()
            val firstId = doctorDao.getByUserId(USER_ID)!!.id

            vm.submit()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, doctorDao.rows.size)
            assertEquals(firstId, doctorDao.getByUserId(USER_ID)!!.id)
        }

    private companion object {
        const val USER_ID = "user-1"

        fun seededProfile() = UserProfileEntity(
            id = USER_ID,
            email = "asha@clinic.example",
            displayName = "Dr Asha Rao",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
    }
}
