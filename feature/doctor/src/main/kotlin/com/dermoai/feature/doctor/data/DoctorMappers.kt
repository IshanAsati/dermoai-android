package com.dermoai.feature.doctor.data

import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.core.domain.model.AuditAction
import com.dermoai.core.domain.model.AuditEntry
import com.dermoai.core.domain.model.ConditionSeverity
import com.dermoai.core.domain.model.DoctorInvite
import com.dermoai.core.domain.model.DoctorProfile
import com.dermoai.core.domain.model.LinkStatus
import com.dermoai.core.domain.model.PatientLink
import com.dermoai.core.domain.model.VerificationStatus

/**
 * Room entity → domain model, for the doctor feature only.
 *
 * The screens are written against the domain types because those carry the
 * derived rules the UI must obey — [PatientLink.grantsAccess],
 * [DoctorProfile.isVerified], [DoctorInvite.isUsable] — and reading raw entity
 * columns in a composable is how a screen ends up re-deriving "is this allowed"
 * and getting it wrong. Enum columns are parsed leniently in one place so a row
 * written by a newer build renders as something rather than crashing a
 * clinician's dashboard.
 *
 * One direction only: writes go through the DAOs' targeted UPDATE statements,
 * which exist precisely so a status change never round-trips a whole row.
 */

fun DoctorProfileEntity.toDomain(): DoctorProfile = DoctorProfile(
    id = id,
    userId = userId,
    fullName = fullName,
    qualifications = DoctorProfileEntity.decodeQualifications(qualifications),
    registrationNumber = registrationNumber,
    specialty = specialty,
    institution = institution,
    yearsExperience = yearsExperience,
    // Unknown parses to UNVERIFIED — the fail-closed reading. A status we cannot
    // interpret must never render as a credentialed clinician.
    verificationStatus = VerificationStatus.entries
        .firstOrNull { it.name == verificationStatus } ?: VerificationStatus.UNVERIFIED,
    verifiedAt = verifiedAt,
    bio = bio,
)

fun PatientLinkEntity.toDomain(): PatientLink = PatientLink(
    id = id,
    doctorId = doctorId,
    patientUserId = patientUserId,
    patientDisplayName = patientDisplayName,
    linkedAt = linkedAt,
    // Unknown parses to INVITED, which does not grant access.
    status = LinkStatus.entries.firstOrNull { it.name == status } ?: LinkStatus.INVITED,
    consentGrantedAt = consentGrantedAt,
)

fun DoctorInviteEntity.toDomain(): DoctorInvite = DoctorInvite(
    id = id,
    doctorId = doctorId,
    code = code,
    createdAt = createdAt,
    expiresAt = expiresAt,
    maxUses = maxUses,
    usedCount = usedCount,
    revoked = revoked,
)

fun AuditEntryEntity.toDomain(): AuditEntry = AuditEntry(
    id = id,
    actorUserId = actorUserId,
    subjectUserId = subjectUserId,
    action = AuditAction.fromStorage(action),
    at = at,
    detail = detail,
)

/**
 * `ScanPredictionEntity.concernBand` holds a [ConditionSeverity] name. Null for
 * an unrecognised value rather than a defaulted LOW: an unknown band is missing
 * evidence, and [com.dermoai.feature.doctor.triage.TriageRanking] already ranks
 * "no evidence" below "evidence of something mild".
 */
fun concernBandToSeverity(raw: String?): ConditionSeverity? =
    ConditionSeverity.entries.firstOrNull { it.name == raw }
