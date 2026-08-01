package com.dermoai.core.environment

import com.dermoai.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

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
            val raw = URL(url).readText()
            json.decodeFromString<OpenMeteoResponse>(raw)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Error(it, "Failed to fetch weather data") },
        )
    }
}
