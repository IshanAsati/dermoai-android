package com.dermoai.core.data.sync

import io.appwrite.Permission
import io.appwrite.Role

/**
 * Document-level ACLs for the five synced collections — and an honest account
 * of what they do and do not enforce.
 *
 * Read this before trusting anything in the doctor dashboard's cross-device
 * story. It is the security design, not a helper file.
 *
 * ---
 * ## The model
 *
 * Appwrite authorises a request against a **static access-control list stored
 * on each document**: a list of roles (`user:<id>`, `users`, `team:<id>`, `any`)
 * crossed with actions (read/create/update/delete). Collections are created with
 * `documentSecurity = true` so those per-document lists are what count.
 *
 * That is genuinely enforced server-side. A client cannot read a document whose
 * ACL does not name it, no matter what the client-side code says. Good.
 *
 * ## The thing it cannot express
 *
 * The requirement is:
 *
 * > a doctor can read a patient's `scan_summaries` **only while an ACTIVE,
 * > consented `patient_links` document exists between them**.
 *
 * **Appwrite document permissions cannot express that, and this file does not
 * implement it.** An ACL is a list of ids; it cannot contain a predicate, cannot
 * dereference another document, and Appwrite has no row-level-security policy
 * language and no notion of "read allowed if a row exists in another
 * collection". There is no arrangement of `Permission.read(Role.…)` that makes
 * the server consult `patient_links` before answering.
 *
 * ## What is implemented instead, and exactly how strong it is
 *
 * The patient's own device **rewrites the ACL** on its `scan_summaries`
 * documents: granting consent adds `Permission.read(Role.user(doctorUserId))`
 * to each one ([scanSummaryPermissions] with a non-empty grant list); revoking
 * rewrites them without it. See
 * [DoctorSyncRepository.grantDoctorScanAccess] / `revokeDoctorScanAccess`.
 *
 * That is a real, server-enforced grant. Its honest limitations:
 *
 *  1. **Revocation is not atomic and not server-side.** It is a batch of client
 *     writes. If the patient revokes consent while offline — or the app is
 *     killed mid-batch, or a document was added by another device that has not
 *     synced — the doctor retains server-side read access to the leftover
 *     documents until that device finishes the job. The `patient_links` row
 *     saying `REVOKED` does **not** stop the server serving those documents. A
 *     UI must not tell the patient that access "has been withdrawn" the instant
 *     they tap; it has been *requested*, and completes when the device syncs.
 *  2. **The link document is advisory, not enforcing.** Its `status` and
 *     `consentGrantedAt` are what the *app* checks. The server checks only the
 *     ACL. The two agree because the same device writes both — which is a
 *     convention, not a guarantee.
 *  3. **Summaries written while offline carry the ACL the device knew about.**
 *     A scan captured after consent was revoked, on a device that has not yet
 *     learned of the revocation, will be pushed granting the old doctor read.
 *  4. **A compromised or modified client can write whatever ACL it likes on its
 *     own documents.** That is inherent: the owner of a document may share it.
 *     It limits blast radius to that user's own data — a patient can leak their
 *     own summaries, not anyone else's — but it is not a guarantee that only
 *     consented doctors ever see a summary.
 *
 * ## The two ways to actually enforce it (neither implemented here)
 *
 *  - **Appwrite Teams.** Give each patient a team; consent = adding the doctor
 *    as a member; summaries carry `Permission.read(Role.team(patientTeamId))`.
 *    Revocation becomes a single server-side membership deletion that is
 *    immediate, atomic, and independent of how many documents exist. This is
 *    the better design and the recommended hardening; it is not here because it
 *    changes the account-provisioning flow, which lives in `feature/auth`.
 *  - **An Appwrite Function** holding an API key, which mediates every doctor
 *    read: it checks `patient_links` server-side and returns summaries only if
 *    the link is ACTIVE and consented. This is the only way to get a truly
 *    relationship-conditional read, and the only way to make audit logging
 *    non-optional (see below).
 *
 * ## Per-collection notes
 *
 * **`doctor_invites` is the weakest link and should be treated as such.** A
 * patient must be able to look up a code before any relationship exists, so the
 * collection is readable by any authenticated user, which means the entire
 * invite collection is **enumerable** — a signed-in attacker can list live codes
 * and redeem them, defeating the 8-character code's expiry and use-cap
 * entirely. Document permissions cannot express "read only the row whose `code`
 * equals the string I typed". Redemption belongs in an Appwrite Function that
 * takes a code and returns a link, with rate limiting. Until then, treat
 * cross-device invite redemption as **convenience, not a security boundary**,
 * and prefer the local (same-device / read-aloud) flow for anything sensitive.
 *
 * **`audit_entries` really is append-only, server-side.** Its ACL grants
 * read to both parties and grants *no* update or delete to anyone, which
 * Appwrite does enforce: once written, neither the doctor nor the patient can
 * alter or remove a row. What it cannot enforce is that a row gets written at
 * all — the doctor's client decides whether to log its own access. A doctor
 * running a modified client can read summaries silently. Making logging
 * mandatory requires the Function-mediated read above.
 *
 * **`doctor_profiles` is readable by any authenticated user.** A patient has to
 * see who they are about to grant access to *before* the link exists, so
 * gating profile reads on the link is circular. These are professional
 * registration details a clinician publishes; that is the justification, and it
 * should stay true — no personal contact details in this collection.
 */
