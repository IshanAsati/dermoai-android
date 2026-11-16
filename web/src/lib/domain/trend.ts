/**
 * Port of Android `ComputeTrendUseCase` + `PatientTrend`.
 *
 * Refuses to overclaim: under three scans it says STABLE and says why, movement
 * under half a severity band is STABLE, and every explanation carries the
 * disclaimer. This sits beside a patient's name on a clinician's screen and is
 * read as more authoritative than it is.
 */
export const MIN_SCANS_FOR_DIRECTION = 3;
export const MEANINGFUL_DELTA = 0.5;
export const TREND_DISCLAIMER = "This is a tracking signal from photos, not a diagnosis.";

export type TrendDirection = "IMPROVING" | "STABLE" | "WORSENING";

export type SeveritySample = {
  timestamp: number;
  /** 0 LOW .. 3 CRITICAL. */
  severityOrdinal: number;
  /** Model confidence 0..1; clamped. A 34% "carcinoma" is not a 96% one. */
  confidence: number;
};

export type Trend = {
  direction: TrendDirection;
  severityDelta: number;
  basisScans: number;
  explanation: string;
  isConfident: boolean;
};

export function computeTrend(samples: readonly SeveritySample[]): Trend {
  const sorted = [...samples].sort((a, b) => a.timestamp - b.timestamp);
  const n = sorted.length;

  if (n < MIN_SCANS_FOR_DIRECTION) {
    return result("STABLE", 0, n, notEnoughData(n));
  }

  // Odd count: the middle scan is excluded from both sides so the comparison stays symmetric.
  const half = Math.floor(n / 2);
  const earlierMean = weightedMean(sorted.slice(0, half));
  const recentMean = weightedMean(sorted.slice(n - half));

  if (earlierMean === null || recentMean === null) {
    return result("STABLE", 0, n, `Recent scans were too low-confidence to compare. ${TREND_DISCLAIMER}`);
  }

  const delta = recentMean - earlierMean;
  const direction: TrendDirection =
    Math.abs(delta) < MEANINGFUL_DELTA ? "STABLE" : delta > 0 ? "WORSENING" : "IMPROVING";
  return result(direction, delta, n, explain(direction, delta, half, n));
}

function result(direction: TrendDirection, delta: number, basis: number, explanation: string): Trend {
  return {
    direction,
    severityDelta: delta,
    basisScans: basis,
    explanation,
    isConfident: basis >= MIN_SCANS_FOR_DIRECTION,
  };
}

/** Null when every weight is zero, the one case where a division would produce NaN. */
function weightedMean(samples: readonly SeveritySample[]): number | null {
  let weighted = 0;
  let weight = 0;
  for (const s of samples) {
    const w = Math.min(1, Math.max(0, s.confidence));
    weighted += s.severityOrdinal * w;
    weight += w;
  }
  return weight <= 0 ? null : weighted / weight;
}

function notEnoughData(count: number): string {
  const have = count === 0 ? "No scans on record" : count === 1 ? "Only 1 scan on record" : `Only ${count} scans on record`;
  return `${have}; at least ${MIN_SCANS_FOR_DIRECTION} are needed before a direction is shown. ${TREND_DISCLAIMER}`;
}

function explain(direction: TrendDirection, delta: number, half: number, total: number): string {
  const magnitude = Math.abs(delta).toFixed(1);
  const window = `the latest ${half} of ${total} scans against the earliest ${half}`;
  const body =
    direction === "WORSENING"
      ? `Severity ratings rose by ${magnitude} comparing ${window}.`
      : direction === "IMPROVING"
        ? `Severity ratings fell by ${magnitude} comparing ${window}.`
        : `Severity ratings moved by ${magnitude} comparing ${window}, which is within normal variation between photos.`;
  return `${body} ${TREND_DISCLAIMER}`;
}
