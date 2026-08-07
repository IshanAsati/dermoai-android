package com.dermoai.core.domain.model

/**
 * The clinician-facing half of a [UserRole.DOCTOR] account.
 *
 * Kept separate from [AuthUser] rather than bolted onto it because the vast
 * majority of accounts will never have one, and because these fields are
 * claims *made by* the user that carry a [VerificationStatus] — mixing them
 * into the identity object would make unverified claims look authoritative
 * everywhere the identity is read.
 */
data class DoctorProfile(
    val id: String,
    /** The [AuthUser.id] this profile belongs to. One profile per account. */
    val userId: String,
    val fullName: String,
    val qualifications: List<String>,
    /** Medical council / board registration number, as typed by the doctor. */
    val registrationNumber: String,
    val specialty: String,
    val institution: String,
    val yearsExperience: Int,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    /** Null until a human or an upstream service actually verifies the claim. */
    val verifiedAt: Long? = null,
    val bio: String = "",
) {
    /**
     * The only flag the UI should use before showing a doctor as credentialed to
     * a patient. Exposed as a property so no screen has to re-derive it and get
     * it subtly wrong (e.g. treating PENDING as good enough).
     */
    val isVerified: Boolean
        get() = verificationStatus == VerificationStatus.VERIFIED
}

/**
 * Lifecycle of a credential claim.
 *
 * [UNVERIFIED] and [REJECTED] are distinct on purpose: the first means nobody
 * has looked, the second means somebody looked and said no, and collapsing them
 * would let a rejected claim quietly re-enter the review queue.
 */
enum class VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED,
}
