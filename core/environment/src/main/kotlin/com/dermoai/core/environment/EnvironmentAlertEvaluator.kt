package com.dermoai.core.environment

object EnvironmentAlertEvaluator {
    fun evaluate(c: WeatherConditions): EnvironmentAlert? = when {
        c.uvIndex >= 6f && c.temperatureC >= 35f -> EnvironmentAlert.RED_COMBINED
        c.uvIndex >= 6f -> EnvironmentAlert.HIGH_UV
        c.temperatureC >= 35f -> EnvironmentAlert.EXTREME_HEAT
        c.humidityPercent >= 85f -> EnvironmentAlert.HIGH_HUMIDITY
        else -> null
    }
}

enum class EnvironmentAlert(val message: String) {
    HIGH_UV("High UV today — wear sunscreen and cover exposed skin"),
    EXTREME_HEAT("Extreme heat — stay hydrated and avoid prolonged sun exposure"),
    HIGH_HUMIDITY("High humidity — fungal conditions may flare; keep skin dry"),
    RED_COMBINED("High-risk skin day — UV + extreme heat combined");

    fun label(): String = when (this) {
        HIGH_UV -> "High UV"
        EXTREME_HEAT -> "Extreme Heat"
        HIGH_HUMIDITY -> "High Humidity"
        RED_COMBINED -> "Combined Risk"
    }
}
