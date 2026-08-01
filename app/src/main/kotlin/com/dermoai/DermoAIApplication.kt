package com.dermoai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
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
    }
}