"use client";

import { useActionState } from "react";
import { SubmitButton } from "@/components/submit-button";
import { createInviteAction, type InviteFormState } from "./actions";

type Option = { value: string; label: string };

export function CreateInviteForm({
  ttlOptions,
  useOptions,
  defaultTtl,
  defaultUses,
  primary,
}: {
  ttlOptions: Option[];
  useOptions: number[];
  defaultTtl: string;
  defaultUses: number;
  primary: boolean;
}) {
  const [state, action] = useActionState<InviteFormState, FormData>(createInviteAction, null);
  return (
    <form action={action} className="form">
      {state?.error ? (
        <div className="alert alert-error" role="alert">
          {state.error}
        </div>
      ) : null}
      <div className="form-grid two">
        <div className="field">
          <label htmlFor="ttl">Code lasts</label>
          <select id="ttl" name="ttl" className="select" defaultValue={defaultTtl}>
            {ttlOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="maxUses">Patients who can use it</label>
          <select id="maxUses" name="maxUses" className="select" defaultValue={String(defaultUses)}>
            {useOptions.map((n) => (
              <option key={n} value={n}>
                {n === 1 ? "1 patient (recommended)" : `${n} patients`}
              </option>
            ))}
          </select>
        </div>
      </div>
      <SubmitButton pendingLabel="Creating code…" className={primary ? "btn btn-primary" : "btn"}>
        {primary ? "Create pairing code" : "Create another code"}
      </SubmitButton>
    </form>
  );
}
