package com.dermoai.core.environment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission") // Runtime-guarded by checkSelfPermission below; lint can't trace the guard through the withContext lambda.
    suspend fun lastCoarseLocation(): Pair<Double, Double>? {
        return withContext(Dispatchers.IO) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@withContext null
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                return@withContext Pair(loc.latitude, loc.longitude)
            }
            null
        }
    }

    fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
