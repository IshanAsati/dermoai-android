package com.dermoai.core.data.sync

import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorSyncRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var localOnlyConfig: AppwriteConfig
    private lateinit var fakeClientProvider: AppwriteClientProvider
    private lateinit var repository: DoctorSyncRepository

    private class FakeLocalOnlyAppwriteConfig : AppwriteConfig() {
        override val endpoint: String = ""
        override val projectId: String = ""
        override val databaseId: String = ""
        override val isConfigured: Boolean = false
        override val isLocalOnlyMode: Boolean = true
    }

    @Before
    fun setUp() {
        localOnlyConfig = FakeLocalOnlyAppwriteConfig()
        fakeClientProvider = AppwriteClientProvider(
            context = android.content.ContextWrapper(null),
            config = localOnlyConfig,
        )
        repository = DoctorSyncRepository(
            config = localOnlyConfig,
            clients = fakeClientProvider,
            dispatchers = testDispatcherProvider,
        )
    }

    @Test
    fun `isLocalOnlyMode returns true when config is empty`() {
        assertTrue(repository.isLocalOnlyMode())
    }

    @Test
    fun `currentSessionUserId returns null in local-only mode`() = runTest(testDispatcher) {
        val result = repository.currentSessionUserId()
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            assertNull(result.data)
        }
    }

    @Test
    fun `ensureSession returns null in local-only mode`() = runTest(testDispatcher) {
        val result = repository.ensureSession()
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            assertNull(result.data)
        }
    }

    @Test
    fun `findInviteByCode returns skipped outcome in local-only mode`() = runTest(testDispatcher) {
        val result = repository.findInviteByCode("X8S36V8R")
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            val pullOutcome = result.data
            assertFalse(pullOutcome.fromServer)
            assertEquals(SyncSkipReason.NOT_CONFIGURED, pullOutcome.skipped)
            assertNull(pullOutcome.value)
        }
    }

    @Test
    fun `pushDoctorProfile returns skipped outcome in local-only mode`() = runTest(testDispatcher) {
        val entity = DoctorProfileEntity(
            id = "doc1",
            userId = "u1",
            fullName = "Dr. Smith",
            qualifications = "MD",
            registrationNumber = "12345",
            specialty = "Dermatology",
            institution = "General Hospital",
            yearsExperience = 10,
            createdAt = 1000L,
            updatedAt = 1000L,
            verificationStatus = "VERIFIED",
            verifiedAt = 1000L,
            bio = "Skin specialist",
        )
        val result = repository.pushDoctorProfile(entity)
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            val pushOutcome = result.data
            assertFalse(pushOutcome.pushed)
            assertEquals(SyncSkipReason.NOT_CONFIGURED, pushOutcome.skipped)
        }
    }

    @Test
    fun `pushDoctorInvite returns skipped outcome in local-only mode`() = runTest(testDispatcher) {
        val invite = DoctorInviteEntity(
            id = "inv1",
            userId = "u1",
            doctorId = "doc1",
            code = "X8S36V8R",
            createdAt = 1000L,
            updatedAt = 1000L,
            expiresAt = 2000L,
            maxUses = 1,
            usedCount = 0,
            revoked = false,
        )
        val result = repository.pushDoctorInvite(invite)
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            val pushOutcome = result.data
            assertFalse(pushOutcome.pushed)
            assertEquals(SyncSkipReason.NOT_CONFIGURED, pushOutcome.skipped)
        }
    }

    @Test
    fun `pushPatientLink returns skipped outcome in local-only mode`() = runTest(testDispatcher) {
        val link = PatientLinkEntity(
            id = "link1",
            userId = "u1",
            doctorId = "doc1",
            patientUserId = "p1",
            patientDisplayName = "John Doe",
            linkedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            status = "ACTIVE",
            consentGrantedAt = 1000L,
        )
        val result = repository.pushPatientLink(link)
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            val pushOutcome = result.data
            assertFalse(pushOutcome.pushed)
            assertEquals(SyncSkipReason.NOT_CONFIGURED, pushOutcome.skipped)
        }
    }

    @Test
    fun `pullDoctorProfile returns skipped outcome in local-only mode`() = runTest(testDispatcher) {
        val result = repository.pullDoctorProfile("u1")
        assertTrue(result is AppResult.Success)
        if (result is AppResult.Success) {
            val pullOutcome = result.data
            assertFalse(pullOutcome.fromServer)
            assertEquals(SyncSkipReason.NOT_CONFIGURED, pullOutcome.skipped)
            assertNull(pullOutcome.value)
        }
    }
}
