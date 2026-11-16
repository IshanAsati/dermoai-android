import "server-only";

/**
 * Server-side configuration, read lazily so `next build` succeeds on a machine
 * with no secrets and fails loudly (naming the variable, never its value) only
 * when a request actually needs one.
 *
 * Endpoint, project id and database id are public in any Appwrite client. The
 * API key is not: it is read only by the sign-in/sign-up path and the admin
 * script, and must never be given a NEXT_PUBLIC_ prefix.
 */
function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new ConfigError(
      `Missing environment variable ${name}. Set it in web/.env.local (local) or in the Vercel project settings (deployed).`,
    );
  }
  return value;
}

export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ConfigError";
  }
}

export function appwriteEnv() {
  const endpoint = required("APPWRITE_ENDPOINT").replace(/\/+$/, "");
  if (!/^https?:\/\//i.test(endpoint)) {
    throw new ConfigError("APPWRITE_ENDPOINT must start with http:// or https://");
  }
  return {
    endpoint,
    projectId: required("APPWRITE_PROJECT_ID"),
    databaseId: required("APPWRITE_DATABASE_ID"),
  };
}

/** Only for the admin client (session creation). Never reaches a response body. */
export function appwriteApiKey(): string {
  return required("APPWRITE_API_KEY");
}

/** IANA zone used to render timestamps. Servers run in UTC; doctors do not. */
export function displayTimeZone(): string {
  return process.env.APP_TIME_ZONE?.trim() || "Asia/Kolkata";
}
