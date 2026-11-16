"use server";

import { randomInt, randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createInvite, listInvites, revokeInvite } from "@/lib/data/repo";
import { requireVerified, sessionDb } from "@/lib/doctor-context";
import { inviteState } from "@/lib/data/schema";
import { MAX_ACTIVE_INVITES, TTL_OPTIONS, USE_OPTIONS } from "@/lib/invite-options";

export type InviteFormState = { error?: string } | null;

export async function createInviteAction(_prev: InviteFormState, form: FormData): Promise<InviteFormState> {
  const doctor = await requireVerified();
  const ttl = TTL_OPTIONS.find((o) => o.value === form.get("ttl"));
  const maxUses = Number(form.get("maxUses"));
  if (!ttl || !(USE_OPTIONS as readonly number[]).includes(maxUses)) {
    return { error: "Choose how long the code lasts and how many patients can use it." };
  }

  const db = sessionDb(doctor.secret);
  const now = Date.now();
  try {
    const active = (await listInvites(db, doctor.account.id)).filter((i) => inviteState(i, now) === "ACTIVE");
    if (active.length >= MAX_ACTIVE_INVITES) {
      return { error: `You already have ${MAX_ACTIVE_INVITES} active codes. Cancel some before making another.` };
    }
    await createInvite(db, doctor.profile, {
      ttlMs: ttl.ms,
      maxUses,
      now,
      randomInt,
      newId: () => randomUUID().replace(/-/g, "").slice(0, 20),
    });
  } catch (e) {
    console.error("[invites] create failed:", e instanceof Error ? e.name : "unknown");
    return { error: "We could not create a code just now. Try again in a moment." };
  }
  revalidatePath("/invites");
  redirect("/invites");
}

export async function revokeInviteAction(form: FormData): Promise<void> {
  const doctor = await requireVerified();
  const id = form.get("inviteId");
  if (typeof id === "string" && /^[a-zA-Z0-9._-]{1,36}$/.test(id)) {
    try {
      await revokeInvite(sessionDb(doctor.secret), doctor.account.id, id);
    } catch (e) {
      console.error("[invites] revoke failed:", e instanceof Error ? e.name : "unknown");
    }
  }
  revalidatePath("/invites");
  redirect("/invites");
}
