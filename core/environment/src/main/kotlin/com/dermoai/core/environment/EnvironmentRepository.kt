package com.dermoai.core.environment

import com.dermoai.core.common.result.AppResult

interface EnvironmentRepository {
    suspend fun fetchCurrent(lat: Double, lon: Double): AppResult<WeatherConditions>
    fun cachedConditions(): WeatherConditions?
    fun cache(conditions: WeatherConditions)
}
