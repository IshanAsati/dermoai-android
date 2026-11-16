"use client";

import { useActionState } from "react";
import { SubmitButton } from "@/components/submit-button";
import { revokeAccessAction, type RevokeState } from "./actions";

/**
 * Two-step: the destructive control only appears after an explicit open, and
 * says what will happen in plain words. Uses <details>, which works without JS
 * and is keyboard accessible.
 */
export function RevokeForm({ patientUserId, patientName }: { patientUserId: string; patientName: string }) {
  const [state, action] = useActionState<RevokeState, FormData>(() => revokeAccessAction(patientUserId), null);
  return (
    <details className="confirm">
      <summary className="btn btn-danger btn-block">End access to this patient</summary>
      <form action={action} className="stack-sm">
        <p className="small">
          You will stop seeing {patientName || "this patient"}&apos;s scans straight away. They can share with you again
          later with a new code.
        </p>
        {state?.error ? (
          <div className="alert alert-error" role="alert">
            {state.error}
          </div>
        ) : null}
        <SubmitButton className="btn btn-danger btn-block" pendingLabel="Ending access…">
          Yes, end access
        </SubmitButton>
      </form>
    </details>
  );
}
