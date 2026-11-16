#!/usr/bin/env node
/**
 * Marks a doctor as verified (or removes verification) by setting the
 * `verifieddoctor` label on their Appwrite account.
 *
 * Why a label and not the profile document: a doctor can edit their own profile
 * document straight through the Appwrite API, so a status stored there is not
 * evidence of anything. Labels can only be set with the server API key.
 *
 *   npm run verify-doctor -- list
 *   npm run verify-doctor -- grant  doctor@example.com
 *   npm run verify-doctor -- revoke doctor@example.com
 *
 * Reads APPWRITE_* from web/.env.local (via `node --env-file`). The key is never printed.
 * Needs the API key to hold the users.read and users.write scopes.
 */
import { Client, Query, Users } from "node-appwrite";

const LABEL = "verifieddoctor";
const need = ["APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID", "APPWRITE_API_KEY"].filter((k) => !process.env[k]);
if (need.length) {
  console.error(`Missing ${need.join(", ")}. Run through npm so web/.env.local is loaded.`);
  process.exit(2);
}

const users = new Users(
  new Client().setEndpoint(process.env.APPWRITE_ENDPOINT).setProject(process.env.APPWRITE_PROJECT_ID).setKey(process.env.APPWRITE_API_KEY),
);

const [cmd, email] = process.argv.slice(2);

async function findByEmail(address) {
  const res = await users.list({ queries: [Query.equal("email", address.toLowerCase()), Query.limit(1)] });
  return res.users[0] ?? null;
}

try {
  if (cmd === "list") {
    const res = await users.list({ queries: [Query.limit(100)] });
    const rows = res.users.map((u) => `${u.labels?.includes(LABEL) ? "verified  " : "unverified"}  ${u.email}  (${u.name || "no name"})`);
    console.log(rows.length ? rows.join("\n") : "No accounts yet.");
  } else if ((cmd === "grant" || cmd === "revoke") && email) {
    const user = await findByEmail(email);
    if (!user) {
      console.error(`No account found for ${email}.`);
      process.exit(1);
    }
    const labels = new Set(user.labels ?? []);
    if (cmd === "grant") labels.add(LABEL);
    else labels.delete(LABEL);
    await users.updateLabels({ userId: user.$id, labels: [...labels] });
    console.log(`${cmd === "grant" ? "Verified" : "Removed verification from"} ${user.email}.`);
  } else {
    console.error("Usage: verify-doctor <list | grant EMAIL | revoke EMAIL>");
    process.exit(2);
  }
} catch (e) {
  console.error(`Failed: [${e.code ?? "?"}] ${e.type ?? ""} ${e.message ?? e}`);
  process.exit(1);
}
