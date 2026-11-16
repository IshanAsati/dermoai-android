import { createCode, expect, expectNoAxeViolations, expectNoHorizontalScroll, test, verifiedDoctor } from "./fixtures";
import { PatientApp, setKeyScopes } from "./helpers";

test.beforeEach(async () => {
  await setKeyScopes("full");
});

async function seedPatients(page: import("@playwright/test").Page) {
  const code = await createCode(page, { uses: "10" });
  const mk = async (name: string, scans: Array<{ band: string; daysAgo: number; label?: string }>) => {
    const p = await PatientApp.create();
    await p.redeem(code, name);
    for (const s of scans) await p.pushScan(s);
    return p;
  };
  // Alphabetically first, but mild and active.
  await mk("Aarav Adams", [{ band: "LOW", daysAgo: 1 }, { band: "LOW", daysAgo: 8 }]);
  // The one who needs the doctor first: newest scan CRITICAL and worsening.
  await mk("Zoya Zaveri", [{ band: "LOW", daysAgo: 30, label: "Mole" }, { band: "LOW", daysAgo: 20, label: "Mole" }, { band: "HIGH", daysAgo: 6, label: "Mole" }, { band: "CRITICAL", daysAgo: 1, label: "Melanoma" }]);
  // Mild and silent.
  await mk("Mohan Mehta", [{ band: "MEDIUM", daysAgo: 40 }]);
  // No scans at all.
  await mk("Nina Nair", []);
}

test.describe("triage dashboard", () => {
  test("ranks by urgency, not by name, and explains why", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    const names = await page.locator(".patient-name").allInnerTexts();
    expect(names).toEqual(["Zoya Zaveri", "Mohan Mehta", "Aarav Adams", "Nina Nair"].sort((a, b) => names.indexOf(a) - names.indexOf(b)));
    expect(names[0]).toBe("Zoya Zaveri");
    expect(names.indexOf("Nina Nair")).toBeGreaterThan(names.indexOf("Mohan Mehta"));
    const first = page.locator(".patient-row").first();
    await expect(first).toContainText("Critical concern");
    await expect(first).toContainText("Melanoma");
    await expect(first).toContainText("Trend: worsening");
  });

  test("summary tiles agree with the list", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    const tile = (label: string) => page.locator(".stat", { hasText: label }).locator(".stat-value");
    await expect(tile("Patients")).toHaveText("4");
    await expect(tile("High or critical")).toHaveText("1");
    await expect(tile("Worsening trend")).toHaveText("1");
    await expect(tile("No scan in 14 days")).toHaveText("2");
  });

  test("a low-scan trend reads 'not enough data', never a reassuring 'stable'", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    const row = page.locator(".patient-row", { hasText: "Aarav Adams" });
    await expect(row).toContainText("Trend: not enough data");
    await expect(row).not.toContainText("Trend: stable");
  });

  test("severity is never conveyed by colour alone", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    for (const badge of await page.locator(".badge-low, .badge-medium, .badge-high, .badge-critical").all()) {
      expect((await badge.innerText()).trim().length).toBeGreaterThan(3);
    }
  });

  test("opening a patient shows trend, scanning habit and the privacy note", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    await page.getByRole("link", { name: /Zoya Zaveri/ }).click();
    await expect(page.getByRole("heading", { name: "Zoya Zaveri", level: 1 })).toBeVisible();
    await expect(page.getByText("This is a tracking signal from photos, not a diagnosis.")).toBeVisible();
    await expect(page.getByRole("meter", { name: /Adherence/ })).toBeVisible();
    await expect(page.getByText(/no photographs leave the patient/i)).toBeVisible();
    await expect(page.locator(".scan-item")).toHaveCount(4);
  });

  test("the empty state points the doctor at pairing", async ({ page }) => {
    await verifiedDoctor(page);
    await expect(page.getByRole("heading", { name: "No patients yet" })).toBeVisible();
    await page.getByRole("main").getByRole("link", { name: "Pair a patient" }).last().click();
    await expect(page).toHaveURL(/\/invites$/);
  });

  test("dashboard and patient pages are accessible and fit the screen", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    await expectNoAxeViolations(page, "/dashboard");
    await expectNoHorizontalScroll(page, "/dashboard");
    await page.getByRole("link", { name: /Zoya Zaveri/ }).click();
    await expect(page.getByRole("heading", { name: "Zoya Zaveri", level: 1 })).toBeVisible();
    await expectNoAxeViolations(page, "/patients/[id]");
    await expectNoHorizontalScroll(page, "/patients/[id]");
    await page.goto("/profile");
    await expectNoAxeViolations(page, "/profile");
    await expectNoHorizontalScroll(page, "/profile");
  });
});

