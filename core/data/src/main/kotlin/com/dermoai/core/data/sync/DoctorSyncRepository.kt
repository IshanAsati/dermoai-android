package com.dermoai.core.data.sync

import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.data.sync.AppwriteSchema.Fields
import com.dermoai.core.database.entity.AuditEntryEntity
import com.dermoai.core.database.entity.DoctorInviteEntity
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Databases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-device sync for doctor↔patient linking.
 *
 * ## What this is for
 * The doctor dashboard works entirely on Room. A doctor can issue an invite, a
 * patient can redeem it, consent can be granted and revoked, and triage renders
 * — all with the network switched off, on one device. What Room cannot do is
 * make a link the doctor created on *their* phone visible on the patient's.
 * That, and only that, is this class's job.
 *
 * ## The contract every method honours
 * **Nothing throws.** Every path returns [AppResult], and the overwhelming
 * majority of unhappy paths return `AppResult.Success` carrying a [PushOutcome]
 * or [PullOutcome] that says the server was not involved. A phone in a lift, a
 * build with no backend configured, a user who has not signed in to Appwrite —
 * these are ordinary operating conditions for this app, not errors, and they
 * must never surface as a red banner over a dashboard that is working fine from
 * local data.
 *
 * `AppResult.Error` is reserved for the genuinely surprising: the backend
 * answered and answered *wrongly* — a collection that does not exist, a schema
 * mismatch, a permission denial. Even those are non-fatal by construction: the
 * caller keeps serving Room. They are surfaced as errors only so a
 * misconfigured deployment is visible to somebody rather than silently
 * degrading forever.
 *
 * This mirrors `FirebaseAuthRepository`, which resolves its Firebase project,
 * detects the placeholder via `isPlaceholderProject()`, and branches every
 * method on a `localMode` flag rather than failing. [isLocalOnlyMode] is the
 * same flag by another name.
 *
 * ## Identity
 * Document permissions are written in terms of Appwrite account ids. This class
 * therefore reads the session's `$id` and **refuses to push a row whose owner id
 * does not match it** ([SyncSkipReason.IDENTITY_MISMATCH]) rather than writing a
 * document whose ACL names the wrong person.
 *
 * That check has a consequence worth stating plainly: the app currently
 * authenticates against Firebase, so `AuthUser.id` is a Firebase uid, not an
 * Appwrite `$id`. Until an Appwrite session exists for the same person — either
 * by moving auth to the Account API or by bridging Firebase → Appwrite with a
 * custom token — every push here will correctly and deliberately skip. Sync
 * turning itself off is the right behaviour for an identity mismatch; silently
 * writing documents keyed to an id the server has never heard of is not.
 *
 * ## What is not synced
 * Scan photographs, thumbnails, voice notes and the ~90 MB TFLite model never
 * leave the device. See [AppwriteSchema] for the reasoning. [ScanSummaryDto] is
 * the derived, image-free row a doctor needs for triage.
 *
 * ## Security caveats
 * [SyncPermissions] documents, honestly and at length, which parts of the
 * consent model Appwrite's document permissions actually enforce and which
 * parts are convention maintained by the client. Read it before relying on
 * revocation.
 */
