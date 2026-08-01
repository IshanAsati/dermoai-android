package com.dermoai.core.data.di

import com.dermoai.core.data.auth.FirebaseAuthRepository
import com.dermoai.core.data.preferences.UserPreferencesRepositoryImpl
import com.dermoai.core.domain.repository.AuthRepository
import com.dermoai.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl,
    ): UserPreferencesRepository
}
