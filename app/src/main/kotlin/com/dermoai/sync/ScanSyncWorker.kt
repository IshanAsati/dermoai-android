package com.dermoai.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dermoai.core.data.sync.ScanSummarySyncer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Retries scan-summary uploads that could not go out earlier (no signal, backend hiccup), so a
 * linked doctor's dashboard catches up even if the patient never reopens the app.
 * Does nothing unless the patient has an active doctor link.
 */
class ScanSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun scanSummarySyncer(): ScanSummarySyncer
    }

    override suspend fun doWork(): Result {
        val syncer = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).scanSummarySyncer()
        val report = syncer.syncNow()
        // A partial failure is retried on the next cycle; nothing here needs WorkManager backoff.
        return if (report.failed > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "scan_summary_sync"

        /** Safe on every app start: KEEP leaves an already-scheduled job alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScanSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
