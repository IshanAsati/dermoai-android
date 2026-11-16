import type { Metadata } from "next";
import { CopyButton } from "@/components/copy-button";
import { LogoMark } from "@/components/icons";
import { deepLink, formatCodeForDisplay, isCompleteCode, normaliseCode } from "@/lib/domain/invite-codes";

// A live credential sits in this URL: keep it out of search indexes and out of Referer headers (see next.config.ts).
export const metadata: Metadata = { title: "Pair with your doctor", robots: { index: false, follow: false } };

/**
 * Landing page for a shared link. It deliberately looks nothing up: the code is
 * validated by the DermoAI app against the live invite, and a public page that
 * confirmed whether a code exists or named its doctor would be an oracle.
 */
export default async function PairPage({ params }: { params: Promise<{ code: string }> }) {
  const code = normaliseCode(decodeURIComponent((await params).code));
  const ok = isCompleteCode(code);

  return (
    <main id="main" className="auth-wrap">
      <div className="card auth-card">
        <LogoMark className="brand-mark" />
        {ok ? (
          <>
            <h1>Share your scans with your doctor</h1>
            <p className="muted" style={{ marginBottom: 18 }}>
              Your doctor sent you this code. Open the DermoAI app on your phone to link up. You choose what to share and
              can stop at any time.
            </p>
            <p className="code-display" style={{ textAlign: "center", margin: "6px 0 14px" }} aria-label={`Pairing code ${code.split("").join(" ")}`}>
              {formatCodeForDisplay(code)}
            </p>
            <div className="stack-sm">
              <a className="btn btn-primary btn-block" href={deepLink(code)}>
                Open in DermoAI
              </a>
              <CopyButton value={code} label="Copy code" className="btn btn-block" />
            </div>
            <ol className="small muted" style={{ margin: "20px 0 0", paddingLeft: 20, display: "grid", gap: 6 }}>
              <li>Open DermoAI and go to More, then Connect with a doctor.</li>
              <li>Scan the QR your doctor shows you, or type this code.</li>
              <li>Read what you are sharing and confirm.</li>
            </ol>
          </>
        ) : (
          <>
            <h1>That code doesn&apos;t look right</h1>
            <p className="muted">
              Pairing codes have 8 letters and numbers, like <strong>ABCD-2345</strong>. Check the code with your doctor
              and try again in the DermoAI app.
            </p>
          </>
        )}
      </div>
    </main>
  );
}
