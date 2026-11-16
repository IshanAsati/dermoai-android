"use client";

import { useFormStatus } from "react-dom";

/**
 * Disables itself while its form is submitting, so a double tap on a phone
 * cannot create two invites or send two sign-in requests.
 */
export function SubmitButton({
  children,
  pendingLabel = "Working…",
  className = "btn btn-primary",
}: {
  children: React.ReactNode;
  pendingLabel?: string;
  className?: string;
}) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className={className} disabled={pending} aria-disabled={pending}>
      {pending ? pendingLabel : children}
    </button>
  );
}
