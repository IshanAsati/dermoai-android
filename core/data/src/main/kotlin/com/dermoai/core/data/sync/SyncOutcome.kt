package com.dermoai.core.data.sync

/**
 * The result vocabulary of the sync layer.
 *
 * The doctor dashboard is a Room-backed feature that happens to have a
 * backend, not a backend-backed feature that happens to cache. That inversion
 * is what these types encode: "the server was not reached" is an ordinary,
 * expected outcome — a phone in a lift, an unprovisioned build, a user who has
 * not signed in to Appwrite — and it must not surface as an error banner over
 * a dashboard that is working perfectly from local data.
 *
 * So the repository returns `AppResult.Success` carrying a [PushOutcome] or
 * [PullOutcome] that says *whether* the server was involved, and reserves
 * `AppResult.Error` for the genuinely surprising: the backend answered, and
 * answered wrongly (missing collection, permission denied, malformed schema).
 * Even then nothing is thrown — the caller keeps serving Room.
 */
enum class SyncSkipReason {
    /** No endpoint/project/database configured. The default for a fresh clone. */
    NOT_CONFIGURED,

    /** Configured, but nobody is signed in to Appwrite. Nothing to authorise as. */
    NO_SESSION,

    /**
     * The Appwrite session belongs to a different account than the row's owner.
     *
     * Fails closed rather than pushing: document permissions are written in
     * terms of the *session* user, so pushing someone else's row would either
     * be rejected by the server or — worse — succeed with an ACL naming the
     * wrong person. See [DoctorSyncRepository.resolveSessionUserId].
     */
    IDENTITY_MISMATCH,

    /** Network unreachable, DNS failure, timeout. The ordinary case. */
    OFFLINE,
}

/**
 * Outcome of a push.
 *
 * @property pushed true only if the document actually reached the server.
 * @property skipped why it did not, or null when [pushed] is true.
 */
data class PushOutcome(
    val pushed: Boolean,
    val skipped: SyncSkipReason? = null,
) {
    companion object {
        fun pushed(): PushOutcome = PushOutcome(pushed = true)
        fun skipped(reason: SyncSkipReason): PushOutcome = PushOutcome(false, reason)
    }
}

/**
 * Outcome of a pull.
 *
 * **Read [fromServer] before reading [value].** When it is false the pull
 * carries *no information*: an empty list means "we did not ask", never
 * "upstream is empty". A caller that treats the two as the same will happily
 * delete a doctor's entire patient list the first time the phone loses signal.
 * That distinction is the whole reason this wrapper exists instead of returning
 * a bare `List`.
 */
data class PullOutcome<T>(
    val value: T,
    val fromServer: Boolean,
    val skipped: SyncSkipReason? = null,
) {
    companion object {
        fun <T> fromServer(value: T): PullOutcome<T> = PullOutcome(value, fromServer = true)

        fun <T> skipped(reason: SyncSkipReason, empty: T): PullOutcome<T> =
            PullOutcome(empty, fromServer = false, skipped = reason)
    }
}
