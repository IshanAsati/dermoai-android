import { Client, Databases, ID, Permission, Role } from "node-appwrite";
import { createCode, expect, signUp, test, verifiedDoctor } from "./fixtures";
import { admin, DB, FAKE, fakeDocs, PatientApp, PROJECT, setKeyScopes } from "./helpers";

test.beforeEach(async () => {
  await setKeyScopes("full");
  await fetch(`${FAKE}/__fake/fail-creates`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
});

/** Any signed-in account at all. On the real backend anyone can mint one, anonymously. */
async function attacker() {
  const anon = await admin.account.createAnonymousSession();
  const db = new Databases(new Client().setEndpoint(FAKE).setProject(PROJECT).setSession(anon.secret));
  return { id: anon.userId, db };
}

const doctorProfile = async () => (await fakeDocs("doctor_profiles"))[0] as { $id: string; userId: string };
const linkDoc = (doctorId: string, doctorUserId: string, patientUserId: string, name: string) => ({
  doctorId,
  doctorUserId,
  patientUserId,
  patientDisplayName: name,
  linkedAt: Date.now(),
  status: "ACTIVE",
  consentGrantedAt: Date.now(),
});

test.describe("who can see a patient's scans", () => {
  test("a genuinely paired patient's scans are shown, newest first, and the view is logged", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Priya Sharma");
    await patient.pushScan({ band: "LOW", label: "Eczema", daysAgo: 20 });
    await patient.pushScan({ band: "HIGH", label: "Melanoma", daysAgo: 1, confidence: 0.82 });

    await page.goto("/dashboard");
    await page.getByRole("link", { name: /Priya Sharma/ }).click();
    await expect(page.getByRole("heading", { name: "Priya Sharma", level: 1 })).toBeVisible();
    const scans = page.locator(".scan-item");
    await expect(scans).toHaveCount(2);
    await expect(scans.first()).toContainText("Melanoma");
    await expect(scans.first()).toContainText("High concern");
    await expect(scans.first()).toContainText("82%");

    const audit = await fakeDocs("audit_entries");
    expect(audit).toHaveLength(1);
    expect(audit[0]).toMatchObject({ action: "VIEWED_PATIENT", subjectUserId: patient.localId });
    const perms = audit[0].$permissions as string[];
    expect(perms.some((p) => p.includes(patient.sessionUserId)), "the patient can read the entry").toBe(true);
    expect(perms.filter((p) => !p.startsWith("read")), "nobody can edit or delete it").toEqual([]);
  });

  test("a consent link the doctor wrote about themselves grants nothing", async ({ page, context }) => {
    await verifiedDoctor(page);
    await createCode(page); // opens an invite window, so provenance alone would accept it
    const victim = await PatientApp.create("victim-local");
    await victim.pushScan({ band: "CRITICAL", label: "SECRET VICTIM FINDING" });

    // Use the doctor's own session, taken from their cookie, exactly as a malicious doctor could via the Appwrite API.
    const cookie = (await context.cookies()).find((c) => c.name === "dermoai_session")!;
    const asDoctor = new Databases(new Client().setEndpoint(FAKE).setProject(PROJECT).setSession(cookie.value));
    const { $id, userId } = await doctorProfile();
    await asDoctor.createDocument({
      databaseId: DB, collectionId: "patient_links", documentId: ID.unique(),
      data: linkDoc($id, userId, "victim-local", "Forged Victim"),
      permissions: [Permission.read(Role.users()), Permission.update(Role.user(userId)), Permission.delete(Role.user(userId))],
    });

    await page.goto("/dashboard");
    await expect(page.getByText("Forged Victim")).toHaveCount(0);
    await page.goto("/patients/victim-local");
    await expect(page.getByRole("heading", { name: "This patient isn't linked to you" })).toBeVisible();
    await expect(page.getByText("SECRET VICTIM FINDING")).toHaveCount(0);
    expect(await fakeDocs("audit_entries")).toHaveLength(0);
  });

  test("a third party's link that names a victim shows a row but never the victim's scans", async ({ page }) => {
    await verifiedDoctor(page);
    await createCode(page);
    const victim = await PatientApp.create("victim-local");
    await victim.pushScan({ band: "CRITICAL", label: "SECRET VICTIM FINDING" });
    const planter = await attacker();
    const { $id, userId } = await doctorProfile();
    await planter.db.createDocument({
      databaseId: DB, collectionId: "patient_links", documentId: ID.unique(),
      data: linkDoc($id, userId, "victim-local", "Attacker Chosen Name"),
      permissions: [Permission.read(Role.users()), Permission.update(Role.user(planter.id))],
    });

    // Any person holding a live code can pair, so a row can appear; what must never appear is the victim's data.
    await page.goto("/dashboard");
    await expect(page.getByText("SECRET VICTIM FINDING")).toHaveCount(0);
    await page.goto("/patients/victim-local");
    await expect(page.getByText("SECRET VICTIM FINDING")).toHaveCount(0);
    await expect(page.getByText("No scans have been shared yet.")).toBeVisible();
  });

  test("a link planted when none of the doctor's invites was open is ignored entirely", async ({ page }) => {
    await verifiedDoctor(page);
    const planter = await attacker();
    const { $id, userId } = await doctorProfile();
    await planter.db.createDocument({
      databaseId: DB, collectionId: "patient_links", documentId: ID.unique(),
      data: linkDoc($id, userId, "planted", "Planted Person"),
      permissions: [Permission.read(Role.users()), Permission.update(Role.user(planter.id))],
    });
    await page.goto("/dashboard");
    await expect(page.getByText("Planted Person")).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "No patients yet" })).toBeVisible();
  });

  test("summaries planted under a patient's id by someone else are never shown", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Real Patient");
    await patient.pushScan({ band: "LOW", label: "Genuine finding" });
    const planter = await attacker();
    const planted = ID.unique();
    await planter.db.createDocument({
      databaseId: DB, collectionId: "scan_summaries", documentId: planted,
      data: { patientUserId: patient.localId, scanId: planted, capturedAt: Date.now(), topLabel: "PLANTED FAKE FINDING", topLabelCode: "x", confidence: 1, concernBand: "CRITICAL", bodyArea: "x" },
      permissions: [Permission.read(Role.user(planter.id))],
    });
    await page.goto(`/patients/${patient.localId}`);
    await expect(page.getByText("Genuine finding")).toBeVisible();
    await expect(page.getByText("PLANTED FAKE FINDING")).toHaveCount(0);
  });

  test("an unlinked patient id shows a neutral page, discloses nothing, and logs nothing", async ({ page }) => {
    await verifiedDoctor(page);
    const stranger = await PatientApp.create("stranger-local");
    await stranger.pushScan({ band: "CRITICAL", label: "STRANGER FINDING" });
    await page.goto("/patients/stranger-local");
    await expect(page.getByRole("heading", { name: "This patient isn't linked to you" })).toBeVisible();
    await expect(page.getByText("STRANGER FINDING")).toHaveCount(0);
    expect(await fakeDocs("audit_entries")).toHaveLength(0);
  });

  test("a different doctor cannot open a patient who linked to someone else", async ({ page, browser, baseURL }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Doctor A's Patient");
    await patient.pushScan({ band: "HIGH", label: "A-ONLY FINDING" });

    const ctx = await browser.newContext({ baseURL });
    const other = await ctx.newPage();
    const second = await signUp(other, { name: "Dr. Other Person" });
    const { users } = await admin.users.list();
    await admin.users.updateLabels({ userId: users.find((u) => u.email === second.email)!.$id, labels: ["verifieddoctor"] });
    await other.goto(`/patients/${patient.localId}`);
    await expect(other.getByRole("heading", { name: "This patient isn't linked to you" })).toBeVisible();
    await expect(other.getByText("A-ONLY FINDING")).toHaveCount(0);
    await ctx.close();
  });
});

