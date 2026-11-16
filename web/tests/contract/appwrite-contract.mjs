// Contract test: the SAME assertions run against the fake server and the real Appwrite.
// If the fake and the real service ever disagree, this fails and the fake gets fixed,
// so the e2e tests that rely on the fake cannot drift into testing fiction.
//
//   node tests/contract/appwrite-contract.mjs fake
//   node --env-file=.env.local tests/contract/appwrite-contract.mjs real
//
// The web app talks TablesDB (tables/rows). The Android SDK still talks the legacy
// Databases API (collections/documents) over the same data, so both are exercised.
// "real" creates throwaway ZZ-CONTRACT users and a few rows, then deletes everything.
import { Account, AppwriteException, Client, Databases, ID, Permission, Query, Role, TablesDB, Users } from "node-appwrite";
import { startFakeAppwrite } from "../fake-appwrite/server.mjs";

const target = process.argv[2] ?? "fake";
let cfg, fake;
if (target === "fake") {
  fake = await startFakeAppwrite({});
  cfg = { endpoint: fake.url, projectId: fake.projectId, databaseId: fake.databaseId, key: "fake-key" };
} else {
  cfg = { endpoint: process.env.APPWRITE_ENDPOINT, projectId: process.env.APPWRITE_PROJECT_ID, databaseId: process.env.APPWRITE_DATABASE_ID, key: process.env.APPWRITE_API_KEY };
}

const mk = (c) => c.setEndpoint(cfg.endpoint).setProject(cfg.projectId);
const admin = mk(new Client()).setKey(cfg.key);
const adminUsers = new Users(admin);
const adminAccount = new Account(admin);
const adminTables = new TablesDB(admin);
const sessionClient = (secret) => mk(new Client()).setSession(secret);
const sessionTables = (secret) => new TablesDB(sessionClient(secret)); // what the web app uses
const sessionLegacy = (secret) => new Databases(sessionClient(secret)); // what the Android SDK uses
const sessionAccount = (secret) => new Account(sessionClient(secret));

const tag = Math.random().toString(36).slice(2, 8);
const results = [];
const findings = [];
const cleanup = [];
let failures = 0;

async function check(name, fn) {
  try {
    const outcome = await fn();
    const ok = outcome === true;
    if (!ok) failures += 1;
    results.push(`${ok ? "PASS" : "FAIL"}  ${name}${ok ? "" : `  -> ${outcome}`}`);
  } catch (e) {
    failures += 1;
    results.push(`FAIL  ${name}  -> threw ${e.code ?? ""} ${e.type ?? e.message}`);
  }
}

/** A security expectation. Failing is reported loudly but is not a fake-vs-real mismatch. */
async function finding(name, fn) {
  try {
    const outcome = await fn();
    findings.push(`${outcome === true ? "SECURE " : "OPEN   "}  ${name}${outcome === true ? "" : `  -> ${outcome === false ? "allowed" : outcome}`}`);
  } catch (e) {
    findings.push(`ERROR   ${name}  -> threw ${e.code ?? ""} ${e.type ?? e.message}`);
  }
}
const rejectsWith = async (fn, code) => {
  try { await fn(); return "did not reject"; } catch (e) { return e instanceof AppwriteException && (code === undefined || e.code === code) ? true : `rejected with ${e.code} ${e.type}`; }
};
const T = (tableId, extra) => ({ databaseId: cfg.databaseId, tableId, ...extra });
const L = (collectionId, extra) => ({ databaseId: cfg.databaseId, collectionId, ...extra });

async function mkUser(label) {
  const email = `zz-contract-${tag}-${label}@example.com`;
  const password = `Pw-${tag}-${label}-1234`;
  const acct = await adminAccount.create({ userId: ID.unique(), email, password, name: `ZZ-CONTRACT ${label}` });
  cleanup.push(async () => adminUsers.delete({ userId: acct.$id }));
  const session = await adminAccount.createEmailPasswordSession({ email, password });
  return { id: acct.$id, email, password, secret: session.secret };
}

