package com.dermoai.core.environment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Best-effort coarse location: a cached fix from any provider first, then a
     * fresh one-shot request (time-bounded) so devices without a cached fix —
     * including emulators right after a `geo fix` — still get a location.
     */
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

            // No cached fix — request a fresh one, bounded to 8s.
            withTimeoutOrNull(8_000) { requestCurrentLocation(lm) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(lm: LocationManager): Pair<Double, Double> =
        suspendCancellableCoroutine { cont ->
            fun onLocation(location: Location) {
                if (!cont.isCompleted) {
                    cont.resume(Pair(location.latitude, location.longitude))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lm.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    null,
                    ContextCompat.getMainExecutor(context),
                    java.util.function.Consumer { loc: Location? -> if (loc != null) onLocation(loc) },
                )
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = onLocation(location)

                    @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                    override fun onProviderDisabled(provider: String) = Unit
                }
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }

    fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
