"use client";

import { useEffect } from "react";

/**
 * Last-resort boundary. Shows nothing from the error itself: messages can carry
 * request details, and a doctor cannot act on a stack trace.
 */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error("[ui] unhandled error", error.digest ?? "");
  }, [error]);

  return (
    <main id="main" className="auth-wrap">
      <div className="card auth-card" role="alert">
        <h1>Something went wrong</h1>
        <p className="muted" style={{ margin: "8px 0 18px" }}>
          The page could not be shown. Nothing was changed. Try again, and if it keeps happening let the DermoAI team know
          {error.digest ? ` (reference ${error.digest})` : ""}.
        </p>
        <button type="button" className="btn btn-primary" onClick={reset}>
          Try again
        </button>
      </div>
    </main>
  );
}
