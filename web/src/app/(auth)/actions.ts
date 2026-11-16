"use server";

import { randomUUID } from "node:crypto";
import { redirect } from "next/navigation";
import { AppwriteException, ID } from "node-appwrite";
import { createAdminClient, createSessionClient } from "@/lib/appwrite/clients";
import { clearSessionSecret, readSessionSecret, writeSessionSecret } from "@/lib/appwrite/session-cookie";
import { ConfigError } from "@/lib/env";
import { sessionDb } from "@/lib/doctor-context";
import { createProfile } from "@/lib/data/repo";
import { clean, type FieldErrors, validateEmail, validatePassword, validateProfile } from "@/lib/validation";

export type FormState = { error?: string; fieldErrors?: FieldErrors; values?: Record<string, string> } | null;

/** Echo non-secret fields back so a failed submit does not wipe a long form on a phone. */
function echo(form: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of form.entries()) if (typeof v === "string" && k !== "password") out[k] = v;
  return out;
}

function describe(e: unknown): string {
  if (e instanceof ConfigError) {
    console.error("[auth] configuration:", e.message);
    return "This dashboard is not configured yet. Ask the administrator to set the server environment variables.";
  }
  if (e instanceof AppwriteException) {
    if (e.code === 429) return "Too many attempts. Wait a minute and try again.";
    console.error("[auth] appwrite:", e.code, e.type);
    return "We could not reach the sign-in service. Try again in a moment.";
  }
  console.error("[auth] unexpected:", e instanceof Error ? e.name : "unknown");
  return "Something went wrong. Try again in a moment.";
}

export async function signIn(_prev: FormState, form: FormData): Promise<FormState> {
  const email = clean(form.get("email")).toLowerCase();
  const password = typeof form.get("password") === "string" ? (form.get("password") as string) : "";
  const emailError = validateEmail(email);
  if (emailError) return { fieldErrors: { email: emailError }, values: { email } };
  if (!password) return { fieldErrors: { password: "Enter your password." }, values: { email } };

  try {
    const { account } = createAdminClient();
    const session = await account.createEmailPasswordSession({ email, password });
    await writeSessionSecret(session.secret);
  } catch (e) {
    if (e instanceof AppwriteException && (e.code === 401 || e.code === 400)) {
      // One message for "no such user" and "wrong password": do not reveal which emails exist.
      return { error: "Incorrect email or password.", values: { email } };
    }
    return { error: describe(e), values: { email } };
  }
  redirect("/dashboard");
}

export async function signUp(_prev: FormState, form: FormData): Promise<FormState> {
  const email = clean(form.get("email")).toLowerCase();
  const password = typeof form.get("password") === "string" ? (form.get("password") as string) : "";
  const profile = validateProfile(form);
  const fieldErrors: FieldErrors = { ...profile.errors };
  const emailError = validateEmail(email);
  const passwordError = validatePassword(password);
  if (emailError) fieldErrors.email = emailError;
  if (passwordError) fieldErrors.password = passwordError;
  if (Object.keys(fieldErrors).length > 0 || !profile.value) {
    return { fieldErrors, values: echo(form) };
  }

  let secret: string;
  let userId: string;
  try {
    const { account } = createAdminClient();
    const created = await account.create({ userId: ID.unique(), email, password, name: profile.value.fullName });
    userId = created.$id;
    const session = await account.createEmailPasswordSession({ email, password });
    secret = session.secret;
  } catch (e) {
    if (e instanceof AppwriteException && e.code === 409) {
      return { fieldErrors: { email: "An account with this email already exists. Sign in instead." }, values: echo(form) };
    }
    if (e instanceof AppwriteException && e.code === 400) {
      return { fieldErrors: { password: "That password was rejected. Choose a longer, less common one." }, values: echo(form) };
    }
    return { error: describe(e), values: echo(form) };
  }

  await writeSessionSecret(secret);
  try {
    await createProfile(sessionDb(secret), userId, profile.value, () => randomUUID().replace(/-/g, "").slice(0, 20));
  } catch (e) {
    // The account exists and they are signed in; the profile page lets them finish.
    console.error("[signup] profile creation failed:", e instanceof Error ? e.name : "unknown");
    redirect("/profile?setup=1");
  }
  redirect("/dashboard");
}

export async function signOut(): Promise<void> {
  const secret = await readSessionSecret();
  if (secret) {
    try {
      await createSessionClient(secret).account.deleteSession({ sessionId: "current" });
    } catch {
      // Already expired or revoked: nothing left to delete, and we clear the cookie regardless.
    }
  }
  await clearSessionSecret();
  redirect("/login");
}
