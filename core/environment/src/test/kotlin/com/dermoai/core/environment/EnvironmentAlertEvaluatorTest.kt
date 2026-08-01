package com.dermoai.core.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvironmentAlertEvaluatorTest {

    private val safe = WeatherConditions(uvIndex = 2f, temperatureC = 20f, humidityPercent = 50f, fetchedAt = 0L)
    private val highUv = WeatherConditions(uvIndex = 7f, temperatureC = 20f, humidityPercent = 50f, fetchedAt = 0L)
    private val heat = WeatherConditions(uvIndex = 2f, temperatureC = 36f, humidityPercent = 50f, fetchedAt = 0L)
    private val humid = WeatherConditions(uvIndex = 2f, temperatureC = 20f, humidityPercent = 90f, fetchedAt = 0L)
    private val combined = WeatherConditions(uvIndex = 7f, temperatureC = 36f, humidityPercent = 50f, fetchedAt = 0L)

    @Test
    fun uvAbove6_returnsHighUv() {
        assertEquals(EnvironmentAlert.HIGH_UV, EnvironmentAlertEvaluator.evaluate(highUv))
    }

    @Test
    fun tempAbove35_returnsExtremeHeat() {
        assertEquals(EnvironmentAlert.EXTREME_HEAT, EnvironmentAlertEvaluator.evaluate(heat))
    }

    @Test
    fun humidityAbove85_returnsHighHumidity() {
        assertEquals(EnvironmentAlert.HIGH_HUMIDITY, EnvironmentAlertEvaluator.evaluate(humid))
    }

    @Test
    fun uvAndTempCombined_returnsRedCombined() {
        assertEquals(EnvironmentAlert.RED_COMBINED, EnvironmentAlertEvaluator.evaluate(combined))
    }

    @Test
    fun safeConditions_returnsNull() {
        assertNull(EnvironmentAlertEvaluator.evaluate(safe))
    }

    @Test
    fun boundaryUv5_returnsNull() {
        val boundary = safe.copy(uvIndex = 5f)
        assertNull(EnvironmentAlertEvaluator.evaluate(boundary))
    }

    @Test
    fun boundaryUv6_returnsAlert() {
        val boundary = safe.copy(uvIndex = 6f)
        assertEquals(EnvironmentAlert.HIGH_UV, EnvironmentAlertEvaluator.evaluate(boundary))
    }
}
