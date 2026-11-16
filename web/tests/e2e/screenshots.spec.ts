import { mkdirSync } from "node:fs";
import { createCode, expect, signUp, test } from "./fixtures";
import { PatientApp, verifyDoctor } from "./helpers";

/**
 * Visual review aid, not a regression test: it only runs when SHOTS=1 and writes
 * full-page screenshots for a human (or reviewer) to look at.
 *   SHOTS=1 npx playwright test screenshots
 */
test.skip(!process.env.SHOTS, "set SHOTS=1 to capture review screenshots");

test("capture every key screen", async ({ page }, info) => {
  const dir = `tests/e2e/.results/shots/${info.project.name}`;
  mkdirSync(dir, { recursive: true });
  const shot = async (name: string) => page.screenshot({ path: `${dir}/${name}.png`, fullPage: true });

  await page.goto("/login");
  await shot("01-login");
  await page.goto("/signup");
  await shot("02-signup");

  const doctor = await signUp(page, { name: "Dr. Asha Verma" });
  await shot("03-unverified-gate");
  await page.goto("/profile");
  await shot("04-profile");

  await verifyDoctor(doctor.email);
  await page.goto("/dashboard");
  await shot("05-dashboard-empty");

  await page.goto("/invites");
  await shot("06-invites-create");
  const code = await createCode(page, { uses: "10" });
  await shot("07-invites-live-code");

  const mk = async (name: string, scans: Array<{ band: string; daysAgo: number; label?: string; area?: string; confidence?: number }>) => {
    const p = await PatientApp.create();
    await p.redeem(code, name);
    for (const s of scans) await p.pushScan(s);
    return p;
  };
  await mk("Aarav Adams", [{ band: "LOW", daysAgo: 1 }, { band: "LOW", daysAgo: 8 }]);
  const zoya = await mk("Zoya Zaveri", [
    { band: "LOW", daysAgo: 30, label: "Benign nevus", area: "Upper back", confidence: 0.91 },
    { band: "LOW", daysAgo: 20, label: "Benign nevus", area: "Upper back", confidence: 0.88 },
    { band: "HIGH", daysAgo: 6, label: "Atypical nevus", area: "Upper back", confidence: 0.74 },
    { band: "CRITICAL", daysAgo: 1, label: "Melanoma", area: "Upper back", confidence: 0.86 },
  ]);
  await mk("Mohan Mehta With A Really Long Display Name To Test Wrapping", [{ band: "MEDIUM", daysAgo: 40, label: "Seborrheic keratosis", area: "Left temple" }]);
  await mk("Nina Nair", []);

  await page.goto("/dashboard");
  await expect(page.locator(".patient-row").first()).toBeVisible();
  await shot("08-dashboard-patients");
  await page.goto(`/patients/${zoya.localId}`);
  await expect(page.locator(".scan-item").first()).toBeVisible();
  await shot("09-patient-detail");
  await page.getByText("End access to this patient").click();
  await shot("10-patient-revoke-open");

  await page.goto("/pair/ABCD2345");
  await shot("11-pair-landing");
  await page.goto("/pair/zzz");
  await shot("12-pair-invalid");
  await page.goto("/does-not-exist");
  await shot("13-not-found");
});