test.describe("revoking access", () => {
  test("ending access removes the patient from the list and the record, at once", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Revoke Me");
    await patient.pushScan({ band: "MEDIUM" });
    await page.goto(`/patients/${patient.localId}`);
    await page.getByText("End access to this patient").click();
    await page.getByRole("button", { name: "Yes, end access" }).click();
    await page.waitForURL("**/dashboard?revoked=1");
    await expect(page.getByText("Access ended")).toBeVisible();
    await expect(page.getByText("Revoke Me")).toHaveCount(0);
    await page.goto(`/patients/${patient.localId}`);
    await expect(page.getByRole("heading", { name: "This patient isn't linked to you" })).toBeVisible();
    expect((await fakeDocs("audit_entries")).map((a) => a.action)).toContain("REVOKED_LINK");
    // The patient's phone learns of it: the link document itself is flipped when the key can write.
    expect(((await fakeDocs("patient_links"))[0] as { status: string }).status).toBe("REVOKED");
  });

  test("a patient who pairs again after a revoke is shown again", async ({ page }) => {
    await verifiedDoctor(page);
    const code1 = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code1, "Boomerang");
    await patient.pushScan({ band: "LOW" });
    await page.goto(`/patients/${patient.localId}`);
    await page.getByText("End access to this patient").click();
    await page.getByRole("button", { name: "Yes, end access" }).click();
    await page.waitForURL("**/dashboard?revoked=1");
    await expect(page.getByText("Boomerang")).toHaveCount(0);

    await page.goto("/invites");
    await page.getByRole("button", { name: /Create pairing code/ }).click();
    const code2 = ((await page.locator(".code-display").textContent()) ?? "").replace(/[^A-Z0-9]/g, "");
    expect((await patient.redeem(code2, "Boomerang")).ok).toBe(true);
    await page.goto("/dashboard");
    await expect(page.getByText("Boomerang")).toBeVisible();
  });
});

