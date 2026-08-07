package com.dermoai.core.domain.model

/**
 * One record of a doctor touching a patient's data.
 *
 * Exists so the patient — not just the doctor — can answer "who looked at my
 * scans, and when". That is the whole reason the doctor dashboard is allowed to
 * show someone else's medical photos at all: access is consented via
 * [PatientLink] and observable after the fact via this log.
 *
 * Append-only by intent. There is no update or delete in [AuditEntry]'s DAO
 * beyond the upsert needed for sync, because a log the observed party's
 * counterparty can edit is not a log.
 */
data class AuditEntry(
    val id: String,
    /** [AuthUser.id] that performed the action — normally the doctor. */
    val actorUserId: String,
    /** [AuthUser.id] whose data was acted on — normally the patient. */
    val subjectUserId: String,
    val action: AuditAction,
    val at: Long,
    /**
     * Short context for the row, e.g. the scan id viewed. Must stay free of
     * clinical content: this log is shown to both parties and is not the place
     * to duplicate findings.
     */
    val detail: String = "",
)

enum class AuditAction {
    VIEWED_PATIENT,
    VIEWED_SCAN,
    EXPORTED_REPORT,
    LINKED_PATIENT,
    REVOKED_LINK,
    ;

    companion object {
        /**
         * Unknown values map to [VIEWED_PATIENT], the least-privilege reading, so
         * a row written by a newer build still renders as *some* access having
         * happened rather than disappearing from the patient's view.
         */
        fun fromStorage(raw: String?): AuditAction =
            entries.firstOrNull { it.name == raw } ?: VIEWED_PATIENT
    }
}
