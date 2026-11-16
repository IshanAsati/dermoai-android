/** Mirrors Android `ConditionSeverity`; the scan summary's `concernBand` holds one of these names. */
export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type Severity = (typeof SEVERITIES)[number];

export function parseSeverity(raw: string | null | undefined): Severity | null {
  if (!raw) return null;
  const upper = raw.trim().toUpperCase();
  return (SEVERITIES as readonly string[]).includes(upper) ? (upper as Severity) : null;
}

/** 0 LOW .. 3 CRITICAL. Trend maths runs on this ordinal. */
export function severityOrdinal(severity: Severity): number {
  return SEVERITIES.indexOf(severity);
}

/**
 * Triage weight. No scans is 0 rather than LOW's weight, so "no evidence"
 * ranks below "evidence of something mild" instead of tying with it.
 */
export function severityWeight(severity: Severity | null): number {
  return severity === null ? 0 : severityOrdinal(severity) + 1;
}

export const SEVERITY_LABEL: Record<Severity, string> = {
  LOW: "Low concern",
  MEDIUM: "Moderate concern",
  HIGH: "High concern",
  CRITICAL: "Critical concern",
};
