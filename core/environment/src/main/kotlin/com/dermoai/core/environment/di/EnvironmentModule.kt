package com.dermoai.core.environment.di

import com.dermoai.core.environment.EnvironmentRepository
import com.dermoai.core.environment.OpenMeteoWeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EnvironmentModule {
    @Binds
    @Singleton
    abstract fun bindEnvironmentRepository(impl: OpenMeteoWeatherRepository): EnvironmentRepository
}
