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
    fun provideUserProfileDetailsDao(database: DermoDatabase) = database.userProfileDetailsDao()

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

    // Doctor dashboard (schema v6).
    @Provides
    fun provideDoctorProfileDao(database: DermoDatabase) = database.doctorProfileDao()

    @Provides
    fun providePatientLinkDao(database: DermoDatabase) = database.patientLinkDao()

    @Provides
    fun provideDoctorInviteDao(database: DermoDatabase) = database.doctorInviteDao()

    @Provides
    fun provideAuditEntryDao(database: DermoDatabase) = database.auditEntryDao()
}