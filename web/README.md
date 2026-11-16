# DermoAI Doctor (web)

The doctor dashboard as a web app, for phones and laptops. Next.js 16 on Vercel, with Appwrite (TablesDB) as the backend, the same project the Android app already syncs to.

A doctor signs up, gets verified, creates a **pairing code** (shown as text and as a **QR**), and the patient scans or types it in the DermoAI app. Once the patient agrees, they appear in a triage list ranked by urgency, with their scan history, trend and scanning habit.

## What is verified

| Check | Result |
|---|---|
| Unit tests (`npm test`) | 101 passing. Triage, trend, adherence, invite codes, access rules, WCAG contrast |
| End-to-end, phone and laptop (`npm run test:e2e`) | 106 passing, against a local fake Appwrite |
| Fake vs real Appwrite (`tests/contract`) | Same 18 checks pass on both. Five security checks pass on a fresh database and fail on yours (issue 1) |
| Live smoke test against the real project (`npm run test:live`) | Passing. Sign-up, verification, QR, patient redeem, scans on the dashboard, audit entry, revoke |
| Type-check, lint, production build, `npm audit --omit=dev` | Clean, 0 vulnerabilities |
| The QR, decoded from rendered pixels | `dermoai://invite/<code>`, exactly what the Android scanner accepts |

## Read this first: the backend permissions

### 1. Tables let any signed-in user read and edit everything (security)

`tools/appwrite/setup_collections.py` used to create each table with `read`, `update` and `delete` for **all signed-in users**. Appwrite combines table and row permissions with OR, so those grants override every per-row permission. I measured this against your live project with one test run on two databases that differ only in this setting:

| Another signed-in account or anonymous session can... | Your live database | A fresh one (create-only) |
|---|---|---|
| read a patient's scan summary | **yes** | no |
| edit a patient's scan summary | **yes** | no |
| cancel a doctor's invite | **yes** | no |
| edit or delete an audit entry | **yes** | no |

Anonymous sessions are what the Android app creates silently for every install. This web app does not rely on those row permissions for its own safety (see "How access is decided"), but anything stored in your tables is exposed regardless of this app.

Fix. It changes permissions only and touches no rows:

```bash
python tools/appwrite/admin.py harden
```

It shows what it will change and asks first. `setup_collections.py` already creates new tables correctly.

### 2. Appwrite renamed its API, and the Android app still uses the old one

Appwrite replaced collections, documents and attributes with **tables, rows and columns** (`/tablesdb` endpoints, `tables.*` and `rows.*` key scopes). This web app and the Python tools use the new API. Your key holds the new scopes, so scan data can be loaded and written. The Android app still uses the old API against the same data, which keeps working.

Not yet done: **the Android app does not upload scan summaries**, so the dashboard shows patients but no scans until it does. Appwrite also will not let a patient's phone grant a doctor read access (a session can only name itself in a permission), which is why the server reads summaries on the doctor's behalf after checking the link. I did not add the upload while issue 1 is open, because anything uploaded would be readable by anyone.
## Setup

```bash
cd web
cp .env.example .env.local      # fill in the values
npm install
npm run dev                     # http://localhost:3000
```

### Verify a doctor

New accounts are unverified and see no patient data. Verification is an Appwrite account label, which only the API key can set, so a doctor cannot grant it to themselves:

```bash
npm run verify-doctor -- list
npm run verify-doctor -- grant doctor@example.com
npm run verify-doctor -- revoke doctor@example.com
```

### Deploy to Vercel

1. Import the repo. Set **Root Directory** to `web`. Vercel detects Next.js.
2. Add the variables from `.env.example` (Production and Preview). Set `APPWRITE_API_KEY` as a **Sensitive** variable.
3. Deploy, then open `/api/health`. It returns `{"ok":true,"configured":true,"appwrite":"reachable"}` when everything is wired up.
4. Appwrite console: if sign-in is ever blocked in the browser, add your Vercel domain under Platforms. Server-side requests from this app do not need one.

Node 22 or newer. The app is fully dynamic, nothing is cached or shared between doctors.

## How access is decided

Appwrite cannot express "a doctor may read a patient's scans while a consented link exists", and a phone cannot name another account in a permission. So the server decides, and a doctor sees a patient only when **all** of these hold (`src/lib/data/repo.ts`):

- the link names this doctor and is ACTIVE with a consent time,
- it was written by someone **other than the doctor** (a doctor can write documents, so a self-made "consent" is rejected),
- Appwrite stored it while one of this doctor's invites was open (anyone can write a link naming any doctor),
- the doctor has not since ended access,
- and only scan summaries written by **the same identity as the link** are returned, so planted records and forged links cannot expose another patient's data.

Every view is written to an audit log before anything is shown, and if it cannot be written the record is not shown. Ending access works even when the key cannot edit documents.

## Known limits

- **Single-use codes are enforced after the fact.** The patient app never reports a redemption back, so the server notices new links and closes the code the next time the doctor's page refreshes (every 5 seconds while open). A second patient could in theory redeem inside that window.
- **The doctor's own profile document is editable by the doctor**, so its `verificationStatus` is display data only. The Android app's "verified" badge reads it. Real verification is the account label.
- **Audit logging is enforced for this web app, not for a doctor calling Appwrite directly.** A hard guarantee needs an Appwrite Function.
- Patient display names come from the patient's phone and are untrusted text.
- No scan photographs are stored or shown. Only derived summaries.

## Layout

```
src/lib/domain/      pure logic: triage, trend, adherence, invite codes, attribution
src/lib/data/        Appwrite row mappers and the access rules (repo.ts)
src/lib/             session, doctor context, loaders, QR, validation
src/app/(auth)/      sign in, sign up
src/app/(app)/       dashboard, patients/[id], invites, profile
src/app/pair/[code]  public landing page for a shared pairing link
tests/               unit, contract, e2e (fake Appwrite), live smoke
scripts/             verify-doctor
```