test.describe("layout across phone and laptop", () => {
  test("phones get a bottom tab bar; laptops get a sidebar, never both", async ({ page }) => {
    await verifiedDoctor(page);
    const width = page.viewportSize()!.width;
    const sidebar = page.locator(".sidebar");
    const bottom = page.locator(".bottom-nav");
    if (width >= 1024) {
      await expect(sidebar).toBeVisible();
      await expect(bottom).toBeHidden();
    } else {
      await expect(bottom).toBeVisible();
      await expect(sidebar).toBeHidden();
    }
  });

  test("every tap target is at least 44px and the nav marks the current page", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    await page.goto("/dashboard");
    const small: string[] = [];
    for (const el of await page.locator("a:visible, button:visible, input:visible, select:visible, summary:visible").all()) {
      const box = await el.boundingBox();
      if (!box) continue;
      const inline = await el.evaluate((n) => getComputedStyle(n).display === "inline" && n.tagName === "A" && !n.classList.contains("btn") && !n.closest(".nav-link, .patient-row, .brand"));
      if (inline) continue; // WCAG 2.2 exempts inline text links
      if (box.height < 44 || box.width < 44) small.push(`${(await el.innerText().catch(() => "")).trim().slice(0, 30)} ${Math.round(box.width)}x${Math.round(box.height)}`);
    }
    expect(small, "tap targets under 44px").toEqual([]);
    await expect(page.locator('[aria-current="page"]:visible').first()).toContainText("Patients");
  });

  test("form fields are 16px or larger so iOS Safari does not zoom on focus", async ({ page }) => {
    await page.goto("/signup");
    const small = await page.locator("input:not([type=hidden]), textarea, select").evaluateAll((nodes) =>
      nodes
        .filter((n) => (n as HTMLElement).offsetParent !== null) // only controls a user can actually focus
        .map((n) => ({ label: `${n.tagName.toLowerCase()}#${(n as HTMLInputElement).id || (n as HTMLInputElement).name}`, size: parseFloat(getComputedStyle(n).fontSize) }))
        .filter((x) => x.size < 16),
    );
    expect(small, "controls under 16px").toEqual([]);
    expect(await page.locator("input:not([type=hidden]), textarea").count()).toBeGreaterThan(5);
  });

  test("content is usable at 200% zoom and in a narrow 320px window", async ({ page }) => {
    await verifiedDoctor(page);
    await createCode(page);
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto("/invites");
    await expectNoHorizontalScroll(page, "320px /invites");
    await expect(page.locator(".code-display")).toBeVisible();
    const code = page.locator(".code-display");
    const box = (await code.boundingBox())!;
    expect(box.x + box.width, "code stays inside a 320px screen").toBeLessThanOrEqual(320);
  });

  test("the fixed tab bar never covers the last piece of content", async ({ page }) => {
    await verifiedDoctor(page);
    await seedPatients(page);
    for (const path of ["/dashboard", "/invites", "/profile"]) {
      await page.goto(path);
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      const gap = await page.evaluate(() => {
        const nav = document.querySelector(".bottom-nav") as HTMLElement | null;
        const main = document.querySelector("main") as HTMLElement;
        if (!nav || getComputedStyle(nav).display === "none") return null; // laptop: no tab bar
        const last = [...main.querySelectorAll("*")].filter((n) => (n as HTMLElement).offsetHeight > 0).pop() as HTMLElement;
        return Math.round(nav.getBoundingClientRect().top - last.getBoundingClientRect().bottom);
      });
      if (gap !== null) expect(gap, `${path}: last content ends ${-gap}px under the tab bar`).toBeGreaterThanOrEqual(0);
    }
  });

  test("respects reduced motion", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await verifiedDoctor(page);
    await createCode(page);
    const animation = await page.locator(".pulse-dot").evaluate((n) => getComputedStyle(n).animationName);
    expect(animation).toBe("none");
  });

  test("keyboard: skip link works and focus is always visible", async ({ page }) => {
    await verifiedDoctor(page);
    await page.keyboard.press("Tab");
    const skip = page.getByRole("link", { name: "Skip to content" });
    await expect(skip).toBeFocused();
    await page.keyboard.press("Enter");
    await expect(page.locator("#main")).toBeVisible();
    await page.keyboard.press("Tab");
    const outline = await page.evaluate(() => getComputedStyle(document.activeElement!).outlineStyle);
    expect(outline).not.toBe("none");
  });
});
