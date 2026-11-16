import "server-only";
import { DbError } from "@/lib/data/db";
import {
  activeLinks,
  authoriseLink,
  genuineLinks,
  listInvites,
  listLinks,
  listRevocations,
  listSummariesForLink,
  reconcileInvites,
} from "@/lib/data/repo";
import type { Invite, PatientLink, ScanSummary } from "@/lib/data/schema";
import { computeAdherence, DEFAULT_CADENCE_DAYS, type Adherence } from "@/lib/domain/adherence";
import { parseSeverity, severityOrdinal, type Severity } from "@/lib/domain/severity";
import { computeTrend, type SeveritySample, type Trend } from "@/lib/domain/trend";
import { rankTriage, summariseTriage, type TriageRow, type TriageSummary } from "@/lib/domain/triage";
import { privilegedDb, sessionDb, type VerifiedDoctor } from "@/lib/doctor-context";

/** Why a screen could not load, in terms the page can render without leaking internals. */
export type LoadProblem = "missing-scope" | "unavailable";

export function problemFrom(error: unknown): LoadProblem {
  if (error instanceof DbError && error.isMissingScope) return "missing-scope";
  console.error("[loader]", error instanceof Error ? `${error.name}: ${error.message}` : "unknown error");
  return "unavailable";
}

export type Loaded<T> = { ok: true; data: T } | { ok: false; problem: LoadProblem };

/** Scan summaries to the numbers the dashboard draws. Shared by list and detail. */
export function describeScans(summaries: readonly ScanSummary[], now: number) {
  const newest = summaries[0] ?? null; // already newest-first
  const samples: SeveritySample[] = [];
  for (const s of summaries) {
    const severity = parseSeverity(s.concernBand);
    if (severity) samples.push({ timestamp: s.capturedAt, severityOrdinal: severityOrdinal(severity), confidence: s.confidence });
  }
  const adherence: Adherence = computeAdherence(summaries.map((s) => s.capturedAt), DEFAULT_CADENCE_DAYS, now);
  const trend: Trend = computeTrend(samples);
  const latestSeverity: Severity | null = newest ? parseSeverity(newest.concernBand) : null;
  return {
    latestSeverity,
    latestFinding: newest?.topLabel || null,
    latestConfidence: newest ? newest.confidence : null,
    lastScanAt: newest ? newest.capturedAt : null,
    adherence,
    trend,
  };
}

export async function loadTriage(
  doctor: VerifiedDoctor,
  now: number,
): Promise<Loaded<{ rows: TriageRow[]; summary: TriageSummary }>> {
  try {
    const sdb = sessionDb(doctor.secret);
    const [links, invites, revocations] = await Promise.all([listLinks(sdb, doctor.account.id), listInvites(sdb, doctor.account.id), listRevocations(sdb, doctor.account.id)]);
    const active = activeLinks(links, invites, doctor.account.id, revocations);
    // One link per patient: if a patient re-linked, the newest wins.
    const byPatient = new Map<string, PatientLink>();
    for (const l of [...active].sort((a, b) => a.storedAt - b.storedAt)) byPatient.set(l.patientUserId, l);

    const admin = privilegedDb();
    const rows = await Promise.all(
      [...byPatient.values()].map(async (link): Promise<TriageRow> => {
        const summaries = await listSummariesForLink(admin, link);
        return {
          patientUserId: link.patientUserId,
          displayName: link.patientDisplayName || "Unnamed patient",
          linkId: link.id,
          linkedAt: link.linkedAt,
          ...describeScans(summaries, now),
        };
      }),
    );
    const ranked = rankTriage(rows);
    return { ok: true, data: { rows: ranked, summary: summariseTriage(ranked) } };
  } catch (e) {
    return { ok: false, problem: problemFrom(e) };
  }
}

export type PatientRecord = {
  link: PatientLink;
  scans: ScanSummary[];
} & ReturnType<typeof describeScans>;

export type PatientLoad =
  | { ok: true; data: PatientRecord }
  | { ok: false; problem: LoadProblem }
  | { ok: false; problem: "not-linked" };

export async function loadPatient(doctor: VerifiedDoctor, patientUserId: string, now: number): Promise<PatientLoad> {
  try {
    const sdb = sessionDb(doctor.secret);
    const [links, invites, revocations] = await Promise.all([listLinks(sdb, doctor.account.id), listInvites(sdb, doctor.account.id), listRevocations(sdb, doctor.account.id)]);
    const link = authoriseLink(links, invites, doctor.account.id, patientUserId, revocations);
    if (!link) return { ok: false, problem: "not-linked" };

    const admin = privilegedDb();
    const scans = await listSummariesForLink(admin, link);
    return { ok: true, data: { link, scans, ...describeScans(scans, now) } };
  } catch (e) {
    return { ok: false, problem: problemFrom(e) };
  }
}

export type InvitesView = {
  invites: Invite[];
  /** Pairings that currently grant access, newest first. */
  paired: PatientLink[];
};

export async function loadInvites(doctor: VerifiedDoctor): Promise<Loaded<InvitesView>> {
  try {
    const sdb = sessionDb(doctor.secret);
    const [links, invites, revocations] = await Promise.all([listLinks(sdb, doctor.account.id), listInvites(sdb, doctor.account.id), listRevocations(sdb, doctor.account.id)]);
    const paired = activeLinks(links, invites, doctor.account.id, revocations).sort((a, b) => b.storedAt - a.storedAt);
    // Usage is counted from every genuine pairing, including ones since revoked.
    const reconciled = await reconcileInvites(sdb, invites, genuineLinks(links, invites, doctor.account.id));
    return { ok: true, data: { invites: reconciled, paired } };
  } catch (e) {
    return { ok: false, problem: problemFrom(e) };
  }
}
