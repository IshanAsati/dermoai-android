# Appwrite backend setup

The doctor dashboard works entirely on-device without this. Appwrite is what
makes a doctor and a patient on **two different phones** able to link — nothing
more. With no config the app runs local-only, exactly as it does when the
Firebase config is the placeholder project.

## One-time setup

1. **Register the Android platform** — Appwrite console → your project → *Add
   platform* → *Android*, package name **`com.dermoai`**. Appwrite rejects client
   connections from unregistered packages, and the failure surfaces later as a
   confusing auth error rather than an obvious one.

2. **Enable an auth method** — Auth → Settings → Email/Password. The sync layer
   authenticates as a user session; with every provider disabled, every call is
   an unauthenticated guest and returns 401.

3. **Create the collections**:

   ```bash
   export APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1
   export APPWRITE_PROJECT_ID=<your project id>
   export APPWRITE_DATABASE_ID=<your database id>
   export APPWRITE_API_KEY=<key with Databases scopes>

   python tools/appwrite/setup_collections.py
   ```

   Idempotent — re-running only creates what is missing.

4. **Point the app at it** — the first three values go in `local.properties`
   (gitignored). The API key does **not**, and never appears in any file.

   ```properties
   APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1
   APPWRITE_PROJECT_ID=...
   APPWRITE_DATABASE_ID=...
   ```

## Why the API key is handled differently

Endpoint, project id and database id are **not secrets**. Every Appwrite client
app ships them and the security model assumes the client knows them, enforcing
access from the user's session plus per-document permissions.

An **API key** is the opposite: it can read, write and delete every document in
the project, bypassing all permissions. An APK is readable with `unzip` and
`strings`, so a key shipped inside one is a key given away. It exists only in
your shell for the seconds the setup script runs.

## What gets created

Five collections, all with `documentSecurity: true` so per-document ACLs apply:

| Collection | Holds |
|---|---|
| `doctor_profiles` | Clinician credentials and verification status |
| `patient_links` | Doctor↔patient consent records — the authorisation boundary |
| `doctor_invites` | Redeemable codes, `code` uniquely indexed |
| `scan_summaries` | Derived triage rows — **no images, no file paths** |
| `audit_entries` | Append-only record of a doctor touching patient data |

**No scan photographs and no model weights are ever uploaded.** That is a
deliberate design decision, not a gap: a doctor triaging a list needs *when,
where on the body, what the model said, how confident, how concerning* — not
pixels. Keeping photographs on the device that took them means a backend
compromise leaks triage metadata, not a patient's medical photographs.

## What the permission model does and does not enforce

**Enforced server-side.** A document is readable only by the account ids in its
ACL. A patient's `scan_summaries` rows name themselves and the specific doctors
they have consented to. That is a real grant — a client cannot read a row it is
not named in, whatever it sends.

**Not enforced server-side.** The rule you actually want is *"a doctor may read
these rows only while an ACTIVE, consented link exists"*. An Appwrite ACL is a
list of ids, not a predicate: it cannot consult another collection. So revocation
works by **rewriting** the summary rows' ACLs to drop that doctor. Consequences,
stated plainly:

- Revocation is a write, so it can fail or race. A revoke that errors leaves the
  old grant in place until it succeeds.
- A doctor added to an ACL keeps access until something rewrites that row.
- Redemption reads an invite by `code`, which no ACL can scope to "the row whose
  code I typed".

Closing those properly needs an **Appwrite Function** holding an API key that
mediates reads and redemption server-side. That is the correct production design
and is not implemented here.

For a school project with per-document ACLs and a local audit trail this is a
reasonable place to stop — but it is worth saying out loud rather than claiming a
guarantee the code does not provide. If asked, the honest answer is: *"consent is
enforced by document ACLs, revocation is best-effort, and a hardened version
moves both behind a server-side Function."*
