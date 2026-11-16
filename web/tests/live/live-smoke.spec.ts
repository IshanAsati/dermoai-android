import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import jsQR from "jsqr";
import { Account, Client, Databases, ID, Permission, Query, Role, TablesDB, Users } from "node-appwrite";
import { PNG } from "pngjs";

const env = (k: string) => {
  const v = process.env[k];
  if (!v) throw new Error(`${k} is not set. Run via: npm run test:live`);
  return v;
};
const endpoint = env("APPWRITE_ENDPOINT");
const projectId = env("APPWRITE_PROJECT_ID");
const databaseId = env("APPWRITE_DATABASE_ID");
const admin = new Client().setEndpoint(endpoint).setProject(projectId).setKey(env("APPWRITE_API_KEY"));
const users = new Users(admin);
const adminAccount = new Account(admin);
const adminTables = new TablesDB(admin); // cleanup, using the same rows.write scope the app uses
const sessionClient = (secret: string) => new Client().setEndpoint(endpoint).setProject(projectId).setSession(secret);
const asDoctor = (secret: string) => new TablesDB(sessionClient(secret)); // the web app uses TablesDB
const asPatient = (secret: string) => new Databases(sessionClient(secret)); // the Android SDK still uses the legacy API

const T = (tableId: string) => ({ databaseId, tableId });

