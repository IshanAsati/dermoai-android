import { describe, expect, it } from "vitest";
import { computeAdherence, WINDOW_DAYS } from "@/lib/domain/adherence";
import { computeTrend, MIN_SCANS_FOR_DIRECTION, TREND_DISCLAIMER, type SeveritySample, type Trend } from "@/lib/domain/trend";
import { compareTriage, rankTriage, summariseTriage, trendWeight, type TriageRow } from "@/lib/domain/triage";
import { parseSeverity, severityWeight } from "@/lib/domain/severity";

const DAY = 24 * 60 * 60 * 1000;
const NOW = Date.UTC(2026, 0, 15, 12);
const ago = (d: number) => NOW - d * DAY;

describe("adherence (port of ComputeAdherenceUseCaseTest)", () => {
  it("no scans is inactive with a zero ratio", () => {
    const r = computeAdherence([], 7, NOW);
    expect(r).toMatchObject({ scansLast14Days: 0, ratio: 0, streakDays: 0, band: "INACTIVE", lastScanAt: null });
  });

  it("zero and negative cadence are coerced, never divide by zero", () => {
    for (const cadence of [0, -3, Number.NaN]) {
      const r = computeAdherence([ago(1)], cadence, NOW);
      expect(r.expectedCadenceDays).toBe(1);
      expect(Number.isFinite(r.ratio)).toBe(true);
      expect(r.ratio).toBeGreaterThanOrEqual(0);
      expect(r.ratio).toBeLessThanOrEqual(1);
    }
  });

  it("counts only scans inside the 14-day window, boundary inclusive", () => {
    expect(computeAdherence([ago(1), ago(5), ago(20)], 7, NOW).scansLast14Days).toBe(2);
    expect(computeAdherence([ago(WINDOW_DAYS)], 7, NOW).scansLast14Days).toBe(1);
  });

  it("scans outside the window are history, not activity", () => {
    const r = computeAdherence([ago(20)], 7, NOW);
    expect(r.scansLast14Days).toBe(0);
    expect(r.band).toBe("INACTIVE");
    expect(r.lastScanAt).toBe(ago(20));
  });

  it("ignores future timestamps and treats only-future as no scans", () => {
    expect(computeAdherence([ago(1), NOW + DAY], 7, NOW)).toMatchObject({ scansLast14Days: 1, lastScanAt: ago(1) });
    expect(computeAdherence([NOW + DAY], 7, NOW).band).toBe("INACTIVE");
  });

  it("lastScanAt is the newest regardless of input order", () => {
    expect(computeAdherence([ago(9), ago(2), ago(5)], 7, NOW).lastScanAt).toBe(ago(2));
  });

  it("meeting a weekly cadence is GOOD, half is SLIPPING, over-scanning clamps to 1", () => {
    expect(computeAdherence([ago(1), ago(8)], 7, NOW)).toMatchObject({ ratio: 1, band: "GOOD" });
    expect(computeAdherence([ago(1)], 7, NOW)).toMatchObject({ ratio: 0.5, band: "SLIPPING" });
    expect(computeAdherence(Array.from({ length: 10 }, (_, i) => ago(i)), 7, NOW).ratio).toBe(1);
  });

  it("streak survives a 1-day grace and breaks beyond it", () => {
    // 7-day cadence + 1 grace = 8 days allowed between scans.
    expect(computeAdherence([ago(1), ago(9)], 7, NOW).streakDays).toBe(9);
    expect(computeAdherence([ago(1), ago(10)], 7, NOW).streakDays).toBe(1);
    expect(computeAdherence([ago(9)], 7, NOW).streakDays).toBe(0);
    expect(computeAdherence([ago(1)], 7, NOW).streakDays).toBe(1);
  });
});

const sample = (day: number, ordinal: number, confidence = 1): SeveritySample => ({
  timestamp: ago(30 - day),
  severityOrdinal: ordinal,
  confidence,
});

