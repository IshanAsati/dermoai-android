import { beforeEach, describe, expect, it } from "vitest";
import {
  activeLinks,
  authoriseLink,
  AUDIT_DEDUPE_WINDOW_MS,
  createInvite,
  grantsAccess,
  listAuditByActor,
  listInvites,
  listRevocations,
  listSummariesForLink,
  reconcileInvites,
  recordAudit,
  revocationsFrom,
  revokeInvite,
  revokePatient,
} from "@/lib/data/repo";
import { auditFromDoc, linkFromDoc, inviteFromDoc, inviteState, type Invite, type PatientLink, type RawDoc } from "@/lib/data/schema";
import { attributeLinks, isRecognisedLink } from "@/lib/domain/invite-attribution";
import { MemoryDb } from "../helpers/memory-db";

const NOW = Date.UTC(2026, 0, 15, 12);
const DOCTOR = "doc-acct";
const PATIENT_SESSION = "pat-session";
const STRANGER = "stranger-session";
const PROFILE = { id: "prof1", userId: DOCTOR, fullName: "Dr A", qualifications: [], registrationNumber: "R1", specialty: "Derm", institution: "H", yearsExperience: 3, bio: "" };

let n = 0;
const newId = () => `id${++n}`;
const countingRandom = (() => {
  let i = 0;
  return (max: number) => i++ % max;
})();

const linkDoc = (over: Record<string, unknown> = {}, author: string | null = PATIENT_SESSION, createdAt = NOW): RawDoc => ({
  $id: "link1",
  $createdAt: new Date(createdAt).toISOString(),
  $permissions: author
    ? [`read("users")`, `update("user:${author}")`, `delete("user:${author}")`]
    : [`read("users")`],
  doctorId: PROFILE.id,
  doctorUserId: DOCTOR,
  patientUserId: "pat-local",
  patientDisplayName: "Pia",
  linkedAt: NOW,
  status: "ACTIVE",
  consentGrantedAt: NOW,
  ...over,
});

const inviteOpen = (over: Partial<Invite> = {}): Invite => ({
  id: "inv1", doctorId: PROFILE.id, doctorUserId: DOCTOR, code: "ABCD2345",
  createdAt: NOW - 60_000, storedAt: NOW - 60_000, expiresAt: NOW + 3_600_000, maxUses: 1, usedCount: 0, revoked: false, ...over,
});

describe("link authorisation", () => {
  const invites = [inviteOpen()];
  const good = linkFromDoc(linkDoc());

  it("grants a genuine, patient-authored, consented link stored while an invite was open", () => {
    expect(grantsAccess(good, invites, DOCTOR)).toBe(true);
    expect(authoriseLink([good], invites, DOCTOR, "pat-local")).toBe(good);
  });

  it("rejects a link the doctor wrote about themselves (forged consent)", () => {
    const forged = linkFromDoc(linkDoc({}, DOCTOR));
    expect(grantsAccess(forged, invites, DOCTOR)).toBe(false);
    expect(authoriseLink([forged], invites, DOCTOR, "pat-local")).toBeNull();
  });

  it("rejects a link with no author permission", () => {
    expect(grantsAccess(linkFromDoc(linkDoc({}, null)), invites, DOCTOR)).toBe(false);
  });

  it("rejects third-party links stored when no invite of this doctor was open", () => {
    const late = linkFromDoc(linkDoc({}, STRANGER, NOW + 10 * 3_600_000));
    expect(grantsAccess(late, invites, DOCTOR)).toBe(false);
    const early = linkFromDoc(linkDoc({}, STRANGER, NOW - 10 * 3_600_000));
    expect(grantsAccess(early, invites, DOCTOR)).toBe(false);
  });

  it("stays recognised after its invite expires or is spent", () => {
    const spent = [inviteOpen({ usedCount: 1, revoked: true, expiresAt: NOW - 1 })];
    expect(grantsAccess(good, spent, DOCTOR)).toBe(true);
  });

  it("rejects non-ACTIVE or unconsented links, and links naming another doctor", () => {
    expect(grantsAccess(linkFromDoc(linkDoc({ status: "REVOKED" })), invites, DOCTOR)).toBe(false);
    expect(grantsAccess(linkFromDoc(linkDoc({ status: "INVITED" })), invites, DOCTOR)).toBe(false);
    expect(grantsAccess(linkFromDoc(linkDoc({ status: "bogus" })), invites, DOCTOR)).toBe(false);
    expect(grantsAccess(linkFromDoc(linkDoc({ consentGrantedAt: undefined })), invites, DOCTOR)).toBe(false);
    expect(grantsAccess(linkFromDoc(linkDoc({ doctorUserId: "someone-else" })), invites, DOCTOR)).toBe(false);
  });

  it("returns null for a patient with no link, with no hint why", () => {
    expect(authoriseLink([good], invites, DOCTOR, "other-patient")).toBeNull();
  });

  it("activeLinks filters out everything unauthorised", () => {
    const links = [good, linkFromDoc(linkDoc({ $id: "l2", status: "REVOKED" })), linkFromDoc(linkDoc({ $id: "l3" }, DOCTOR))];
    expect(activeLinks(links, invites, DOCTOR).map((l) => l.id)).toEqual(["link1"]);
  });
});

