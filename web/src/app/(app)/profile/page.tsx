import type { Metadata } from "next";
import { requireSignedIn } from "@/lib/doctor-context";
import { signOut } from "../../(auth)/actions";
import { ProfileForm, type ProfileDefaults } from "./profile-form";

export const metadata: Metadata = { title: "Profile" };

export default async function ProfilePage({ searchParams }: { searchParams: Promise<{ setup?: string }> }) {
  const ctx = await requireSignedIn();
  const { setup } = await searchParams;
  const isNew = ctx.state === "no-profile";
  const p = ctx.state === "no-profile" ? null : ctx.profile;

  const defaults: ProfileDefaults = {
    fullName: p?.fullName ?? ctx.account.name ?? "",
    registrationNumber: p?.registrationNumber ?? "",
    specialty: p?.specialty ?? "",
    institution: p?.institution ?? "",
    yearsExperience: p ? String(p.yearsExperience) : "",
    qualifications: p?.qualifications.join(", ") ?? "",
    bio: p?.bio ?? "",
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{isNew ? "Finish your profile" : "Your profile"}</h1>
          <p>
            {isNew
              ? "Your account was created but your doctor profile is missing. Add it to continue."
              : "Patients see these details when they decide whether to share with you."}
          </p>
        </div>
        {ctx.state === "verified" ? <span className="badge badge-pine">Verified</span> : null}
        {ctx.state === "unverified" ? <span className="badge badge-medium">Awaiting verification</span> : null}
      </div>

      {setup ? (
        <div className="alert alert-warn" role="status" style={{ marginBottom: 18 }}>
          We created your account but could not save your profile. Check the details below and save again.
        </div>
      ) : null}

      <section className="card" aria-labelledby="details-heading">
        <h2 id="details-heading" style={{ marginBottom: 16 }}>
          Details
        </h2>
        <ProfileForm defaults={defaults} isNew={isNew} />
      </section>

      <section className="card" style={{ marginTop: 20 }} aria-labelledby="account-heading">
        <h2 id="account-heading" style={{ marginBottom: 8 }}>
          Account
        </h2>
        <p className="muted small" style={{ marginBottom: 14 }}>
          Signed in as {ctx.account.email}
        </p>
        <form action={signOut}>
          <button type="submit" className="btn">
            Sign out
          </button>
        </form>
      </section>
    </>
  );
}
