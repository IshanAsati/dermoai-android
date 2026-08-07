package com.dermoai.core.domain.model

/**
 * A short code a doctor reads out (or prints on a card) for a patient to type in,
 * which then creates a [PatientLink].
 *
 * Short and human-typable is the whole point — a deep link or QR code fails in
 * the exact setting this is for: a consultation room, patient's own phone, no
 * shared channel. That shortness is also why every invite carries an expiry and
 * a use cap: an 8-character code is guessable given unlimited attempts and
 * unlimited lifetime, so neither is allowed.
 */
data class DoctorInvite(
    val id: String,
    /** [DoctorProfile.id] that issued this code. */
    val doctorId: String,
    /** Short, human-typable, case-insensitive by convention. See [CODE_LENGTH]. */
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
    /** How many patients may redeem this code. 1 for a single consultation. */
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val revoked: Boolean = false,
) {
    /**
     * Whether this code may still be redeemed at [now].
     *
     * Takes the clock as a parameter rather than reading it: redemption checks
     * must be testable and must be able to run against a server-supplied time
     * instead of a device clock the holder of the code controls.
     */
    fun isUsable(now: Long): Boolean =
        !revoked && now < expiresAt && usedCount < maxUses

    /** Why it is not usable, for a message the patient can act on. */
    fun unusableReason(now: Long): String? = when {
        revoked -> "This invite code was cancelled by the doctor."
        now >= expiresAt -> "This invite code has expired."
        usedCount >= maxUses -> "This invite code has already been used."
        else -> null
    }

    val remainingUses: Int
        get() = (maxUses - usedCount).coerceAtLeast(0)

    companion object {
        /**
         * Eight characters read aloud without ambiguity when drawn from an
         * alphabet that omits look-alikes. Generation lives with whoever creates
         * invites; this constant is here so the UI and the generator agree on the
         * field width instead of each guessing.
         */
        const val CODE_LENGTH: Int = 8

        /** Excludes 0/O and 1/I/L, which are the characters people mistype. */
        const val CODE_ALPHABET: String = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}
