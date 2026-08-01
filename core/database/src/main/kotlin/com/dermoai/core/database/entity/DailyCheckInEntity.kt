package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val skinFeel: Int = 3,
    val itchDiscomfort: Int = 0,
    val sleepQuality: Int = 3,
    val stressLevel: Int = 3,
    val newProductUsed: Boolean = false,
    val newProductNote: String = "",
    val notes: String = "",
    val voiceNotePath: String? = null,
    val createdAt: Long,
    val syncStatus: String = "PENDING",
)
