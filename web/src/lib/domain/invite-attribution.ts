/**
 * Two jobs, both because the patient app never reports a redemption back:
 *
 * 1. **Usage.** Server-side `usedCount` would stay 0 forever, so a "single use"
 *    code could be redeemed by anyone who learns it until it expires. Each new
 *    link is attributed to the newest invite that could have produced it, and
 *    the count is written back, closing the window on the next refresh.
 * 2. **Provenance.** A doctor's account id is readable by any signed-in user, so
 *    anyone can write a link naming a doctor. A link only counts as a pairing
 *    if Appwrite stored it while one of that doctor's invites was open.
 *
 * Every comparison is between two timestamps Appwrite itself assigned
 * (`storedAt` on the link and on the invite), so they share one clock and need
 * no tolerance. A tolerance here is actively harmful: it lets a link stored a
 * moment before a new code is made get credited to that new code, which would
 * mark a fresh code as used before anyone has seen it.
 */
export type AttributableInvite = {
  id: string;
  /** Appwrite's server time when the invite was stored. */
  storedAt: number;
  /** Doctor-set expiry, epoch ms. The doctor's clock; only an upper bound on the window. */
  expiresAt: number;
  maxUses: number;
  usedCount: number;
  revoked: boolean;
};

export type AttributableLink = { id: string; storedAt: number };

/**
 * An invite is open for a link when the link was stored at or after the invite
 * and before it expired. A small allowance after expiry covers a patient who
 * redeemed in the last seconds and whose request finished after the deadline.
 */
export const EXPIRY_GRACE_MS = 2 * 60 * 1000;

const opensAt = (i: AttributableInvite, t: number) => t >= i.storedAt && t <= i.expiresAt + EXPIRY_GRACE_MS;

/**
 * Whether any invite this doctor ever issued (in any state) was open when the
 * link was stored. Stable over time: invites are never deleted, so a recognised
 * link stays recognised after the invite expires or is spent.
 */
export function isRecognisedLink(invites: readonly AttributableInvite[], link: AttributableLink): boolean {
  return invites.some((i) => opensAt(i, link.storedAt));
}

export type InviteUsageUpdate = { inviteId: string; usedCount: number; revoked: boolean };

export function attributeLinks(
  invites: readonly AttributableInvite[],
  links: readonly AttributableLink[],
): InviteUsageUpdate[] {
  // Manual revoke leaves usedCount below maxUses; a spent auto-revoke does not.
  // Manually cancelled invites were cancelled on purpose and take no attribution.
  const eligible = invites.filter((i) => !i.revoked || i.usedCount >= i.maxUses);
  const used = new Map(eligible.map((i) => [i.id, 0]));

  for (const link of [...links].sort((a, b) => a.storedAt - b.storedAt)) {
    const candidate = eligible
      .filter((i) => opensAt(i, link.storedAt) && (used.get(i.id) ?? 0) < i.maxUses)
      .sort((a, b) => b.storedAt - a.storedAt)[0];
    if (candidate) used.set(candidate.id, (used.get(candidate.id) ?? 0) + 1);
  }

  const updates: InviteUsageUpdate[] = [];
  for (const invite of eligible) {
    const usedCount = Math.max(invite.usedCount, used.get(invite.id) ?? 0);
    const revoked = invite.revoked || usedCount >= invite.maxUses;
    if (usedCount !== invite.usedCount || revoked !== invite.revoked) {
      updates.push({ inviteId: invite.id, usedCount, revoked });
    }
  }
  return updates;
}
