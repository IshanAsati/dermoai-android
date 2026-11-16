import type { Metadata } from "next";
import Link from "next/link";
import { AdherenceBadge, SeverityBadge, TrendBadge } from "@/components/badges";
import { ProblemCard } from "@/components/problem-card";
import { VerificationGate } from "@/components/verification-gate";
import { requestTime } from "@/lib/clock";
import { requireProfile } from "@/lib/doctor-context";
import { displayTimeZone } from "@/lib/env";
import { formatDate, initials, relativeTime } from "@/lib/format";
import { loadTriage } from "@/lib/loaders";

export const metadata: Metadata = { title: "Patients" };

export default async function DashboardPage({ searchParams }: { searchParams: Promise<{ revoked?: string }> }) {
  const ctx = await requireProfile();
  const { revoked } = await searchParams;

  if (ctx.state === "unverified") {
    return (
      <>
        <PageHead name={ctx.profile.fullName} />
        <VerificationGate ctx={ctx} />
      </>
    );
  }

  const now = requestTime();
  const result = await loadTriage(ctx, now);
  const zone = displayTimeZone();

  return (
    <>
      <PageHead name={ctx.profile.fullName} />
      {revoked ? (
        <div className="alert alert-info" role="status" style={{ marginBottom: 18 }}>
          Access ended. That patient no longer appears in your list.
        </div>
      ) : null}

      {!result.ok ? (
        <ProblemCard problem={result.problem} />
      ) : result.data.rows.length === 0 ? (
        <div className="card empty">
          <h2>No patients yet</h2>
          <p>
            Create a pairing code and have your patient scan the QR in the DermoAI app, or type the code under
            More &rarr; Connect with a doctor. They appear here once they agree to share.
          </p>
          <Link className="btn btn-primary" href="/invites">
            Pair a patient
          </Link>
        </div>
      ) : (
        <>
          <section className="stats" aria-label="Summary">
            <Stat value={result.data.summary.total} label="Patients" />
            <Stat value={result.data.summary.needsAttention} label="High or critical" />
            <Stat value={result.data.summary.worsening} label="Worsening trend" />
            <Stat value={result.data.summary.inactive} label="No scan in 14 days" />
          </section>

          <h2 className="sr-only">Patients, most urgent first</h2>
          <ul className="patient-list">
            {result.data.rows.map((row) => (
              <li key={row.patientUserId}>
                <Link href={`/patients/${encodeURIComponent(row.patientUserId)}`} className="patient-row">
                  <span className="avatar" aria-hidden="true">
                    {initials(row.displayName)}
                  </span>
                  <span className="patient-main">
                    <span className="patient-name">{row.displayName}</span>
                    <br />
                    <span className="patient-seen">
                      {row.lastScanAt ? `Last scan ${relativeTime(row.lastScanAt, now)} · ${formatDate(row.lastScanAt, zone)}` : `Linked ${formatDate(row.linkedAt, zone)} · no scans yet`}
                    </span>
                  </span>
                  <span className="patient-meta">
                    <SeverityBadge severity={row.latestSeverity} />
                    {/* No scans: one clear badge. Trend and habit would only repeat the same fact. */}
                    {row.lastScanAt ? (
                      <>
                        {row.latestFinding ? <span className="small">{row.latestFinding}</span> : null}
                        <TrendBadge trend={row.trend} />
                        <AdherenceBadge adherence={row.adherence} />
                      </>
                    ) : null}
                  </span>
                  <span className="sr-only">Open record</span>
                </Link>
              </li>
            ))}
          </ul>
          <p className="disclaimer">
            Findings come from an educational screening model run on patient photos. They are a prompt to look, not a
            diagnosis.
          </p>
        </>
      )}
    </>
  );
}

function PageHead({ name }: { name: string }) {
  const first = name.replace(/^dr\.?\s+/i, "").split(/\s+/)[0] || "doctor";
  return (
    <div className="page-head">
      <div>
        <h1>Patients</h1>
        <p>Most urgent first. Welcome back, Dr. {first}.</p>
      </div>
      <Link className="btn btn-primary" href="/invites">
        Pair a patient
      </Link>
    </div>
  );
}

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="card stat">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}
