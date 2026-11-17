package com.dermoai.core.environment

import com.dermoai.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OpenMeteoResponse(val current: CurrentData)

@Serializable
data class CurrentData(
    val temperature_2m: Float,
    val relative_humidity_2m: Float,
    val uv_index: Float,
)

object OpenMeteoWeatherApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(lat: Double, lon: Double): AppResult<OpenMeteoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,uv_index"
            json.decodeFromString<OpenMeteoResponse>(httpGet(url))
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, "Failed to fetch weather data") },
        )
    }
}

@Serializable
data class AirQualityResponse(val current: AirQualityCurrent)

@Serializable
data class AirQualityCurrent(
    val us_aqi: Float? = null,
    val pm2_5: Float? = null,
)

/** Free, key-less air-quality feed from Open-Meteo (same provider as the weather data). */
object OpenMeteoAirQualityApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(lat: Double, lon: Double): AppResult<AirQualityResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&current=us_aqi,pm2_5"
            json.decodeFromString<AirQualityResponse>(httpGet(url))
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, "Failed to fetch air quality") },
        )
    }
}