try {
  const a = await mkUser("a");
  const b = await mkUser("b");

  // ── accounts and verification ─────────────────────────────────────────────
  await check("keyed createEmailPasswordSession returns a usable session secret", async () => (a.secret?.length ?? 0) > 20 && (await sessionAccount(a.secret).get()).$id === a.id);
  await check("wrong password is rejected with 401", () => rejectsWith(() => adminAccount.createEmailPasswordSession({ email: a.email, password: "definitely-wrong-1" }), 401));
  await check("duplicate email on account.create is 409", () => rejectsWith(() => adminAccount.create({ userId: ID.unique(), email: a.email, password: "Another-pass-123", name: "dup" }), 409));
  await check("a signed-in session sees its own labels (none yet)", async () => (await sessionAccount(a.secret).get()).labels.length === 0);
  await adminUsers.updateLabels({ userId: a.id, labels: ["verifieddoctor"] });
  await check("key-set label shows on the user's own account.get", async () => (await sessionAccount(a.secret).get()).labels.includes("verifieddoctor"));
  await check("labels with a hyphen are rejected (so the label must be alphanumeric)", () => rejectsWith(() => adminUsers.updateLabels({ userId: a.id, labels: ["verified-doctor"] }), 400));

  // ── invites through TablesDB, as the web app does ─────────────────────────
  const code = `ZZ${tag.toUpperCase()}`.slice(0, 8).padEnd(8, "Z");
  const inviteData = (c) => ({ doctorId: "p", doctorUserId: a.id, code: c, createdAt: 1, expiresAt: 2, maxUses: 1, usedCount: 0, revoked: false });
  const ownerPerms = (uid) => [Permission.read(Role.users()), Permission.update(Role.user(uid)), Permission.delete(Role.user(uid))];
  let inviteId;
  await check("session creates an invite row naming itself in the permissions", async () => {
    const row = await sessionTables(a.secret).createRow(T("doctor_invites", { rowId: ID.unique(), data: inviteData(code), permissions: ownerPerms(a.id) }));
    inviteId = row.$id;
    cleanup.push(() => adminTables.deleteRow(T("doctor_invites", { rowId: row.$id })));
    return !!row.$id;
  });
  await check("a duplicate invite code is a 409 (unique index)", () => rejectsWith(() => sessionTables(b.secret).createRow(T("doctor_invites", { rowId: ID.unique(), data: inviteData(code), permissions: ownerPerms(b.id) })), 409));
  await check("any signed-in user can read the invite by code", async () => (await sessionTables(b.secret).listRows(T("doctor_invites", { queries: [Query.equal("code", code)] }))).rows.length === 1);
  await check("the owner can update the invite", async () => (await sessionTables(a.secret).updateRow(T("doctor_invites", { rowId: inviteId, data: { usedCount: 1 } }))).usedCount === 1);
  await check("a row read exposes $permissions and $createdAt (the trust rules depend on both)", async () => {
    const [r] = (await sessionTables(a.secret).listRows(T("doctor_invites", { queries: [Query.equal("code", code)] }))).rows;
    return Array.isArray(r.$permissions) && r.$permissions.some((p) => p.startsWith("update(")) && !Number.isNaN(Date.parse(r.$createdAt));
  });
  await check("a session cannot name ANOTHER account in a row's permissions", () => rejectsWith(() => sessionTables(a.secret).createRow(T("scan_summaries", { rowId: ID.unique(), data: summary(`zz-${tag}-x`), permissions: [Permission.read(Role.user(a.id)), Permission.read(Role.user(b.id))] })), 401));

  // ── patient side: the Android SDK uses the legacy API over the same data ──
  function summary(scanId) { return { patientUserId: `zz-${tag}`, scanId, capturedAt: 1, topLabel: "probe", topLabelCode: "p", confidence: 0.5, concernBand: "LOW", bodyArea: "probe" }; }
  let summaryId;
  await check("legacy API: a session can create a summary naming only itself", async () => {
    const doc = await sessionLegacy(a.secret).createDocument(L("scan_summaries", { documentId: ID.unique(), data: summary(`zz-${tag}-own`), permissions: [Permission.read(Role.user(a.id)), Permission.update(Role.user(a.id)), Permission.delete(Role.user(a.id))] }));
    summaryId = doc.$id;
    cleanup.push(() => adminTables.deleteRow(T("scan_summaries", { rowId: doc.$id })));
    return true;
  });
  await check("a row written through the legacy API is visible through TablesDB (one store)", async () => (await sessionTables(a.secret).listRows(T("scan_summaries", { queries: [Query.equal("scanId", `zz-${tag}-own`)] }))).rows.length === 1);

  // ── what the web app's privileged path relies on (needs rows.read / rows.write) ──
  await check("the key can list rows written by someone else's session", async () => (await adminTables.listRows(T("scan_summaries", { queries: [Query.equal("scanId", `zz-${tag}-own`)] }))).rows.length === 1);
  await check("a keyed list returns $permissions, so the author of a row can be identified", async () => {
    const [r] = (await adminTables.listRows(T("scan_summaries", { queries: [Query.equal("scanId", `zz-${tag}-own`)] }))).rows;
    return r.$permissions.includes(`update("user:${a.id}")`);
  });
  await check("the key can update a row it did not write (flip a link to REVOKED)", async () => {
    const link = await sessionLegacy(b.secret).createDocument(L("patient_links", { documentId: ID.unique(), data: { doctorId: "p", doctorUserId: a.id, patientUserId: `zz-${tag}`, patientDisplayName: "ZZ-CONTRACT", linkedAt: 1, status: "ACTIVE", consentGrantedAt: 1 }, permissions: [Permission.read(Role.users()), Permission.update(Role.user(b.id)), Permission.delete(Role.user(b.id))] }));
    cleanup.push(() => adminTables.deleteRow(T("patient_links", { rowId: link.$id })));
    const updated = await adminTables.updateRow(T("patient_links", { rowId: link.$id, data: { status: "REVOKED" } }));
    return updated.status === "REVOKED";
  });
  await check("the key may name ANOTHER account in a row's permissions (audit entries need this)", async () => {
    const row = await adminTables.createRow(T("audit_entries", { rowId: ID.unique(), data: { actorUserId: a.id, subjectUserId: `zz-${tag}`, action: "VIEWED_PATIENT", at: 1, detail: "zz-contract" }, permissions: [Permission.read(Role.user(a.id)), Permission.read(Role.user(b.id))] }));
    cleanup.push(() => adminTables.deleteRow(T("audit_entries", { rowId: row.$id })));
    return row.$permissions.length === 2;
  });
  // ── SECURITY FINDINGS ─────────────────────────────────────────────────────
  // How the backend SHOULD behave with row-level permissions. A failure here is a
  // deployment misconfiguration (table-level grants override row permissions), not a
  // mismatch between the fake and the real service. Anonymous sessions are what the
  // Android app creates silently for every install.
  const anon = await adminAccount.createAnonymousSession().catch(() => null);
  if (anon) cleanup.push(async () => adminUsers.delete({ userId: anon.userId }));
  const summaryQuery = T("scan_summaries", { queries: [Query.equal("patientUserId", `zz-${tag}`)] });
  await finding("another account cannot READ a summary its permissions do not name", async () => (await sessionTables(b.secret).listRows(summaryQuery)).rows.length === 0);
  await finding("an ANONYMOUS session cannot read that summary", async () => !anon || (await sessionTables(anon.secret).listRows(summaryQuery)).rows.length === 0);
  await finding("another account cannot UPDATE a summary it does not own", async () => rejectsWith(() => sessionTables(b.secret).updateRow(T("scan_summaries", { rowId: summaryId, data: { topLabel: "tampered" } })), 401));
  await finding("another account cannot UPDATE a doctor's invite (a real change, e.g. cancel it)", async () => rejectsWith(() => sessionTables(b.secret).updateRow(T("doctor_invites", { rowId: inviteId, data: { revoked: true } })), 401));
  await finding("audit entries are immutable: their reader cannot edit or delete them", async () => {
    const row = await adminTables.createRow(T("audit_entries", { rowId: ID.unique(), data: { actorUserId: a.id, subjectUserId: `zz-${tag}`, action: "VIEWED_PATIENT", at: 1, detail: "zz-contract-imm" }, permissions: [Permission.read(Role.user(a.id))] }));
    cleanup.push(() => adminTables.deleteRow(T("audit_entries", { rowId: row.$id })));
    const upd = await rejectsWith(() => sessionTables(a.secret).updateRow(T("audit_entries", { rowId: row.$id, data: { detail: "tampered" } })), 401);
    const del = await rejectsWith(() => sessionTables(a.secret).deleteRow(T("audit_entries", { rowId: row.$id })), 401);
    return upd === true && del === true ? true : `reader could update=${upd !== true} delete=${del !== true}`;
  });} finally {
  for (const fn of cleanup.reverse()) { try { await fn(); } catch { /* best effort */ } }
  await fake?.close();
}

console.log(`\nContract results vs ${target}:\n${results.join("\n")}\n\n${failures === 0 ? "ALL PASS" : `${failures} FAILED`}`);
console.log(`\nSecurity findings (row-level permission enforcement) vs ${target}:\n${findings.join("\n")}`);
process.exit(failures === 0 ? 0 : 1);
