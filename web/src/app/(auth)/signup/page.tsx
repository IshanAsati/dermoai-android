import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { LogoMark } from "@/components/icons";
import { getDoctorContext } from "@/lib/doctor-context";
import { SignupForm } from "./signup-form";

export const metadata: Metadata = { title: "Create doctor account" };

export default async function SignupPage() {
  if ((await getDoctorContext()).state !== "signed-out") redirect("/dashboard");
  return (
    <main id="main" className="auth-wrap">
      <div className="card auth-card wide">
        <LogoMark className="brand-mark" />
        <h1>Create your doctor account</h1>
        <p className="muted" style={{ marginBottom: 20 }}>
          Dermatologists and physicians can follow the patients who share their DermoAI scans.
        </p>
        <SignupForm />
      </div>
    </main>
  );
}