test.describe("audit guarantee", () => {
  test("if the view cannot be logged anywhere, the record is not shown", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Audit Patient");
    await patient.pushScan({ band: "LOW", label: "SHOULD NOT SHOW" });
    await fetch(`${FAKE}/__fake/fail-creates`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ collection: "audit_entries" }) });
    await page.goto(`/patients/${patient.localId}`);
    await expect(page.getByRole("heading", { name: "We couldn't record your access" })).toBeVisible();
    await expect(page.getByText("SHOULD NOT SHOW")).toHaveCount(0);
    expect(await fakeDocs("audit_entries")).toHaveLength(0);
  });

  test("when only the doctor's own session can write, the view is still logged and shown", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Audit Patient");
    await patient.pushScan({ band: "LOW", label: "Visible finding" });
    await setKeyScopes("read-only"); // can read rows, cannot write them
    await page.goto(`/patients/${patient.localId}`);
    await expect(page.locator(".scan-item", { hasText: "Visible finding" })).toBeVisible();
    const audit = await fakeDocs("audit_entries");
    expect(audit).toHaveLength(1);
    expect((audit[0].$permissions as string[]).some((p) => p.includes(patient.sessionUserId)), "patient cannot read the fallback entry").toBe(false);
  });

  test("refreshing a record does not create duplicate audit entries", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    await patient.redeem(code, "Dedupe Patient");
    await patient.pushScan({ band: "LOW" });
    for (let i = 0; i < 4; i += 1) await page.goto(`/patients/${patient.localId}`);
    expect(await fakeDocs("audit_entries")).toHaveLength(1);
  });
});

test.describe("with a minimal key that has no rows scopes", () => {
  test("sign up, verification, pairing codes and QR all still work", async ({ page }) => {
    await verifiedDoctor(page, "no-rows");
    const code = await createCode(page);
    expect(code).toMatch(/^[A-Z2-9]{8}$/);
    await expect(page.locator(".qr-frame img")).toBeVisible();
    const patient = await PatientApp.create();
    expect((await patient.redeem(code, "Works Without Scopes")).ok).toBe(true);
    await expect(page.getByRole("heading", { name: /is now sharing with you/ })).toBeVisible({ timeout: 15_000 });
  });

  test("the dashboard explains the missing permission instead of failing", async ({ page }) => {
    await verifiedDoctor(page, "no-rows");
    const code = await createCode(page);
    await (await PatientApp.create()).redeem(code, "Someone");
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Patient data is not connected yet" })).toBeVisible();
    await expect(page.getByText(/rows\.read/).first()).toBeVisible();
    // The explanation is for the administrator and carries no internals.
    expect(await page.content()).not.toMatch(/general_unauthorized_scope|applications\)/);
  });
});

test.describe("the key stays server-side", () => {
  test("no response body or header contains the API key", async ({ page }) => {
    const seen: string[] = [];
    page.on("response", async (r) => {
      if (/text|json|javascript/.test(r.headers()["content-type"] ?? "")) seen.push(await r.text().catch(() => ""));
      seen.push(JSON.stringify(r.headers()));
    });
    await verifiedDoctor(page);
    await createCode(page);
    await page.goto("/dashboard");
    await page.goto("/profile");
    const blob = seen.join("\n");
    expect(blob).not.toContain("fake-key");
    expect(blob).not.toMatch(/x-appwrite-key/i);
  });

  test("health endpoint reports status and nothing else", async ({ request }) => {
    const res = await request.get("/api/health");
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ ok: true, configured: true, appwrite: "reachable" });
  });

  test("security headers are set", async ({ request }) => {
    const res = await request.get("/login");
    const h = res.headers();
    expect(h["x-frame-options"]).toBe("DENY");
    expect(h["referrer-policy"]).toBe("no-referrer");
    expect(h["x-content-type-options"]).toBe("nosniff");
    expect(h["content-security-policy"]).toContain("frame-ancestors 'none'");
    expect(h["x-powered-by"]).toBeUndefined();
  });
});