test("real Appwrite: sign up, verify, pair via code and QR, see scans, audit, revoke", async ({ page, context }) => {
  const tag = Date.now().toString(36);
  const email = `zz-live-${tag}@example.com`;
  const password = `Live-${tag}-Pass-1`;
  const cleanups: Array<() => Promise<unknown>> = [];
  const queued = new Set<string>(); // the same audit row can be listed twice; delete each row once
  const deleteRow = (tableId: string, rowId: string) => () => adminTables.deleteRow({ ...T(tableId), rowId });
  const cleanupRow = (tableId: string, rowId: string) => {
    if (queued.has(`${tableId}/${rowId}`)) return;
    queued.add(`${tableId}/${rowId}`);
    cleanups.push(deleteRow(tableId, rowId));
  };

  try {
    // 0. The deployment health check reports a reachable backend.
    const health = await page.request.get("/api/health");
    expect(await health.json()).toEqual({ ok: true, configured: true, appwrite: "reachable" });

    // 1. Sign up against the real service.
    await page.goto("/signup");
    await page.getByLabel("Full name").fill(`ZZ-LIVE Dr ${tag}`);
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill(password);
    await page.getByLabel("Medical registration number").fill("ZZ-LIVE-1");
    await page.getByLabel("Specialty").fill("Dermatology");
    await page.getByLabel("Clinic or hospital").fill("ZZ-LIVE Clinic");
    await page.getByRole("button", { name: "Create account" }).click();
    await page.waitForURL("**/dashboard");
    const { users: found } = await users.list({ queries: [Query.equal("email", email)] });
    const doctorId = found[0].$id;
    cleanups.push(() => users.delete({ userId: doctorId }));

    // 2. Unverified: the gate, not patient data.
    await expect(page.getByRole("heading", { name: "Your account is awaiting verification" })).toBeVisible();

    // 3. Verify with the same mechanism the admin script uses.
    await users.updateLabels({ userId: doctorId, labels: ["verifieddoctor"] });
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "No patients yet" })).toBeVisible();

    // 4. Sign out and back in with the real session endpoint.
    await page.goto("/profile");
    await page.getByRole("button", { name: "Sign out" }).last().click();
    await page.waitForURL("**/login");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.waitForURL("**/dashboard");

    const cookie = (await context.cookies()).find((c) => c.name === "dermoai_session")!;
    const doctorDb = asDoctor(cookie.value);

    // 5. Create a pairing code. The QR, decoded from pixels, is the Android deep link.
    await page.goto("/invites");
    await page.getByRole("button", { name: /Create pairing code/ }).click();
    const code = ((await page.locator(".code-display").textContent()) ?? "").replace(/[^A-Z0-9]/g, "");
    expect(code).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/);
    const png = PNG.sync.read(await page.locator(".qr-frame img").screenshot());
    expect(jsQR(new Uint8ClampedArray(png.data), png.width, png.height)?.data).toBe(`dermoai://invite/${code}`);

    const [profile] = (await doctorDb.listRows({ ...T("doctor_profiles"), queries: [Query.equal("userId", doctorId)] })).rows;
    const [invite] = (await doctorDb.listRows({ ...T("doctor_invites"), queries: [Query.equal("code", code)] })).rows;
    cleanups.push(deleteRow("doctor_invites", invite.$id));
    cleanups.push(deleteRow("doctor_profiles", profile.$id));
    expect(invite).toMatchObject({ doctorUserId: doctorId, doctorId: profile.$id, maxUses: 1, usedCount: 0, revoked: false });

    // 6. A patient phone (anonymous session, legacy SDK) redeems it like RedeemInviteViewModel.
    const anon = await adminAccount.createAnonymousSession();
    cleanups.push(() => users.delete({ userId: anon.userId }));
    const patientDb = asPatient(anon.secret);
    const legacy = (collectionId: string) => ({ databaseId, collectionId });
    const looked = (await patientDb.listDocuments({ ...legacy("doctor_invites"), queries: [Query.equal("code", code), Query.limit(1)] })).documents[0];
    expect(looked.$id).toBe(invite.$id);
    const resolved = (await patientDb.listDocuments({ ...legacy("doctor_profiles"), queries: [Query.equal("userId", String(looked.doctorUserId)), Query.limit(1)] })).documents[0];
    expect(resolved.$id).toBe(profile.$id);
    const patientLocalId = `zz-live-${tag}`;
    const now = Date.now(); // 1.7e12: this is what the bigint columns must hold
    const link = await patientDb.createDocument({
      ...legacy("patient_links"), documentId: ID.unique(),
      data: { doctorId: profile.$id, doctorUserId: doctorId, patientUserId: patientLocalId, patientDisplayName: "ZZ-LIVE Patient", linkedAt: now, status: "ACTIVE", consentGrantedAt: now },
      permissions: [Permission.read(Role.users()), Permission.update(Role.user(anon.userId)), Permission.delete(Role.user(anon.userId))],
    });
    cleanups.push(deleteRow("patient_links", link.$id));

    // 7. The patient scans twice. Summaries only: no photos, owner-only permissions.
    const pushScan = async (band: string, label: string, daysAgo: number, confidence: number) => {
      const id = ID.unique();
      const doc = await patientDb.createDocument({
        ...legacy("scan_summaries"), documentId: id,
        data: { patientUserId: patientLocalId, scanId: id, capturedAt: Date.now() - daysAgo * 86_400_000, topLabel: label, topLabelCode: "zz", confidence, concernBand: band, bodyArea: "Left forearm" },
        permissions: [Permission.read(Role.user(anon.userId)), Permission.update(Role.user(anon.userId)), Permission.delete(Role.user(anon.userId))],
      });
      cleanups.push(deleteRow("scan_summaries", doc.$id));
    };
    await pushScan("LOW", "ZZ Benign finding", 9, 0.91);
    await pushScan("HIGH", "ZZ Atypical finding", 1, 0.8);

    // 8. The doctor's page picks up the pairing, and the single-use code is spent server-side.
    await expect(page.getByRole("heading", { name: "ZZ-LIVE Patient is now sharing with you" })).toBeVisible({ timeout: 20_000 });
    await expect.poll(async () => (await doctorDb.getRow({ ...T("doctor_invites"), rowId: invite.$id })).usedCount, { timeout: 20_000 }).toBe(1);
    expect((await doctorDb.getRow({ ...T("doctor_invites"), rowId: invite.$id })).revoked).toBe(true);

    // 9. The dashboard shows the patient with their newest finding (this needs rows.read on the key).
    await page.goto("/dashboard");
    const row = page.locator(".patient-row", { hasText: "ZZ-LIVE Patient" });
    await expect(row).toBeVisible();
    await expect(row).toContainText("High concern");
    await expect(row).toContainText("ZZ Atypical finding");

    // 10. The record lists both scans, newest first, and the view is written to the audit log.
    await row.click();
    await expect(page.getByRole("heading", { name: "ZZ-LIVE Patient", level: 1 })).toBeVisible();
    const scans = page.locator(".scan-item");
    await expect(scans).toHaveCount(2);
    await expect(scans.first()).toContainText("ZZ Atypical finding");
    const audit = (await adminTables.listRows({ ...T("audit_entries"), queries: [Query.equal("actorUserId", doctorId)] })).rows;
    audit.forEach((a) => cleanupRow("audit_entries", a.$id));
    expect(audit.map((a) => a.action)).toContain("VIEWED_PATIENT");
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
    expect(axe.violations.map((v) => v.id)).toEqual([]);

    // 11. Ending access removes the patient, writes a REVOKED_LINK entry, and flips the link the patient's phone reads.
    await page.getByText("End access to this patient").click();
    await page.getByRole("button", { name: "Yes, end access" }).click();
    await page.waitForURL("**/dashboard?revoked=1");
    await expect(page.locator(".patient-row", { hasText: "ZZ-LIVE Patient" })).toHaveCount(0);
    const after = (await adminTables.listRows({ ...T("audit_entries"), queries: [Query.equal("actorUserId", doctorId)] })).rows;
    after.forEach((a) => cleanupRow("audit_entries", a.$id));
    expect(after.map((a) => a.action)).toContain("REVOKED_LINK");
    expect((await patientDb.getDocument({ ...legacy("patient_links"), documentId: link.$id })).status).toBe("REVOKED");
  } finally {
    for (const fn of cleanups.reverse()) {
      try { await fn(); } catch (e) { console.log("cleanup:", (e as Error).message); }
    }
  }
});
