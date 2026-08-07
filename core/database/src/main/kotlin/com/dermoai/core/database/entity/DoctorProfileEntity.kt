package com.dermoai.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Clinician credentials for a doctor account.
 *
 * Indexed on `userId` because every read is "the profile for the signed-in
 * account" — the existing scan tables scan without indices, which is survivable
 * at their row counts but is not a pattern worth copying into a table joined on
 * every dashboard load.
 */
@Entity(
    tableName = "doctor_profiles",
    indices = [Index(value = ["userId"], unique = true)],
)
data class DoctorProfileEntity(
    @PrimaryKey val id: String,
    /** One profile per account, enforced by the unique index above. */
    val userId: String,
    val fullName: String,
    /**
     * Newline-joined list. Stored as a single column rather than a child table or
     * a JSON converter because nothing ever queries an individual qualification —
     * they are displayed as a block — and a child table would buy a join for no
     * query. Use [encodeQualifications] / [decodeQualifications] so the separator
     * lives in exactly one place.
     */
    val qualifications: String,
    val registrationNumber: String,
    val specialty: String,
    val institution: String,
    val yearsExperience: Int,
    val createdAt: Long,
    val updatedAt: Long,
    // Defaulted fields last so callers are never forced into named arguments for
    // the required ones.
    /** Name of `VerificationStatus`. Parsed leniently on read. */
    val verificationStatus: String = "UNVERIFIED",
    val verifiedAt: Long? = null,
    val bio: String = "",
    val syncStatus: String = "PENDING",
) {
    companion object {
        /** Newline: qualification strings routinely contain commas and semicolons. */
        private const val QUALIFICATION_SEPARATOR = "\n"

        fun encodeQualifications(values: List<String>): String =
            values.map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(QUALIFICATION_SEPARATOR)

        fun decodeQualifications(raw: String): List<String> =
            raw.split(QUALIFICATION_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
