import type { LoadProblem } from "@/lib/loaders";

/**
 * Shown when patient data could not be loaded. Says what is wrong in terms the
 * doctor can act on, and never an internal error string.
 */
export function ProblemCard({ problem }: { problem: LoadProblem }) {
  if (problem === "missing-scope") {
    return (
      <div className="card" role="alert">
        <h2>Patient data is not connected yet</h2>
        <p className="muted" style={{ marginTop: 8 }}>
          The server&apos;s Appwrite API key is missing the <strong>rows.read</strong> permission, so scan results
          cannot be loaded. Pairing codes still work. Ask the administrator to add the Databases scopes
          (<code>rows.read</code>, <code>rows.write</code>) to the key, then reload this page.
        </p>
      </div>
    );
  }
  return (
    <div className="card" role="alert">
      <h2>We couldn&apos;t load this right now</h2>
      <p className="muted" style={{ marginTop: 8 }}>
        The patient service did not respond. Nothing was changed. Reload the page in a moment.
      </p>
    </div>
  );
}