object SyncPermissions {

    /**
     * Owner may do anything; every authenticated user may read.
     *
     * The broad read is deliberate and scoped to credentials only — see the
     * class note on `doctor_profiles`.
     */
    fun doctorProfilePermissions(ownerUserId: String): List<String> = listOf(
        Permission.read(Role.users()),
        Permission.update(Role.user(ownerUserId)),
        Permission.delete(Role.user(ownerUserId)),
    )

    /**
     * Both parties read and update; nobody else sees the link at all.
     *
     * Update is granted to both because both sides legitimately change it: the
     * doctor invites and revokes, the patient consents and withdraws. Delete is
     * granted to neither — [com.dermoai.core.domain.model.LinkStatus.REVOKED]
     * is a terminal *state*, kept as a row precisely so the audit trail can say
     * a doctor used to have access and when that stopped. A deletable link is a
     * deniable one.
     */
    fun patientLinkPermissions(doctorUserId: String, patientUserId: String): List<String> =
        buildList {
            add(Permission.read(Role.user(doctorUserId)))
            add(Permission.update(Role.user(doctorUserId)))
            if (patientUserId != doctorUserId) {
                add(Permission.read(Role.user(patientUserId)))
                add(Permission.update(Role.user(patientUserId)))
            }
        }

    /**
     * Issuing doctor may update/revoke; any authenticated user may read.
     *
     * The broad read is what makes cross-device redemption work and is also the
     * enumeration weakness described in the class note. It is written down here
     * rather than hidden because a reader of this function deserves to know they
     * are looking at the soft spot.
     */
    fun doctorInvitePermissions(doctorUserId: String): List<String> = listOf(
        Permission.read(Role.users()),
        Permission.update(Role.user(doctorUserId)),
        Permission.delete(Role.user(doctorUserId)),
    )

    /**
     * The patient owns their summaries; consented doctors are added by id.
     *
     * @param grantedDoctorUserIds accounts of doctors whose links are currently
     *   ACTIVE **and** consented, as judged by the calling device. This list is
     *   the entire enforcement mechanism, and it is a snapshot — re-read the
     *   caveats at the top of this file before assuming it tracks revocation.
     *
     * Doctors get read only. A doctor must never be able to edit or delete a
     * patient's clinical record, and no product requirement here needs it.
     */
    fun scanSummaryPermissions(
        patientUserId: String,
        grantedDoctorUserIds: Collection<String> = emptyList(),
    ): List<String> = buildList {
        add(Permission.read(Role.user(patientUserId)))
        add(Permission.update(Role.user(patientUserId)))
        add(Permission.delete(Role.user(patientUserId)))
        grantedDoctorUserIds.asSequence()
            .filter { it.isNotBlank() && it != patientUserId }
            .distinct()
            .forEach { add(Permission.read(Role.user(it))) }
    }

    /**
     * Read for both parties, write for nobody.
     *
     * The omission of update and delete is the point: Appwrite will refuse a
     * modification from either side, so a written entry is immutable. A log the
     * observed party's counterparty can edit is not a log.
     */
    fun auditEntryPermissions(actorUserId: String, subjectUserId: String): List<String> =
        buildList {
            add(Permission.read(Role.user(actorUserId)))
            if (subjectUserId != actorUserId) {
                add(Permission.read(Role.user(subjectUserId)))
            }
        }
}
