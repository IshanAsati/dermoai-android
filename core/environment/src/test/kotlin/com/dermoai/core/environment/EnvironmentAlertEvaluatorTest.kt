package com.dermoai.core.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ── Air quality ──────────────────────────────────────────────────────────

    @Test
    fun aqi100_isStillFine() {
        assertNull(EnvironmentAlertEvaluator.evaluate(safe.copy(aqi = 100)))
    }

    @Test
    fun aqi101_returnsPoorAir() {
        assertEquals(EnvironmentAlert.POOR_AIR, EnvironmentAlertEvaluator.evaluate(safe.copy(aqi = 101)))
    }

    @Test
    fun aqi151_returnsUnhealthyAir() {
        assertEquals(EnvironmentAlert.UNHEALTHY_AIR, EnvironmentAlertEvaluator.evaluate(safe.copy(aqi = 151)))
    }

    @Test
    fun missingAqi_isIgnored() {
        assertNull(EnvironmentAlertEvaluator.evaluate(safe.copy(aqi = null)))
    }

    @Test
    fun unhealthyAir_outranksHighUvAlone() {
        assertEquals(EnvironmentAlert.UNHEALTHY_AIR, EnvironmentAlertEvaluator.evaluate(highUv.copy(aqi = 200)))
    }

    @Test
    fun uvPlusHeat_outranksEvenUnhealthyAir() {
        assertEquals(EnvironmentAlert.RED_COMBINED, EnvironmentAlertEvaluator.evaluate(combined.copy(aqi = 300)))
    }

    @Test
    fun poorAir_outranksHumidity() {
        assertEquals(EnvironmentAlert.POOR_AIR, EnvironmentAlertEvaluator.evaluate(humid.copy(aqi = 120)))
    }

    // ── When to notify ───────────────────────────────────────────────────────

    private val dayMs = AlertNotifyPolicy.REPEAT_AFTER_MS

    @Test
    fun noAlert_neverNotifies() {
        assertFalse(AlertNotifyPolicy.shouldNotify(null, EnvironmentAlert.POOR_AIR, 0L, 10 * dayMs))
    }

    @Test
    fun firstAlertAfterClear_notifies() {
        assertTrue(AlertNotifyPolicy.shouldNotify(EnvironmentAlert.POOR_AIR, null, 0L, 1L))
    }

    @Test
    fun aqiGoingUp_notifies() {
        // The headline case: AQI climbs from "poor" into "unhealthy" within the same day.
        assertTrue(AlertNotifyPolicy.shouldNotify(EnvironmentAlert.UNHEALTHY_AIR, EnvironmentAlert.POOR_AIR, 1_000L, 2_000L))
    }

    @Test
    fun sameAlert_staysQuietWithinADay() {
        assertFalse(AlertNotifyPolicy.shouldNotify(EnvironmentAlert.POOR_AIR, EnvironmentAlert.POOR_AIR, 1_000L, 1_000L + dayMs - 1))
    }

    @Test
    fun sameAlert_repeatsAfterADay() {
        assertTrue(AlertNotifyPolicy.shouldNotify(EnvironmentAlert.POOR_AIR, EnvironmentAlert.POOR_AIR, 1_000L, 1_000L + dayMs))
    }

    @Test
    fun easingAlert_staysQuiet() {
        assertFalse(AlertNotifyPolicy.shouldNotify(EnvironmentAlert.POOR_AIR, EnvironmentAlert.UNHEALTHY_AIR, 1_000L, 2_000L))
    }
}
