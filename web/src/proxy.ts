import { NextResponse, type NextRequest } from "next/server";

/**
 * Optimistic gate only: it checks that a session cookie exists, not that it is
 * valid, so signed-out visitors are bounced before any page runs. Real
 * authentication happens in each page and action (`lib/doctor-context.ts`),
 * which re-validate the session with Appwrite. Never rely on this alone.
 */
const SESSION_COOKIE = "dermoai_session";
const PROTECTED = ["/dashboard", "/invites", "/patients", "/profile"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const signedIn = request.cookies.has(SESSION_COOKIE);
  const isProtected = PROTECTED.some((p) => pathname === p || pathname.startsWith(`${p}/`));

  if (isProtected && !signedIn) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/invites/:path*", "/patients/:path*", "/profile/:path*"],
};
