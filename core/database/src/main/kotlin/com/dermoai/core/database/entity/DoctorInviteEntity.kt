package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A short redeemable code that creates a patient link.
 *
 * `code` carries a **unique** index, not merely an index: redemption looks a
 * patient's typed code up directly, and two live rows sharing a code would make
 * "which doctor did I just grant access to" ambiguous. Letting the database
 * refuse the collision is stronger than hoping the generator never repeats.
 * `doctorId` is indexed for the issuing doctor's own list of outstanding codes.
 */
@Entity(
    tableName = "doctor_invites",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["doctorId"]),
    ],
)
data class DoctorInviteEntity(
    @PrimaryKey val id: String,
    /** Owning account: the issuing doctor's `AuthUser.id` (not [doctorId]). */
    val userId: String,
    /** `DoctorProfileEntity.id` that issued this code. */
    val doctorId: String,
    /** Stored uppercase by convention so lookup is not case-sensitive by accident. */
    val code: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    // Defaulted fields last so callers are never forced into named arguments.
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val revoked: Boolean = false,
    val syncStatus: String = "PENDING",
)
