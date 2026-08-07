package com.dermoai.core.data.sync

import com.dermoai.core.data.sync.AppwriteSchema.Fields

/**
 * Wire shapes for the five synced collections.
 *
 * ## Why plain maps and not kotlinx.serialization
 * The Appwrite Android SDK takes document data as `Any` (Gson-serialised on the
 * way out) and hands it back as `Map<String, Any>`. Introducing a second
 * serialisation framework to describe shapes the SDK is going to flatten to a
 * map anyway would buy annotations and cost a dependency. Explicit `toMap` /
 * `fromMap` also puts the *coercion* in one readable place, which matters more
 * than the boilerplate saved — see below.
 *
 * ## Why every read is coerced rather than cast
 * JSON has one number type. Gson materialises it as [Double] unless told
 * otherwise, so a `capturedAt` written as a Kotlin `Long` comes back as
 * `1.7038656E12` and a direct `as Long` throws. Worse, it throws *inside* a
 * pull, so one stray field takes out a whole dashboard refresh. Every accessor
 * below therefore reads through [Number] and falls back rather than throwing:
 * a document that is partly unreadable should cost the fields it broke, not the
 * screen.
 *
 * ## Why DTOs at all, given Room entities exist
 * The entities carry columns the server has no business holding — `syncStatus`
 * is a purely local bookkeeping column, and `createdAt`/`updatedAt` duplicate
 * Appwrite's own `$createdAt`/`$updatedAt`. Sending an entity verbatim would
 * push local sync state to the server and then read it back as authoritative,
 * which is how a sync loop starts. The DTOs are the subset that is genuinely
 * shared.
 */

/** @see AppwriteSchema.DOCTOR_PROFILES */
data class DoctorProfileDto(
    /** Appwrite document id. Mirrors `DoctorProfileEntity.id` so pushes are idempotent. */
    val id: String,
    val userId: String,
    val fullName: String,
    /** Newline-joined, byte-identical to the Room column. */
    val qualifications: String,
    val registrationNumber: String,
    val specialty: String,
    val institution: String,
    val yearsExperience: Int,
    val verificationStatus: String,
    val verifiedAt: Long?,
    val bio: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        Fields.DoctorProfiles.USER_ID to userId,
        Fields.DoctorProfiles.FULL_NAME to fullName,
        Fields.DoctorProfiles.QUALIFICATIONS to qualifications,
        Fields.DoctorProfiles.REGISTRATION_NUMBER to registrationNumber,
        Fields.DoctorProfiles.SPECIALTY to specialty,
        Fields.DoctorProfiles.INSTITUTION to institution,
        Fields.DoctorProfiles.YEARS_EXPERIENCE to yearsExperience,
        Fields.DoctorProfiles.VERIFICATION_STATUS to verificationStatus,
        Fields.DoctorProfiles.VERIFIED_AT to verifiedAt,
        Fields.DoctorProfiles.BIO to bio,
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): DoctorProfileDto = DoctorProfileDto(
            id = id,
            userId = data.string(Fields.DoctorProfiles.USER_ID),
            fullName = data.string(Fields.DoctorProfiles.FULL_NAME),
            qualifications = data.string(Fields.DoctorProfiles.QUALIFICATIONS),
            registrationNumber = data.string(Fields.DoctorProfiles.REGISTRATION_NUMBER),
            specialty = data.string(Fields.DoctorProfiles.SPECIALTY),
            institution = data.string(Fields.DoctorProfiles.INSTITUTION),
            yearsExperience = data.int(Fields.DoctorProfiles.YEARS_EXPERIENCE),
            // Note: NOT defaulted to VERIFIED. An unreadable status must never
            // render a clinician as credentialed — same fail-closed reading the
            // doctor feature's own mappers use.
            verificationStatus = data.string(
                Fields.DoctorProfiles.VERIFICATION_STATUS,
                fallback = DEFAULT_VERIFICATION_STATUS,
            ),
            verifiedAt = data.longOrNull(Fields.DoctorProfiles.VERIFIED_AT),
            bio = data.string(Fields.DoctorProfiles.BIO),
        )

        internal const val DEFAULT_VERIFICATION_STATUS = "UNVERIFIED"
    }
}

