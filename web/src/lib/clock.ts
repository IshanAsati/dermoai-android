/**
 * Wall-clock time for the current request. Pages and loaders take `now` as a
 * parameter so every rule is testable; this is the single place the real clock
 * is read for rendering. Server components are request-time, so reading it
 * here is correct, and it keeps `Date.now()` out of component bodies.
 */
export function requestTime(): number {
  return Date.now();
}