describe("scan summary visibility", () => {
  let db: MemoryDb;
  const summary = (id: string, author: string, over: Record<string, unknown> = {}): RawDoc => ({
    $id: id, $createdAt: new Date(NOW).toISOString(),
    $permissions: [`read("user:${author}")`, `update("user:${author}")`, `delete("user:${author}")`],
    patientUserId: "pat-local", scanId: id, capturedAt: NOW, topLabel: "Eczema", topLabelCode: "ecz",
    confidence: 0.9, concernBand: "LOW", bodyArea: "arm", ...over,
  });
  beforeEach(() => { db = new MemoryDb(); });

  it("returns only summaries written by the same identity that wrote the consenting link", async () => {
    db.seed("scan_summaries", summary("mine1", PATIENT_SESSION, { capturedAt: NOW - 1000 }));
    db.seed("scan_summaries", summary("mine2", PATIENT_SESSION, { capturedAt: NOW }));
    db.seed("scan_summaries", summary("planted", STRANGER)); // same patientUserId, different author
    const link = linkFromDoc(linkDoc());
    const got = await listSummariesForLink(db, link);
    expect(got.map((s) => s.id)).toEqual(["mine2", "mine1"]); // newest first, planted excluded
  });

  it("a forged link cannot be used to read a victim's summaries", async () => {
    db.seed("scan_summaries", summary("victim1", "victim-session", { patientUserId: "victim-local" }));
    const forged = linkFromDoc(linkDoc({ patientUserId: "victim-local" }, DOCTOR));
    expect(await listSummariesForLink(db, forged)).toEqual([]);
  });

  it("returns nothing when the link has no author", async () => {
    db.seed("scan_summaries", summary("s", PATIENT_SESSION));
    expect(await listSummariesForLink(db, linkFromDoc(linkDoc({}, null)))).toEqual([]);
  });
});