@Singleton
class DoctorSyncRepository @Inject constructor(
    private val config: AppwriteConfig,
    private val clients: AppwriteClientProvider,
    private val dispatchers: DispatcherProvider,
) {

    /** True when there is no usable backend. Callers should stay on Room. */
    fun isLocalOnlyMode(): Boolean = config.isLocalOnlyMode

    /**
     * The Appwrite account id of the current session, or null if there is none.
     *
     * Exposed because callers building an ACL-sensitive flow (granting a doctor
     * access, say) need to know which identity the server will see, and finding
     * that out by watching a push skip is a poor way to learn it.
     */
    suspend fun currentSessionUserId(): AppResult<String?> = withContext(dispatchers.io) {
        if (config.isLocalOnlyMode) return@withContext AppResult.Success(null)
        attempt(degraded = { null }) { resolveSessionUserId() }
    }

    // ── doctor_profiles ──────────────────────────────────────────────────────

    suspend fun pushDoctorProfile(entity: DoctorProfileEntity): AppResult<PushOutcome> =
        push(entity.userId) { databases, _ ->
            val dto = entity.toDto()
            databases.upsert(
                collectionId = AppwriteSchema.DOCTOR_PROFILES,
                documentId = dto.id,
                data = dto.toMap(),
                permissions = SyncPermissions.doctorProfilePermissions(entity.userId),
            )
        }

    /**
     * The doctor profile for [userId], or null when the server has none.
     *
     * Readable by any signed-in user by design: a patient must be able to see
     * who they are about to grant access to, before the link that would
     * otherwise authorise the read exists. See [SyncPermissions].
     */
    suspend fun pullDoctorProfile(userId: String): AppResult<PullOutcome<DoctorProfileDto?>> =
        pull<DoctorProfileDto?>(requireOwner = null, empty = null) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.DOCTOR_PROFILES,
                queries = listOf(
                    Query.equal(Fields.DoctorProfiles.USER_ID, userId),
                    Query.limit(1),
                ),
            ).firstOrNull()?.let { DoctorProfileDto.fromMap(it.id, it.data) }
        }

    // ── patient_links ────────────────────────────────────────────────────────

    /**
     * @param doctorUserId the clinician's account id. Defaults to the row's
     *   owning account, which is correct on the doctor's own device and wrong on
     *   the patient's — hence the parameter. See [PatientLinkEntity.toDto].
     */
    suspend fun pushPatientLink(
        entity: PatientLinkEntity,
        doctorUserId: String = entity.userId,
    ): AppResult<PushOutcome> = push(entity.userId) { databases, _ ->
        val dto = entity.toDto(doctorUserId = doctorUserId)
        databases.upsert(
            collectionId = AppwriteSchema.PATIENT_LINKS,
            documentId = dto.id,
            data = dto.toMap(),
            permissions = SyncPermissions.patientLinkPermissions(
                doctorUserId = dto.doctorUserId,
                patientUserId = dto.patientUserId,
            ),
        )
    }

    /** Links a doctor holds, for the dashboard list. Keyed by `DoctorProfile.id`. */
    suspend fun pullPatientLinksForDoctor(
        doctorId: String,
    ): AppResult<PullOutcome<List<PatientLinkDto>>> =
        pull(requireOwner = null, empty = emptyList()) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.PATIENT_LINKS,
                queries = listOf(
                    Query.equal(Fields.PatientLinks.DOCTOR_ID, doctorId),
                    Query.limit(AppwriteSchema.MAX_PAGE_SIZE),
                ),
            ).map { PatientLinkDto.fromMap(it.id, it.data) }
        }

    /**
     * Links naming this patient — the "who has access to me" list.
     *
     * Note this returns what the *server's ACLs* let the caller see, which for a
     * patient is every link naming them. It is not proof that those doctors can
     * currently read anything; that depends on the scan-summary ACLs. The two
     * are kept in step by the patient's own device, not by the server.
     */
    suspend fun pullPatientLinksForPatient(
        patientUserId: String,
    ): AppResult<PullOutcome<List<PatientLinkDto>>> =
        pull(requireOwner = patientUserId, empty = emptyList()) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.PATIENT_LINKS,
                queries = listOf(
                    Query.equal(Fields.PatientLinks.PATIENT_USER_ID, patientUserId),
                    Query.limit(AppwriteSchema.MAX_PAGE_SIZE),
                ),
            ).map { PatientLinkDto.fromMap(it.id, it.data) }
        }

    // ── doctor_invites ───────────────────────────────────────────────────────

    suspend fun pushDoctorInvite(
        entity: DoctorInviteEntity,
        doctorUserId: String = entity.userId,
    ): AppResult<PushOutcome> = push(entity.userId) { databases, _ ->
        val dto = entity.toDto(doctorUserId = doctorUserId)
        databases.upsert(
            collectionId = AppwriteSchema.DOCTOR_INVITES,
            documentId = dto.id,
            data = dto.toMap(),
            permissions = SyncPermissions.doctorInvitePermissions(dto.doctorUserId),
        )
    }

    /**
     * Look up a typed invite code.
     *
     * **This is the weakest point in the design and it is not a security
     * boundary.** The collection has to be readable by any authenticated user
     * for a patient to resolve a code before any relationship exists, which
     * means it is enumerable: a signed-in attacker can list live codes rather
     * than guessing them, which defeats the code's length, expiry and use cap.
     * Document permissions cannot express "only the row whose `code` matches
     * what I typed". Doing this properly needs an Appwrite Function that takes
     * a code, checks it server-side and creates the link, with rate limiting.
     * See [SyncPermissions].
     *
     * The returned invite's `isUsable(now)` must still be checked by the caller
     * — this method resolves a code, it does not authorise a redemption.
     */
    suspend fun findInviteByCode(code: String): AppResult<PullOutcome<DoctorInviteDto?>> =
        pull<DoctorInviteDto?>(requireOwner = null, empty = null) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.DOCTOR_INVITES,
                queries = listOf(
                    // Uppercased on both write and read so the lookup is not
                    // accidentally case-sensitive for a code read aloud.
                    Query.equal(Fields.DoctorInvites.CODE, code.trim().uppercase()),
                    Query.limit(1),
                ),
            ).firstOrNull()?.let { DoctorInviteDto.fromMap(it.id, it.data) }
        }

    // ── scan_summaries ───────────────────────────────────────────────────────

    /**
     * Push one derived triage row. No image, no thumbnail, no file path.
     *
     * @param grantedDoctorUserIds doctors whose links this device believes are
     *   ACTIVE and consented. They are baked into the document ACL at write
     *   time, which is what actually grants the read — the link row does not.
     *   A summary written while offline therefore carries whatever consent this
     *   device last knew about. See [SyncPermissions].
     */
    suspend fun pushScanSummary(
        summary: ScanSummaryDto,
        grantedDoctorUserIds: Collection<String> = emptyList(),
    ): AppResult<PushOutcome> = push(summary.patientUserId) { databases, _ ->
        databases.upsert(
            collectionId = AppwriteSchema.SCAN_SUMMARIES,
            documentId = summary.id,
            data = summary.toMap(),
            permissions = SyncPermissions.scanSummaryPermissions(
                patientUserId = summary.patientUserId,
                grantedDoctorUserIds = grantedDoctorUserIds,
            ),
        )
    }

    /**
     * Triage rows for a patient, newest first.
     *
     * A doctor calling this gets exactly the documents whose ACL names them.
     * An empty list from a doctor therefore means "nothing shared with me",
     * which is *not* distinguishable from "this patient has no scans" — and
     * neither is distinguishable from "we never asked" unless the caller checks
     * [PullOutcome.fromServer] first.
     */
    suspend fun pullScanSummaries(
        patientUserId: String,
        limit: Int = AppwriteSchema.MAX_PAGE_SIZE,
    ): AppResult<PullOutcome<List<ScanSummaryDto>>> =
        pull(requireOwner = null, empty = emptyList()) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.SCAN_SUMMARIES,
                queries = listOf(
                    Query.equal(Fields.ScanSummaries.PATIENT_USER_ID, patientUserId),
                    Query.orderDesc(Fields.ScanSummaries.CAPTURED_AT),
                    Query.limit(limit.coerceIn(1, AppwriteSchema.MAX_PAGE_SIZE)),
                ),
            ).map { ScanSummaryDto.fromMap(it.id, it.data) }
        }

    /**
     * Add [doctorUserIds] to the read ACL of every summary owned by the session
     * user, and return how many documents were rewritten.
     *
     * This is the grant. It is a batch of client-side ACL writes, not a
     * server-side policy — read the caveats in [SyncPermissions] before
     * describing it to a user as anything stronger. In particular it can only
     * touch documents the server currently holds, so a summary pushed later
     * picks up its consent from [pushScanSummary]'s argument instead.
     */
    suspend fun grantDoctorScanAccess(
        doctorUserIds: Collection<String>,
    ): AppResult<PushOutcome> = rewriteScanAcls { current -> current + doctorUserIds }

    /**
     * Remove [doctorUserIds] from the read ACL of every summary owned by the
     * session user.
     *
     * **Not atomic and not instantaneous.** If this call is interrupted — no
     * signal, process death, a document held by a device that has not synced —
     * the doctor keeps server-side read access to whatever was not rewritten.
     * The `patient_links` row flipping to REVOKED does not stop the server
     * serving those documents. UI copy should say access is being withdrawn,
     * and confirm only on a successful [PushOutcome.pushed].
     */
    suspend fun revokeDoctorScanAccess(
        doctorUserIds: Collection<String>,
    ): AppResult<PushOutcome> = rewriteScanAcls { current -> current - doctorUserIds.toSet() }

    // ── audit_entries ────────────────────────────────────────────────────────

    /**
     * Append one access record.
     *
     * The document is written with no update or delete permission for anyone,
     * so Appwrite makes it immutable once stored — that part is genuinely
     * enforced. What is not enforced is that this method gets called at all:
     * the actor's own client decides whether to log its access. A client-side
     * audit log is evidence, not a guarantee. See [SyncPermissions].
     */
    suspend fun pushAuditEntry(entity: AuditEntryEntity): AppResult<PushOutcome> =
        push(entity.actorUserId) { databases, _ ->
            val dto = entity.toDto()
            databases.upsert(
                collectionId = AppwriteSchema.AUDIT_ENTRIES,
                documentId = dto.id,
                data = dto.toMap(),
                permissions = SyncPermissions.auditEntryPermissions(
                    actorUserId = dto.actorUserId,
                    subjectUserId = dto.subjectUserId,
                ),
            )
        }

    /** The patient's "who looked at my scans" list, newest first. */
    suspend fun pullAuditEntriesForSubject(
        subjectUserId: String,
    ): AppResult<PullOutcome<List<AuditEntryDto>>> =
        pull(requireOwner = subjectUserId, empty = emptyList()) { databases, _ ->
            databases.list(
                collectionId = AppwriteSchema.AUDIT_ENTRIES,
                queries = listOf(
                    Query.equal(Fields.AuditEntries.SUBJECT_USER_ID, subjectUserId),
                    Query.orderDesc(Fields.AuditEntries.AT),
                    Query.limit(AppwriteSchema.MAX_PAGE_SIZE),
                ),
            ).map { AuditEntryDto.fromMap(it.id, it.data) }
        }

    // ── plumbing ─────────────────────────────────────────────────────────────

    /**
     * Rewrites the read ACL of every scan summary owned by the session user.
     *
     * Reads the *existing* ACL off each document rather than reconstructing it
     * from local link state, so a concurrent grant made on another device is
     * not clobbered by a revoke made on this one.
     */
    private suspend fun rewriteScanAcls(
        transform: (Set<String>) -> Set<String>,
    ): AppResult<PushOutcome> = withContext(dispatchers.io) {
        when (val gate = openGate(requiredOwnerUserId = null)) {
            is Gate.Blocked -> AppResult.Success(PushOutcome.skipped(gate.reason))
            is Gate.Failed -> gate.error
            is Gate.Ready -> attempt(degraded = { PushOutcome.skipped(it) }) {
                val owner = gate.sessionUserId
                val documents = gate.databases.list(
                    collectionId = AppwriteSchema.SCAN_SUMMARIES,
                    queries = listOf(
                        Query.equal(Fields.ScanSummaries.PATIENT_USER_ID, owner),
                        Query.limit(AppwriteSchema.MAX_PAGE_SIZE),
                    ),
                    withPermissions = true,
                )
                for (document in documents) {
                    val dto = ScanSummaryDto.fromMap(document.id, document.data)
                    val granted = transform(readerUserIdsIn(document.permissions) - owner)
                    gate.databases.updateDocument(
                        config.databaseId,
                        AppwriteSchema.SCAN_SUMMARIES,
                        document.id,
                        // Rebuilt from the DTO rather than echoed back: the SDK
                        // hands `data` back including Appwrite's own `$`-prefixed
                        // system fields, which the server rejects as attributes.
                        dto.toMap().filterValues { it != null },
                        SyncPermissions.scanSummaryPermissions(owner, granted),
                    )
                }
                PushOutcome.pushed()
            }
        }
    }

    /**
     * Pulls the user ids out of `read("user:<id>")` entries.
     *
     * String parsing because the SDK hands permissions back as the raw strings
     * it sent; there is no structured type to inspect.
     */
    private fun readerUserIdsIn(permissions: List<String>): Set<String> =
        permissions.mapNotNullTo(mutableSetOf()) { raw ->
            READ_USER_PERMISSION.matchEntire(raw.trim())?.groupValues?.getOrNull(1)
        }

    private suspend fun push(
        ownerUserId: String,
        block: suspend (Databases, String) -> Unit,
    ): AppResult<PushOutcome> = withContext(dispatchers.io) {
        when (val gate = openGate(requiredOwnerUserId = ownerUserId)) {
            is Gate.Blocked -> AppResult.Success(PushOutcome.skipped(gate.reason))
            is Gate.Failed -> gate.error
            is Gate.Ready -> attempt(degraded = { PushOutcome.skipped(it) }) {
                block(gate.databases, gate.sessionUserId)
                PushOutcome.pushed()
            }
        }
    }

    private suspend fun <T> pull(
        requireOwner: String?,
        empty: T,
        block: suspend (Databases, String) -> T,
    ): AppResult<PullOutcome<T>> = withContext(dispatchers.io) {
        when (val gate = openGate(requiredOwnerUserId = requireOwner)) {
            is Gate.Blocked -> AppResult.Success(PullOutcome.skipped(gate.reason, empty))
            is Gate.Failed -> gate.error
            is Gate.Ready -> attempt(degraded = { PullOutcome.skipped(it, empty) }) {
                PullOutcome.fromServer(block(gate.databases, gate.sessionUserId))
            }
        }
    }

    /**
     * Resolves "can we talk to the server, as whom, and is that the right whom".
     *
     * @param requiredOwnerUserId when non-null, the session must belong to this
     *   account or the call is refused with [SyncSkipReason.IDENTITY_MISMATCH].
     *   Null for reads that are legitimately about somebody else's data — a
     *   patient reading a doctor's profile, a doctor reading a shared summary —
     *   where the server's own ACL is the authority, not us.
     */
    private suspend fun openGate(requiredOwnerUserId: String?): Gate {
        if (config.isLocalOnlyMode) return Gate.Blocked(SyncSkipReason.NOT_CONFIGURED)
        val databases = clients.databases ?: return Gate.Blocked(SyncSkipReason.NOT_CONFIGURED)

        val sessionUserId = try {
            resolveSessionUserId()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppwriteException) {
            return when (val reason = skipReasonFor(e)) {
                null -> Gate.Failed(AppResult.Error(e, BACKEND_UNAVAILABLE_MESSAGE))
                else -> Gate.Blocked(reason)
            }
        } catch (_: IOException) {
            return Gate.Blocked(SyncSkipReason.OFFLINE)
        } catch (t: Throwable) {
            return Gate.Failed(AppResult.Error(t, BACKEND_UNAVAILABLE_MESSAGE))
        }

        if (sessionUserId.isNullOrBlank()) return Gate.Blocked(SyncSkipReason.NO_SESSION)
        if (requiredOwnerUserId != null && requiredOwnerUserId != sessionUserId) {
            return Gate.Blocked(SyncSkipReason.IDENTITY_MISMATCH)
        }
        return Gate.Ready(databases, sessionUserId)
    }

    /** `null` when nobody is signed in to Appwrite. */
    private suspend fun resolveSessionUserId(): String? =
        try {
            clients.account?.get()?.id
        } catch (e: AppwriteException) {
            // 401 is the ordinary "no session" answer, not a failure.
            if (e.code == HTTP_UNAUTHORIZED) null else throw e
        }

    /**
     * Makes sure there is *some* Appwrite session, creating an anonymous one if
     * not, and returns its user id (null when local-only or unreachable).
     *
     * Without this every write returns 401: the client authenticates as whatever
     * session the Account API established, and nothing in the app was ever
     * establishing one. Sign-in is local (the Firebase config is a placeholder),
     * so there are no credentials to forward — an anonymous session is what is
     * actually available.
     *
     * The trade-off, stated plainly because it affects what the backend can
     * enforce: an anonymous identity is per-install and not tied to the local
     * account. Reinstalling gets a new Appwrite user, and a doctor signing in on
     * a second device is, to Appwrite, a different person. Document ACLs written
     * for one anonymous id therefore do not follow the human. Making identity
     * durable means creating real Appwrite accounts during sign-up, which is the
     * right next step and is not done here.
     */
    suspend fun ensureSession(): AppResult<String?> = withContext(dispatchers.io) {
        if (config.isLocalOnlyMode) return@withContext AppResult.Success(null)
        attempt(degraded = { null }) {
            resolveSessionUserId() ?: run {
                clients.account?.createAnonymousSession()
                resolveSessionUserId()
            }
        }
    }

    /**
     * Runs [block], converting every expected failure into a degraded success.
     *
     * [CancellationException] is re-thrown deliberately: swallowing it would
     * make a cancelled screen's coroutine look like a successful no-op and keep
     * the enclosing scope alive.
     */
    private suspend fun <T> attempt(
        degraded: (SyncSkipReason) -> T,
        block: suspend () -> T,
    ): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppwriteException) {
        when (val reason = skipReasonFor(e)) {
            null -> AppResult.Error(e, e.message?.takeIf { it.isNotBlank() } ?: BACKEND_UNAVAILABLE_MESSAGE)
            else -> AppResult.Success(degraded(reason))
        }
    } catch (_: IOException) {
        AppResult.Success(degraded(SyncSkipReason.OFFLINE))
    } catch (t: Throwable) {
        AppResult.Error(t, BACKEND_UNAVAILABLE_MESSAGE)
    }

    /**
     * Which failures are "the network happened" rather than "the backend is
     * wrong". Null means the latter, and only the latter reaches the UI as an
     * error.
     *
     * The SDK reports transport failures as an [AppwriteException] with a null
     * code — there is no HTTP response to take a status from — so a null code is
     * read as offline rather than as an unknown server fault.
     */
    private fun skipReasonFor(e: AppwriteException): SyncSkipReason? = when (e.code) {
        null, 0 -> SyncSkipReason.OFFLINE
        HTTP_UNAUTHORIZED -> SyncSkipReason.NO_SESSION
        HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT -> SyncSkipReason.OFFLINE
        else -> null
    }

    /**
     * `upsertDocument` with a fallback for older self-hosted servers.
     *
     * Upsert reached the Appwrite API well after this SDK's document endpoints
     * did, so a server that predates it answers 404/405 for the route. Falling
     * back to update-then-create keeps this layer working against an older
     * deployment instead of failing in a way that looks like a missing
     * collection.
     *
     * Null values are stripped: Gson omits them anyway, and being explicit
     * makes the consequence visible — pushing a null optional leaves the
     * server's existing value alone rather than clearing it. Nothing in this
     * schema relies on clearing a field (consent withdrawal sets a status
     * string, it does not null a timestamp).
     */
    private suspend fun Databases.upsert(
        collectionId: String,
        documentId: String,
        data: Map<String, Any?>,
        permissions: List<String>,
    ) {
        val payload = data.filterValues { it != null }
        try {
            upsertDocument(config.databaseId, collectionId, documentId, payload, permissions)
        } catch (e: AppwriteException) {
            if (e.code != HTTP_NOT_FOUND && e.code != HTTP_METHOD_NOT_ALLOWED) throw e
            try {
                updateDocument(config.databaseId, collectionId, documentId, payload, permissions)
            } catch (_: AppwriteException) {
                createDocument(config.databaseId, collectionId, documentId, payload, permissions)
            }
        }
    }

    /**
     * Lists documents as `(id, data)` — or `(id, data, permissions)` when
     * [withPermissions] is set — so callers never touch SDK model types.
     *
     * Returned as triples of plain values rather than `Document<…>` so the DTO
     * layer and its tests stay free of the Appwrite SDK.
     */
    private suspend fun Databases.list(
        collectionId: String,
        queries: List<String>,
        withPermissions: Boolean = false,
    ): List<RawDocument> =
        listDocuments(config.databaseId, collectionId, queries).documents.map { document ->
            RawDocument(
                id = document.id,
                data = document.data,
                permissions = if (withPermissions) document.permissions else emptyList(),
            )
        }

    private sealed interface Gate {
        data class Ready(val databases: Databases, val sessionUserId: String) : Gate
        data class Blocked(val reason: SyncSkipReason) : Gate
        data class Failed(val error: AppResult.Error) : Gate
    }

    /** `(documentId, attributes, permissions)` — the shape [list] hands back. */
    private data class RawDocument(
        val id: String,
        val data: Map<String, Any>,
        val permissions: List<String>,
    )

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_UNAVAILABLE = 503
        const val HTTP_GATEWAY_TIMEOUT = 504

        const val BACKEND_UNAVAILABLE_MESSAGE =
            "Could not reach the backend. Showing data from this device."

        /** Matches `read("user:abc123")`, capturing the account id. */
        val READ_USER_PERMISSION = Regex("""read\("user:([^"/]+)"\)""")
    }
}
