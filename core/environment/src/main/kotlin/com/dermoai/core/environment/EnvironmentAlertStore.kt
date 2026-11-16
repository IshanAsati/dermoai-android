package com.dermoai.core.environment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

/**
 * Remembers what the background alert check needs between runs.
 *
 * The location is saved only when the app is open (the foreground fetch), so the
 * background worker never needs the sensitive "background location" permission.
 * It is rounded to 2 decimals (about 1 km) — plenty for weather and air quality,
 * and it never leaves the device except in the weather request itself.
 */
@Singleton
class EnvironmentAlertStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("environment_alerts", Context.MODE_PRIVATE)

    fun saveLocation(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat(KEY_LAT, roundCoarse(lat).toFloat())
            .putFloat(KEY_LON, roundCoarse(lon).toFloat())
            .apply()
    }

    fun lastLocation(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        return prefs.getFloat(KEY_LAT, 0f).toDouble() to prefs.getFloat(KEY_LON, 0f).toDouble()
    }

    fun lastAlert(): EnvironmentAlert? =
        prefs.getString(KEY_ALERT, null)?.let { name -> EnvironmentAlert.entries.firstOrNull { it.name == name } }

    fun lastNotifiedAt(): Long = prefs.getLong(KEY_NOTIFIED_AT, 0L)

    /** Records the latest alert state; [notifiedAt] is only advanced when a notification was sent. */
    fun recordAlert(alert: EnvironmentAlert?, notifiedAt: Long? = null) {
        prefs.edit().apply {
            if (alert == null) remove(KEY_ALERT) else putString(KEY_ALERT, alert.name)
            if (notifiedAt != null) putLong(KEY_NOTIFIED_AT, notifiedAt)
        }.apply()
    }

    private fun roundCoarse(v: Double): Double = round(v * 100.0) / 100.0

    private companion object {
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_ALERT = "last_alert"
        const val KEY_NOTIFIED_AT = "last_notified_at"
    }
}
