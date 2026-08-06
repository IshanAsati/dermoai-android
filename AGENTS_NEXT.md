# DermoAI — state of play and what to do next

Written 2026-08-06, immediately before a presentation. Read this first; it is
current, unlike `AGENTS.md`, which describes the pre-gate ML pipeline.

Branch: `feat/doctor-dashboard` in `C:\Users\aarus\Downloads\dermoai-android`
(a fork of `IshanAsati/dermoai-android`; PR #1 carries the ML work).

Build: `./gradlew :app:assembleDebug -Dorg.gradle.java.home="C:/Program Files/Java/jdk-22"`
(`gradle.properties` in the *other* repo hardcodes a Linux JDK path; this one is fine.)

---

## 1. What works

| Area | Status |
|---|---|
| ML model + healthy gate | Working. 178 unit tests green. |
| Doctor dashboard (triage, invites, QR, consent, audit) | Working on-device. |
| Appwrite backend | Provisioned: 5 collections, 38 attributes, 9 indexes. |
| Doctor → Appwrite push | **Confirmed working** — invite `X8S36V8R` reached the server. |
| Patient redeem across devices | **Untested.** This is the open item. |

Two phones, both on the current build:
`1c3aa67a` = Xiaomi 25057RN09I, Android 15 — **doctor** (`doctorh@gmail.com`, VERIFIED)
`5abebc6` = Redmi Note 10, Android 12 — **patient**

## 2. Verify the last mile

```bash
# after the patient enters a code on 5abebc6
python tools/appwrite/admin.py docs patient_links      # a row here == it worked
adb -s 5abebc6 logcat -d | grep DoctorSync             # named failure if not
```

`DoctorSync` is the log tag for every sync failure. It was added because the
original code swallowed all of them, which cost hours — **do not remove it.**

If the patient still sees "No invite matches that code", the likely causes in
order: no session (`blocked: no Appwrite session`), a 401 naming permissions
(an ACL is naming a non-session id somewhere), or the pull returning empty
(check `code` is stored uppercase on both sides).

## 3. Verification is manual, by design

Sign-up writes `PENDING`; nothing in the app produces `VERIFIED`. To unlock a
doctor dashboard for a demo, edit the device DB (the app must be stopped first,
and `/sdcard` is not readable by `run-as` — use `/data/local/tmp`):

```bash
adb -s <dev> shell am force-stop com.dermoai
adb -s <dev> exec-out run-as com.dermoai cat /data/data/com.dermoai/databases/dermoai.db > db
python -c "import sqlite3,time; c=sqlite3.connect('db'); n=int(time.time()*1000); \
  c.execute(\"UPDATE doctor_profiles SET verificationStatus='VERIFIED',verifiedAt=?,updatedAt=? \
  WHERE userId=(SELECT id FROM user_profiles WHERE email='doctorh@gmail.com')\",(n,n)); c.commit()"
adb -s <dev> push db /data/local/tmp/_db && adb -s <dev> shell \
  "run-as com.dermoai sh -c 'rm -f /data/data/com.dermoai/databases/dermoai.db-wal \
   /data/data/com.dermoai/databases/dermoai.db-shm; cat /data/local/tmp/_db > \
   /data/data/com.dermoai/databases/dermoai.db'"
```

A debug-only in-app toggle would be ~20 lines and is the obvious next
convenience. It was offered and not built.

## 4. Known limitations — say these out loud, do not let a judge find them

**Train/test contamination is unresolved.** The model scores 94.2% on 2,388 ISIC
clinical images but 82.79% on its own validation split. A model does not beat its
own validation on unseen data. The training dataset is undocumented and the
upstream README cites Kaggle sources, which are usually ISIC-derived. **Do not
quote 94%.** The one clean measurement is phone photos taken this week: the
12-class head got 1/11, and the gate lifted it to ~88% out-of-fold.

**MEL 77.8% / SCC 84.5%** even on that optimistic set — the two most dangerous
classes are the weakest. Property of the checkpoint, untouched by any of this.

**The healthy gate is validated on one person and one camera.** Cross-person
performance, and skin-tone bias in particular, is unmeasured. This is the single
most likely thing to break in front of a judge who tests it on their own hand.

**Appwrite reads are collection-wide.** Anonymous sessions mean a device cannot
name the counterparty in an ACL, so `patient_links` and `doctor_invites` are
readable by *any* signed-in user — live invite codes are enumerable, and
doctor↔patient pairings are visible. Writes are still restricted to the writer.
Honest framing: *"consent is enforced on writes; reads are collection-scoped
because anonymous client identity cannot name the other party."*

**Anonymous identity is per-install.** Reinstalling mints a new Appwrite user, so
document ACLs do not follow the human, and the same doctor on a second device is
a different principal.

## 5. Highest-value next steps, in order

1. **Finish testing patient redemption** (§2). Everything else is polish.
2. **Real Appwrite accounts at sign-up** instead of anonymous sessions. This one
   change fixes the ACL problem, the enumerability problem and the per-install
   identity problem together — they are all the same root cause.
3. **Healthy-gate photos from 2–3 other people**, ideally different skin tones.
   Cheapest possible credibility win; retrain with
   `tools/ml/train_healthy_gate.py`.
4. **Tests for the sync layer.** The agent that wrote it was cut off before
   writing any; it is the only substantial code here with zero coverage.
5. An Appwrite Function mediating invite lookup and revocation — the correct fix
   for §4's read problem, and a real piece of engineering to talk about.

## 6. Secrets

`tools/appwrite/local.env` holds the API key and is gitignored — verified not in
git history. `local.properties` holds endpoint/project/database ids (not secrets;
public in every Appwrite client) and is also gitignored. **Never commit either,
and never put an API key in the app** — an APK is readable with `unzip` and
`strings`.

Admin tooling: `python tools/appwrite/admin.py status | docs <collection> | wipe <collection>`.
