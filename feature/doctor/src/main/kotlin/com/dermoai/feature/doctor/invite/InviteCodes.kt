package com.dermoai.feature.doctor.invite

import com.dermoai.core.domain.model.DoctorInvite
import kotlin.random.Random

/**
 * Generation and input-normalisation for [DoctorInvite] codes.
 *
 * Split out of the ViewModels because both halves of the flow touch it — the
 * doctor's screen generates, the patient's screen normalises what gets typed —
 * and because "the code the doctor reads out is the code the patient's field
 * accepts" is exactly the kind of agreement that rots when two screens each
 * roll their own string handling.
 *
 * Pure Kotlin, no Android: the alphabet rules are the most testable and most
 * breakable thing in the invite flow.
 */
object InviteCodes {

    /** Deep-link shape the QR encodes. Generation only — nothing here parses inbound links. */
    const val DEEP_LINK_PREFIX: String = "dermoai://invite/"

    /**
     * A fresh code drawn uniformly from [DoctorInvite.CODE_ALPHABET].
     *
     * @param random injected so tests can pin a seed. Callers in production must
     *   pass a cryptographically strong source — an 8-character code guarded
     *   only by an expiry is guessable if the generator is predictable, and
     *   [Random.Default] is not a security primitive.
     */
    fun generate(random: Random): String = buildString(DoctorInvite.CODE_LENGTH) {
        repeat(DoctorInvite.CODE_LENGTH) {
            append(DoctorInvite.CODE_ALPHABET[random.nextInt(DoctorInvite.CODE_ALPHABET.length)])
        }
    }

    /**
     * What the patient's text field stores for whatever the patient typed.
     *
     * Uppercases first, then drops anything outside the alphabet. That single
     * filter covers the three things people actually do — lowercase, spaces,
     * hyphens copied from a printed card — without a rule per separator, and it
     * is safe precisely because the alphabet already excludes the ambiguous
     * glyphs (I, L, O, 0, 1), so nothing legitimate is a near-miss for a
     * character we discard.
     *
     * Truncates at [DoctorInvite.CODE_LENGTH] so a pasted deep link or a
     * double-paste cannot produce a code the lookup will never match.
     */
    fun normalise(raw: String): String =
        raw.uppercase()
            .filter { it in DoctorInvite.CODE_ALPHABET }
            .take(DoctorInvite.CODE_LENGTH)

    /** Whether a normalised code is long enough to be worth a database lookup. */
    fun isComplete(code: String): Boolean = code.length == DoctorInvite.CODE_LENGTH

    /** The string encoded into the QR bitmap. */
    fun deepLink(code: String): String = DEEP_LINK_PREFIX + code

    /** `ABCD-1234` — hyphenated purely for display; never store or look up this form. */
    fun formatForDisplay(code: String): String =
        if (code.length == DoctorInvite.CODE_LENGTH) {
            code.substring(0, 4) + "-" + code.substring(4)
        } else {
            code
        }
}
