import "server-only";
import { cache } from "react";
import { redirect } from "next/navigation";
import { AppwriteException } from "node-appwrite";
import { createAdminClient, createSessionClient } from "@/lib/appwrite/clients";
import { readSessionSecret } from "@/lib/appwrite/session-cookie";
import { appwriteDb } from "@/lib/data/appwrite-db";
import type { Db } from "@/lib/data/db";
import { getProfileByUserId } from "@/lib/data/repo";
import type { DoctorProfile } from "@/lib/data/schema";

/**
 * Verification authority is this account label, not the profile document.
 *
 * A doctor holds update rights on their own `doctor_profiles` document, so any
 * `verificationStatus` stored there can be self-edited straight through the
 * Appwrite API. Labels can only be set by the API key (see
 * `scripts/verify-doctor.mjs`), so a doctor cannot grant it to themselves.
 * Appwrite labels must be alphanumeric, hence no hyphen.
 */
export const VERIFIED_LABEL = "verifieddoctor";

export type Account = { id: string; name: string; email: string; labels: string[] };

export type DoctorContext =
  | { state: "signed-out" }
  | { state: "no-profile"; account: Account; secret: string }
  | { state: "unverified"; account: Account; profile: DoctorProfile; secret: string }
  | { state: "verified"; account: Account; profile: DoctorProfile; secret: string };

export type VerifiedDoctor = Extract<DoctorContext, { state: "verified" }>;

/** Memoised per request, so layout and page share one lookup. */
export const getDoctorContext = cache(async (): Promise<DoctorContext> => {
  const secret = await readSessionSecret();
  if (!secret) return { state: "signed-out" };

  const { account: accountApi, tablesDb, databaseId } = createSessionClient(secret);
  let account: Account;
  try {
    const me = await accountApi.get();
    account = { id: me.$id, name: me.name, email: me.email, labels: me.labels ?? [] };
  } catch (e) {
    // 401: expired or revoked session. Anything else is a real outage and must surface.
    if (e instanceof AppwriteException && (e.code === 401 || e.type === "user_unauthorized")) {
      return { state: "signed-out" };
    }
    throw e;
  }

  const profile = await getProfileByUserId(appwriteDb(tablesDb, databaseId), account.id);
  if (!profile) return { state: "no-profile", account, secret };
  if (!account.labels.includes(VERIFIED_LABEL)) return { state: "unverified", account, profile, secret };
  return { state: "verified", account, profile, secret };
});

export async function requireSignedIn(): Promise<Exclude<DoctorContext, { state: "signed-out" }>> {
  const ctx = await getDoctorContext();
  if (ctx.state === "signed-out") redirect("/login");
  return ctx;
}

/** For pages that only make sense with a profile (invites, dashboard). */
export async function requireProfile(): Promise<Exclude<DoctorContext, { state: "signed-out" | "no-profile" }>> {
  const ctx = await requireSignedIn();
  if (ctx.state === "no-profile") redirect("/profile");
  return ctx;
}

export async function requireVerified(): Promise<VerifiedDoctor> {
  const ctx = await requireProfile();
  if (ctx.state !== "verified") redirect("/dashboard");
  return ctx;
}

/** Acts as the doctor. Appwrite's document permissions decide what it can touch. */
export function sessionDb(secret: string): Db {
  const { tablesDb, databaseId } = createSessionClient(secret);
  return appwriteDb(tablesDb, databaseId);
}

/** Acts as the server. Only call after `authoriseLink` has passed. */
export function privilegedDb(): Db {
  const { tablesDb, databaseId } = createAdminClient();
  return appwriteDb(tablesDb, databaseId);
}
