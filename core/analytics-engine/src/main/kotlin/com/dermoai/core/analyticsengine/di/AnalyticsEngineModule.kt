package com.dermoai.core.analyticsengine.di

import com.dermoai.core.analyticsengine.RuleBasedInsightsEngine
import com.dermoai.core.domain.insights.InsightsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsEngineModule {
    @Binds
    @Singleton
    abstract fun bindInsightsEngine(impl: RuleBasedInsightsEngine): InsightsEngine
}