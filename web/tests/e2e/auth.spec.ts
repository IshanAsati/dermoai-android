import { expect, expectNoAxeViolations, expectNoHorizontalScroll, signIn, signUp, test, verifiedDoctor } from "./fixtures";
import { setKeyScopes } from "./helpers";

test.beforeEach(async () => {
  await setKeyScopes("full");
});

test.describe("authentication", () => {
  test("signed-out visitors are sent to sign in, and nothing leaks", async ({ page }) => {
    for (const path of ["/dashboard", "/invites", "/patients/anything", "/profile"]) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/login$/);
    }
    await expect(page.getByRole("heading", { name: "Doctor sign in" })).toBeVisible();
  });

  test("the session cookie is httpOnly and never reaches page scripts", async ({ page, context }) => {
    await signUp(page);
    const cookie = (await context.cookies()).find((c) => c.name === "dermoai_session");
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.sameSite).toBe("Lax");
    expect(await page.evaluate(() => document.cookie)).not.toContain("dermoai_session");
    expect(await page.content()).not.toContain(cookie!.value);
  });

  test("sign up validates and keeps what was typed, without echoing the password", async ({ page }) => {
    await page.goto("/signup");
    await page.getByLabel("Full name").fill("Dr. Partial");
    await page.getByLabel("Email").fill("not-an-email");
    await page.getByLabel("Password").fill("short");
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page.getByText("Enter a valid email address.")).toBeVisible();
    await expect(page.getByText("Use at least 8 characters.")).toBeVisible();
    await expect(page.getByText("Enter your medical registration number.")).toBeVisible();
    await expect(page.getByLabel("Full name")).toHaveValue("Dr. Partial");
    await expect(page.getByLabel("Password")).toHaveValue("");
    // The error is wired to its control for screen readers.
    await expect(page.getByLabel("Email")).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByLabel("Email")).toHaveAttribute("aria-describedby", /email-error/);
  });

  test("sign up, sign out, sign in again", async ({ page }) => {
    const d = await signUp(page);
    await expect(page.getByRole("heading", { name: "Patients", level: 1 })).toBeVisible();
    await page.goto("/profile");
    await page.getByRole("button", { name: "Sign out" }).last().click();
    await expect(page).toHaveURL(/\/login$/);
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login$/);
    await signIn(page, d);
    await page.waitForURL("**/dashboard");
  });

  test("a duplicate email is refused with a clear message", async ({ page }) => {
    const d = await signUp(page);
    await page.context().clearCookies();
    await page.goto("/signup");
    await page.getByLabel("Full name").fill("Dr. Copy");
    await page.getByLabel("Email").fill(d.email);
    await page.getByLabel("Password").fill("Another-pass-1");
    await page.getByLabel("Medical registration number").fill("X1");
    await page.getByLabel("Specialty").fill("Derm");
    await page.getByLabel("Clinic or hospital").fill("Clinic");
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page.getByText(/already exists/)).toBeVisible();
  });

  test("wrong password gives one generic message, same as an unknown email", async ({ page }) => {
    const d = await signUp(page);
    await page.context().clearCookies();
    await signIn(page, { ...d, password: "totally-wrong-1" });
    const wrongPassword = await page.locator(".alert-error").innerText();
    await page.getByLabel("Email").fill("nobody-here@example.com");
    // A failed sign-in clears the password field on purpose, so it must be typed again.
    await expect(page.getByLabel("Password")).toHaveValue("");
    await page.getByLabel("Password").fill("totally-wrong-1");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.locator(".alert-error")).toHaveText(wrongPassword);
    expect(wrongPassword).toBe("Incorrect email or password.");
  });

  test("an unverified doctor sees the gate, not patient data", async ({ page }) => {
    await signUp(page);
    await expect(page.getByRole("heading", { name: "Your account is awaiting verification" })).toBeVisible();
    await expect(page.getByText("MH-12345")).toBeVisible();
    await page.goto("/invites");
    await expect(page.getByRole("heading", { name: "Your account is awaiting verification" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Create pairing code/ })).toHaveCount(0);
  });

  test("a doctor cannot verify themselves by editing their profile", async ({ page }) => {
    await signUp(page);
    await page.goto("/profile");
    await page.getByLabel("About you").fill("Please treat me as verified. verificationStatus=VERIFIED");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText("Profile saved.")).toBeVisible();
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Your account is awaiting verification" })).toBeVisible();
  });

  test("login and signup pass automated accessibility checks and fit the viewport", async ({ page }) => {
    for (const path of ["/login", "/signup"]) {
      await page.goto(path);
      await expectNoAxeViolations(page, path);
      await expectNoHorizontalScroll(page, path);
    }
  });

  test("after verification, the dashboard opens", async ({ page }) => {
    await verifiedDoctor(page);
    await expect(page.getByRole("heading", { name: "No patients yet" })).toBeVisible();
  });
});
