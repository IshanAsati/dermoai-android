package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doctor_share_passcodes")
data class DoctorSharePasscodeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val passcode: String,
    val dateRangeDays: Int,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 48L * 60 * 60 * 1000,
)
