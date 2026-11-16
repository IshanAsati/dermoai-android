import { NextResponse } from "next/server";

/**
 * Deployment check. Reports whether configuration is present and whether
 * Appwrite answers, and nothing else: no values, no key names beyond a boolean.
 * Safe to expose; useful as a post-deploy smoke test.
 */
export const dynamic = "force-dynamic";

export async function GET() {
  const configured = ["APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID", "APPWRITE_DATABASE_ID", "APPWRITE_API_KEY"].every(
    (name) => (process.env[name] ?? "").trim() !== "",
  );
  let appwrite: "reachable" | "unreachable" | "not-configured" = "not-configured";
  if (configured) {
    try {
      const res = await fetch(`${process.env.APPWRITE_ENDPOINT!.replace(/\/+$/, "")}/health/version`, {
        headers: { "X-Appwrite-Project": process.env.APPWRITE_PROJECT_ID! },
        signal: AbortSignal.timeout(4000),
        cache: "no-store",
      });
      appwrite = res.ok ? "reachable" : "unreachable";
    } catch {
      appwrite = "unreachable";
    }
  }
  const ok = configured && appwrite === "reachable";
  return NextResponse.json({ ok, configured, appwrite }, { status: ok ? 200 : 503, headers: { "Cache-Control": "no-store" } });
}
