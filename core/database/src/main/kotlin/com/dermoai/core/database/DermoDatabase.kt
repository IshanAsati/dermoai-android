package com.dermoai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dermoai.core.database.dao.AuditEntryDao
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.dao.DoctorInviteDao
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.JournalEntryDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.dao.RoutineStepDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.dao.StepCompletionDao
import com.dermoai.core.database.dao.TreatmentRoutineDao
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.database.entity.DailyCheckInEntity
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.JournalEntryEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.core.database.entity.RoutineStepEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.database.entity.StepCompletionEntity
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import com.dermoai.core.database.entity.UserProfileEntity
import com.dermoai.core.database.entity.UserProfileDetailsEntity

/**
 * Version 6 adds the doctor dashboard tables (doctor_profiles, patient_links,
 * doctor_invites, audit_entries) and a `role` column on user_profiles.
 *
 * No migration is written: DatabaseModule still falls back to destructive
 * migration and there are no production users, so the honest move is to say so
 * rather than ship an untested migration path. That stops being acceptable the
 * moment this reaches a real device fleet — at which point audit_entries in
 * particular must survive, since a wiped access log cannot be reconstructed.
 */
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
        DoctorProfileEntity::class,
        PatientLinkEntity::class,
        DoctorInviteEntity::class,
        AuditEntryEntity::class,
    ],
    version = 6,
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
    abstract fun doctorProfileDao(): DoctorProfileDao
    abstract fun patientLinkDao(): PatientLinkDao
    abstract fun doctorInviteDao(): DoctorInviteDao
    abstract fun auditEntryDao(): AuditEntryDao
}
