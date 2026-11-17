package com.dermoai.core.data.sync

import android.util.Log
import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends the signed-in patient's scan *summaries* (label, confidence, concern band, body area,
 * time — never the photo) to the backend so a doctor they have linked with can see them on
 * the doctor dashboard.
 *
 * Nothing leaves the phone until the patient has an ACTIVE, consented link with a doctor, and
 * a summary is uploaded at most once (the scan row is flagged SYNCED afterwards). Offline is
 * ordinary: whatever could not be sent stays pending and goes out on the next trigger.
 *
 * Triggers: [start] watches the patient's links and scans, so a new link (backfills every
 * earlier scan) and a new scan both sync within a couple of seconds; [syncNow] is also called
 * from a periodic background job to retry anything that failed while offline.
 */
@Singleton
class ScanSummarySyncer @Inject constructor(
    private val sync: DoctorSyncRepository,
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val patientLinkDao: PatientLinkDao,
    private val authRepository: AuthRepository,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val started = AtomicBoolean(false)
    private val mutex = Mutex()

    /** Idempotent; call once at app start. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            authRepository.observeAuthState()
                .flatMapLatest { user ->
                    if (user == null) {
                        emptyFlow()
                    } else {
                        // Either table changing may make something sendable: a new link
                        // (send the backlog) or a new scan (send it). The debounce also lets
                        // a fresh scan's predictions finish writing before we read them.
                        combine(
                            patientLinkDao.observeByPatientUserId(user.id),
                            skinScanDao.observeByUserId(user.id),
                        ) { _, _ -> Unit }
                    }
                }
                .debounce(DEBOUNCE_MS)
                .collect { runCatching { syncNow() } }
        }
    }

    data class Report(val pushed: Int, val failed: Int, val note: String? = null)

    /** Pushes every pending summary for the signed-in patient. Never throws. */
    suspend fun syncNow(): Report = mutex.withLock {
        if (sync.isLocalOnlyMode()) return@withLock Report(0, 0, "backend not configured")
        val user = authRepository.getCurrentUser() ?: return@withLock Report(0, 0, "signed out")
        if (patientLinkDao.countActiveForPatient(user.id) == 0) {
            return@withLock Report(0, 0, "no active doctor link")
        }
        val pending = skinScanDao.getUnsyncedByUserId(user.id)
        if (pending.isEmpty()) return@withLock Report(0, 0)

        // The summaries must be written by the same Appwrite session that wrote the link:
        // the dashboard matches the two. This is a no-op when a session already exists.
        val session = (sync.ensureSession() as? AppResult.Success)?.data
        if (session.isNullOrBlank()) return@withLock Report(0, 0, "no backend session")

        var pushed = 0
        var failed = 0
        for (scan in pending) {
            // A scan with no prediction yet is not triage-worthy; it stays pending.
            val top = predictionDao.topPrediction(scan.id) ?: continue
            when (val result = sync.pushScanSummary(scanSummaryDtoOf(scan, top))) {
                is AppResult.Success -> {
                    val outcome = result.data
                    if (outcome.pushed) {
                        skinScanDao.markSynced(scan.id)
                        pushed++
                    } else {
                        // Offline / no session: no point trying the rest of the queue now.
                        Log.i(TAG, "paused: ${outcome.skipped}")
                        return@withLock Report(pushed, failed, "paused: ${outcome.skipped}")
                    }
                }
                is AppResult.Error -> {
                    failed++
                    Log.w(TAG, "scan ${scan.id} rejected: ${result.message}")
                }
                else -> Unit
            }
        }
        Log.i(TAG, "synced $pushed scan summaries ($failed failed)")
        Report(pushed, failed)
    }

    private companion object {
        const val TAG = "ScanSync"
        const val DEBOUNCE_MS = 1_500L
    }
}
