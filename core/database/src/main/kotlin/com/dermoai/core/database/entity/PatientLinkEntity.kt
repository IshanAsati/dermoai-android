package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted consent link between a doctor and a patient.
 *
 * Indexed because both columns are hot filter paths: the doctor's list reads by
 * `doctorId` on every dashboard render, and every access check reads the pair.
 * The existing scan tables carry no indices, which is survivable at their row
 * counts, but a table consulted on the *authorisation* path should not be doing
 * a full scan — a slow check is a check somebody eventually caches wrongly.
 *
 * The `(doctorId, patientUserId)` index is unique rather than plain: a doctor
 * must never hold two links to one patient, or "is access granted" has two
 * answers. Its leading column also serves the `doctorId`-only queries, so no
 * separate index on that column is needed.
 */
@Entity(
    tableName = "patient_links",
    indices = [
        Index(value = ["doctorId", "patientUserId"], unique = true),
        Index(value = ["patientUserId"]),
    ],
)
data class PatientLinkEntity(
    @PrimaryKey val id: String,
    /**
     * Owning account, per the table convention: the doctor's `AuthUser.id`.
     * Distinct from [doctorId], which is a `DoctorProfileEntity.id`. Carried
     * separately so sync can scope rows to an account without joining profiles.
     */
    val userId: String,
    val doctorId: String,
    val patientUserId: String,
    /** Snapshot for display when the patient's own profile row is not on this device. */
    val patientDisplayName: String,
    val linkedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** Name of `LinkStatus`. */
    val status: String = "INVITED",
    /** Null until the patient accepts. Access requires both this and status ACTIVE. */
    val consentGrantedAt: Long? = null,
    val syncStatus: String = "PENDING",
)
