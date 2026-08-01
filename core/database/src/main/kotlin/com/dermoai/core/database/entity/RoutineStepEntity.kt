package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routine_steps")
data class RoutineStepEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val productName: String,
    val timeOfDay: String,
    val sortOrder: Int,
)
