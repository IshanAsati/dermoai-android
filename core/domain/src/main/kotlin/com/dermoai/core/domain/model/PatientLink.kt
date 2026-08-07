package com.dermoai.core.domain.model

/**
 * The consent record that lets one doctor see one patient's scans.
 *
 * This — not [UserRole] — is the actual authorisation boundary for the doctor
 * dashboard. Being a DOCTOR grants a different UI; only an [LinkStatus.ACTIVE]
 * link with a non-null [consentGrantedAt] grants sight of someone's photos.
 * Every read path should check this object, not the role.
 */
data class PatientLink(
    val id: String,
    /** [DoctorProfile.id] of the clinician on this link. */
    val doctorId: String,
    /** [AuthUser.id] of the patient. Never a profile id — patients have no profile. */
    val patientUserId: String,
    /**
     * Snapshotted at link time so a doctor's list still renders when the patient's
     * own profile row has not synced to this device. Display only — never treat it
     * as identity.
     */
    val patientDisplayName: String,
    val linkedAt: Long,
    val status: LinkStatus = LinkStatus.INVITED,
    /**
     * When the *patient* accepted. Null while [LinkStatus.INVITED]: a doctor
     * creating a link is an offer, not consent, so this stays null until the
     * patient acts.
     */
    val consentGrantedAt: Long? = null,
) {
    /**
     * The single question every read path should ask. Requires both an active
     * status and a recorded consent timestamp, so a link force-set to ACTIVE
     * without the patient ever accepting still fails closed.
     */
    val grantsAccess: Boolean
        get() = status == LinkStatus.ACTIVE && consentGrantedAt != null
}

/**
 * [REVOKED] is a terminal state kept as a row rather than a delete, because the
 * audit trail needs to be able to say a doctor *used to* have access and when
 * that stopped. Re-inviting creates a new link.
 */
enum class LinkStatus {
    INVITED,
    ACTIVE,
    REVOKED,
}
