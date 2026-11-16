import Link from "next/link";
import { LogoMark } from "@/components/icons";
import { NavLinks } from "@/components/nav-links";
import { requireSignedIn } from "@/lib/doctor-context";
import { signOut } from "../(auth)/actions";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const ctx = await requireSignedIn();
  const name = ctx.state === "no-profile" ? ctx.account.name : ctx.profile.fullName;
  const verified = ctx.state === "verified";

  return (
    <div className="shell">
      <aside className="sidebar" aria-label="Sidebar">
        <Link href="/dashboard" className="brand">
          <LogoMark className="brand-mark" />
          DermoAI Doctor
        </Link>
        <nav aria-label="Main" style={{ display: "grid", gap: 6 }}>
          <NavLinks />
        </nav>
        <div className="sidebar-foot">
          <div className="well">
            <p style={{ fontWeight: 700 }}>{name || "Doctor"}</p>
            <p className="small muted">{verified ? "Verified" : "Awaiting verification"}</p>
          </div>
          <form action={signOut}>
            <button type="submit" className="btn btn-block">
              Sign out
            </button>
          </form>
        </div>
      </aside>

      <div>
        <header className="topbar">
          <Link href="/dashboard" className="brand">
            <LogoMark className="brand-mark" />
            DermoAI Doctor
          </Link>
          <span className={`badge ${verified ? "badge-pine" : "badge-medium"}`}>{verified ? "Verified" : "Unverified"}</span>
        </header>
        <main id="main" className="main">
          {children}
        </main>
      </div>

      <nav className="bottom-nav" aria-label="Main">
        <NavLinks />
      </nav>
    </div>
  );
}