/** @see AppwriteSchema.PATIENT_LINKS */
data class PatientLinkDto(
    val id: String,
    /** `DoctorProfileEntity.id` of the clinician. */
    val doctorId: String,
    /** The clinician's *account* id. Needed to rebuild the document ACL. */
    val doctorUserId: String,
    val patientUserId: String,
    val patientDisplayName: String,
    val linkedAt: Long,
    val status: String,
    val consentGrantedAt: Long?,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        Fields.PatientLinks.DOCTOR_ID to doctorId,
        Fields.PatientLinks.DOCTOR_USER_ID to doctorUserId,
        Fields.PatientLinks.PATIENT_USER_ID to patientUserId,
        Fields.PatientLinks.PATIENT_DISPLAY_NAME to patientDisplayName,
        Fields.PatientLinks.LINKED_AT to linkedAt,
        Fields.PatientLinks.STATUS to status,
        Fields.PatientLinks.CONSENT_GRANTED_AT to consentGrantedAt,
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): PatientLinkDto = PatientLinkDto(
            id = id,
            doctorId = data.string(Fields.PatientLinks.DOCTOR_ID),
            doctorUserId = data.string(Fields.PatientLinks.DOCTOR_USER_ID),
            patientUserId = data.string(Fields.PatientLinks.PATIENT_USER_ID),
            patientDisplayName = data.string(Fields.PatientLinks.PATIENT_DISPLAY_NAME),
            linkedAt = data.long(Fields.PatientLinks.LINKED_AT),
            // Unknown/absent parses to INVITED, which grants nothing.
            status = data.string(Fields.PatientLinks.STATUS, fallback = DEFAULT_STATUS),
            consentGrantedAt = data.longOrNull(Fields.PatientLinks.CONSENT_GRANTED_AT),
        )

        internal const val DEFAULT_STATUS = "INVITED"
    }
}

/** @see AppwriteSchema.DOCTOR_INVITES */
data class DoctorInviteDto(
    val id: String,
    val doctorId: String,
    val doctorUserId: String,
    /** Uppercase by convention so lookup is not accidentally case-sensitive. */
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
    val maxUses: Int,
    val usedCount: Int,
    val revoked: Boolean,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        Fields.DoctorInvites.DOCTOR_ID to doctorId,
        Fields.DoctorInvites.DOCTOR_USER_ID to doctorUserId,
        Fields.DoctorInvites.CODE to code,
        Fields.DoctorInvites.CREATED_AT to createdAt,
        Fields.DoctorInvites.EXPIRES_AT to expiresAt,
        Fields.DoctorInvites.MAX_USES to maxUses,
        Fields.DoctorInvites.USED_COUNT to usedCount,
        Fields.DoctorInvites.REVOKED to revoked,
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): DoctorInviteDto = DoctorInviteDto(
            id = id,
            doctorId = data.string(Fields.DoctorInvites.DOCTOR_ID),
            doctorUserId = data.string(Fields.DoctorInvites.DOCTOR_USER_ID),
            code = data.string(Fields.DoctorInvites.CODE).uppercase(),
            createdAt = data.long(Fields.DoctorInvites.CREATED_AT),
            // Absent expiry reads as already-expired, not as forever. An invite
            // whose lifetime we cannot determine must not be redeemable — the
            // whole point of an 8-character code carrying an expiry.
            expiresAt = data.long(Fields.DoctorInvites.EXPIRES_AT, fallback = 0L),
            maxUses = data.int(Fields.DoctorInvites.MAX_USES, fallback = 1),
            usedCount = data.int(Fields.DoctorInvites.USED_COUNT),
            // Absent reads as revoked, for the same fail-closed reason.
            revoked = data.bool(Fields.DoctorInvites.REVOKED, fallback = true),
        )
    }
}

/**
 * Derived triage row. Deliberately image-free — see [AppwriteSchema].
 *
 * @see AppwriteSchema.SCAN_SUMMARIES
 */
