"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Field } from "@/components/field";
import { SubmitButton } from "@/components/submit-button";
import { signIn, type FormState } from "../actions";

export function LoginForm() {
  const [state, action] = useActionState<FormState, FormData>(signIn, null);
  return (
    <form action={action} className="form" noValidate>
      {state?.error ? (
        <div className="alert alert-error" role="alert">
          {state.error}
        </div>
      ) : null}
      <Field name="email" label="Email" type="email" autoComplete="email" inputMode="email" required errors={state?.fieldErrors} defaultValue={state?.values?.email} />
      <Field name="password" label="Password" type="password" autoComplete="current-password" required errors={state?.fieldErrors} />
      <SubmitButton pendingLabel="Signing in…">Sign in</SubmitButton>
      <p className="small muted">
        New to DermoAI? <Link href="/signup">Create a doctor account</Link>
      </p>
    </form>
  );
}
