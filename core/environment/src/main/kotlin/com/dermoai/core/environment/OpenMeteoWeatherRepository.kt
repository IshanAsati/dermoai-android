package com.dermoai.core.environment

import com.dermoai.core.common.result.AppResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class OpenMeteoWeatherRepository @Inject constructor(
    private val alertStore: EnvironmentAlertStore,
) : EnvironmentRepository {
    private var cached: WeatherConditions? = null

    override suspend fun fetchCurrent(lat: Double, lon: Double): AppResult<WeatherConditions> = coroutineScope {
        val weatherCall = async { OpenMeteoWeatherApi.fetch(lat, lon) }
        val airCall = async { OpenMeteoAirQualityApi.fetch(lat, lon) }
        val weather = weatherCall.await()
        val air = airCall.await()

        when (weather) {
            is AppResult.Success -> {
                val d = weather.data.current
                // Air quality is a bonus: if only that call fails, the weather alert still works.
                val aq = (air as? AppResult.Success)?.data?.current
                val wc = WeatherConditions(
                    uvIndex = d.uv_index,
                    temperatureC = d.temperature_2m,
                    humidityPercent = d.relative_humidity_2m,
                    fetchedAt = System.currentTimeMillis(),
                    aqi = aq?.us_aqi?.roundToInt(),
                    pm25 = aq?.pm2_5,
                )
                cache(wc)
                alertStore.saveLocation(lat, lon)
                AppResult.Success(wc)
            }
            is AppResult.Error -> AppResult.Error(weather.exception, weather.message)
            else -> AppResult.Error(Exception("Unexpected"), "Failed to fetch weather")
        }
    }

    override fun cachedConditions(): WeatherConditions? = cached

    override fun cache(conditions: WeatherConditions) {
        cached = conditions
    }
}
