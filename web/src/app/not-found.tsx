import Link from "next/link";

export default function NotFound() {
  return (
    <main id="main" className="auth-wrap">
      <div className="card auth-card">
        <h1>Page not found</h1>
        <p className="muted" style={{ margin: "8px 0 18px" }}>
          That page does not exist, or it moved.
        </p>
        <Link className="btn btn-primary" href="/dashboard">
          Go to patients
        </Link>
      </div>
    </main>
  );
}
