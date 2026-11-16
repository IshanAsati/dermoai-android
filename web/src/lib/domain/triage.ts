import type { Adherence, AdherenceBand } from "./adherence";
import { type Severity, severityWeight } from "./severity";
import type { Trend } from "./trend";

/**
 * Port of Android `TriageRanking`. An inbox's job is "who needs me first", so
 * alphabetical is the wrong default: it buries a CRITICAL finding under whoever
 * is called Adams. Order, highest priority first:
 *
 *  1. Severity of the newest scan. The only signal derived from an image.
 *  2. Trend: WORSENING, then STABLE, then IMPROVING. An unconfident trend
 *     counts as STABLE, since ranking on two photos is ranking on noise.
 *  3. Adherence: INACTIVE, SLIPPING, GOOD. Last on purpose: going quiet is an
 *     engagement problem and must never outrank a real finding.
 *  4. Newest scan, then name, then id, so the list does not shuffle between loads.
 */
export type TriageRow = {
  patientUserId: string;
  displayName: string;
  linkId: string;
  linkedAt: number;
  latestSeverity: Severity | null;
  latestFinding: string | null;
  latestConfidence: number | null;
  lastScanAt: number | null;
  adherence: Adherence;
  trend: Trend;
};

const STABLE_WEIGHT = 1;

export function trendWeight(trend: Trend): number {
  if (!trend.isConfident) return STABLE_WEIGHT;
  switch (trend.direction) {
    case "WORSENING":
      return 2;
    case "STABLE":
      return STABLE_WEIGHT;
    case "IMPROVING":
      return 0;
  }
}

export function adherenceWeight(band: AdherenceBand): number {
  return band === "INACTIVE" ? 2 : band === "SLIPPING" ? 1 : 0;
}

export function compareTriage(a: TriageRow, b: TriageRow): number {
  return (
    severityWeight(b.latestSeverity) - severityWeight(a.latestSeverity) ||
    trendWeight(b.trend) - trendWeight(a.trend) ||
    adherenceWeight(b.adherence.band) - adherenceWeight(a.adherence.band) ||
    (b.lastScanAt ?? Number.NEGATIVE_INFINITY) - (a.lastScanAt ?? Number.NEGATIVE_INFINITY) ||
    a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()) ||
    a.patientUserId.localeCompare(b.patientUserId)
  );
}

export function rankTriage(rows: readonly TriageRow[]): TriageRow[] {
  // Number.NEGATIVE_INFINITY - NEGATIVE_INFINITY is NaN, which is falsy and falls through correctly.
  return [...rows].sort(compareTriage);
}

export type TriageSummary = {
  total: number;
  needsAttention: number;
  worsening: number;
  inactive: number;
};

export function summariseTriage(rows: readonly TriageRow[]): TriageSummary {
  return {
    total: rows.length,
    needsAttention: rows.filter((r) => r.latestSeverity === "HIGH" || r.latestSeverity === "CRITICAL").length,
    worsening: rows.filter((r) => r.trend.isConfident && r.trend.direction === "WORSENING").length,
    inactive: rows.filter((r) => r.adherence.band === "INACTIVE").length,
  };
}
