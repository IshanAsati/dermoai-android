package com.dermoai.core.environment

import kotlinx.serialization.Serializable

@Serializable
data class WeatherConditions(
    val uvIndex: Float,
    val temperatureC: Float,
    val humidityPercent: Float,
    val fetchedAt: Long,
    val locationLabel: String = "",
)
