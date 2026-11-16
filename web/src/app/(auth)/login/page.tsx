import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { LogoMark } from "@/components/icons";
import { getDoctorContext } from "@/lib/doctor-context";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Sign in" };

export default async function LoginPage() {
  if ((await getDoctorContext()).state !== "signed-out") redirect("/dashboard");
  return (
    <main id="main" className="auth-wrap">
      <div className="card auth-card">
        <LogoMark className="brand-mark" />
        <h1>Doctor sign in</h1>
        <p className="muted" style={{ marginBottom: 20 }}>
          Review the patients who have chosen to share their skin scans with you.
        </p>
        <LoginForm />
      </div>
    </main>
  );
}
