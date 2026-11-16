import { Permission, Query, Role } from "node-appwrite";
import { attributeLinks, isRecognisedLink } from "@/lib/domain/invite-attribution";
import { generateCode, type RandomInt } from "@/lib/domain/invite-codes";
import { type Db, DbError, MAX_PAGE_SIZE } from "./db";
import {
  auditFromDoc,
  type AuditAction,
  type AuditEntry,
  Collections,
  type DoctorProfile,
  type Invite,
  inviteFromDoc,
  linkFromDoc,
  type PatientLink,
  type ProfileInput,
  profileFromDoc,
  profileFields,
  profileToData,
  type ScanSummary,
  summaryFromDoc,
  authorOf,
} from "./schema";

/**
 * Data access rules. Two kinds of `Db` are used and the difference matters:
 *
 * - the **session** Db acts as the doctor. Appwrite's document permissions
 *   decide what it can touch, so it is used for the doctor's own profile and
 *   invites, which the doctor owns.
 * - the **admin** Db acts as the server and bypasses permissions. It is used
 *   only after {@link authoriseLink} has passed, because Appwrite cannot
 *   express "a doctor may read a patient's summaries while a consented link
 *   exists", and a client cannot name another user in a document ACL.
 */

const ONE_ID_SAFE = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,35}$/;

const ownerPermissions = (userId: string) => [
  // Profiles and invites are readable by any signed-in user: a patient has to
  // resolve a code and see who is asking before any relationship exists.
  Permission.read(Role.users()),
  Permission.update(Role.user(userId)),
  Permission.delete(Role.user(userId)),
];

// ── profile ────────────────────────────────────────────────────────────────

export async function getProfileByUserId(db: Db, userId: string): Promise<DoctorProfile | null> {
  const docs = await db.list(Collections.doctorProfiles, [Query.equal("userId", userId), Query.limit(1)]);
  return docs[0] ? profileFromDoc(docs[0]) : null;
}

export async function createProfile(db: Db, userId: string, input: ProfileInput, newId: () => string): Promise<DoctorProfile> {
  const id = newId();
  const doc = await db.create(Collections.doctorProfiles, id, profileToData(userId, input), ownerPermissions(userId));
  return profileFromDoc(doc);
}

export async function updateProfile(db: Db, profile: DoctorProfile, input: ProfileInput): Promise<DoctorProfile> {
  const doc = await db.update(Collections.doctorProfiles, profile.id, profileFields(input));
  return profileFromDoc(doc);
}

// ── invites ────────────────────────────────────────────────────────────────

export async function listInvites(db: Db, doctorUserId: string): Promise<Invite[]> {
  const docs = await db.list(Collections.doctorInvites, [
    Query.equal("doctorUserId", doctorUserId),
    Query.limit(MAX_PAGE_SIZE),
  ]);
  return docs.map(inviteFromDoc).sort((a, b) => b.createdAt - a.createdAt);
}

export type NewInviteOptions = {
  ttlMs: number;
  maxUses: number;
  now: number;
  randomInt: RandomInt;
  newId: () => string;
};

const MAX_CODE_ATTEMPTS = 6;

/**
 * Retries on a unique-index collision rather than trusting the generator: the
 * `code` index is unique, so a repeat surfaces as a 409 on insert.
 */
export async function createInvite(db: Db, profile: DoctorProfile, o: NewInviteOptions): Promise<Invite> {
  for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
    const code = generateCode(o.randomInt);
    try {
      const doc = await db.create(
        Collections.doctorInvites,
        o.newId(),
        {
          doctorId: profile.id,
          doctorUserId: profile.userId,
          code,
          createdAt: o.now,
          expiresAt: o.now + o.ttlMs,
          maxUses: o.maxUses,
          usedCount: 0,
          revoked: false,
        },
        ownerPermissions(profile.userId),
      );
      return inviteFromDoc(doc);
    } catch (e) {
      if (e instanceof DbError && e.isConflict) continue;
      throw e;
    }
  }
  throw new Error("Could not generate a unique invite code. Please try again.");
}

export async function revokeInvite(db: Db, doctorUserId: string, inviteId: string): Promise<void> {
  const mine = (await listInvites(db, doctorUserId)).find((i) => i.id === inviteId);
  if (!mine) throw new Error("Invite not found.");
  await db.update(Collections.doctorInvites, inviteId, { revoked: true });
}

