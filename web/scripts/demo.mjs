#!/usr/bin/env node
/**
 * Local demo of the doctor dashboard. Never touches your real Appwrite project:
 * it runs its own throwaway Appwrite stand-in (the same one the tests use) and
 * points the web app at it.
 *
 *   npm run demo                          start the backend + dashboard, seed a doctor
 *   npm run demo:phone -- pair ABCD2345 "Priya Sharma"
 *   npm run demo:phone -- scan HIGH "Atypical nevus"
 *   npm run demo:phone -- status
 *
 * The "phone" is a script, not the Android app. WHAT IS REAL AND WHAT IS NOT:
 *   pair  - does exactly what the Android app does today (RedeemInviteViewModel):
 *           looks up the code, resolves the doctor, writes a patient link.
 *   scan  - writes a scan summary the way the Android app WOULD, once it uploads
 *           them. The shipping app does not do this yet, so with the real app a
 *           doctor sees the patient but no scans.
 */
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Account, Client, Databases, ID, Permission, Query, Role, TablesDB, Users } from "node-appwrite";
import { startFakeAppwrite } from "../tests/fake-appwrite/server.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const FAKE_PORT = 4020;
const WEB_PORT = 3200;
const ENDPOINT = `http://127.0.0.1:${FAKE_PORT}/v1`;
const PROJECT = "testproject";
const DATABASE = "testdb";
const DOCTOR = { email: "demo@dermoai.test", password: "demo-pass-123", name: "Dr. Asha Verma" };
const PHONE_FILE = resolve(ROOT, ".demo", "phone.json");

const client = () => new Client().setEndpoint(ENDPOINT).setProject(PROJECT);
const admin = () => client().setKey("demo-key");

async function seedDoctor() {
  const account = new Account(admin());
  const users = new Users(admin());
  const created = await account.create({ userId: ID.unique(), email: DOCTOR.email, password: DOCTOR.password, name: DOCTOR.name });
  const session = await account.createEmailPasswordSession({ email: DOCTOR.email, password: DOCTOR.password });
  const tables = new TablesDB(client().setSession(session.secret));
  await tables.createRow({
    databaseId: DATABASE, tableId: "doctor_profiles", rowId: ID.unique(),
    data: {
      userId: created.$id, fullName: DOCTOR.name, qualifications: "MBBS\nMD Dermatology", registrationNumber: "MH-12345",
      specialty: "Dermatology", institution: "City Skin Clinic", yearsExperience: 9, verificationStatus: "UNVERIFIED", bio: "",
    },
    permissions: [Permission.read(Role.users()), Permission.update(Role.user(created.$id)), Permission.delete(Role.user(created.$id))],
  });
  await users.updateLabels({ userId: created.$id, labels: ["verifieddoctor"] });
}

async function up() {
  const nextBin = resolve(ROOT, "node_modules", "next", "dist", "bin", "next");
  if (!existsSync(resolve(ROOT, ".next", "BUILD_ID"))) {
    console.error("No production build found. Run `npm run build` first.");
    process.exit(1);
  }
  const fake = await startFakeAppwrite({ port: FAKE_PORT, projectId: PROJECT, databaseId: DATABASE });
  await seedDoctor();
  const web = spawn(process.execPath, [nextBin, "start", "-p", String(WEB_PORT)], {
    cwd: ROOT,
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      NODE_ENV: "production",
      APPWRITE_ENDPOINT: ENDPOINT,
      APPWRITE_PROJECT_ID: PROJECT,
      APPWRITE_DATABASE_ID: DATABASE,
      APPWRITE_API_KEY: "demo-key",
      APP_TIME_ZONE: "Asia/Kolkata",
    },
  });
  web.stdout.on("data", () => {});
  web.stderr.on("data", (d) => { const s = String(d); if (/error/i.test(s) && !/warn/i.test(s)) process.stderr.write(s); });
  const stop = async () => { web.kill(); await fake.close(); process.exit(0); };
  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);

  for (let i = 0; i < 60; i += 1) {
    try { if ((await fetch(`http://localhost:${WEB_PORT}/api/health`)).ok) break; } catch { /* still starting */ }
    await new Promise((r) => setTimeout(r, 500));
  }
  console.log(`
  DermoAI doctor dashboard demo is running (local, throwaway data).

    Open        http://localhost:${WEB_PORT}
    Sign in     ${DOCTOR.email}
    Password    ${DOCTOR.password}      (already verified)

  1. Go to "Pair patient" and create a code. You will see the QR and the code.
  2. In another terminal, play the patient's phone:
       npm run demo:phone -- pair <CODE> "Priya Sharma"
     The page flips to "Priya Sharma is now sharing with you" by itself.
  3. Have the phone take scans:
       npm run demo:phone -- scan LOW "Benign nevus" 20
       npm run demo:phone -- scan HIGH "Atypical nevus" 6
       npm run demo:phone -- scan CRITICAL "Melanoma" 1
     Open Patients and click the patient to see the record.

  Press Ctrl+C to stop. Everything is discarded when you do.
`);
}

