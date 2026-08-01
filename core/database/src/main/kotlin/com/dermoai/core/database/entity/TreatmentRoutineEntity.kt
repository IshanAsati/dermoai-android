package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatment_routines")
data class TreatmentRoutineEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val reminderTime: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
)
