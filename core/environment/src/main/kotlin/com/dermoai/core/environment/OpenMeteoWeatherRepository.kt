package com.dermoai.core.environment

import com.dermoai.core.common.result.AppResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenMeteoWeatherRepository @Inject constructor() : EnvironmentRepository {
    private var cached: WeatherConditions? = null
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchCurrent(lat: Double, lon: Double): AppResult<WeatherConditions> {
        return when (val result = OpenMeteoWeatherApi.fetch(lat, lon)) {
            is AppResult.Success -> {
                val d = result.data.current
                val wc = WeatherConditions(
                    uvIndex = d.uv_index,
                    temperatureC = d.temperature_2m,
                    humidityPercent = d.relative_humidity_2m,
                    fetchedAt = System.currentTimeMillis(),
                )
                cache(wc)
                AppResult.Success(wc)
            }
            is AppResult.Error -> AppResult.Error(result.exception, result.message)
            else -> AppResult.Error(Exception("Unexpected"), "Failed to fetch weather")
        }
    }

    override fun cachedConditions(): WeatherConditions? = cached

    override fun cache(conditions: WeatherConditions) {
        cached = conditions
    }
}