describe("trend (port of ComputeTrendUseCaseTest)", () => {
  it("under three scans is STABLE, not confident, and says so", () => {
    for (const n of [0, 1, 2]) {
      const t = computeTrend(Array.from({ length: n }, (_, i) => sample(i, i === 0 ? 0 : 3)));
      expect(t.direction).toBe("STABLE");
      expect(t.severityDelta).toBe(0);
      expect(t.isConfident).toBe(false);
      expect(t.explanation).toContain(TREND_DISCLAIMER);
      expect(t.explanation).toContain(`at least ${MIN_SCANS_FOR_DIRECTION}`);
    }
    expect(computeTrend([]).explanation.startsWith("No scans on record")).toBe(true);
    expect(computeTrend([sample(1, 1)]).explanation.startsWith("Only 1 scan on record")).toBe(true);
    expect(computeTrend([sample(1, 1), sample(2, 1)]).explanation.startsWith("Only 2 scans on record")).toBe(true);
  });

  it("three scans is the first confident basis", () => {
    const t = computeTrend([sample(1, 0), sample(2, 1), sample(3, 3)]);
    expect(t).toMatchObject({ basisScans: 3, isConfident: true, direction: "WORSENING" });
  });

  it("rising is WORSENING (+), falling is IMPROVING (-), flat is STABLE", () => {
    const up = computeTrend([sample(1, 0), sample(2, 0), sample(3, 2), sample(4, 3)]);
    expect(up.direction).toBe("WORSENING");
    expect(up.severityDelta).toBeGreaterThan(0);
    const down = computeTrend([sample(1, 3), sample(2, 3), sample(3, 1), sample(4, 0)]);
    expect(down.direction).toBe("IMPROVING");
    expect(down.severityDelta).toBeLessThan(0);
    const flat = computeTrend([sample(1, 2), sample(2, 2), sample(3, 2), sample(4, 2)]);
    expect(flat).toMatchObject({ direction: "STABLE", severityDelta: 0 });
  });

  it("sorts by timestamp, ignoring input order", () => {
    const ordered = [sample(1, 0), sample(2, 0), sample(3, 3), sample(4, 3)];
    expect(computeTrend([...ordered].reverse()).direction).toBe("WORSENING");
  });

  it("zero-confidence recent scans give no direction instead of NaN", () => {
    const t = computeTrend([sample(1, 0), sample(2, 0), sample(3, 3, 0), sample(4, 3, 0)]);
    expect(t.direction).toBe("STABLE");
    expect(Number.isNaN(t.severityDelta)).toBe(false);
    expect(t.explanation).toContain("low-confidence");
  });

  it("low-confidence scans carry less weight", () => {
    // Recent half: a CRITICAL at 5% confidence beside a LOW at 100% barely moves the mean.
    const t = computeTrend([sample(1, 0), sample(2, 0), sample(3, 0), sample(4, 3, 0.05)]);
    expect(t.direction).toBe("STABLE");
  });

  it("every explanation carries the disclaimer", () => {
    expect(computeTrend([sample(1, 0), sample(2, 0), sample(3, 3), sample(4, 3)]).explanation).toContain(TREND_DISCLAIMER);
  });
});

const row = (over: Partial<TriageRow> & { patientUserId: string }): TriageRow => ({
  displayName: over.patientUserId,
  linkId: "l-" + over.patientUserId,
  linkedAt: ago(30),
  latestSeverity: null,
  latestFinding: null,
  latestConfidence: null,
  lastScanAt: null,
  adherence: computeAdherence([], 7, NOW),
  trend: computeTrend([]),
  ...over,
});

const worsening = computeTrend([sample(1, 0), sample(2, 0), sample(3, 3), sample(4, 3)]);
const improving = computeTrend([sample(1, 3), sample(2, 3), sample(3, 0), sample(4, 0)]);
// computeTrend never yields this itself (under 3 scans it returns STABLE); built by hand to
// exercise the defensive guard in trendWeight, which Android has too.
const unconfidentWorse: Trend = { ...worsening, basisScans: 2, isConfident: false };

