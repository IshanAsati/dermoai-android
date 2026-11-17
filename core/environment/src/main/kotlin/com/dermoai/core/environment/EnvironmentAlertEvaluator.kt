package com.dermoai.core.environment

object EnvironmentAlertEvaluator {
    /** US AQI bands: 101+ is "unhealthy for sensitive groups", 151+ is "unhealthy" for everyone. */
    const val AQI_POOR = 101
    const val AQI_UNHEALTHY = 151

    fun evaluate(c: WeatherConditions): EnvironmentAlert? {
        val aqi = c.aqi ?: 0
        return when {
            c.uvIndex >= 6f && c.temperatureC >= 35f -> EnvironmentAlert.RED_COMBINED
            aqi >= AQI_UNHEALTHY -> EnvironmentAlert.UNHEALTHY_AIR
            c.uvIndex >= 6f -> EnvironmentAlert.HIGH_UV
            c.temperatureC >= 35f -> EnvironmentAlert.EXTREME_HEAT
            aqi >= AQI_POOR -> EnvironmentAlert.POOR_AIR
            c.humidityPercent >= 85f -> EnvironmentAlert.HIGH_HUMIDITY
            else -> null
        }
    }
}

/**
 * [rank] orders alerts by how worrying they are; the notifier only pings again
 * when the rank goes up (see [AlertNotifyPolicy]).
 */
enum class EnvironmentAlert(val message: String, val rank: Int) {
    HIGH_HUMIDITY("High humidity — fungal conditions may flare; keep skin dry", 1),
    POOR_AIR("Air quality is poor — pollution can aggravate acne and eczema; cleanse your face tonight", 2),
    EXTREME_HEAT("Extreme heat — stay hydrated and avoid prolonged sun exposure", 3),
    HIGH_UV("High UV today — wear sunscreen and cover exposed skin", 4),
    UNHEALTHY_AIR("Very poor air quality — wash your face after going outside, moisturise, and limit time outdoors", 5),
    RED_COMBINED("High-risk skin day — UV + extreme heat combined", 6);

    fun label(): String = when (this) {
        HIGH_UV -> "High UV"
        EXTREME_HEAT -> "Extreme Heat"
        HIGH_HUMIDITY -> "High Humidity"
        RED_COMBINED -> "Combined Risk"
        POOR_AIR -> "Poor Air Quality"
        UNHEALTHY_AIR -> "Unhealthy Air"
    }
}

/** Decides whether a background check should raise a notification. Pure, so it's unit-tested. */
object AlertNotifyPolicy {
    /** An unchanged alert is repeated at most once a day. */
    const val REPEAT_AFTER_MS = 24L * 60 * 60 * 1000

    /**
     * Notify when a warning appears after a clear spell, when it gets worse than
     * the last one seen, or when [REPEAT_AFTER_MS] has passed since the last ping.
     * A warning that is easing or unchanged stays quiet.
     */
    fun shouldNotify(
        current: EnvironmentAlert?,
        previous: EnvironmentAlert?,
        lastNotifiedAt: Long,
        now: Long,
    ): Boolean {
        if (current == null) return false
        if (previous == null) return true
        if (current.rank > previous.rank) return true
        return now - lastNotifiedAt >= REPEAT_AFTER_MS
    }
}