describe("invites", () => {
  let db: MemoryDb;
  beforeEach(() => { db = new MemoryDb(() => NOW); });

  it("creates an Android-compatible document owned by the doctor", async () => {
    const invite = await createInvite(db, PROFILE, { ttlMs: 7 * 86_400_000, maxUses: 1, now: NOW, randomInt: countingRandom, newId });
    expect(invite).toMatchObject({ doctorId: "prof1", doctorUserId: DOCTOR, usedCount: 0, revoked: false, maxUses: 1 });
    expect(invite.code).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/);
    expect(invite.expiresAt).toBe(NOW + 7 * 86_400_000);
    const stored = [...db.store.get("doctor_invites")!.values()][0];
    expect(stored.$permissions).toEqual([`read("users")`, `update("user:${DOCTOR}")`, `delete("user:${DOCTOR}")`]);
  });

  it("retries on a code collision and gives up loudly after repeated collisions", async () => {
    db.seed("doctor_invites", { $id: "x", code: "ABCDEFGH" });
    let calls = 0;
    // Collides once (ABCDEFGH), then yields different characters.
    const rnd = (max: number) => (calls++ < 8 ? calls - 1 : (calls * 7) % max);
    const ok = await createInvite(db, PROFILE, { ttlMs: 1000, maxUses: 1, now: NOW, randomInt: rnd, newId });
    expect(ok.code).not.toBe("ABCDEFGH");

    const always = (): number => 0; // always "AAAAAAAA"
    db.seed("doctor_invites", { $id: "y", code: "AAAAAAAA" });
    await expect(createInvite(db, PROFILE, { ttlMs: 1000, maxUses: 1, now: NOW, randomInt: always, newId })).rejects.toThrow(/unique invite code/);
  });

  it("only the issuing doctor's invites can be revoked", async () => {
    db.seed("doctor_invites", { $id: "mine", doctorUserId: DOCTOR, code: "AAAAAAAA", revoked: false });
    db.seed("doctor_invites", { $id: "theirs", doctorUserId: "other-doc", code: "BBBBBBBB", revoked: false });
    await revokeInvite(db, DOCTOR, "mine");
    expect(db.store.get("doctor_invites")!.get("mine")!.revoked).toBe(true);
    await expect(revokeInvite(db, DOCTOR, "theirs")).rejects.toThrow(/not found/i);
    expect(db.store.get("doctor_invites")!.get("theirs")!.revoked).toBe(false);
    const listed = await listInvites(db, DOCTOR);
    expect(listed.map((i) => i.id)).toEqual(["mine"]);
  });

  it("derives state, failing closed on a half-written document", () => {
    expect(inviteState(inviteOpen(), NOW)).toBe("ACTIVE");
    expect(inviteState(inviteOpen({ revoked: true }), NOW)).toBe("REVOKED");
    expect(inviteState(inviteOpen({ expiresAt: NOW }), NOW)).toBe("EXPIRED");
    expect(inviteState(inviteOpen({ usedCount: 1 }), NOW)).toBe("USED");
    const broken = inviteFromDoc({ $id: "b", code: "abcd2345" });
    expect(broken.code).toBe("ABCD2345");
    expect(inviteState(broken, NOW)).toBe("REVOKED");
  });
});

describe("invite usage attribution", () => {
  const inv = (id: string, createdAt: number, over: Partial<Invite> = {}) =>
    inviteOpen({ id, createdAt, storedAt: createdAt, expiresAt: createdAt + 3_600_000, ...over });
  const link = (id: string, storedAt: number) => ({ id, storedAt });

  it("never credits a link that was stored BEFORE the code existed (fresh code must not start 'used')", () => {
    const fresh = inv("fresh", NOW);
    expect(attributeLinks([fresh], [link("earlier", NOW - 1)])).toEqual([]);
    expect(attributeLinks([fresh], [link("30s-earlier", NOW - 30_000)])).toEqual([]);
    expect(isRecognisedLink([fresh], link("earlier", NOW - 1))).toBe(false);
  });

  it("credits a link stored at the same instant or later", () => {
    expect(attributeLinks([inv("a", NOW)], [link("same", NOW)])).toHaveLength(1);
  });

  it("marks a single-use invite spent and revoked after one pairing", () => {
    expect(attributeLinks([inv("a", NOW)], [link("l1", NOW + 60_000)])).toEqual([{ inviteId: "a", usedCount: 1, revoked: true }]);
  });

  it("keeps a multi-use invite open until the cap", () => {
    const invites = [inv("a", NOW, { maxUses: 3 })];
    expect(attributeLinks(invites, [link("l1", NOW + 1000), link("l2", NOW + 2000)])).toEqual([{ inviteId: "a", usedCount: 2, revoked: false }]);
    expect(attributeLinks(invites, [1, 2, 3].map((i) => link("l" + i, NOW + i * 1000)))).toEqual([{ inviteId: "a", usedCount: 3, revoked: true }]);
  });

  it("attributes to the newest invite that was open, and spills to older ones once spent", () => {
    const invites = [inv("old", NOW, { maxUses: 1 }), inv("new", NOW + 10_000, { maxUses: 1 })];
    const ups = attributeLinks(invites, [link("l1", NOW + 20_000), link("l2", NOW + 30_000)]);
    expect(ups).toHaveLength(2);
    expect(ups.every((u) => u.usedCount === 1 && u.revoked)).toBe(true);
  });

  it("never lowers an existing count and ignores links outside every window", () => {
    expect(attributeLinks([inv("a", NOW, { usedCount: 2, maxUses: 5 })], [])).toEqual([]);
    expect(attributeLinks([inv("a", NOW)], [link("l", NOW + 10 * 3_600_000)])).toEqual([]);
  });

  it("does not resurrect or count against a manually revoked invite", () => {
    expect(attributeLinks([inv("a", NOW, { revoked: true })], [link("l", NOW + 1000)])).toEqual([]);
  });

  it("is idempotent: applying the result and re-running changes nothing", () => {
    const invites = [inv("a", NOW)];
    const [u] = attributeLinks(invites, [link("l1", NOW + 1000)]);
    const applied = invites.map((i) => (i.id === u.inviteId ? { ...i, usedCount: u.usedCount, revoked: u.revoked } : i));
    expect(attributeLinks(applied, [link("l1", NOW + 1000)])).toEqual([]);
  });

  it("reconcileInvites persists the update through the Db", async () => {
    const db = new MemoryDb(() => NOW);
    db.seed("doctor_invites", { $id: "a", doctorUserId: DOCTOR, code: "AAAAAAAA", createdAt: NOW, expiresAt: NOW + 3_600_000, maxUses: 1, usedCount: 0, revoked: false });
    const invites = await listInvites(db, DOCTOR);
    const links = [linkFromDoc(linkDoc({}, PATIENT_SESSION, NOW + 5000))];
    const out = await reconcileInvites(db, invites, links);
    expect(out[0]).toMatchObject({ usedCount: 1, revoked: true });
    expect(db.store.get("doctor_invites")!.get("a")).toMatchObject({ usedCount: 1, revoked: true });
  });
});

