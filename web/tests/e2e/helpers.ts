import { Account, Client, Databases, ID, Permission, Query, Role, Users } from "node-appwrite";

export const FAKE = "http://127.0.0.1:4010/v1";
export const PROJECT = "testproject";
export const DB = "testdb";

const adminClient = () => new Client().setEndpoint(FAKE).setProject(PROJECT).setKey("fake-key");
const sessionClient = (secret: string) => new Client().setEndpoint(FAKE).setProject(PROJECT).setSession(secret);

export const admin = {
  users: new Users(adminClient()),
  account: new Account(adminClient()),
  db: new Databases(adminClient()),
};

async function control(path: string, body: unknown = {}) {
  const res = await fetch(`${FAKE}/__fake/${path}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  return res.json();
}

/**
 * Key scope sets. "full" is the key actually deployed: users, sessions, tables and rows.
 * "no-rows" is a minimal key someone might create instead (users + sessions only), which
 * must degrade gracefully. "read-only" can read rows but not write them.
 */
export type KeyMode = "full" | "no-rows" | "read-only";
export async function setKeyScopes(mode: KeyMode) {
  const base = ["users.read", "users.write", "sessions.write"];
  const scopes = {
    full: [...base, "tables.read", "tables.write", "rows.read", "rows.write"],
    "no-rows": base,
    "read-only": [...base, "tables.read", "rows.read"],
  }[mode];
  await control("key-scopes", { scopes });
}

export async function fakeDocs(collection: string): Promise<Array<Record<string, unknown>>> {
  const res = await fetch(`${FAKE}/__fake/docs/${collection}`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
  return (await res.json()).docs;
}

export async function fakeRequests(): Promise<string[]> {
  const res = await fetch(`${FAKE}/__fake/requests`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
  return (await res.json()).requests;
}

let counter = 0;
export const unique = (label: string) => `${label}${Date.now().toString(36)}${(counter += 1)}`;

export async function verifyDoctor(email: string) {
  const { users } = await admin.users.list({ queries: [Query.equal("email", email)] });
  await admin.users.updateLabels({ userId: users[0].$id, labels: ["verifieddoctor"] });
}

/**
 * Plays the patient's phone using the same calls as the Android app's redeem flow
 * (RedeemInviteViewModel + DoctorSyncRepository): look up the invite by code,
 * resolve the doctor's profile, then publish a link the patient authored.
 */
export class PatientApp {
  private constructor(
    readonly sessionUserId: string,
    private readonly db: Databases,
    readonly localId: string,
  ) {}

  static async create(localId = unique("pat")) {
    const anon = await admin.account.createAnonymousSession();
    return new PatientApp(anon.userId, new Databases(sessionClient(anon.secret)), localId);
  }

  async lookup(code: string) {
    const { documents } = await this.db.listDocuments({ databaseId: DB, collectionId: "doctor_invites", queries: [Query.equal("code", code.toUpperCase()), Query.limit(1)] });
    return documents[0] ?? null;
  }

  /** Mirrors the app's consent screen checks, then grantConsent's pushPatientLink. */
  async redeem(code: string, displayName: string, now = Date.now()) {
    const invite = await this.lookup(code);
    if (!invite) return { ok: false as const, reason: "not-found" };
    if (invite.revoked) return { ok: false as const, reason: "revoked" };
    if (now >= Number(invite.expiresAt)) return { ok: false as const, reason: "expired" };
    if (Number(invite.usedCount) >= Number(invite.maxUses)) return { ok: false as const, reason: "used" };
    const profiles = await this.db.listDocuments({ databaseId: DB, collectionId: "doctor_profiles", queries: [Query.equal("userId", String(invite.doctorUserId)), Query.limit(1)] });
    if (!profiles.documents[0]) return { ok: false as const, reason: "no-profile" };
    const link = await this.db.createDocument({
      databaseId: DB,
      collectionId: "patient_links",
      documentId: ID.unique(),
      data: {
        doctorId: String(invite.doctorId),
        doctorUserId: String(invite.doctorUserId),
        patientUserId: this.localId,
        patientDisplayName: displayName,
        linkedAt: now,
        status: "ACTIVE",
        consentGrantedAt: now,
      },
      // SyncPermissions.sessionOwnedReadableByUsers: writer keeps control, any signed-in user can read.
      permissions: [Permission.read(Role.users()), Permission.update(Role.user(this.sessionUserId)), Permission.delete(Role.user(this.sessionUserId))],
    });
    return { ok: true as const, linkId: link.$id };
  }

  /** SyncPermissions.scanSummaryPermissions with no doctors named: owner only. */
  async pushScan(s: { band: string; label?: string; confidence?: number; daysAgo?: number; area?: string }) {
    const id = ID.unique();
    return this.db.createDocument({
      databaseId: DB,
      collectionId: "scan_summaries",
      documentId: id,
      data: {
        patientUserId: this.localId,
        scanId: id,
        capturedAt: Date.now() - (s.daysAgo ?? 0) * 86_400_000,
        topLabel: s.label ?? "Eczema",
        topLabelCode: "ecz",
        confidence: s.confidence ?? 0.9,
        concernBand: s.band,
        bodyArea: s.area ?? "Left forearm",
      },
      permissions: [Permission.read(Role.user(this.sessionUserId)), Permission.update(Role.user(this.sessionUserId)), Permission.delete(Role.user(this.sessionUserId))],
    });
  }
}
