package com.dermoai.core.database.di

import android.content.Context
import androidx.room.Room
import com.dermoai.core.database.DermoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DermoDatabase {
        return Room.databaseBuilder(
            context,
            DermoDatabase::class.java,
            "dermoai.db",
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserProfileDao(database: DermoDatabase) = database.userProfileDao()

    @Provides
    fun provideSkinScanDao(database: DermoDatabase) = database.skinScanDao()

    @Provides
    fun provideScanPredictionDao(database: DermoDatabase) = database.scanPredictionDao()

    @Provides
    fun provideDailyCheckInDao(database: DermoDatabase) = database.dailyCheckInDao()

    @Provides
    fun provideTreatmentRoutineDao(database: DermoDatabase) = database.treatmentRoutineDao()

    @Provides
    fun provideRoutineStepDao(database: DermoDatabase) = database.routineStepDao()

    @Provides
    fun provideStepCompletionDao(database: DermoDatabase) = database.stepCompletionDao()

    @Provides
    fun provideJournalEntryDao(database: DermoDatabase) = database.journalEntryDao()
}