/** Persists inferred usage so a spent single-use code stops resolving on patients' phones. */
export async function reconcileInvites(db: Db, invites: Invite[], links: PatientLink[]): Promise<Invite[]> {
  const updates = attributeLinks(invites, links);
  if (updates.length === 0) return invites;
  const byId = new Map(invites.map((i) => [i.id, i]));
  for (const u of updates) {
    try {
      await db.update(Collections.doctorInvites, u.inviteId, { usedCount: u.usedCount, revoked: u.revoked });
      const current = byId.get(u.inviteId);
      if (current) byId.set(u.inviteId, { ...current, usedCount: u.usedCount, revoked: u.revoked });
    } catch {
      // Best effort: a failed write leaves the invite as it was; the next refresh retries.
    }
  }
  return invites.map((i) => byId.get(i.id) ?? i);
}

// ── links ──────────────────────────────────────────────────────────────────
export async function listLinks(db: Db, doctorUserId: string): Promise<PatientLink[]> {
  const docs = await db.list(Collections.patientLinks, [
    Query.equal("doctorUserId", doctorUserId),
    Query.limit(MAX_PAGE_SIZE),
  ]);
  return docs.map(linkFromDoc);
}

/**
 * Patient ids this doctor has revoked, each with the server time of the latest
 * revoke. Kept as audit entries because the doctor's own session may write
 * those even when no one may edit the patient-authored link document.
 */
export type RevocationMap = ReadonlyMap<string, number>;

export function revocationsFrom(entries: readonly AuditEntry[], doctorUserId: string): RevocationMap {
  const out = new Map<string, number>();
  for (const e of entries) {
    if (e.action !== "REVOKED_LINK" || e.actorUserId !== doctorUserId) continue;
    out.set(e.subjectUserId, Math.max(out.get(e.subjectUserId) ?? 0, e.storedAt));
  }
  return out;
}

/** Queried by action, not by recency, so an old revoke can never fall off a page. */
export async function listRevocations(db: Db, doctorUserId: string): Promise<RevocationMap> {
  const docs = await db.list(Collections.auditEntries, [
    Query.equal("actorUserId", doctorUserId),
    Query.equal("action", "REVOKED_LINK"),
    Query.limit(MAX_PAGE_SIZE),
  ]);
  return revocationsFrom(docs.map(auditFromDoc), doctorUserId);
}

/**
 * The trust rule for every read of patient data.
 *
 * A link grants access only if all of these hold:
 *  - it names this doctor's account,
 *  - it is ACTIVE with a recorded consent time,
 *  - it was written by an identity other than the doctor. A doctor holds
 *    update rights on documents they create, so a doctor-forged "consent" is
 *    authored by the doctor and is rejected here,
 *  - the server stored it while one of this doctor's invites was open. A
 *    doctor's account id is readable by any signed-in user, so without this a
 *    third party could write a link naming the doctor and a victim's patient id,
 *  - the doctor has not revoked this patient since the link was last written.
 *    A patient who links again after a revoke is a new consent and counts.
 *
 * Returns the link, or null with no hint as to why, so a probe learns nothing.
 */
export function grantsAccess(
  link: PatientLink,
  invites: readonly Invite[],
  doctorUserId: string,
  revocations: RevocationMap = new Map(),
): boolean {
  const revokedAt = revocations.get(link.patientUserId);
  const revokedAfterLink = revokedAt !== undefined && revokedAt >= Math.max(link.storedAt, link.updatedAt);
  return (
    link.status === "ACTIVE" &&
    link.consentGrantedAt !== null &&
    link.doctorUserId === doctorUserId &&
    link.authorUserId !== null &&
    link.authorUserId !== doctorUserId &&
    isRecognisedLink(invites, link) &&
    !revokedAfterLink
  );
}

export function authoriseLink(
  links: readonly PatientLink[],
  invites: readonly Invite[],
  doctorUserId: string,
  patientUserId: string,
  revocations: RevocationMap = new Map(),
): PatientLink | null {
  return (
    links.find((l) => l.patientUserId === patientUserId && grantsAccess(l, invites, doctorUserId, revocations)) ?? null
  );
}

export function activeLinks(
  links: readonly PatientLink[],
  invites: readonly Invite[],
  doctorUserId: string,
  revocations: RevocationMap = new Map(),
): PatientLink[] {
  return links.filter((l) => grantsAccess(l, invites, doctorUserId, revocations));
}

/**
 * Every real pairing this doctor's codes produced, whether or not access has
 * since been ended. Code usage is about what happened to the code, not about
 * whether the patient is still shared: if ending access un-counted the use, a
 * single-use code would become redeemable again the moment the doctor revoked.
 */
export function genuineLinks(links: readonly PatientLink[], invites: readonly Invite[], doctorUserId: string): PatientLink[] {
  return links.filter(
    (l) =>
      l.doctorUserId === doctorUserId &&
      l.consentGrantedAt !== null &&
      l.authorUserId !== null &&
      l.authorUserId !== doctorUserId &&
      isRecognisedLink(invites, l),
  );
}

// ── scan summaries (privileged, only after authoriseLink) ──────────────────

