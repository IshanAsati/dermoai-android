package com.dermoai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dermoai.alerts.EnvironmentAlertWorker
import com.dermoai.core.data.sync.ScanSummarySyncer
import com.dermoai.sync.ScanSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DermoAIApplication : Application() {

    @Inject lateinit var scanSummarySyncer: ScanSummarySyncer

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "skin_alerts",
            "Skin Alerts",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Environmental skin health warnings" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // Background UV / heat / humidity / air-quality check (every ~3 hours).
        EnvironmentAlertWorker.schedule(this)

        // Share scan summaries with a linked doctor: now, whenever a scan or link changes,
        // and (via the worker) retrying in the background if the phone was offline.
        scanSummarySyncer.start()
        ScanSyncWorker.schedule(this)
    }
}
