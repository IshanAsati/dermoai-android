package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skin_scans")
data class SkinScanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val imagePath: String,
    val thumbnailPath: String,
    val capturedAt: Long,
    val note: String = "",
    val bodyArea: String = "",
    val rotation: Float = 0f,
    val voiceNotePath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
)
