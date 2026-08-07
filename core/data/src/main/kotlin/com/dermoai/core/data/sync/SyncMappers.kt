package com.dermoai.core.data.sync

import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity

/**
 * Room entity ↔ Appwrite DTO.
 *
 * Both directions here, unlike the doctor feature's one-way
 * `DoctorMappers.kt` — sync is inherently bidirectional, and the round trip is
 * exactly what the unit tests pin down.
 *
 * ## Three things these mappers are careful about
 *
 * **`syncStatus` never crosses the wire.** It is local bookkeeping about
 * whether *this device* has pushed a row. Sending it would let one device's
 * queue state become another device's truth.
 *
 * **`createdAt` / `updatedAt` never cross the wire either.** Appwrite keeps its
 * own `$createdAt`/`$updatedAt`, and a device with a skewed clock writing
 * timestamps that another device then treats as authoritative is a bug that
 * only shows up in the field. On the way *in* they are set from the caller's
 * clock, which is honest: they mean "when this device learned about the row".
 * The domain-meaningful timestamps — `linkedAt`, `consentGrantedAt`,
 * `capturedAt`, `at`, `expiresAt` — are synced, because those are facts about
 * the world rather than about a database row.
 *
 * **`userId` is reconstructed, not transmitted blindly.** Room's convention is
 * that every row carries an owning account id. For a patient link that owner
 * differs by which side of the link you are on, so [toEntity] takes it as a
 * parameter rather than guessing.
 */

// ── doctor_profiles ──────────────────────────────────────────────────────────

fun DoctorProfileEntity.toDto(): DoctorProfileDto = DoctorProfileDto(
    id = id,
    userId = userId,
    fullName = fullName,
    // Kept newline-joined rather than exploded into an Appwrite array: nothing
    // queries an individual qualification, and a lossless string round trip is
    // one fewer place for the separator convention to drift.
    qualifications = qualifications,
    registrationNumber = registrationNumber,
    specialty = specialty,
    institution = institution,
    yearsExperience = yearsExperience,
    verificationStatus = verificationStatus,
    verifiedAt = verifiedAt,
    bio = bio,
)

/**
 * @param now clock reading for the local bookkeeping columns. Passed in so the
 *   round-trip test is deterministic and so a caller can preserve an existing
 *   row's `createdAt`.
 */
fun DoctorProfileDto.toEntity(
    now: Long,
    createdAt: Long = now,
    syncStatus: String = SYNC_STATUS_SYNCED,
): DoctorProfileEntity = DoctorProfileEntity(
    id = id,
    userId = userId,
    fullName = fullName,
    qualifications = qualifications,
    registrationNumber = registrationNumber,
    specialty = specialty,
    institution = institution,
    yearsExperience = yearsExperience,
    createdAt = createdAt,
    updatedAt = now,
    verificationStatus = verificationStatus,
    verifiedAt = verifiedAt,
    bio = bio,
    syncStatus = syncStatus,
)

// ── patient_links ────────────────────────────────────────────────────────────

/**
 * @param doctorUserId the clinician's *account* id.
 *
 * `PatientLinkEntity.userId` already holds it when the row was written by the
 * doctor's device, but it holds the *patient's* id on the patient's device —
 * the column means "owning account", not "the doctor". Deriving it from
 * `userId` would therefore be right half the time and silently wrong the other
 * half, and the half it gets wrong is the half that builds the document ACL.
 * So it is an explicit parameter, defaulting to the common case.
 */
fun PatientLinkEntity.toDto(doctorUserId: String = userId): PatientLinkDto = PatientLinkDto(
    id = id,
    doctorId = doctorId,
    doctorUserId = doctorUserId,
    patientUserId = patientUserId,
    patientDisplayName = patientDisplayName,
    linkedAt = linkedAt,
    status = status,
    consentGrantedAt = consentGrantedAt,
)

/**
 * @param ownerUserId which account owns this row *on this device* — the doctor's
 *   id on a doctor's phone, the patient's on a patient's phone.
 */
fun PatientLinkDto.toEntity(
    ownerUserId: String,
    now: Long,
    createdAt: Long = now,
    syncStatus: String = SYNC_STATUS_SYNCED,
): PatientLinkEntity = PatientLinkEntity(
    id = id,
    userId = ownerUserId,
    doctorId = doctorId,
    patientUserId = patientUserId,
    patientDisplayName = patientDisplayName,
    linkedAt = linkedAt,
    createdAt = createdAt,
    updatedAt = now,
    status = status,
    consentGrantedAt = consentGrantedAt,
    syncStatus = syncStatus,
)