data class ScanSummaryDto(
    /** Document id; equals [scanId] so re-pushing a scan overwrites rather than duplicates. */
    val id: String,
    val patientUserId: String,
    val scanId: String,
    val capturedAt: Long,
    val topLabel: String,
    val topLabelCode: String,
    val confidence: Float,
    val concernBand: String,
    val bodyArea: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        Fields.ScanSummaries.PATIENT_USER_ID to patientUserId,
        Fields.ScanSummaries.SCAN_ID to scanId,
        Fields.ScanSummaries.CAPTURED_AT to capturedAt,
        Fields.ScanSummaries.TOP_LABEL to topLabel,
        Fields.ScanSummaries.TOP_LABEL_CODE to topLabelCode,
        // Widened to Double: Appwrite has no float attribute type, only double.
        Fields.ScanSummaries.CONFIDENCE to confidence.toDouble(),
        Fields.ScanSummaries.CONCERN_BAND to concernBand,
        Fields.ScanSummaries.BODY_AREA to bodyArea,
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): ScanSummaryDto = ScanSummaryDto(
            id = id,
            patientUserId = data.string(Fields.ScanSummaries.PATIENT_USER_ID),
            scanId = data.string(Fields.ScanSummaries.SCAN_ID, fallback = id),
            capturedAt = data.long(Fields.ScanSummaries.CAPTURED_AT),
            topLabel = data.string(Fields.ScanSummaries.TOP_LABEL),
            topLabelCode = data.string(Fields.ScanSummaries.TOP_LABEL_CODE),
            confidence = data.float(Fields.ScanSummaries.CONFIDENCE),
            // Empty rather than a defaulted band: the doctor feature already
            // ranks "no evidence" below "evidence of something mild", and
            // inventing LOW here would quietly promote an unknown row.
            concernBand = data.string(Fields.ScanSummaries.CONCERN_BAND),
            bodyArea = data.string(Fields.ScanSummaries.BODY_AREA),
        )
    }
}

/** @see AppwriteSchema.AUDIT_ENTRIES */
data class AuditEntryDto(
    val id: String,
    val actorUserId: String,
    val subjectUserId: String,
    val action: String,
    val at: Long,
    /** Short context only. Never clinical content — both parties read this log. */
    val detail: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        Fields.AuditEntries.ACTOR_USER_ID to actorUserId,
        Fields.AuditEntries.SUBJECT_USER_ID to subjectUserId,
        Fields.AuditEntries.ACTION to action,
        Fields.AuditEntries.AT to at,
        Fields.AuditEntries.DETAIL to detail,
    )

    companion object {
        fun fromMap(id: String, data: Map<String, Any?>): AuditEntryDto = AuditEntryDto(
            id = id,
            actorUserId = data.string(Fields.AuditEntries.ACTOR_USER_ID),
            subjectUserId = data.string(Fields.AuditEntries.SUBJECT_USER_ID),
            // Unrecognised actions are kept verbatim and parsed leniently by the
            // domain layer, so a row written by a newer build still tells the
            // patient that *something* was accessed rather than vanishing.
            action = data.string(Fields.AuditEntries.ACTION),
            at = data.long(Fields.AuditEntries.AT),
            detail = data.string(Fields.AuditEntries.DETAIL),
        )
    }
}

// ── Coercion helpers ─────────────────────────────────────────────────────────
// `internal` so the tests can exercise them directly: these are where a real
// Appwrite response (Doubles for every number, nulls for every optional) meets
// Kotlin's type system, and they are far and away the most likely place for a
// pull to break.

internal fun Map<String, Any?>.string(key: String, fallback: String = ""): String =
    when (val value = this[key]) {
        is String -> value
        null -> fallback
        else -> value.toString()
    }

internal fun Map<String, Any?>.long(key: String, fallback: Long = 0L): Long =
    longOrNull(key) ?: fallback

internal fun Map<String, Any?>.longOrNull(key: String): Long? = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}

internal fun Map<String, Any?>.int(key: String, fallback: Int = 0): Int =
    when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: fallback
        else -> fallback
    }

internal fun Map<String, Any?>.float(key: String, fallback: Float = 0f): Float =
    when (val value = this[key]) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: fallback
        else -> fallback
    }

internal fun Map<String, Any?>.bool(key: String, fallback: Boolean = false): Boolean =
    when (val value = this[key]) {
        is Boolean -> value
        // Some JSON producers emit 0/1 or "true". Accepting them costs three
        // lines and avoids a silently-wrong `revoked` flag.
        is Number -> value.toInt() != 0
        is String -> value.toBooleanStrictOrNull() ?: fallback
        else -> fallback
    }
