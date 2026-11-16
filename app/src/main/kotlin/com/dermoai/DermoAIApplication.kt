package com.dermoai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dermoai.alerts.EnvironmentAlertWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DermoAIApplication : Application() {
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
    }
}