describe("triage ranking (port of TriageRanking)", () => {
  it("severity outranks everything, so CRITICAL beats an alphabetical 'Adams'", () => {
    const ranked = rankTriage([
      row({ patientUserId: "a", displayName: "Adams", latestSeverity: "LOW" }),
      row({ patientUserId: "z", displayName: "Zed", latestSeverity: "CRITICAL" }),
    ]);
    expect(ranked.map((r) => r.displayName)).toEqual(["Zed", "Adams"]);
  });

  it("no scans ranks below a mild finding", () => {
    expect(severityWeight(null)).toBeLessThan(severityWeight("LOW"));
  });

  it("at equal severity: WORSENING > STABLE > IMPROVING", () => {
    const ranked = rankTriage([
      row({ patientUserId: "i", latestSeverity: "HIGH", trend: improving }),
      row({ patientUserId: "w", latestSeverity: "HIGH", trend: worsening }),
      row({ patientUserId: "s", latestSeverity: "HIGH" }),
    ]);
    expect(ranked.map((r) => r.patientUserId)).toEqual(["w", "s", "i"]);
  });

  it("an unconfident WORSENING is weighted as STABLE, never as a real worsening", () => {
    expect(computeTrend([sample(1, 0), sample(2, 3)]).direction).toBe("STABLE");
    expect(trendWeight(unconfidentWorse)).toBe(trendWeight(computeTrend([])));
    expect(trendWeight(worsening)).toBeGreaterThan(trendWeight(unconfidentWorse));
    // Same severity: a confident worsening ranks above a fresher unconfident one.
    const confident = row({ patientUserId: "c", latestSeverity: "HIGH", trend: worsening, lastScanAt: ago(5) });
    const unconfident = row({ patientUserId: "u", latestSeverity: "HIGH", trend: unconfidentWorse, lastScanAt: ago(1) });
    expect(compareTriage(confident, unconfident)).toBeLessThan(0);
    // And the unconfident trend earns no boost over a plain stable patient: recency decides.
    const stableOlder = row({ patientUserId: "s", latestSeverity: "HIGH", lastScanAt: ago(9) });
    expect(compareTriage(unconfident, stableOlder)).toBeLessThan(0);
    const stableNewer = row({ patientUserId: "n", latestSeverity: "HIGH", lastScanAt: ago(0) });
    expect(compareTriage(stableNewer, unconfident)).toBeLessThan(0);
  });

  it("adherence breaks ties last: silent patient before on-track one", () => {
    const quiet = row({ patientUserId: "q", latestSeverity: "MEDIUM" });
    const active = row({
      patientUserId: "g",
      latestSeverity: "MEDIUM",
      adherence: computeAdherence([ago(1), ago(8)], 7, NOW),
    });
    expect(rankTriage([active, quiet]).map((r) => r.patientUserId)).toEqual(["q", "g"]);
  });

  it("is stable and total: same input in any order gives the same ranking", () => {
    const rows = [
      row({ patientUserId: "b", displayName: "Bea", latestSeverity: "LOW", lastScanAt: ago(2) }),
      row({ patientUserId: "a", displayName: "Abe", latestSeverity: "LOW", lastScanAt: ago(2) }),
      row({ patientUserId: "c", displayName: "Cy" }),
      row({ patientUserId: "d", displayName: "Di" }),
    ];
    const once = rankTriage(rows).map((r) => r.patientUserId);
    expect(rankTriage([...rows].reverse()).map((r) => r.patientUserId)).toEqual(once);
    expect(once).toEqual(["a", "b", "c", "d"]);
  });

  it("summary counts needs-attention, confident worsening, and inactive", () => {
    const s = summariseTriage([
      row({ patientUserId: "1", latestSeverity: "CRITICAL", trend: worsening }),
      row({ patientUserId: "2", latestSeverity: "HIGH", trend: unconfidentWorse }),
      row({ patientUserId: "3", latestSeverity: "LOW" }),
    ]);
    expect(s).toEqual({ total: 3, needsAttention: 2, worsening: 1, inactive: 3 });
  });
});

describe("severity parsing", () => {
  it("is case-insensitive and rejects unknown values", () => {
    expect(parseSeverity("high")).toBe("HIGH");
    expect(parseSeverity(" Critical ")).toBe("CRITICAL");
    expect(parseSeverity("moderate")).toBeNull();
    expect(parseSeverity(null)).toBeNull();
    expect(parseSeverity("")).toBeNull();
  });
});
