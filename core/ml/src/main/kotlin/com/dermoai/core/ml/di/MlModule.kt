package com.dermoai.core.ml.di

import com.dermoai.core.domain.ml.SkinInferenceEngine
import com.dermoai.core.ml.TfliteSkinInferenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    @Singleton
    abstract fun bindSkinInferenceEngine(impl: TfliteSkinInferenceEngine): SkinInferenceEngine
}