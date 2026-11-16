/**
 * Date formatting. Servers run in UTC and doctors do not, so every timestamp is
 * rendered in one explicit IANA zone (default Asia/Kolkata, see `APP_TIME_ZONE`).
 * Pure: the zone and clock are parameters.
 */
const DAY = 24 * 60 * 60 * 1000;
const HOUR = 60 * 60 * 1000;
const MINUTE = 60 * 1000;

export function formatDate(ms: number, timeZone: string): string {
  if (!Number.isFinite(ms) || ms <= 0) return "Unknown date";
  return new Intl.DateTimeFormat("en-IN", { timeZone, day: "numeric", month: "short", year: "numeric" }).format(ms);
}

export function formatDateTime(ms: number, timeZone: string): string {
  if (!Number.isFinite(ms) || ms <= 0) return "Unknown date";
  return new Intl.DateTimeFormat("en-IN", {
    timeZone,
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(ms);
}

/** "3 days ago", "today", "just now". Future times clamp to "just now" (device clocks drift). */
export function relativeTime(ms: number, now: number): string {
  if (!Number.isFinite(ms) || ms <= 0) return "never";
  const diff = Math.max(0, now - ms);
  if (diff < MINUTE) return "just now";
  if (diff < HOUR) return plural(Math.floor(diff / MINUTE), "minute") + " ago";
  if (diff < DAY) return plural(Math.floor(diff / HOUR), "hour") + " ago";
  const days = Math.floor(diff / DAY);
  if (days < 60) return plural(days, "day") + " ago";
  return plural(Math.floor(days / 30), "month") + " ago";
}

/** "6d 23h", "45m", "expired". */
export function timeRemaining(expiresAt: number, now: number): string {
  const left = expiresAt - now;
  if (left <= 0) return "expired";
  const d = Math.floor(left / DAY);
  const h = Math.floor((left % DAY) / HOUR);
  const m = Math.floor((left % HOUR) / MINUTE);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m`;
  return "under a minute";
}

function plural(n: number, unit: string): string {
  return `${n} ${unit}${n === 1 ? "" : "s"}`;
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0] ?? "";
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? "") : "";
  return (first + last).toUpperCase();
}
