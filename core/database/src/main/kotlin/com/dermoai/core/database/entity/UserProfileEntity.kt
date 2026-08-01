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
) {
    companion object {
        const val SYNC_PENDING = "PENDING"
        const val SYNCED = "SYNCED"
    }
}