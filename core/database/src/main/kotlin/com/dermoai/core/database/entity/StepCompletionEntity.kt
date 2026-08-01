package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "step_completions")
data class StepCompletionEntity(
    @PrimaryKey val id: String,
    val stepId: String,
    val routineId: String,
    val completedAt: Long,
    val dateKey: String,
)