/**
 * Only summaries owned by the same identity that wrote the consenting link.
 * Without that, an attacker could forge a link naming a victim's patient id and
 * read the victim's summaries; with it, they can only ever read their own.
 */
export async function listSummariesForLink(adminDb: Db, link: PatientLink): Promise<ScanSummary[]> {
  if (link.authorUserId === null) return [];
  const docs = await adminDb.list(Collections.scanSummaries, [
    Query.equal("patientUserId", link.patientUserId),
    Query.limit(MAX_PAGE_SIZE),
  ]);
  return docs
    .filter((d) => authorOf(d.$permissions) === link.authorUserId)
    .map(summaryFromDoc)
    .sort((a, b) => b.capturedAt - a.capturedAt);
}

// ── audit ──────────────────────────────────────────────────────────────────

/** Re-renders and refreshes are not new views; one entry per actor/patient per window. */
export const AUDIT_DEDUPE_WINDOW_MS = 10 * 60 * 1000;

export type AuditInput = {
  actorUserId: string;
  link: PatientLink;
  action: AuditAction;
  detail: string;
  now: number;
  newId: () => string;
  dedupe?: boolean;
  /** Extra readers. Only a privileged writer may name an account other than itself. */
  readerExtras?: string[];
  /** Where to look for recent duplicates when `dedupe` is on. Defaults to the write Db. */
  dedupeWith?: Db;
};

/**
 * Appends an immutable entry: read for the listed accounts, update/delete for
 * nobody, so a written entry cannot be altered. Dedupe is opt-in and only for views.
 */
export async function recordAudit(db: Db, entry: AuditInput): Promise<void> {
  const { actorUserId, link, action, detail, now } = entry;
  if (entry.dedupe) {
    const recent = await listAuditByActor(entry.dedupeWith ?? db, actorUserId);
    const dupe = recent.some(
      (e) =>
        e.action === action &&
        e.subjectUserId === link.patientUserId &&
        e.detail === detail &&
        now - e.at < AUDIT_DEDUPE_WINDOW_MS,
    );
    if (dupe) return;
  }
  // Invalid ids are dropped rather than failing the write.
  const readers = [actorUserId, ...(entry.readerExtras ?? [])].filter(
    (id, i, all): id is string => !!id && ONE_ID_SAFE.test(id) && all.indexOf(id) === i,
  );
  await db.create(
    Collections.auditEntries,
    entry.newId(),
    { actorUserId, subjectUserId: link.patientUserId, action, at: now, detail },
    readers.map((id) => Permission.read(Role.user(id))),
  );
}

export type AuditOutcome = "patient-can-read" | "doctor-only";

/**
 * The patient is who the audit log is for, but Appwrite only lets a session
 * name itself in an ACL. So try the privileged writer first (names the doctor
 * and the patient's session), and fall back to the doctor's own session (names
 * the doctor only) when the key cannot write documents. The entry is recorded
 * either way; the outcome says whether the patient can see it.
 */
export async function auditWithFallback(sessionDb: Db, adminDb: Db, entry: AuditInput): Promise<AuditOutcome> {
  const extras = entry.link.authorUserId ? [entry.link.authorUserId] : [];
  const withDedupe = { ...entry, dedupeWith: sessionDb };
  try {
    await recordAudit(adminDb, { ...withDedupe, readerExtras: extras });
    return "patient-can-read";
  } catch {
    await recordAudit(sessionDb, withDedupe);
    return "doctor-only";
  }
}

export async function listAuditByActor(db: Db, actorUserId: string): Promise<AuditEntry[]> {
  const docs = await db.list(Collections.auditEntries, [
    Query.equal("actorUserId", actorUserId),
    Query.limit(MAX_PAGE_SIZE),
  ]);
  return docs.map(auditFromDoc).sort((a, b) => b.at - a.at);
}

/**
 * Doctor-side revoke. Always records the revoke, which is what ends access on
 * this dashboard even when nothing privileged can write. Then tries to flip the
 * patient-authored link to REVOKED so the patient's phone learns of it; that
 * step needs privileged write access and is best effort.
 */
export async function revokePatient(
  sessionDb: Db,
  adminDb: Db,
  link: PatientLink,
  actorUserId: string,
  now: number,
  newId: () => string,
): Promise<{ linkDocumentUpdated: boolean; audit: AuditOutcome }> {
  const audit = await auditWithFallback(sessionDb, adminDb, {
    actorUserId,
    link,
    action: "REVOKED_LINK",
    detail: link.id,
    now,
    newId,
  });
  try {
    await adminDb.update(Collections.patientLinks, link.id, { status: "REVOKED" });
    return { linkDocumentUpdated: true, audit };
  } catch {
    return { linkDocumentUpdated: false, audit };
  }
}
