package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile_details")
data class UserProfileDetailsEntity(
    @PrimaryKey val userId: String,
    val age: Int = 0,
    val gender: String = "",
    val skinType: String = "",
    val skinTone: String = "",
    val skinConcerns: String = "",
    val allergies: String = "",
    val medications: String = "",
    val sunExposure: String = "",
    val waterIntake: String = "",
    val sleepHours: String = "",
    val stressLevel: String = "",
    val diet: String = "",
    val smoking: Boolean = false,
    val alcohol: Boolean = false,
    val exercise: String = "",
    val skinCareRoutine: String = "",
    val language: String = "en",
    val createdAt: Long,
)
