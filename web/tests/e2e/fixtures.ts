import AxeBuilder from "@axe-core/playwright";
import { expect, test as base, type Page } from "@playwright/test";
import jsQR from "jsqr";
import { PNG } from "pngjs";
import { admin, FAKE, type KeyMode, setKeyScopes, unique, verifyDoctor } from "./helpers";

export type Doctor = { email: string; password: string; name: string };

type Fixtures = {
  /** Fails the test on any uncaught page error or console.error. */
  guard: void;
  /** Every test starts from an empty backend with the same key scopes as production. */
  freshBackend: void;
};

export const test = base.extend<Fixtures>({
  freshBackend: [
    async ({}, use) => {
      await fetch(`${FAKE}/__fake/reset`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
      await setKeyScopes("full");
      await use();
    },
    { auto: true },
  ],
  guard: [
    async ({ page }, use) => {
      const problems: string[] = [];
      page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
      page.on("console", (m) => {
        if (m.type() !== "error") return;
        const text = m.text();
        // Failed sign-in probes legitimately produce a 401 resource line in the console.
        if (/Failed to load resource: the server responded with a status of (401|404)/.test(text)) return;
        problems.push(`console.error: ${text}`);
      });
      await use();
      expect(problems, "browser console/page errors").toEqual([]);
    },
    { auto: true },
  ],
});
export { expect };

export async function signUp(page: Page, over: Partial<Doctor> = {}): Promise<Doctor> {
  const id = unique("dr");
  const doctor: Doctor = { email: `${id}@example.com`, password: "Sup3r-secret-pass", name: over.name ?? `Dr. Asha ${id}`, ...over };
  await page.goto("/signup");
  await page.getByLabel("Full name").fill(doctor.name);
  await page.getByLabel("Email").fill(doctor.email);
  await page.getByLabel("Password").fill(doctor.password);
  await page.getByLabel("Medical registration number").fill("MH-12345");
  await page.getByLabel("Specialty").fill("Dermatology");
  await page.getByLabel("Clinic or hospital").fill("City Skin Clinic");
  await page.getByLabel("Years of experience").fill("9");
  await page.getByLabel("Qualifications").fill("MBBS, MD Dermatology");
  await page.getByRole("button", { name: "Create account" }).click();
  await page.waitForURL("**/dashboard");
  return doctor;
}

export async function signIn(page: Page, d: Doctor) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(d.email);
  await page.getByLabel("Password").fill(d.password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

/** A verified doctor, signed in, with the deployed key's scopes unless told otherwise. */
export async function verifiedDoctor(page: Page, scopes: KeyMode = "full"): Promise<Doctor> {
  await setKeyScopes(scopes);
  const d = await signUp(page);
  await verifyDoctor(d.email);
  await page.goto("/dashboard");
  return d;
}

export async function createCode(page: Page, opts: { ttl?: string; uses?: string } = {}): Promise<string> {
  await page.goto("/invites");
  if (opts.ttl) await page.getByLabel("Code lasts").selectOption(opts.ttl);
  if (opts.uses) await page.getByLabel("Patients who can use it").selectOption(opts.uses);
  await page.getByRole("button", { name: /Create pairing code/ }).click();
  const display = page.locator(".code-display");
  await expect(display).toBeVisible();
  return ((await display.textContent()) ?? "").replace(/[^A-Z0-9]/g, "");
}

/** Decodes the QR the way a phone camera would: from rendered pixels, not from the source string. */
export async function decodeQr(page: Page): Promise<string | null> {
  const img = page.locator(".qr-frame img");
  await expect(img).toBeVisible();
  const png = PNG.sync.read(await img.screenshot());
  const result = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
  return result?.data ?? null;
}

export async function expectNoAxeViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "best-practice"]).analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(" ")).slice(0, 3).join(" | ")}`);
  expect(summary, `axe violations on ${label}`).toEqual([]);
}

export async function expectNoHorizontalScroll(page: Page, label: string) {
  const overflow = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, inner: window.innerWidth }));
  expect(overflow.scroll, `${label}: page is ${overflow.scroll}px wide in a ${overflow.inner}px viewport`).toBeLessThanOrEqual(overflow.inner);
}

export { admin };
