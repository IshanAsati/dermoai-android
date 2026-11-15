package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doctor_clinical_notes")
data class DoctorClinicalNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val doctorName: String,
    val clinicName: String = "",
    val clinicalImpression: String,
    val recommendedTreatment: String = "",
    val followupDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