describe("doctor-side revoke", () => {
  const invites = [inviteOpen()];
  const link = linkFromDoc(linkDoc({}, PATIENT_SESSION, NOW));
  let session: MemoryDb;
  let admin: MemoryDb;
  beforeEach(() => { session = new MemoryDb(() => NOW + 60_000); admin = new MemoryDb(() => NOW + 60_000); n = 0; });

  it("a revoke recorded after the link ends access, even if the link doc could not be edited", async () => {
    admin.update = async () => { throw new Error("missing scope rows.write"); };
    const res = await revokePatient(session, admin, link, DOCTOR, NOW + 60_000, newId);
    expect(res.linkDocumentUpdated).toBe(false);
    // Privileged audit write also failed? No: admin.create still works in this fake, so patient can read.
    const revs = await listRevocations(admin, DOCTOR).catch(() => new Map());
    expect(grantsAccess(link, invites, DOCTOR, revs)).toBe(false);
  });

  it("falls back to the doctor's session when privileged writes are unavailable", async () => {
    admin.create = async () => { throw new Error("missing scope rows.write"); };
    admin.update = async () => { throw new Error("missing scope rows.write"); };
    const res = await revokePatient(session, admin, link, DOCTOR, NOW + 60_000, newId);
    expect(res).toEqual({ linkDocumentUpdated: false, audit: "doctor-only" });
    const [entry] = [...session.store.get("audit_entries")!.values()];
    expect(entry.$permissions).toEqual([`read("user:${DOCTOR}")`]);
    const revs = await listRevocations(session, DOCTOR);
    expect(grantsAccess(link, invites, DOCTOR, revs)).toBe(false);
  });

  it("when privileged writes work, the patient can read the entry and the link is flipped", async () => {
    admin.seed("patient_links", { ...linkDoc() });
    const res = await revokePatient(session, admin, link, DOCTOR, NOW + 60_000, newId);
    expect(res).toEqual({ linkDocumentUpdated: true, audit: "patient-can-read" });
    expect(admin.store.get("patient_links")!.get("link1")!.status).toBe("REVOKED");
    const [entry] = [...admin.store.get("audit_entries")!.values()];
    expect(entry.$permissions).toEqual([`read("user:${DOCTOR}")`, `read("user:${PATIENT_SESSION}")`]);
  });

  it("a patient who links again after a revoke is a new consent and counts", () => {
    const revokedAt = new Map([["pat-local", NOW + 60_000]]);
    expect(grantsAccess(link, invites, DOCTOR, revokedAt)).toBe(false);
    const relinked = linkFromDoc(linkDoc({}, PATIENT_SESSION, NOW + 120_000));
    expect(grantsAccess(relinked, [inviteOpen({ expiresAt: NOW + 4_000_000 })], DOCTOR, revokedAt)).toBe(true);
  });

  it("only this doctor's revokes count, and only REVOKED_LINK actions", () => {
    const e = (over: Record<string, unknown>) => auditFromDoc({ $id: "e", $createdAt: new Date(NOW).toISOString(), actorUserId: DOCTOR, subjectUserId: "pat-local", action: "REVOKED_LINK", at: NOW, detail: "", ...over });
    expect(revocationsFrom([e({ actorUserId: "someone-else" })], DOCTOR).size).toBe(0);
    expect(revocationsFrom([e({ action: "VIEWED_PATIENT" })], DOCTOR).size).toBe(0);
    expect(revocationsFrom([e({})], DOCTOR).get("pat-local")).toBe(NOW);
  });

  it("listRevocations queries by action so an old revoke cannot age out of a page", async () => {
    for (let i = 0; i < 150; i += 1) {
      session.seed("audit_entries", { $id: "v" + i, $createdAt: new Date(NOW + i).toISOString(), actorUserId: DOCTOR, subjectUserId: "p" + i, action: "VIEWED_PATIENT", at: NOW + i, detail: "" });
    }
    session.seed("audit_entries", { $id: "old-revoke", $createdAt: new Date(NOW - 99_999_999).toISOString(), actorUserId: DOCTOR, subjectUserId: "pat-local", action: "REVOKED_LINK", at: NOW - 99_999_999, detail: "" });
    expect((await listRevocations(session, DOCTOR)).has("pat-local")).toBe(true);
  });
});

