"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { authoriseLink, listInvites, listLinks, listRevocations, revokePatient } from "@/lib/data/repo";
import { privilegedDb, requireVerified, sessionDb } from "@/lib/doctor-context";

export type RevokeState = { error?: string } | null;

/**
 * Ends this doctor's access. Re-authorises from scratch rather than trusting the
 * form: a hidden field is attacker-controlled, so the patient id is only a
 * lookup key, and the link must still prove itself against the server's records.
 */
export async function revokeAccessAction(patientUserId: string): Promise<RevokeState> {
  const doctor = await requireVerified();
  const sdb = sessionDb(doctor.secret);
  try {
    const [links, invites, revocations] = await Promise.all([
      listLinks(sdb, doctor.account.id),
      listInvites(sdb, doctor.account.id),
      listRevocations(sdb, doctor.account.id),
    ]);
    const link = authoriseLink(links, invites, doctor.account.id, patientUserId, revocations);
    if (!link) return { error: "This patient is no longer linked to you." };
    await revokePatient(sdb, privilegedDb(), link, doctor.account.id, Date.now(), () =>
      randomUUID().replace(/-/g, "").slice(0, 20),
    );
  } catch (e) {
    console.error("[revoke] failed:", e instanceof Error ? e.name : "unknown");
    return { error: "We could not end access just now. Nothing was changed. Try again." };
  }
  revalidatePath("/dashboard");
  redirect("/dashboard?revoked=1");
}
