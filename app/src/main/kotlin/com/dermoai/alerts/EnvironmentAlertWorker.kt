package com.dermoai.alerts

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.data.preferences.UserPreferencesDataStore
import com.dermoai.core.environment.AlertNotifyPolicy
import com.dermoai.core.environment.EnvironmentAlertEvaluator
import com.dermoai.core.environment.EnvironmentAlertStore
import com.dermoai.core.environment.EnvironmentNotifier
import com.dermoai.core.environment.EnvironmentRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs every few hours in the background: re-reads UV / heat / humidity / air
 * quality for the last place the app saw the user, and posts a notification when
 * a warning appears or gets worse ([AlertNotifyPolicy]).
 *
 * It uses the location the app saved while open, so it needs no background
 * location permission. Respects the "Environmental alerts" switch in Settings.
 */
class EnvironmentAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun environmentRepository(): EnvironmentRepository
        fun alertStore(): EnvironmentAlertStore
        fun preferences(): UserPreferencesDataStore
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)

        if (!deps.preferences().envAlertsEnabled.first()) return Result.success()
        val store = deps.alertStore()
        val (lat, lon) = store.lastLocation() ?: return Result.success() // app hasn't seen a location yet

        return when (val result = deps.environmentRepository().fetchCurrent(lat, lon)) {
            is AppResult.Success -> {
                val now = System.currentTimeMillis()
                val alert = EnvironmentAlertEvaluator.evaluate(result.data)
                val wanted = AlertNotifyPolicy.shouldNotify(alert, store.lastAlert(), store.lastNotifiedAt(), now)
                if (wanted && alert != null && EnvironmentNotifier.canPost(applicationContext)) {
                    EnvironmentNotifier.show(applicationContext, alert, result.data)
                    store.recordAlert(alert, notifiedAt = now)
                } else {
                    // Keep the alert state current so a later rise is noticed, but don't
                    // count a skipped/blocked notification as "sent".
                    store.recordAlert(alert)
                }
                Result.success()
            }
            // Offline or API hiccup: try again a couple of times, then wait for the next cycle.
            is AppResult.Error -> if (runAttemptCount < 2) Result.retry() else Result.success()
            is AppResult.Loading -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "environment_alert_check"
        private const val UNIQUE_NAME_SOON = "environment_alert_check_soon"

        /** Safe to call on every app start: KEEP leaves an already-scheduled job alone. */
        fun schedule(context: Context) {
            EnvironmentNotifier.ensureChannel(context)
            val request = PeriodicWorkRequestBuilder<EnvironmentAlertWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

            // The periodic job first fires one interval from now. Also run one check shortly
            // after launch so the first alert arrives in minutes, not hours. KEEP means
            // re-opening the app while one is pending doesn't queue another, and the notify
            // policy still prevents repeats.
            val soon = OneTimeWorkRequestBuilder<EnvironmentAlertWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME_SOON, ExistingWorkPolicy.KEEP, soon)
        }
    }
}
