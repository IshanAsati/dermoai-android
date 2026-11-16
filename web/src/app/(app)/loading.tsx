export default function Loading() {
  return (
    <div role="status" aria-live="polite" aria-label="Loading">
      <div className="page-head">
        <div>
          <div className="skeleton" style={{ width: 220, minHeight: 36 }} />
        </div>
      </div>
      <div className="stats">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="skeleton" style={{ minHeight: 88 }} />
        ))}
      </div>
      <div className="stack-sm">
        {[0, 1, 2].map((i) => (
          <div key={i} className="skeleton" />
        ))}
      </div>
      <span className="sr-only">Loading…</span>
    </div>
  );
}
