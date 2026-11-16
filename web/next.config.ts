import path from "node:path";
import type { NextConfig } from "next";

/**
 * Every page here is per-doctor medical data read with the doctor's own
 * Appwrite session, so nothing is prerendered or shared across users:
 * Cache Components stays off and each request renders on demand.
 */
const securityHeaders = [
  // The pairing page carries a live invite code in its URL; never leak it via Referer.
  { key: "Referrer-Policy", value: "no-referrer" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'; base-uri 'self'; form-action 'self'" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // The repo root is the Android project; pin the web app's own root so a stray
  // lockfile higher up the tree can never be mistaken for it.
  turbopack: { root: path.resolve(process.cwd()) },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
