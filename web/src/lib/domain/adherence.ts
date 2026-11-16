/**
 * Port of Android `ComputeAdherenceUseCase` + `PatientAdherence`. Pure: the
 * clock is a parameter so every boundary is testable.
 */
export const WINDOW_DAYS = 14;
export const GOOD_RATIO = 0.75;
export const DEFAULT_CADENCE_DAYS = 7;
const GRACE_DAYS = 1;
const MIN_EXPECTED = 1;
const DAY_MS = 24 * 60 * 60 * 1000;

export type AdherenceBand = "GOOD" | "SLIPPING" | "INACTIVE";

export type Adherence = {
  scansLast14Days: number;
  expectedCadenceDays: number;
  streakDays: number;
  lastScanAt: number | null;
  ratio: number;
  band: AdherenceBand;
};

function bandFor(scans: number, ratio: number): AdherenceBand {
  if (scans === 0) return "INACTIVE";
  return ratio >= GOOD_RATIO ? "GOOD" : "SLIPPING";
}

function none(cadence: number): Adherence {
  return {
    scansLast14Days: 0,
    expectedCadenceDays: cadence,
    streakDays: 0,
    lastScanAt: null,
    ratio: 0,
    band: "INACTIVE",
  };
}

export function computeAdherence(
  scanTimestamps: readonly number[],
  expectedCadenceDays: number,
  now: number,
): Adherence {
  const cadence = Number.isFinite(expectedCadenceDays) ? Math.max(1, Math.trunc(expectedCadenceDays)) : 1;
  if (scanTimestamps.length === 0) return none(cadence);

  // Future-dated scans are dropped: a skewed device clock must not hide a patient who stopped.
  const sorted = scanTimestamps.filter((t) => Number.isFinite(t) && t <= now).sort((a, b) => a - b);
  if (sorted.length === 0) return none(cadence);

  const windowStart = now - WINDOW_DAYS * DAY_MS;
  const inWindow = sorted.filter((t) => t >= windowStart).length;
  // Fractional on purpose: 14/5 = 2.8, and rounding to 3 would cap a compliant patient at 93%.
  const expectedInWindow = WINDOW_DAYS / cadence;
  const ratio = Math.min(1, Math.max(0, inWindow / Math.max(expectedInWindow, MIN_EXPECTED)));

  return {
    scansLast14Days: inWindow,
    expectedCadenceDays: cadence,
    streakDays: streakDays(sorted, cadence, now),
    lastScanAt: sorted[sorted.length - 1],
    ratio,
    band: bandFor(inWindow, ratio),
  };
}

/** Days spanned by the unbroken run of on-cadence scans that reaches the present. */
function streakDays(sortedAscending: readonly number[], cadence: number, now: number): number {
  const allowedGap = (cadence + GRACE_DAYS) * DAY_MS;
  const newest = sortedAscending[sortedAscending.length - 1];
  if (now - newest > allowedGap) return 0;

  let runStart = newest;
  for (let i = sortedAscending.length - 1; i >= 1; i -= 1) {
    if (sortedAscending[i] - sortedAscending[i - 1] > allowedGap) break;
    runStart = sortedAscending[i - 1];
  }
  // +1 so a single on-time scan reads as a 1-day streak, not 0 (which would look lapsed).
  return Math.trunc((newest - runStart) / DAY_MS) + 1;
}
