import { type AttributableInvite, EXPIRY_GRACE_MS } from "./invite-attribution";

/** How long a finished pairing is announced on the pairing page. */
export const JUST_PAIRED_WINDOW_MS = 30 * 60 * 1000;

/**
 * A single-use code is revoked the moment it is spent, which removes the "ready
 * to pair" panel the doctor was watching. Without this the page would snap to an
 * empty "create a code" form with no sign that the pairing worked.
 *
 * Returns the pairing to announce: only when the newest code is fully spent (so
 * a still-open or deliberately cancelled code announces nothing) and the link
 * was stored while it was open, and recently.
 */
export function findJustPaired<L extends { storedAt: number }>(
  invites: readonly AttributableInvite[],
  links: readonly L[],
  now: number,
  windowMs: number = JUST_PAIRED_WINDOW_MS,
): L | null {
  const newest = [...invites].sort((a, b) => b.storedAt - a.storedAt)[0];
  if (!newest || newest.usedCount < newest.maxUses) return null;
  const candidates = links
    .filter((l) => l.storedAt >= newest.storedAt && l.storedAt <= newest.expiresAt + EXPIRY_GRACE_MS && now - l.storedAt <= windowMs)
    .sort((a, b) => b.storedAt - a.storedAt);
  return candidates[0] ?? null;
}
