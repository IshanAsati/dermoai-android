package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local user profile persisted after authentication.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = SYNC_PENDING,
    /**
     * Name of `UserRole`. Defaults to PATIENT so rows written before roles
     * existed, and every non-doctor account, need no migration thought.
     *
     * Note this is the *presentation* role only — it decides which surface the
     * app shows. Access to another person's data is gated on a `patient_links`
     * row, never on this column, because a local string is trivially editable by
     * anyone with the device.
     */
    val role: String = ROLE_PATIENT,
) {
    companion object {
        const val SYNC_PENDING = "PENDING"
        const val SYNCED = "SYNCED"
        const val ROLE_PATIENT = "PATIENT"
        const val ROLE_DOCTOR = "DOCTOR"
    }
}