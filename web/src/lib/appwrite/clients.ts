import "server-only";
import { Account, Client, TablesDB, Users } from "node-appwrite";
import { appwriteApiKey, appwriteEnv } from "@/lib/env";

/**
 * Two kinds of client, deliberately kept apart:
 *
 * - {@link createAdminClient} carries the API key. It does exactly three jobs:
 *   turn an email + password into a session secret (Appwrite only returns the
 *   secret to a keyed caller), set the `verifieddoctor` label (admin script),
 *   and read/write rows that a doctor's own session cannot reach. That last job
 *   is only ever done after `authoriseLink` has passed. See `data/repo.ts`.
 * - {@link createSessionClient} carries the doctor's own session secret. The
 *   doctor's profile and invites go through it, so Appwrite's row permissions
 *   are the authority there, exactly as they are for the Android app.
 *
 * Uses the TablesDB API (tables, rows, columns). It replaced the older
 * Databases API (collections, documents, attributes), which now needs separate
 * `collections.*` / `documents.*` key scopes that current keys do not carry.
 */
export function createAdminClient() {
  const { endpoint, projectId, databaseId } = appwriteEnv();
  const client = new Client().setEndpoint(endpoint).setProject(projectId).setKey(appwriteApiKey());
  return {
    account: new Account(client),
    users: new Users(client),
    tablesDb: new TablesDB(client),
    databaseId,
  };
}

export function createSessionClient(sessionSecret: string) {
  const { endpoint, projectId, databaseId } = appwriteEnv();
  const client = new Client().setEndpoint(endpoint).setProject(projectId).setSession(sessionSecret);
  return {
    account: new Account(client),
    tablesDb: new TablesDB(client),
    databaseId,
  };
}
