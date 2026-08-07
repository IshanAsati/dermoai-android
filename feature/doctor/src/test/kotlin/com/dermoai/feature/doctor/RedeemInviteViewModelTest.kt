package com.dermoai.feature.doctor

import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.data.sync.AppwriteClientProvider
import com.dermoai.core.data.sync.AppwriteConfig
import com.dermoai.core.data.sync.DoctorSyncRepository
import com.dermoai.core.database.dao.AuditEntryDao
import com.dermoai.core.database.dao.DoctorInviteDao
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.feature.doctor.data.AuditLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RedeemInviteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val fakeDoctorInviteDao = object : DoctorInviteDao {
        val invites = mutableMapOf<String, DoctorInviteEntity>()
        override fun observeByDoctorId(doctorId: String): Flow<List<DoctorInviteEntity>> =
            flowOf(invites.values.filter { it.doctorId == doctorId })
        override suspend fun getByCode(code: String): DoctorInviteEntity? =
            invites.values.firstOrNull { it.code.equals(code, ignoreCase = true) }
        override suspend fun getById(inviteId: String): DoctorInviteEntity? = invites[inviteId]
        override suspend fun upsert(invite: DoctorInviteEntity) { invites[invite.id] = invite }
        override suspend fun incrementUse(inviteId: String, now: Long, updatedAt: Long): Int {
            val existing = invites[inviteId] ?: return 0
            if (existing.revoked || existing.usedCount >= existing.maxUses || existing.expiresAt <= now) {
                return 0
            }
            invites[inviteId] = existing.copy(usedCount = existing.usedCount + 1, updatedAt = updatedAt)
            return 1
        }
        override suspend fun revoke(inviteId: String, updatedAt: Long) {
            val existing = invites[inviteId] ?: return
            invites[inviteId] = existing.copy(revoked = true, updatedAt = updatedAt)
        }
        override suspend fun deleteById(inviteId: String) { invites.remove(inviteId) }
    }

    private val fakeDoctorProfileDao = object : DoctorProfileDao {
        val profiles = mutableMapOf<String, DoctorProfileEntity>()
        override fun observeByUserId(userId: String): Flow<DoctorProfileEntity?> =
            flowOf(profiles.values.firstOrNull { it.userId == userId })
        override suspend fun getById(doctorId: String): DoctorProfileEntity? = profiles[doctorId]
        override suspend fun getByUserId(userId: String): DoctorProfileEntity? =
            profiles.values.firstOrNull { it.userId == userId }
        override suspend fun upsert(profile: DoctorProfileEntity) { profiles[profile.id] = profile }
        override suspend fun updateVerification(
            doctorId: String,
            status: String,
            verifiedAt: Long?,
            updatedAt: Long,
        ) {
            val existing = profiles[doctorId] ?: return
            profiles[doctorId] = existing.copy(
                verificationStatus = status,
                verifiedAt = verifiedAt,
                updatedAt = updatedAt,
            )
        }
        override suspend fun deleteById(doctorId: String) { profiles.remove(doctorId) }
    }

    private val fakePatientLinkDao = object : PatientLinkDao {
        val links = mutableMapOf<String, PatientLinkEntity>()
        override fun observeByDoctorId(doctorId: String): Flow<List<PatientLinkEntity>> =
            flowOf(links.values.filter { it.doctorId == doctorId })
        override fun observeActiveByDoctorId(doctorId: String): Flow<List<PatientLinkEntity>> =
            flowOf(links.values.filter { it.doctorId == doctorId && it.status == "ACTIVE" && it.consentGrantedAt != null })
        override fun observeByPatientUserId(patientUserId: String): Flow<List<PatientLinkEntity>> =
            flowOf(links.values.filter { it.patientUserId == patientUserId })
        override suspend fun getById(linkId: String): PatientLinkEntity? = links[linkId]
        override suspend fun getByPatientAndDoctor(patientUserId: String, doctorId: String): PatientLinkEntity? =
            links.values.firstOrNull { it.patientUserId == patientUserId && it.doctorId == doctorId }
        override fun observeActiveCount(doctorId: String): Flow<Int> =
            flowOf(links.values.count { it.doctorId == doctorId && it.status == "ACTIVE" && it.consentGrantedAt != null })
        override suspend fun upsert(link: PatientLinkEntity) { links[link.id] = link }
        override suspend fun upsertAll(links: List<PatientLinkEntity>) {
            links.forEach { this.links[it.id] = it }
        }
        override suspend fun updateStatus(
            linkId: String,
            status: String,
            consentGrantedAt: Long?,
            updatedAt: Long,
        ) {
            val existing = links[linkId] ?: return
            links[linkId] = existing.copy(
                status = status,
                consentGrantedAt = consentGrantedAt,
                updatedAt = updatedAt,
            )
        }
        override suspend fun delete(linkId: String) { links.remove(linkId) }
    }

    private val fakeAuditEntryDao = object : AuditEntryDao {
        val entries = mutableListOf<AuditEntryEntity>()
        override fun observeBySubject(subjectUserId: String): Flow<List<AuditEntryEntity>> =
            flowOf(entries.filter { it.subjectUserId == subjectUserId })
        override fun observeByActor(actorUserId: String): Flow<List<AuditEntryEntity>> =
            flowOf(entries.filter { it.actorUserId == actorUserId })
        override fun observeByActorAndSubject(actorUserId: String, subjectUserId: String): Flow<List<AuditEntryEntity>> =
            flowOf(entries.filter { it.actorUserId == actorUserId && it.subjectUserId == subjectUserId })
        override suspend fun getLatestBySubject(subjectUserId: String): AuditEntryEntity? =
            entries.lastOrNull { it.subjectUserId == subjectUserId }
        override suspend fun upsert(entry: AuditEntryEntity) { entries.add(entry) }
        override suspend fun upsertAll(entries: List<AuditEntryEntity>) { this.entries.addAll(entries) }
    }

    private lateinit var syncRepository: DoctorSyncRepository
    private lateinit var auditLogger: AuditLogger
    private lateinit var viewModel: RedeemInviteViewModel

    private class FakeLocalOnlyAppwriteConfig : AppwriteConfig() {
        override val endpoint: String = ""
        override val projectId: String = ""
        override val databaseId: String = ""
        override val isConfigured: Boolean = false
        override val isLocalOnlyMode: Boolean = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val config = FakeLocalOnlyAppwriteConfig()
        val clientProvider = AppwriteClientProvider(
            context = android.content.ContextWrapper(null),
            config = config,
        )
        syncRepository = DoctorSyncRepository(
            config = config,
            clients = clientProvider,
            dispatchers = testDispatcherProvider,
        )
        auditLogger = AuditLogger(fakeAuditEntryDao)
        viewModel = RedeemInviteViewModel(
            doctorInviteDao = fakeDoctorInviteDao,
            doctorProfileDao = fakeDoctorProfileDao,
            patientLinkDao = fakePatientLinkDao,
            auditLogger = auditLogger,
            sync = syncRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is RedeemUiState Entry with empty code`() {
        val state = viewModel.state.value
        assertTrue(state is RedeemUiState.Entry)
        assertEquals("", viewModel.code.value)
        assertFalse(viewModel.isCodeComplete)
    }

    @Test
    fun `onCodeChanged normalises input to uppercase and filters separators`() {
        viewModel.onCodeChanged(" x8s3-6v8r ")
        assertEquals("X8S36V8R", viewModel.code.value)
        assertTrue(viewModel.isCodeComplete)
    }

    @Test
    fun `checkCode with incomplete code is a no-op`() = runTest {
        viewModel.onCodeChanged("X8S3")
        viewModel.checkCode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value is RedeemUiState.Entry)
    }

    @Test
    fun `checkCode when code does not exist locally and backend is unconfigured returns Offline`() = runTest {
        // FakeLocalOnlyAppwriteConfig means the server is never actually asked
        // — PullOutcome.fromServer is false — so this must not be reported as
        // "no such code" (that claims the server was consulted and said no).
        // It is reported as Offline/unreachable, which is the honest answer:
        // this build has no backend to check.
        viewModel.onCodeChanged("X8S36V8R")
        viewModel.checkCode()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state is RedeemUiState.Entry)
        assertEquals(RedeemRejection.Offline, (state as RedeemUiState.Entry).rejection)
    }

    @Test
    fun `checkCode when valid local invite and profile exist moves to Consent`() = runTest {
        val doctorProfile = DoctorProfileEntity(
            id = "doc1",
            userId = "doc_user_1",
            fullName = "Dr. Alice",
            qualifications = "MD",
            registrationNumber = "REG123",
            specialty = "Dermatology",
            institution = "Central Clinic",
            yearsExperience = 12,
            createdAt = 1000L,
            updatedAt = 1000L,
            verificationStatus = "VERIFIED",
            verifiedAt = 1000L,
            bio = "",
        )
        fakeDoctorProfileDao.profiles["doc1"] = doctorProfile

        val invite = DoctorInviteEntity(
            id = "inv1",
            userId = "doc_user_1",
            doctorId = "doc1",
            code = "X8S36V8R",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            maxUses = 5,
            usedCount = 0,
            revoked = false,
        )
        fakeDoctorInviteDao.invites["inv1"] = invite

        viewModel.onCodeChanged("X8S36V8R")
        viewModel.checkCode()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is RedeemUiState.Consent)
        val consent = state as RedeemUiState.Consent
        assertEquals("X8S36V8R", consent.invite.code)
        assertEquals("Dr. Alice", consent.doctor.fullName)
    }

    @Test
    fun `declineConsent returns state to Entry`() = runTest {
        val doctorProfile = DoctorProfileEntity(
            id = "doc1",
            userId = "doc_user_1",
            fullName = "Dr. Alice",
            qualifications = "MD",
            registrationNumber = "REG123",
            specialty = "Dermatology",
            institution = "Central Clinic",
            yearsExperience = 12,
            createdAt = 1000L,
            updatedAt = 1000L,
            verificationStatus = "VERIFIED",
            verifiedAt = 1000L,
            bio = "",
        )
        fakeDoctorProfileDao.profiles["doc1"] = doctorProfile

        val invite = DoctorInviteEntity(
            id = "inv1",
            userId = "doc_user_1",
            doctorId = "doc1",
            code = "X8S36V8R",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            maxUses = 5,
            usedCount = 0,
            revoked = false,
        )
        fakeDoctorInviteDao.invites["inv1"] = invite

        viewModel.onCodeChanged("X8S36V8R")
        viewModel.checkCode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is RedeemUiState.Consent)
        viewModel.declineConsent()
        assertTrue(viewModel.state.value is RedeemUiState.Entry)
    }

    @Test
    fun `grantConsent creates patient link, increments invite use, and moves to Linked`() = runTest {
        val doctorProfile = DoctorProfileEntity(
            id = "doc1",
            userId = "doc_user_1",
            fullName = "Dr. Alice",
            qualifications = "MD",
            registrationNumber = "REG123",
            specialty = "Dermatology",
            institution = "Central Clinic",
            yearsExperience = 12,
            createdAt = 1000L,
            updatedAt = 1000L,
            verificationStatus = "VERIFIED",
            verifiedAt = 1000L,
            bio = "",
        )
        fakeDoctorProfileDao.profiles["doc1"] = doctorProfile

        val invite = DoctorInviteEntity(
            id = "inv1",
            userId = "doc_user_1",
            doctorId = "doc1",
            code = "X8S36V8R",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            maxUses = 5,
            usedCount = 0,
            revoked = false,
        )
        fakeDoctorInviteDao.invites["inv1"] = invite

        viewModel.onCodeChanged("X8S36V8R")
        viewModel.checkCode()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.grantConsent(patientUserId = "patient1", patientDisplayName = "Bob Patient")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is RedeemUiState.Linked)
        val linked = state as RedeemUiState.Linked
        assertEquals("Dr. Alice", linked.doctorName)
        assertFalse(linked.alreadyHadAccess)

        assertEquals(1, fakeDoctorInviteDao.invites["inv1"]?.usedCount)
        assertEquals(1, fakePatientLinkDao.links.size)
        assertEquals("patient1", fakePatientLinkDao.links.values.first().patientUserId)
        assertEquals("doc1", fakePatientLinkDao.links.values.first().doctorId)
        assertEquals(1, fakeAuditEntryDao.entries.size)
    }
}
