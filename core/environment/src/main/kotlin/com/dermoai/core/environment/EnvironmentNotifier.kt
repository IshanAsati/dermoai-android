package com.dermoai.core.environment

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Builds and posts the environment (UV / heat / humidity / air-quality) notification. */
object EnvironmentNotifier {
    /**
     * A new channel id on purpose: the older "skin_alerts" channel was created with
     * IMPORTANCE_LOW (silent, no heads-up), and Android won't let an app raise the
     * importance of a channel that already exists.
     */
    const val CHANNEL_ID = "environment_alerts"
    private const val NOTIFICATION_ID = 4101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Environment alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Warnings about UV, heat, humidity and air quality that can affect your skin" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** True when the user has allowed notifications (runtime permission on Android 13+). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // guarded by canPost() just before notify()
    fun show(context: Context, alert: EnvironmentAlert, conditions: WeatherConditions) {
        ensureChannel(context)
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_env_alert)
            .setContentTitle(alert.label())
            .setContentText(alert.message)
            .setSubText(details(conditions))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.message}\n${details(conditions)}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        // Callers check canPost() first; the guard here keeps lint (and a revoked permission) honest.
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** One-line summary of the readings behind the alert, e.g. "AQI 168 · PM2.5 72 µg/m³ · UV 7 · 36°C". */
    fun details(c: WeatherConditions): String = listOfNotNull(
        c.aqi?.let { "AQI $it" },
        c.pm25?.let { String.format(Locale.US, "PM2.5 %.0f µg/m³", it) },
        String.format(Locale.US, "UV %.0f", c.uvIndex),
        String.format(Locale.US, "%.0f°C", c.temperatureC),
    ).joinToString(" · ")
}
