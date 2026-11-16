"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { createProfile, updateProfile } from "@/lib/data/repo";
import { requireSignedIn, sessionDb } from "@/lib/doctor-context";
import { type FieldErrors, validateProfile } from "@/lib/validation";

export type ProfileState = { error?: string; saved?: boolean; fieldErrors?: FieldErrors; values?: Record<string, string> } | null;

export async function saveProfileAction(_prev: ProfileState, form: FormData): Promise<ProfileState> {
  const ctx = await requireSignedIn();
  const parsed = validateProfile(form);
  if (!parsed.value) {
    const values: Record<string, string> = {};
    for (const [k, v] of form.entries()) if (typeof v === "string") values[k] = v;
    return { fieldErrors: parsed.errors, values };
  }
  const db = sessionDb(ctx.secret);
  try {
    if (ctx.state === "no-profile") {
      await createProfile(db, ctx.account.id, parsed.value, () => randomUUID().replace(/-/g, "").slice(0, 20));
    } else {
      await updateProfile(db, ctx.profile, parsed.value);
    }
  } catch (e) {
    console.error("[profile] save failed:", e instanceof Error ? e.name : "unknown");
    return { error: "We could not save your profile. Try again in a moment." };
  }
  revalidatePath("/", "layout");
  return { saved: true };
}
