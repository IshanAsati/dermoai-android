package com.dermoai.core.domain.model

/**
 * Authenticated user identity used across the app.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val isAnonymous: Boolean = false,
    val photoUrl: String? = null,
    /**
     * Defaults to [UserRole.PATIENT] so every existing call site — and every
     * account created before roles existed — keeps working unchanged. A doctor
     * is the exception, never the assumption.
     */
    val role: UserRole = UserRole.PATIENT,
)

/**
 * Which product surface an account gets.
 *
 * Deliberately two values and no `ADMIN`: the role decides what the app *shows*,
 * not what a user is *allowed* to read. Anything privacy-bearing (another
 * person's scans) is gated on an explicit [PatientLink], because a role that
 * lives in a local database is trivially forgeable and must never be the only
 * thing standing between an account and someone else's medical photos.
 */
enum class UserRole {
    PATIENT,
    DOCTOR,
    ;

    companion object {
        /**
         * Parses the string form persisted in Room. Unknown or missing values fall
         * back to [PATIENT] rather than throwing: a corrupt row should downgrade a
         * user's surface, never crash them out of the app or silently promote them.
         */
        fun fromStorage(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: PATIENT
    }
}
