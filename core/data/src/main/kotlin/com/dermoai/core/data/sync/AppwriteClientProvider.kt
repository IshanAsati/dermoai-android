package com.dermoai.core.data.sync

import android.content.Context
import com.dermoai.core.data.sync.AppwriteClientProvider.Companion.TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single [Client] instance, or `null` when there is no backend.
 *
 * Three things make this worth its own class rather than a `by lazy` on the
 * repository:
 *
 *  1. **Null is the normal answer.** With a blank [AppwriteConfig] there is no
 *     client to build, and callers must get a `null` they can branch on instead
 *     of an object that throws on first use. Same shape as
 *     `FirebaseAuthRepository.resolveFirebaseAuth()`, which returns null when
 *     no `FirebaseApp` was initialised, and lets `localMode` take over.
 *  2. **The SDK client is stateful.** It carries the cookie jar holding the
 *     user's Appwrite session. Building a second one silently signs the user
 *     out of the first, so exactly one may exist per process — hence
 *     `@Singleton` plus double-checked locking rather than a lazy per injection
 *     site.
 *  3. **Construction can fail.** `setEndpoint` throws on a malformed URL. A
 *     misconfigured `local.properties` must degrade this feature to local-only,
 *     not crash the app at Hilt graph construction time, so construction is
 *     wrapped and failure is cached as "no client".
 *
 * No API key is ever set on this client. It authenticates as whatever user
 * session the Account API established; if there is no session, requests fail
 * with 401 and the sync layer reports [SyncSkipReason.NO_SESSION]. See
 * [SyncPermissions] for what the server does and does not enforce from there.
 */
@Singleton
class AppwriteClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AppwriteConfig,
) {

    private val lock = Any()

    @Volatile
    private var cachedClient: Client? = null

    @Volatile
    private var attempted: Boolean = false

    /** The shared client, or null when unconfigured or unbuildable. */
    val client: Client?
        get() {
            if (config.isLocalOnlyMode) return null
            cachedClient?.let { return it }
            synchronized(lock) {
                cachedClient?.let { return it }
                // One attempt only. A malformed endpoint will not become
                // well-formed on the next call, and retrying per request would
                // turn a config typo into a per-scroll exception storm.
                if (attempted) return null
                attempted = true
                cachedClient = buildClient()
                return cachedClient
            }
        }

    /** Databases service, or null when there is no client. */
    val databases: Databases?
        get() = client?.let(::Databases)

    /**
     * Account service, or null when there is no client.
     *
     * This is the only thing that establishes identity for the sync layer. The
     * repository reads `account.get().id` and refuses to write documents whose
     * permissions would name a different user — see
     * [DoctorSyncRepository.resolveSessionUserId].
     */
    val account: Account?
        get() = client?.let(::Account)

    private fun buildClient(): Client? = try {
        Client(context)
            .setEndpoint(config.endpoint)
            .setProject(config.projectId)
    } catch (t: Throwable) {
        // Swallowed on purpose: an unreachable or malformed backend must leave
        // the app in local-only mode, which is a fully working product.
        android.util.Log.w(TAG, "Appwrite client unavailable; staying local-only", t)
        null
    }

    companion object {
        internal const val TAG = "AppwriteClient"
    }
}
