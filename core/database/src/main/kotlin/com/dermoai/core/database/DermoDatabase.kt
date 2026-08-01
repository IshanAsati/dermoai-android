package com.dermoai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.JournalEntryDao
import com.dermoai.core.database.dao.RoutineStepDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.dao.StepCompletionDao
import com.dermoai.core.database.dao.TreatmentRoutineDao
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.DailyCheckInEntity
import com.dermoai.core.database.entity.JournalEntryEntity
import com.dermoai.core.database.entity.RoutineStepEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.database.entity.StepCompletionEntity
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import com.dermoai.core.database.entity.UserProfileEntity
import com.dermoai.core.database.entity.UserProfileDetailsEntity

@Database(
    entities = [
        UserProfileEntity::class,
        UserProfileDetailsEntity::class,
        SkinScanEntity::class,
        ScanPredictionEntity::class,
        DailyCheckInEntity::class,
        TreatmentRoutineEntity::class,
        RoutineStepEntity::class,
        StepCompletionEntity::class,
        JournalEntryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class DermoDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userProfileDetailsDao(): UserProfileDetailsDao
    abstract fun skinScanDao(): SkinScanDao
    abstract fun scanPredictionDao(): ScanPredictionDao
    abstract fun dailyCheckInDao(): DailyCheckInDao
    abstract fun treatmentRoutineDao(): TreatmentRoutineDao
    abstract fun routineStepDao(): RoutineStepDao
    abstract fun stepCompletionDao(): StepCompletionDao
    abstract fun journalEntryDao(): JournalEntryDao
}