// ── doctor_invites ───────────────────────────────────────────────────────────

fun DoctorInviteEntity.toDto(doctorUserId: String = userId): DoctorInviteDto = DoctorInviteDto(
    id = id,
    doctorId = doctorId,
    doctorUserId = doctorUserId,
    code = code.uppercase(),
    createdAt = createdAt,
    expiresAt = expiresAt,
    maxUses = maxUses,
    usedCount = usedCount,
    revoked = revoked,
)

fun DoctorInviteDto.toEntity(
    now: Long,
    ownerUserId: String = doctorUserId,
    syncStatus: String = SYNC_STATUS_SYNCED,
): DoctorInviteEntity = DoctorInviteEntity(
    id = id,
    userId = ownerUserId,
    doctorId = doctorId,
    code = code.uppercase(),
    // `createdAt` is a real fact about the invite (it bounds the code's life
    // alongside expiresAt), so unlike the other tables it *is* synced.
    createdAt = createdAt,
    updatedAt = now,
    expiresAt = expiresAt,
    maxUses = maxUses,
    usedCount = usedCount,
    revoked = revoked,
    syncStatus = syncStatus,
)

// ── scan_summaries ───────────────────────────────────────────────────────────

/**
 * Builds the image-free triage row a doctor actually needs from the two local
 * tables that hold it.
 *
 * Note what is *not* read from [scan]: `imagePath`, `thumbnailPath`,
 * `voiceNotePath`, and `note`. The paths are device-local and meaningless
 * elsewhere; the free-text note is patient-authored content that has not been
 * consented for clinician review and does not belong in a triage list. If a
 * doctor should see notes, that is a product decision with its own consent
 * copy, not a field quietly added to a sync DTO.
 *
 * @param topPrediction the rank-0 prediction, or null when inference has not
 *   run. Null yields an empty label/band and zero confidence, which the doctor
 *   feature's triage ranking already treats as "no evidence" — strictly weaker
 *   than any real finding, which is the correct place for an unanalysed scan.
 */
fun scanSummaryDtoOf(
    scan: SkinScanEntity,
    topPrediction: ScanPredictionEntity?,
): ScanSummaryDto = ScanSummaryDto(
    // Document id = scan id, so re-pushing an updated scan overwrites its
    // summary instead of accumulating one row per sync.
    id = scan.id,
    patientUserId = scan.userId,
    scanId = scan.id,
    capturedAt = scan.capturedAt,
    topLabel = topPrediction?.label.orEmpty(),
    topLabelCode = topPrediction?.labelCode.orEmpty(),
    confidence = topPrediction?.confidence ?: 0f,
    concernBand = topPrediction?.concernBand.orEmpty(),
    bodyArea = scan.bodyArea,
)

// ── audit_entries ────────────────────────────────────────────────────────────

fun AuditEntryEntity.toDto(): AuditEntryDto = AuditEntryDto(
    id = id,
    actorUserId = actorUserId,
    subjectUserId = subjectUserId,
    action = action,
    at = at,
    detail = detail,
)

/**
 * @param ownerUserId which account owns this row locally. The table convention
 *   mirrors the actor, but a patient pulling "who looked at my scans" owns rows
 *   whose actor is somebody else — so, again, explicit.
 */
fun AuditEntryDto.toEntity(
    now: Long,
    ownerUserId: String = actorUserId,
    syncStatus: String = SYNC_STATUS_SYNCED,
): AuditEntryEntity = AuditEntryEntity(
    id = id,
    userId = ownerUserId,
    actorUserId = actorUserId,
    subjectUserId = subjectUserId,
    action = action,
    at = at,
    createdAt = at,
    updatedAt = now,
    detail = detail,
    syncStatus = syncStatus,
)

/**
 * The `syncStatus` value meaning "this row matches the server".
 *
 * A literal rather than an entity constant because the entities only define
 * their default (`"PENDING"`); the entity files are owned by the database
 * module and this sync layer must not reach in and add to them.
 */
const val SYNC_STATUS_SYNCED: String = "SYNCED"
