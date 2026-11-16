import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { AutoRefresh } from "@/components/auto-refresh";
import { CopyButton } from "@/components/copy-button";
import { Countdown } from "@/components/countdown";
import { ProblemCard } from "@/components/problem-card";
import { VerificationGate } from "@/components/verification-gate";
import { inviteState, type Invite } from "@/lib/data/schema";
import { formatCodeForDisplay } from "@/lib/domain/invite-codes";
import { findJustPaired } from "@/lib/domain/pairing-status";
import { requestTime } from "@/lib/clock";
import { requireProfile } from "@/lib/doctor-context";
import { displayTimeZone } from "@/lib/env";
import { formatDateTime } from "@/lib/format";
import { loadInvites } from "@/lib/loaders";
import { inviteQrDataUri } from "@/lib/qr";
import { CreateInviteForm } from "./create-invite-form";
import { revokeInviteAction } from "./actions";
import { DEFAULT_TTL, DEFAULT_USES, TTL_OPTIONS, USE_OPTIONS } from "@/lib/invite-options";

export const metadata: Metadata = { title: "Pair a patient" };

export default async function InvitesPage({ searchParams }: { searchParams: Promise<{ code?: string }> }) {
  const ctx = await requireProfile();
  if (ctx.state === "unverified") {
    return (
      <>
        <Head />
        <VerificationGate ctx={ctx} />
      </>
    );
  }

  const [{ code: selectedCode }, now, zone] = [await searchParams, requestTime(), displayTimeZone()];
  const result = await loadInvites(ctx);
  if (!result.ok) {
    return (
      <>
        <Head />
        <ProblemCard problem={result.problem} />
      </>
    );
  }

  const { invites, paired } = result.data;
  const active = invites.filter((i) => inviteState(i, now) === "ACTIVE");
  const shown: Invite | undefined = active.find((i) => i.code === selectedCode) ?? active[0];
  const justPaired = shown ? null : findJustPaired(invites, paired, now);
  const others = invites.filter((i) => i.id !== shown?.id).slice(0, 12);
  const qr = shown ? await inviteQrDataUri(shown.code) : null;
  const pairedSinceShown = shown ? paired.filter((l) => l.storedAt >= shown.createdAt - 60_000) : [];
  if (selectedCode && !shown) redirect("/invites");

  return (
    <>
      <Head />
      {shown ? <AutoRefresh everyMs={5000} /> : null}

      {shown ? (
        <section className="card" aria-labelledby="active-code-heading">
          <div className="pairing">
            <div className="qr-frame">
              {qr ? (
                // eslint-disable-next-line @next/next/no-img-element -- inline SVG data URI; next/image adds nothing
                <img src={qr} alt={`QR code for pairing code ${formatCodeForDisplay(shown.code)}. Scan it in the DermoAI app.`} width={260} height={260} />
              ) : (
                <p style={{ padding: 24, lineHeight: 1.4 }}>QR unavailable. Read the code aloud instead.</p>
              )}
            </div>
            <div className="stack-sm">
              <h2 id="active-code-heading">Ready to pair</h2>
              <p className="muted">
                Ask your patient to open DermoAI, go to <strong>More &rarr; Connect with a doctor</strong>, and scan this QR
                or type the code.
              </p>
              <p className="code-display" aria-label={`Pairing code ${shown.code.split("").join(" ")}`}>
                {formatCodeForDisplay(shown.code)}
              </p>
              <div className="row">
                <CopyButton value={shown.code} label="Copy code" />
              </div>
              <p className="small muted">
                <Countdown expiresAt={shown.expiresAt} serverNow={now} /> · {shown.usedCount} of {shown.maxUses} used
              </p>
              <p className="row small" aria-live="polite">
                {pairedSinceShown.length > 0 ? (
                  <>
                    <span className="badge badge-better">Paired</span>
                    <span>
                      {pairedSinceShown[0].patientDisplayName || "A patient"} linked{" "}
                      {formatDateTime(pairedSinceShown[0].storedAt, zone)}.{" "}
                      <Link href={`/patients/${encodeURIComponent(pairedSinceShown[0].patientUserId)}`}>Open record</Link>
                    </span>
                  </>
                ) : (
                  <>
                    <span className="pulse-dot" aria-hidden="true" />
                    <span>Waiting for your patient to pair…</span>
                  </>
                )}
              </p>
              <form action={revokeInviteAction}>
                <input type="hidden" name="inviteId" value={shown.id} />
                <button type="submit" className="btn btn-link">
                  Cancel this code
                </button>
              </form>
            </div>
          </div>
        </section>
      ) : (
        <>
          {justPaired ? (
            <section className="card" aria-labelledby="paired-now-heading" style={{ marginBottom: 20 }}>
              <div className="row" style={{ gap: 14, flexWrap: "nowrap", alignItems: "flex-start" }}>
                <span className="badge badge-better">Paired</span>
                <div className="stack-sm">
                  <h2 id="paired-now-heading">{justPaired.patientDisplayName || "A patient"} is now sharing with you</h2>
                  <p className="muted small">
                    Linked {formatDateTime(justPaired.storedAt, zone)}. That code is used up, so no one else can pair with it.
                  </p>
                  <Link className="btn btn-primary" href={`/patients/${encodeURIComponent(justPaired.patientUserId)}`}>
                    Open their record
                  </Link>
                </div>
              </div>
            </section>
          ) : null}
          <section className="card" aria-labelledby="create-heading">
            <h2 id="create-heading">{justPaired ? "Pair someone else" : "Create a pairing code"}</h2>
            <p className="muted" style={{ margin: "8px 0 18px" }}>
              Patients scan the QR or type the code in the DermoAI app. Nothing is shared until they agree.
            </p>
            <CreateInviteForm
              ttlOptions={TTL_OPTIONS.map(({ value, label }) => ({ value, label }))}
              useOptions={[...USE_OPTIONS]}
              defaultTtl={DEFAULT_TTL}
              defaultUses={DEFAULT_USES}
              primary
            />
          </section>
        </>
      )}

      {shown ? (
        <section className="card" aria-labelledby="another-heading" style={{ marginTop: 20 }}>
          <h2 id="another-heading" style={{ marginBottom: 14 }}>Another code</h2>
          <CreateInviteForm
            ttlOptions={TTL_OPTIONS.map(({ value, label }) => ({ value, label }))}
            useOptions={[...USE_OPTIONS]}
            defaultTtl={DEFAULT_TTL}
            defaultUses={DEFAULT_USES}
            primary={false}
          />
        </section>
      ) : null}

      {paired.length > 0 ? (
        <section style={{ marginTop: 28 }} aria-labelledby="paired-heading">
          <h2 id="paired-heading" style={{ marginBottom: 12 }}>Paired patients</h2>
          <ul className="invite-list">
            {paired.slice(0, 10).map((l) => (
              <li key={l.id} className="invite-item">
                <span>
                  <strong>{l.patientDisplayName || "Unnamed patient"}</strong>
                  <span className="small muted"> · linked {formatDateTime(l.storedAt, zone)}</span>
                </span>
                <Link href={`/patients/${encodeURIComponent(l.patientUserId)}`}>Open record</Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {others.length > 0 ? (
        <section style={{ marginTop: 28 }} aria-labelledby="history-heading">
          <h2 id="history-heading" style={{ marginBottom: 12 }}>Recent codes</h2>
          <ul className="invite-list">
            {others.map((i) => {
              const state = inviteState(i, now);
              return (
                <li key={i.id} className="invite-item">
                  <span>
                    <span className="mono">{formatCodeForDisplay(i.code)}</span>
                    <span className="small muted"> · created {formatDateTime(i.createdAt, zone)}</span>
                  </span>
                  <span className="row">
                    <span className={`badge ${state === "ACTIVE" ? "badge-pine" : "badge-neutral"}`}>
                      {state === "ACTIVE" ? "Active" : state === "USED" ? "Used" : state === "EXPIRED" ? "Expired" : "Cancelled"}
                    </span>
                    {state === "ACTIVE" ? (
                      <Link className="btn btn-link" href={`/invites?code=${i.code}`}>
                        Show
                      </Link>
                    ) : null}
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}
    </>
  );
}

function Head() {
  return (
    <div className="page-head">
      <div>
        <h1>Pair a patient</h1>
        <p>Give your patient a one-time code. They choose whether to share their scans with you.</p>
      </div>
    </div>
  );
}
