"use client";

import { useActionState } from "react";
import { Field, TextArea } from "@/components/field";
import { SubmitButton } from "@/components/submit-button";
import { LIMITS } from "@/lib/validation";
import { saveProfileAction, type ProfileState } from "./actions";

export type ProfileDefaults = {
  fullName: string;
  registrationNumber: string;
  specialty: string;
  institution: string;
  yearsExperience: string;
  qualifications: string;
  bio: string;
};

export function ProfileForm({ defaults, isNew }: { defaults: ProfileDefaults; isNew: boolean }) {
  const [state, action] = useActionState<ProfileState, FormData>(saveProfileAction, null);
  const v = { ...defaults, ...(state?.values ?? {}) };
  const e = state?.fieldErrors;
  return (
    <form action={action} className="form" noValidate>
      {state?.error ? (
        <div className="alert alert-error" role="alert">
          {state.error}
        </div>
      ) : null}
      {state?.saved ? (
        <div className="alert alert-info" role="status">
          Profile saved.
        </div>
      ) : null}
      <div className="form-grid two">
        <Field name="fullName" label="Full name" autoComplete="name" required errors={e} defaultValue={v.fullName} maxLength={LIMITS.name} span2 />
        <Field name="registrationNumber" label="Medical registration number" required errors={e} defaultValue={v.registrationNumber} maxLength={LIMITS.registrationNumber} />
        <Field name="specialty" label="Specialty" required errors={e} defaultValue={v.specialty} maxLength={LIMITS.specialty} />
        <Field name="institution" label="Clinic or hospital" required errors={e} defaultValue={v.institution} maxLength={LIMITS.institution} />
        <Field name="yearsExperience" label="Years of experience" type="number" inputMode="numeric" min={0} max={LIMITS.yearsMax} errors={e} defaultValue={v.yearsExperience} />
        <TextArea name="qualifications" label="Qualifications" errors={e} defaultValue={v.qualifications} rows={2} hint="Separate with commas." span2 />
        <TextArea name="bio" label="About you" errors={e} defaultValue={v.bio} rows={3} maxLength={LIMITS.bio} hint="Patients see this when they are asked to share with you." span2 />
      </div>
      <SubmitButton pendingLabel="Saving…">{isNew ? "Create profile" : "Save changes"}</SubmitButton>
    </form>
  );
}
