package com.dermoai.core.data.sync

import com.dermoai.core.data.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the Appwrite backend lives — and, more importantly, whether there *is*
 * one.
 *
 * The doctor dashboard is designed to work with no backend at all: linking,
 * invites and triage all run off Room. Appwrite only makes those links survive
 * a change of device. So "not configured" is a supported, first-class state,
 * not a misconfiguration to shout about, and every caller must be able to ask
 * that question cheaply before doing anything network-shaped.
 *
 * This deliberately mirrors [com.dermoai.core.data.auth.FirebaseAuthRepository]:
 * that class resolves its Firebase project, notices it is the placeholder
 * (`isPlaceholderProject()`), and flips a `localMode` flag that every method
 * branches on rather than failing. [isLocalOnlyMode] is the same idea with the
 * same intent — a missing backend degrades the feature set, never the session.
 *
 * ## Why these three values are allowed to be in the binary
 * Endpoint, project id and database id are public in every Appwrite client
 * application. Appwrite's model is that the client knows them and that
 * authorisation is enforced server-side from the caller's *session* plus
 * per-document permissions (see [SyncPermissions]). An Appwrite **API key** is
 * a different animal entirely and must never reach this class, BuildConfig, or
 * any committed file — an APK is readable with `unzip` + `strings`, so an
 * embedded key is an embedded root credential. Provisioning that needs a key is
 * `tools/appwrite/setup_collections.py`, run from a developer machine with
 * `APPWRITE_API_KEY` in the environment.
 */
@Singleton
class AppwriteConfig @Inject constructor() {

    /** Appwrite REST base, e.g. `https://cloud.appwrite.io/v1`. Blank when unset. */
    val endpoint: String = normalise(BuildConfig.APPWRITE_ENDPOINT)

    /** Appwrite project id. Blank when unset. */
    val projectId: String = normalise(BuildConfig.APPWRITE_PROJECT_ID)

    /** Appwrite database id holding the collections in [AppwriteSchema]. Blank when unset. */
    val databaseId: String = normalise(BuildConfig.APPWRITE_DATABASE_ID)

    /**
     * True only when all three values are present and the endpoint at least
     * looks like an HTTP(S) base URL.
     *
     * Fails closed on purpose: a half-filled config produces a client that
     * throws on first use somewhere deep in a coroutine, which is a far worse
     * failure than never building one.
     */
    val isConfigured: Boolean
        get() = isConfigured(endpoint, projectId, databaseId)

    /**
     * The flag callers should branch on, named after the concept rather than
     * the negation so read sites say what they mean.
     */
    val isLocalOnlyMode: Boolean
        get() = !isConfigured

    companion object {
        /**
         * Values that are syntactically present but semantically empty.
         *
         * Templates and onboarding docs get copy-pasted verbatim more often than
         * anyone admits, and a project id of `YOUR_PROJECT_ID` would otherwise
         * pass a naive `isNotBlank()` check and produce 404s at runtime instead
         * of a clean fallback to local-only mode. Same instinct as
         * `FirebaseAuthRepository.isPlaceholderProject()`.
         */
        internal val PLACEHOLDER_VALUES: Set<String> = setOf(
            "todo",
            "tbd",
            "changeme",
            "change_me",
            "placeholder",
            "your_endpoint",
            "your_project_id",
            "your_database_id",
            "xxx",
        )

        /**
         * Pure form of [isConfigured], so the rule can be tested without an
         * Android BuildConfig and without constructing the whole object.
         */
        fun isConfigured(endpoint: String, projectId: String, databaseId: String): Boolean {
            if (!isUsable(endpoint) || !isUsable(projectId) || !isUsable(databaseId)) return false
            // A bare host or a stray shell quote is a misconfiguration we can
            // detect here for free, rather than at the first request.
            return endpoint.startsWith("http://", ignoreCase = true) ||
                endpoint.startsWith("https://", ignoreCase = true)
        }

        internal fun normalise(raw: String?): String = raw?.trim().orEmpty()

        private fun isUsable(value: String): Boolean =
            value.isNotBlank() && value.lowercase() !in PLACEHOLDER_VALUES
    }
}
