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

    /**
     * Races a fresh fix from every currently-enabled provider (GPS + network) and
     * resolves with whichever answers first. GPS alone often can't get a fix
     * indoors/without sky visibility, and on many devices "Battery saving" location
     * mode disables GPS_PROVIDER entirely while NETWORK_PROVIDER still works — so
     * GPS-only requests can stall for the full timeout or throw outright on a
     * disabled provider. Disabled providers are skipped up front, and any provider
     * that still throws synchronously (e.g. [IllegalArgumentException] for an
     * unknown/disabled provider) is treated as "no result from this provider"
     * rather than crashing the coroutine — the remaining provider(s) can still win.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(lm: LocationManager): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { provider -> runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false) }

            if (candidates.isEmpty()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val activeListeners = mutableListOf<LocationListener>()
            var pending = candidates.size

            fun onLocation(location: Location) {
                if (!cont.isCompleted) {
                    cont.resume(Pair(location.latitude, location.longitude))
                }
            }

            // Every requested provider has now failed to produce a result (disabled,
            // unsupported, or gave up internally) — resolve as "no location" instead
            // of leaving the caller to wait out the rest of the 8s timeout for nothing.
            fun onProviderFailed() {
                pending--
                if (pending <= 0 && !cont.isCompleted) {
                    cont.resume(null)
                }
            }

            cont.invokeOnCancellation {
                activeListeners.forEach { listener -> runCatching { lm.removeUpdates(listener) } }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                candidates.forEach { provider ->
                    try {
                        lm.getCurrentLocation(
                            provider,
                            null,
                            ContextCompat.getMainExecutor(context),
                            java.util.function.Consumer { loc: Location? ->
                                if (loc != null) onLocation(loc) else onProviderFailed()
                            },
                        )
                    } catch (e: Exception) {
                        onProviderFailed()
                    }
                }
            } else {
                candidates.forEach { provider ->
                    try {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) = onLocation(location)

                            @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                            @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                            override fun onProviderEnabled(provider: String) = Unit

                            @Deprecated("Deprecated in API 29", level = DeprecationLevel.HIDDEN)
                            override fun onProviderDisabled(provider: String) = Unit
                        }
                        activeListeners.add(listener)
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } catch (e: Exception) {
                        onProviderFailed()
                    }
                }
            }
        }

    fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