describe("audit", () => {
  let db: MemoryDb;
  const link: PatientLink = linkFromDoc(linkDoc());
  beforeEach(() => { db = new MemoryDb(() => NOW); n = 0; });

  const view = (now: number, dedupe = true) =>
    recordAudit(db, { actorUserId: DOCTOR, link, action: "VIEWED_PATIENT", detail: link.id, now, newId, dedupe });

  it("writes an entry readable by the doctor and any extra reader, writable by neither", async () => {
    await recordAudit(db, { actorUserId: DOCTOR, link, action: "VIEWED_PATIENT", detail: link.id, now: NOW, newId, readerExtras: [PATIENT_SESSION] });
    const [e] = [...db.store.get("audit_entries")!.values()];
    expect(e).toMatchObject({ actorUserId: DOCTOR, subjectUserId: "pat-local", action: "VIEWED_PATIENT", detail: "link1" });
    expect(e.$permissions).toEqual([`read("user:${DOCTOR}")`, `read("user:${PATIENT_SESSION}")`]);
    expect((e.$permissions as string[]).some((p) => p.startsWith("update") || p.startsWith("delete"))).toBe(false);
  });

  it("a session write names only the doctor (Appwrite forbids naming another account)", async () => {
    await view(NOW);
    const [e] = [...db.store.get("audit_entries")!.values()];
    expect(e.$permissions).toEqual([`read("user:${DOCTOR}")`]);
  });

  it("collapses refreshes inside the window to one entry, then logs again after it", async () => {
    await view(NOW);
    await view(NOW + 60_000);
    await view(NOW + AUDIT_DEDUPE_WINDOW_MS - 1);
    expect(db.store.get("audit_entries")!.size).toBe(1);
    await view(NOW + AUDIT_DEDUPE_WINDOW_MS + 1);
    expect(db.store.get("audit_entries")!.size).toBe(2);
    expect((await listAuditByActor(db, DOCTOR))[0].at).toBe(NOW + AUDIT_DEDUPE_WINDOW_MS + 1);
  });

  it("never dedupes a revoke (dedupe is opt-in)", async () => {
    await recordAudit(db, { actorUserId: DOCTOR, link, action: "REVOKED_LINK", detail: link.id, now: NOW, newId });
    await recordAudit(db, { actorUserId: DOCTOR, link, action: "REVOKED_LINK", detail: link.id, now: NOW + 1, newId });
    expect(db.store.get("audit_entries")!.size).toBe(2);
  });

  it("drops reader ids that Appwrite would reject rather than failing the write", async () => {
    await recordAudit(db, { actorUserId: DOCTOR, link, action: "VIEWED_PATIENT", detail: "x", now: NOW, newId, readerExtras: ["has spaces!", ""] });
    const [e] = [...db.store.get("audit_entries")!.values()];
    expect(e.$permissions).toEqual([`read("user:${DOCTOR}")`]);
  });
});
