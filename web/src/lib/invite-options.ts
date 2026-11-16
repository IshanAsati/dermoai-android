const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * Whitelisted on the server: form values are untrusted input, not the policy.
 * (Kept out of the "use server" actions file, which may only export async functions.)
 */
export const TTL_OPTIONS = [
  { value: "15m", label: "15 minutes", ms: 15 * MINUTE },
  { value: "1h", label: "1 hour", ms: HOUR },
  { value: "1d", label: "1 day", ms: DAY },
  { value: "7d", label: "7 days", ms: 7 * DAY },
] as const;

export const USE_OPTIONS = [1, 3, 5, 10] as const;

export const DEFAULT_TTL = "7d"; // matches Android's DEFAULT_EXPIRY_DAYS
export const DEFAULT_USES = 1; // matches Android's DEFAULT_MAX_USES

/** Bounds how many live credentials one account can have outstanding. */
export const MAX_ACTIVE_INVITES = 20;
