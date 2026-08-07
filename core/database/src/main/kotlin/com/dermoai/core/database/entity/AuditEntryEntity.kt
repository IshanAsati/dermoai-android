package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only record of a doctor accessing patient data.
 *
 * Indexed on both `subjectUserId` and `actorUserId` because the log is read from
 * both directions and neither read is optional: the patient's "who saw my scans"
 * screen filters by subject, the doctor's own activity view filters by actor.
 * This table grows monotonically — unlike the scan tables it is deliberately
 * never pruned — so an unindexed scan here degrades with time rather than
 * staying merely inefficient.
 */
@Entity(
    tableName = "audit_entries",
    indices = [
        Index(value = ["subjectUserId", "at"]),
        Index(value = ["actorUserId", "at"]),
    ],
)
data class AuditEntryEntity(
    @PrimaryKey val id: String,
    /** Owning account, per the table convention: mirrors [actorUserId]. */
    val userId: String,
    val actorUserId: String,
    val subjectUserId: String,
    /** Name of `AuditAction`. */
    val action: String,
    val at: Long,
    val createdAt: Long,
    val updatedAt: Long,
    // Defaulted fields last so callers are never forced into named arguments.
    /** Short context (e.g. a scan id). Never clinical content — both parties read this. */
    val detail: String = "",
    val syncStatus: String = "PENDING",
)
