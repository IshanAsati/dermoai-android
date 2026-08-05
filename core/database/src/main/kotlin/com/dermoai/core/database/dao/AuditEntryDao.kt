package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.AuditEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append and read only. There is intentionally no update or delete statement:
 * the point of this log is that the party being observed can see it, and a log
 * the observer can edit or clear is not evidence of anything. `upsert` exists
 * solely so a sync pass can re-land the same immutable row idempotently.
 */
@Dao
interface AuditEntryDao {

    /** The patient's "who accessed my data" view. */
    @Query("SELECT * FROM audit_entries WHERE subjectUserId = :subjectUserId ORDER BY at DESC")
    fun observeBySubject(subjectUserId: String): Flow<List<AuditEntryEntity>>

    /** The doctor's own activity view. */
    @Query("SELECT * FROM audit_entries WHERE actorUserId = :actorUserId ORDER BY at DESC")
    fun observeByActor(actorUserId: String): Flow<List<AuditEntryEntity>>

    /**
     * One doctor's access to one patient — what the patient sees when they tap a
     * doctor in their list, and what is attached to a revocation.
     */
    @Query(
        "SELECT * FROM audit_entries WHERE subjectUserId = :subjectUserId " +
            "AND actorUserId = :actorUserId ORDER BY at DESC",
    )
    fun observeByActorAndSubject(
        actorUserId: String,
        subjectUserId: String,
    ): Flow<List<AuditEntryEntity>>

    @Query("SELECT * FROM audit_entries WHERE subjectUserId = :subjectUserId ORDER BY at DESC LIMIT 1")
    suspend fun getLatestBySubject(subjectUserId: String): AuditEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AuditEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AuditEntryEntity>)
}
