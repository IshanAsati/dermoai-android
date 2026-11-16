import Link from "next/link";
import type { DoctorContext } from "@/lib/doctor-context";

/**
 * What an unverified doctor sees instead of patient data. No patient query has
 * been run by the time this renders: the gate is in the loader path, not in CSS.
 */
export function VerificationGate({ ctx }: { ctx: Extract<DoctorContext, { state: "unverified" }> }) {
  return (
    <div className="card empty">
      <h2>Your account is awaiting verification</h2>
      <p>
        We check each doctor&apos;s medical registration before patient data is shown. Your details are with the DermoAI
        team. You can still review and update your profile while you wait.
      </p>
      <div className="well" style={{ textAlign: "left", maxWidth: 440, margin: "0 auto 20px" }}>
        <p className="small muted">Registration number on file</p>
        <p style={{ fontWeight: 700 }}>{ctx.profile.registrationNumber || "Not provided"}</p>
      </div>
      <div className="row" style={{ justifyContent: "center" }}>
        <Link className="btn" href="/profile">
          Review my profile
        </Link>
        <Link className="btn btn-link" href="/dashboard">
          Check again
        </Link>
      </div>
    </div>
  );
}
