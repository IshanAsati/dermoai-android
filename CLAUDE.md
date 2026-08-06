# Changes Done with Claude Code

**Session Date:** 2026-08-07
**Repository Path:** `C:\Users\aarus\Downloads\dermoai-android`
**Branch:** `feat/doctor-dashboard`

---

## Context

Pre-competition fix session (competition: 2026-08-08). Two physical phones connected via adb
(`1c3aa67a`, `5abebc6`), one running the doctor role, one the patient role. Four issues were
fixed in parallel using isolated git worktrees (`git worktree add`), then merged into this branch
and installed on both devices. Worktrees have been removed; branches were merged and deleted.

---

## 1. Doctor-patient pairing (invite code redemption) — root cause fixed

**Symptom:** doctor generates an invite code, reads it out, patient types it in on a different
phone — redemption silently fails. The only visible text was the static field hint ("The code
never contains I, L, O, 0 or 1"), which was mistaken for the error itself.

**Root cause (confirmed server-side via `tools/appwrite/admin.py docs <collection>`):**
`InvitePatientViewModel.createInvite()` pushed the new invite to the `doctor_invites` Appwrite
collection, but nothing in the app ever pushed the issuing doctor's own profile to
`doctor_profiles`. Live check showed `doctor_invites`: 4 documents, `doctor_profiles`: 0
documents. The patient's device could resolve the invite code fine
(`DoctorSyncRepository.findInviteByCode`) but `pullDoctorProfile(doctorUserId)` always came back
empty, and `RedeemInviteViewModel.checkCode()` treated a missing profile as a hard failure.

**Fix:**
- `InvitePatientViewModel.kt` — `createInvite()` now pushes the doctor's own profile
  (`sync.pushDoctorProfile`) before pushing the invite, so a freshly generated code is redeemable
  immediately. `load()` also opportunistically re-pushes the profile in the background whenever it
  changes, so an already-registered doctor account syncs retroactively the next time the invite
  screen opens (no re-registration needed).
- `InvitePatientScreen.kt` — new `InviteSyncBanner` shows sync state (`Syncing` / `Synced` /
  `NotSynced(reason)` / `Failed`) so the doctor can see whether a code has actually left the device
  before reading it out.
- `RedeemInviteViewModel.kt` / `RedeemInviteScreen.kt` — remote invite lookup now distinguishes
  "no such code" from "couldn't reach the server" (new `RedeemRejection.Offline`) instead of
  collapsing both into one generic failure.
- `DoctorSignUpViewModel.kt` — pushes the profile immediately on sign-up too (belt-and-suspenders
  for future accounts). Required adding `core:data` as a dependency of `feature/auth`.

**Added: QR code scanning as a fallback to typing.** Doctor side already rendered the invite as a
QR (`dermoai://invite/<code>`, zxing `QRCodeWriter`) — no change needed there. New on the patient
side: `QrScanScreen.kt` + `invite/QrScanAnalyzer.kt` (CameraX `ImageAnalysis.Analyzer` + zxing
`QRCodeReader`), reachable via a "Scan QR code instead" button on the redeem screen. A decoded code
feeds through the exact same `onCodeChanged`/`checkCode()` path as typed input — no duplicated
lookup logic.

**Not verified (needs live devices, was out of scope for the fixing agent):** whether the venue's
wifi can reach `sgp.cloud.appwrite.io` at all — if not, the new sync banners at least make that
diagnosable live instead of silently doing nothing. Before the demo starts, open "Invite a
patient" on the doctor phone once and confirm the sync banner goes green.

---

## 2. "Dermatologists near me" — location fetch fixed

**Symptom:** location never resolves, so the nearby-dermatologist search never runs.

**Root cause:** `core/environment/.../LocationProvider.kt` — the cached-fix path already checked
both `NETWORK_PROVIDER` and `GPS_PROVIDER`, but the live one-shot fallback
(`requestCurrentLocation`, used when there's no cached fix) only ever requested from
`GPS_PROVIDER`, with no `isProviderEnabled` check. Indoors, or with the device's Location Accuracy
set to battery-saving/Wi-Fi-only mode (which disables GPS outright), the request either timed out
after 8s or could throw synchronously — even though `NETWORK_PROVIDER` would have answered.

**Fix:** `requestCurrentLocation` now filters to enabled providers, races `GPS_PROVIDER` and
`NETWORK_PROVIDER` in parallel (first result wins), and treats a synchronous request failure from
one provider as "no result from that provider" instead of crashing the coroutine. No new
dependency (stayed within `android.location`, no FusedLocationProviderClient).

**Not verified:** real behavior with GPS fully disabled at the OS level, and with location off
entirely — reasoned through but needs a live device to confirm the clean fall-through to
`FinderUiState.NoLocation`.

---

## 3. Dark mode text contrast — fixed (low priority)

**Root cause:** `core/ui/.../theme/DermoTheme.kt`'s `DarkColorScheme` was already correct
(`onSurfaceVariant → DarkSlate`, `onSurface → DarkInk`), but ~13 screens read the static
`DermoColors.Slate` / `DermoColors.Ink` object directly instead of going through
`MaterialTheme.colorScheme`, so dark mode reused colors tuned only for the light palette —
contrast as low as ~1.22:1 in one case (`DermoColors.Ink` on `DarkCanvas`).

**Fix:** pointed those call sites at `MaterialTheme.colorScheme.onSurfaceVariant` /
`.onSurface` instead of the static colors. No color *values* changed — the theme tokens were
already right, just not used consistently. Files: `MedicalDisclaimerBar.kt`, `OnboardingScreen.kt`,
`DoctorStatusScreen.kt`, `DoctorSignUpScreen.kt`, `DoctorComponents.kt`, `DoctorDashboardScreen.kt`,
`InvitePatientScreen.kt` (incl. one instance introduced by the pairing fix above, same session),
`PatientDetailScreen.kt`, `PatientPrivacyScreen.kt`, `RedeemInviteScreen.kt`,
`HomeDashboardScreen.kt`, `BreathingScreen.kt`, `JournalScreen.kt`.

**Known follow-up, not fixed (flagged, out of scope for this pass):** `DoctorVerificationBadge.kt`
has the same class of bug — its four status "pill" badges reuse light-tuned
`SageText`/`AmberText`/`CoralText`/`Slate` as both a translucent chip background and full-opacity
foreground text. Fixing it properly needs dedicated `Dark*Text` tokens plus threading
`isSystemInDarkTheme()` through a currently non-composable `badgeStyle()` function — a bigger,
more design-y change than a token swap.

---

## 4. Demo data seeding — new debug-only tool

**Goal:** make both demo phones look like real, long-term-used accounts without live data entry
during the demo.

**How accounts actually work here (important context):** Firebase auth is still wired to a
placeholder project, so `FirebaseAuthRepository` runs in **local-only mode** — "signing in" just
derives a deterministic UUID from the lowercased email and upserts a local `UserProfileEntity` row.
There is no cross-device account sync; the two phones are two independent SQLite databases that
only ever talk to each other through the invite/link flow (see #1). "Doctor account" vs. "patient
account" is just `UserProfileEntity.role`, set by going through `DoctorSignUpScreen` vs. regular
sign-up.

**What was built:** `feature/settings` — a `BuildConfig.DEBUG`-gated "Load Demo Data" button
(Settings tab), backed by `DemoDataSeeder` + `DemoDataPlan` (`feature/settings/.../demo/`), which
writes through the **real DAOs** (`DoctorProfileDao`, `PatientLinkDao`, `SkinScanDao`,
`ScanPredictionDao`, `UserProfileDetailsDao` — no raw SQL) so seeded rows are indistinguishable
from genuine ones to the rest of the app (severity/adherence/trend are all derived on read, never
stored). Deterministic ids, safe to tap more than once (`OnConflictStrategy.REPLACE`, no
duplicates).

- **Doctor role:** fills in realistic profile fields (only blanks — never overwrites what a human
  typed at sign-up), sets `verificationStatus = VERIFIED` directly (debug-only escape hatch, same
  pattern as the existing debug self-approve flow), then creates 4 linked patients with
  deliberately different clinical stories so the triage ranking visibly differentiates them:
  one worsening → CRITICAL, one inactive (no recent scans), one stable/benign, one improving.
  15 scans total across the four.
- **Patient role:** fleshes out demographics, then writes 19 scans spread over ~88 days, mostly
  low-severity with two flagged findings ("follow-up" noted).
- Seeded scans have no real photo file (`imagePath` points at a nonexistent path) — verified this
  is safe, `TimelineCard`/`PatientDetailScreen` already null-safely fall back to a placeholder tile
  on decode failure.

**Steps for tomorrow (order matters — patient links need the doctor profile to exist first):**
1. **Doctor phone:** Sign In screen → "sign up as a doctor" → any demo email/details (seeder
   backfills blanks) → Settings → **Load Demo Data**. Dashboard is populated with 4 patients.
2. **Patient phone:** regular Sign Up with a *different* email → Settings → **Load Demo Data**.
   Timeline/insights show ~19 scans over 3 months.
3. The 4 seeded patients on the doctor phone are synthetic — they don't correspond to the real
   patient phone's account. To also demo the *live* invite/QR flow between the two real phones,
   that's a separate manual step on top (doctor's Invite Patient screen → code or QR → patient's
   Redeem Invite screen), independent of seeding.

---

## Build/verification status

`./gradlew assembleDebug` → `BUILD SUCCESSFUL`. Installed via `adb install -r` on both connected
devices (`1c3aa67a`, `5abebc6`). Per-fix unit tests (`:feature:doctor`, `:core:data`,
`:feature:auth`, `:core:environment`, `:feature:settings`) all pass. Live end-to-end verification
(actual pairing between the two phones, actual indoor location fetch, visual dark-mode check,
running the seeder) is the next step, done by hand on-device.

---

*File generated by Claude Code. See also `AGENTS.md` (UI/UX audit trail) and
`changes_done_with_agy.md` (prior session, unit tests + APK build/sideload).*
