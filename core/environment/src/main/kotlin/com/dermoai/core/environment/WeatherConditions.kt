package com.dermoai.core.environment

import kotlinx.serialization.Serializable

@Serializable
data class WeatherConditions(
    val uvIndex: Float,
    val temperatureC: Float,
    val humidityPercent: Float,
    val fetchedAt: Long,
    val locationLabel: String = "",
    /** US AQI (0-500) from the air-quality service, or null when it couldn't be fetched. */
    val aqi: Int? = null,
    /** Fine particulate matter (PM2.5) in µg/m³, or null when unavailable. */
    val pm25: Float? = null,
)