// ── the simulated phone ─────────────────────────────────────────────────────

const readPhone = () => (existsSync(PHONE_FILE) ? JSON.parse(readFileSync(PHONE_FILE, "utf8")) : null);
const writePhone = (p) => { mkdirSync(dirname(PHONE_FILE), { recursive: true }); writeFileSync(PHONE_FILE, JSON.stringify(p, null, 2)); };

async function phoneSession(createIfMissing) {
  let phone = readPhone();
  if (phone) {
    try { await new Account(client().setSession(phone.secret)).get(); return phone; } catch { phone = null; }
  }
  if (!createIfMissing) return null;
  const anon = await new Account(admin()).createAnonymousSession();
  phone = { secret: anon.secret, userId: anon.userId, localId: `phone-${anon.userId.slice(-8)}`, name: "" };
  writePhone(phone);
  return phone;
}

const legacy = (secret) => new Databases(client().setSession(secret));

async function pair(code, name) {
  const phone = await phoneSession(true);
  const db = legacy(phone.secret);
  const normalised = code.toUpperCase().replace(/[^A-Z0-9]/g, "");
  const invite = (await db.listDocuments({ databaseId: DATABASE, collectionId: "doctor_invites", queries: [Query.equal("code", normalised), Query.limit(1)] })).documents[0];
  if (!invite) return console.log(`No invite matches ${normalised}. Check the code on the dashboard.`);
  if (invite.revoked) return console.log("That code was cancelled or already used.");
  if (Date.now() >= Number(invite.expiresAt)) return console.log("That code has expired.");
  const profile = (await db.listDocuments({ databaseId: DATABASE, collectionId: "doctor_profiles", queries: [Query.equal("userId", String(invite.doctorUserId)), Query.limit(1)] })).documents[0];
  if (!profile) return console.log("The doctor's profile could not be found.");
  console.log(`Consent screen: share your scan results with ${profile.fullName} (${profile.specialty}, ${profile.institution})? ... yes`);
  const now = Date.now();
  await db.createDocument({
    databaseId: DATABASE, collectionId: "patient_links", documentId: ID.unique(),
    data: { doctorId: String(invite.doctorId), doctorUserId: String(invite.doctorUserId), patientUserId: phone.localId, patientDisplayName: name, linkedAt: now, status: "ACTIVE", consentGrantedAt: now },
    permissions: [Permission.read(Role.users()), Permission.update(Role.user(phone.userId)), Permission.delete(Role.user(phone.userId))],
  });
  writePhone({ ...phone, name });
  console.log(`Paired as "${name}". Watch the dashboard.`);
}

async function scan(band, label, daysAgo) {
  const phone = await phoneSession(false);
  if (!phone?.name) return console.log("This phone is not paired yet. Run: npm run demo:phone -- pair <CODE> \"Your Name\"");
  const id = ID.unique();
  const confidence = { LOW: 0.91, MEDIUM: 0.84, HIGH: 0.78, CRITICAL: 0.86 }[band];
  await legacy(phone.secret).createDocument({
    databaseId: DATABASE, collectionId: "scan_summaries", documentId: id,
    data: { patientUserId: phone.localId, scanId: id, capturedAt: Date.now() - daysAgo * 86_400_000, topLabel: label, topLabelCode: label.slice(0, 3).toLowerCase(), confidence, concernBand: band, bodyArea: "Upper back" },
    permissions: [Permission.read(Role.user(phone.userId)), Permission.update(Role.user(phone.userId)), Permission.delete(Role.user(phone.userId))],
  });
  console.log(`Uploaded scan summary: ${label}, ${band}, ${daysAgo} day(s) ago. No photo is sent.`);
}

async function status() {
  const phone = await phoneSession(false);
  console.log(phone?.name ? `Paired as "${phone.name}" (id ${phone.localId}).` : "Not paired.");
}

const [cmd, sub, ...rest] = process.argv.slice(2);
if (cmd === "up") await up();
else if (cmd === "phone") {
  if (sub === "pair" && rest[0] && rest[1]) await pair(rest[0], rest.slice(1).join(" "));
  else if (sub === "scan" && ["LOW", "MEDIUM", "HIGH", "CRITICAL"].includes(String(rest[0]).toUpperCase())) {
    await scan(String(rest[0]).toUpperCase(), rest[1] ?? "Skin finding", Number(rest[2] ?? 0));
  } else if (sub === "status") await status();
  else console.log('Usage: demo:phone -- pair <CODE> "Name" | scan <LOW|MEDIUM|HIGH|CRITICAL> ["Label"] [daysAgo] | status');
} else {
  console.log("Usage: node scripts/demo.mjs up | phone ...");
  process.exit(2);
}
