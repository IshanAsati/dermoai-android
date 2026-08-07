package com.dermoai.feature.doctor.data

import com.dermoai.core.database.dao.AuditEntryDao
import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.domain.model.AuditAction
import java.util.UUID
import javax.inject.Inject

/**
 * The single place this feature writes to the access log.
 *
 * Centralised for one reason: the actor/subject convention has to be identical
 * across four screens or the patient's privacy view stops making sense. The
 * rule is **the actor is always the doctor whose access changed, and the
 * subject is always the patient**, even for the two events the patient
 * physically performs (granting on [com.dermoai.feature.doctor.RedeemInviteScreen],
 * revoking on [com.dermoai.feature.doctor.PatientPrivacyScreen]).
 *
 * Attributing those to the patient would read more literally but would scatter
 * a doctor's history across two actor ids, so "everything Dr X's access ever
 * did" could no longer be answered with one query — and that question is the
 * entire point of showing the patient this log. Who *initiated* the change is
 * carried in [AuditEntryEntity.detail] instead.
 *
 * `detail` never carries clinical content: both parties read these rows, and
 * duplicating findings into an audit log spreads them somewhere nothing
 * revokes.
 */
class AuditLogger @Inject constructor(
    private val auditEntryDao: AuditEntryDao,
) {

    suspend fun record(
        doctorUserId: String,
        patientUserId: String,
        action: AuditAction,
        detail: String = "",
        now: Long = System.currentTimeMillis(),
    ) {
        auditEntryDao.upsert(
            AuditEntryEntity(
                id = UUID.randomUUID().toString(),
                // Owning account mirrors the actor, per the table convention.
                userId = doctorUserId,
                actorUserId = doctorUserId,
                subjectUserId = patientUserId,
                action = action.name,
                at = now,
                createdAt = now,
                updatedAt = now,
                detail = detail,
            ),
        )
    }
}
