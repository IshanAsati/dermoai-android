import { randomUUID } from "node:crypto";
import type { Metadata } from "next";
import Link from "next/link";
import { SeverityBadge, TrendBadge } from "@/components/badges";
import { ProblemCard } from "@/components/problem-card";
import { auditWithFallback } from "@/lib/data/repo";
import { parseSeverity } from "@/lib/domain/severity";
import { requestTime } from "@/lib/clock";
import { privilegedDb, requireVerified, sessionDb } from "@/lib/doctor-context";
import { displayTimeZone } from "@/lib/env";
import { formatDate, formatDateTime, initials, relativeTime } from "@/lib/format";
import { loadPatient } from "@/lib/loaders";
import { RevokeForm } from "./revoke-form";

export const metadata: Metadata = { title: "Patient record" };

export default async function PatientPage({ params }: { params: Promise<{ id: string }> }) {
  const doctor = await requireVerified();
  const patientUserId = decodeURIComponent((await params).id);
  const now = requestTime();
  const zone = displayTimeZone();

  const result = await loadPatient(doctor, patientUserId, now);

  if (!result.ok && result.problem === "not-linked") {
    // Reads as a normal outcome: a stale link after the patient or doctor ended access.
    return (
      <div className="card empty">
        <h2>This patient isn&apos;t linked to you</h2>
        <p>Access may have ended, or the link was never completed. Patients appear in your list once they share with you.</p>
        <Link className="btn btn-primary" href="/dashboard">
          Back to patients
        </Link>
      </div>
    );
  }
  if (!result.ok) return <ProblemCard problem={result.problem} />;

  const { link, scans, adherence, trend, latestSeverity } = result.data;

  // The patient's guarantee is that a view is recorded, not that a view which
  // finished loading is. So the entry is written before any scan is drawn, and
  // if it cannot be written the record is not shown.
  try {
    await auditWithFallback(sessionDb(doctor.secret), privilegedDb(), {
      actorUserId: doctor.account.id,
      link,
      action: "VIEWED_PATIENT",
      detail: link.id,
      now,
      newId: () => randomUUID().replace(/-/g, "").slice(0, 20),
      dedupe: true,
    });
  } catch (e) {
    console.error("[patient] audit write failed:", e instanceof Error ? e.name : "unknown");
    return (
      <div className="card empty" role="alert">
        <h2>We couldn&apos;t record your access</h2>
        <p>Every view of a patient record is logged for the patient. Because that log could not be written, the record is not shown. Reload to try again.</p>
        <Link className="btn" href="/dashboard">
          Back to patients
        </Link>
      </div>
    );
  }

  const name = link.patientDisplayName || "Unnamed patient";
  const lastScan = scans[0];

  return (
    <>
      <p style={{ marginBottom: 12 }}>
        <Link href="/dashboard">&larr; All patients</Link>
      </p>
      <div className="page-head">
        <div className="row" style={{ gap: 14, flexWrap: "nowrap" }}>
          <span className="avatar" aria-hidden="true" style={{ width: 56, height: 56 }}>
            {initials(name)}
          </span>
          <div>
            <h1>{name}</h1>
            <p style={{ marginTop: 2 }}>Shared with you since {formatDate(link.storedAt, zone)}</p>
          </div>
        </div>
        <div className="row">
          <SeverityBadge severity={latestSeverity} />
          <TrendBadge trend={trend} />
        </div>
      </div>

      <div className="detail-grid">
        <section className="card" aria-labelledby="scans-heading">
          <h2 id="scans-heading" style={{ marginBottom: 14 }}>
            Scan history
          </h2>
          {scans.length === 0 ? (
            <div className="well">
              <p>No scans have been shared yet.</p>
              <p className="small muted" style={{ marginTop: 4 }}>
                Scans appear here as your patient takes them in the DermoAI app.
              </p>
            </div>
          ) : (
            <ul className="scan-list">
              {scans.map((s) => (
                <li key={s.id} className="scan-item">
                  <span>
                    <strong>{s.topLabel || "Unlabelled finding"}</strong>
                    <br />
                    <span className="small muted">
                      {s.bodyArea || "Area not recorded"} · {formatDateTime(s.capturedAt, zone)} · model confidence {Math.round(s.confidence * 100)}%
                    </span>
                  </span>
                  <SeverityBadge severity={parseSeverity(s.concernBand)} />
                </li>
              ))}
            </ul>
          )}
          <p className="disclaimer">
            Summaries only: no photographs leave the patient&apos;s phone. If you need to see the lesion, ask the patient to
            show you. Model output is educational and is not a diagnosis.
          </p>
        </section>

        <div className="stack">
          <section className="card" aria-labelledby="trend-heading">
            <h2 id="trend-heading" style={{ marginBottom: 10 }}>
              Trend
            </h2>
            <p className="small">{trend.explanation}</p>
          </section>

          <section className="card" aria-labelledby="adh-heading">
            <h2 id="adh-heading" style={{ marginBottom: 12 }}>
              Scanning habit
            </h2>
            <div
              className="meter"
              role="meter"
              aria-label="Adherence over the last 14 days"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(adherence.ratio * 100)}
            >
              <span style={{ width: `${Math.round(adherence.ratio * 100)}%` }} />
            </div>
            <dl className="kv" style={{ marginTop: 14 }}>
              <dt>Scans in 14 days</dt>
              <dd>
                {adherence.scansLast14Days} of {Math.max(1, Math.round(14 / adherence.expectedCadenceDays))} expected
              </dd>
              <dt>Current streak</dt>
              <dd>
                {adherence.streakDays} day{adherence.streakDays === 1 ? "" : "s"}
              </dd>
              <dt>Last scan</dt>
              <dd>{lastScan ? relativeTime(lastScan.capturedAt, now) : "Never"}</dd>
            </dl>
          </section>

          <section className="card" aria-labelledby="access-heading">
            <h2 id="access-heading" style={{ marginBottom: 8 }}>
              Access
            </h2>
            <p className="small muted" style={{ marginBottom: 14 }}>
              Your view of this record is logged and visible to the patient.
            </p>
            <RevokeForm patientUserId={link.patientUserId} patientName={name} />
          </section>
        </div>
      </div>
    </>
  );
}
