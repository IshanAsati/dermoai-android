import { createCode, decodeQr, expect, expectNoAxeViolations, expectNoHorizontalScroll, test, verifiedDoctor } from "./fixtures";
import { fakeDocs, PatientApp, setKeyScopes } from "./helpers";

test.beforeEach(async () => {
  await setKeyScopes("full");
});

test.describe("pairing codes and QR", () => {
  test("creates a code in the Android alphabet and shows the QR, code and expiry", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    expect(code).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/);
    await expect(page.getByText(/Expires in 6d/)).toBeVisible();
    await expect(page.getByText("0 of 1 used")).toBeVisible();
    await expect(page.getByText("Waiting for your patient to pair…")).toBeVisible();
    await expect(page.locator(".qr-frame img")).toHaveAttribute("alt", /QR code for pairing code/);
  });

  test("the QR, decoded from rendered pixels, is exactly what the Android scanner accepts", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const payload = await decodeQr(page);
    expect(payload).toBe(`dermoai://invite/${code}`);
    expect(payload!.startsWith("dermoai://invite/")).toBe(true);
  });

  test("the document written to Appwrite is the exact shape the Android app reads", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page, { ttl: "1h", uses: "3" });
    const profiles = await fakeDocs("doctor_profiles");
    const invites = await fakeDocs("doctor_invites");
    expect(profiles).toHaveLength(1);
    expect(invites).toHaveLength(1);
    const [inv] = invites as Array<Record<string, number | string | boolean | string[]>>;
    const [prof] = profiles as Array<Record<string, number | string | string[]>>;
    expect(inv).toMatchObject({ code, doctorId: prof.$id, doctorUserId: prof.userId, maxUses: 3, usedCount: 0, revoked: false });
    expect((inv.expiresAt as number) - (inv.createdAt as number)).toBe(3_600_000);
    // The patient's phone resolves the doctor by userId == invite.doctorUserId.
    expect(prof.userId).toBe(inv.doctorUserId);
    expect(prof).toMatchObject({ fullName: expect.any(String), verificationStatus: "UNVERIFIED", qualifications: "MBBS\nMD Dermatology", yearsExperience: 9 });
    // Readable by any signed-in session (so the patient can resolve it); only the owner can change it.
    const perms = inv.$permissions as string[];
    expect(perms).toContain('read("users")');
    expect(perms.filter((p) => p.startsWith("update") || p.startsWith("delete"))).toHaveLength(2);
  });

  test("a patient redeems the code like the Android app, and the doctor sees the pairing live", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    const patient = await PatientApp.create();
    const res = await patient.redeem(code, "Priya Sharma");
    expect(res.ok).toBe(true);
    // No manual reload: the page polls and shows the pairing.
    await expect(page.getByRole("heading", { name: "Priya Sharma is now sharing with you" })).toBeVisible({ timeout: 15_000 });
  });

  test("a single-use code stops working for the next patient", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page, { uses: "1" });
    const first = await PatientApp.create();
    expect((await first.redeem(code, "First Patient")).ok).toBe(true);
    await expect(page.getByRole("heading", { name: /is now sharing with you/ })).toBeVisible({ timeout: 15_000 });
    await expect.poll(async () => ((await fakeDocs("doctor_invites"))[0] as { revoked: boolean }).revoked, { timeout: 15_000 }).toBe(true);
    const second = await PatientApp.create();
    expect(await second.redeem(code, "Second Patient")).toEqual({ ok: false, reason: "revoked" });
  });

  test("a multi-use code stays open until its cap", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page, { uses: "3" });
    for (const name of ["A", "B"]) expect((await (await PatientApp.create()).redeem(code, name)).ok).toBe(true);
    await expect.poll(async () => ((await fakeDocs("doctor_invites"))[0] as { usedCount: number }).usedCount, { timeout: 15_000 }).toBe(2);
    expect(((await fakeDocs("doctor_invites"))[0] as { revoked: boolean }).revoked).toBe(false);
    expect((await (await PatientApp.create()).redeem(code, "C")).ok).toBe(true);
  });

  test("cancelling a code makes it unredeemable at once", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page);
    await page.getByRole("button", { name: "Cancel this code" }).click();
    await expect(page.getByRole("heading", { name: "Create a pairing code" })).toBeVisible();
    expect(await (await PatientApp.create()).redeem(code, "Late Patient")).toEqual({ ok: false, reason: "revoked" });
    await expect(page.getByText("Cancelled")).toBeVisible();
  });

  test("an expired code cannot be redeemed", async ({ page }) => {
    await verifiedDoctor(page);
    const code = await createCode(page, { ttl: "15m" });
    const later = Date.now() + 16 * 60_000;
    expect(await (await PatientApp.create()).redeem(code, "Slow Patient", later)).toEqual({ ok: false, reason: "expired" });
  });

  test("a doctor can create a second code and switch between them", async ({ page }) => {
    await verifiedDoctor(page);
    const first = await createCode(page);
    await page.getByRole("button", { name: "Create another code" }).click();
    await expect(page.locator(".invite-item")).toHaveCount(1);
    const shown = ((await page.locator(".code-display").textContent()) ?? "").replace(/[^A-Z0-9]/g, "");
    expect(shown).not.toBe("");
    const other = shown === first ? null : first;
    if (other) {
      await page.getByRole("link", { name: "Show" }).click();
      await expect(page.locator(".code-display")).toHaveText(/./);
      expect(((await page.locator(".code-display").textContent()) ?? "").replace(/[^A-Z0-9]/g, "")).toBe(first);
    }
  });

  test("rejects tampered form values for expiry and uses", async ({ page }) => {
    await verifiedDoctor(page);
    await page.goto("/invites");
    // Tampering with the DOM before React hydrates would be undone by hydration, so let the page settle first.
    await page.waitForLoadState("networkidle");
    await page.getByLabel("Code lasts").evaluate((el: HTMLSelectElement) => {
      el.insertAdjacentHTML("beforeend", '<option value="9999d">forever</option>');
      el.value = "9999d";
    });
    await page.getByRole("button", { name: /Create pairing code/ }).click();
    await expect(page.locator(".alert-error")).toContainText("Choose how long the code lasts");
    expect(await fakeDocs("doctor_invites")).toHaveLength(0);
  });

  test("the public /pair link shows the code but never looks anything up", async ({ page }) => {
    await page.goto("/pair/abcd-2345");
    await expect(page.getByRole("heading", { name: "Share your scans with your doctor" })).toBeVisible();
    await expect(page.locator(".code-display")).toHaveText("ABCD-2345");
    await expect(page.getByRole("link", { name: "Open in DermoAI" })).toHaveAttribute("href", "dermoai://invite/ABCD2345");
    await page.goto("/pair/bogus");
    await expect(page.getByRole("heading", { name: /doesn't look right/ })).toBeVisible();
  });

  test("pairing and pair-link pages are accessible and fit the screen", async ({ page }) => {
    await verifiedDoctor(page);
    await createCode(page);
    await expectNoAxeViolations(page, "/invites with a live code");
    await expectNoHorizontalScroll(page, "/invites");
    await page.goto("/pair/ABCD2345");
    await expectNoAxeViolations(page, "/pair");
    await expectNoHorizontalScroll(page, "/pair");
  });
});
