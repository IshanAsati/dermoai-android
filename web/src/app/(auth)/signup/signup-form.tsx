"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Field, TextArea } from "@/components/field";
import { SubmitButton } from "@/components/submit-button";
import { LIMITS } from "@/lib/validation";
import { signUp, type FormState } from "../actions";

export function SignupForm() {
  const [state, action] = useActionState<FormState, FormData>(signUp, null);
  const v = state?.values ?? {};
  const e = state?.fieldErrors;
  return (
    <form action={action} className="form" noValidate>
      {state?.error ? (
        <div className="alert alert-error" role="alert">
          {state.error}
        </div>
      ) : null}
      <div className="form-grid two">
        <Field name="fullName" label="Full name" autoComplete="name" required errors={e} defaultValue={v.fullName} maxLength={LIMITS.name} span2 />
        <Field name="email" label="Email" type="email" autoComplete="email" inputMode="email" required errors={e} defaultValue={v.email} />
        <Field name="password" label="Password" type="password" autoComplete="new-password" required errors={e} hint={`At least ${LIMITS.passwordMin} characters.`} />
        <Field name="registrationNumber" label="Medical registration number" required errors={e} defaultValue={v.registrationNumber} maxLength={LIMITS.registrationNumber} hint="As issued by your medical council. Used to verify you." />
        <Field name="specialty" label="Specialty" required errors={e} defaultValue={v.specialty} maxLength={LIMITS.specialty} placeholder="Dermatology" />
        <Field name="institution" label="Clinic or hospital" required errors={e} defaultValue={v.institution} maxLength={LIMITS.institution} />
        <Field name="yearsExperience" label="Years of experience" type="number" inputMode="numeric" min={0} max={LIMITS.yearsMax} errors={e} defaultValue={v.yearsExperience} />
        <TextArea name="qualifications" label="Qualifications" errors={e} defaultValue={v.qualifications} rows={2} hint="Separate with commas, e.g. MBBS, MD Dermatology." span2 />
      </div>
      <p className="small muted">
        After you sign up, your registration is checked by the DermoAI team. You can set up your account straight away,
        but patient data stays locked until you are verified.
      </p>
      <SubmitButton pendingLabel="Creating account…">Create account</SubmitButton>
      <p className="small muted">
        Already registered? <Link href="/login">Sign in</Link>
      </p>
    </form>
  );
}
