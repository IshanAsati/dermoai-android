import "server-only";
import { cookies } from "next/headers";

/**
 * The Appwrite session secret lives only in this httpOnly cookie. Scripts on
 * the page cannot read it, and it is never put in a response body or URL.
 */
export const SESSION_COOKIE = "dermoai_session";
const MAX_AGE_SECONDS = 60 * 60 * 24 * 7;

export async function readSessionSecret(): Promise<string | null> {
  const value = (await cookies()).get(SESSION_COOKIE)?.value;
  return value && value.length > 0 ? value : null;
}

export async function writeSessionSecret(secret: string): Promise<void> {
  (await cookies()).set(SESSION_COOKIE, secret, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: MAX_AGE_SECONDS,
  });
}

export async function clearSessionSecret(): Promise<void> {
  (await cookies()).delete(SESSION_COOKIE);
}
