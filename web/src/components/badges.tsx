import type { Adherence } from "@/lib/domain/adherence";
import { SEVERITY_LABEL, type Severity } from "@/lib/domain/severity";
import type { Trend } from "@/lib/domain/trend";

const SEVERITY_CLASS: Record<Severity, string> = {
  LOW: "badge-low",
  MEDIUM: "badge-medium",
  HIGH: "badge-high",
  CRITICAL: "badge-critical",
};

/** Severity is always spelled out; colour is never the only signal. */
export function SeverityBadge({ severity }: { severity: Severity | null }) {
  if (!severity) return <span className="badge badge-neutral">No scans yet</span>;
  return <span className={`badge ${SEVERITY_CLASS[severity]}`}>{SEVERITY_LABEL[severity]}</span>;
}

export function TrendBadge({ trend }: { trend: Trend }) {
  // An unconfident trend reads as "not enough data", never as a reassuring STABLE.
  if (!trend.isConfident) return <span className="badge badge-neutral">Trend: not enough data</span>;
  if (trend.direction === "WORSENING") return <span className="badge badge-worse">Trend: worsening ↑</span>;
  if (trend.direction === "IMPROVING") return <span className="badge badge-better">Trend: improving ↓</span>;
  return <span className="badge badge-pine">Trend: stable →</span>;
}

export function AdherenceBadge({ adherence }: { adherence: Adherence }) {
  if (adherence.band === "INACTIVE") return <span className="badge badge-medium">No scans in 14 days</span>;
  const n = adherence.scansLast14Days;
  const label = `${n} scan${n === 1 ? "" : "s"} in 14 days`;
  return <span className={`badge ${adherence.band === "GOOD" ? "badge-better" : "badge-neutral"}`}>{label}</span>;
}
