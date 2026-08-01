# DermoAI — Agent Guide

Production-quality Android dermatology companion. This file is the **implementation** source of truth for AI agents (phases, gates, build).

**Product & UI source of truth:** [`docs/PRD.md`](docs/PRD.md) (PRD 2.0 — detailed requirements, design system, screen inventory, acceptance criteria).  
When product intent and this file disagree, update the PRD; **phase order** in this file wins until the PRD roadmap is synced.

---

## Current Work — START HERE

**Continue from Phase 10.** Phases 1–9 are complete. Do not redo them. Do not skip phases.

| | |
|---|---|
| **Active phase** | **10 — Treatment tracker** |
| **Last completed** | Phases 7, 8, 9 — Timeline + SkinMind + Insights engine |
| **Next after Phase 10** | Phase 11 — Environmental alerts + analytics |

### What agents must do on `/init` or session start

1. Read this file.
2. Confirm build passes: `./gradlew :app:assembleDebug`
3. **Implement Phase 10 only** — Treatment tracker routines + reminders; do not jump ahead
4. When Phase 10 gate passes, update this section to **Phase 11** and repeat.

### What the running app shows today (Phase 6)

Auth-gated app with 5-tab bottom navigation, real Home dashboard, working scan capture + inference:

- Splash → 3-slide onboarding → email/password (+ Google button) → authenticated Main
- Bottom nav: Home · Timeline · **Scan** (center emphasized, camera permission + viewfinder) · SkinMind · More
- **Home tab:** time-based greeting, medical disclaimer, scan CTA card → opens scan flow, latest scan card, dynamic SkinMind chip (streak + completion state, tappable → opens SkinMind), insight cards from correlation engine
- **Timeline tab:** scan history grid with detail view (predictions, voice notes, delete), empty state, flash toggle, shutter, gallery upload → review screen → **results screen with ConvNeXt TFLite inference (top-k predictions, confidence bars, concern bands, disclaimer)** → save to Room
- Scan capture hides bottom bar
- **SkinMind tab:** 30s daily check-in with sliders + emoji + voice recording + celebration streak
- More hub: glass rows + sign-out

**Implement TFLite conversion + inference in Phase 6.** The captured photo path is ready to feed into the model pipeline.

---

## Mission

DermoAI helps users scan skin conditions, track progression, understand conditions, follow treatments, stay motivated, and share reports with doctors. It is **not** a diagnostic tool.

**Mandatory disclaimer** (show on Home, Scan results, Timeline detail, Doctor report preview):

> This app is an educational and awareness tool and does not replace a dermatologist.

Never use language like "diagnosis", "you have", or certainty claims. Use "possible condition", "confidence estimate", "educational information".

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 (dynamic color / Material You) |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Local DB | Room (offline-first source of truth) |
| Prefs | DataStore |
| Navigation | Navigation Compose (typed routes in Phase 3+) |
| Camera | CameraX |
| ML | TensorFlow Lite (on-device) |
| Backend | Firebase Auth, Firestore, Cloud Storage, Analytics, Crashlytics |
| Images | Coil 3 |
| Async | Coroutines + Flow + StateFlow |
| Background | WorkManager |
| Reports | `android.graphics.pdf.PdfDocument` |
| i18n | English + Hindi (`values/`, `values-hi/`) |

---

## Repository Layout

```
DermoAI/
├── app/                    # Application entry, navigation root, Hilt app module
├── core/
│   ├── common/             # AppResult, UiState, dispatchers, MedicalDisclaimer
│   ├── domain/             # Models, repository interfaces, use case contracts
│   ├── data/             # Repository implementations, DataStore, Firebase
│   ├── database/           # Room entities, DAOs, DatabaseModule
│   ├── ui/                 # Theme, design system, shared composables
│   ├── camera/             # CameraX abstraction
│   ├── ml/                 # TFLite inference pipeline
│   ├── analytics-engine/   # Rule-based InsightsEngine (ML-swappable)
│   └── reports/            # PDF generation
├── feature/
│   ├── auth/               # Splash, onboarding, sign-in/up
│   ├── home/               # Home dashboard
│   ├── scan/               # Camera + AI scan flow
│   ├── timeline/           # Photo timeline + compare
│   ├── skinnmind/          # Daily 30s check-in
│   ├── treatment/          # Routines + reminders
│   ├── wellness/           # Confidence journal, breathing, streaks
│   ├── analytics/          # Charts dashboard
│   ├── reports/            # Doctor share UI
│   └── settings/           # Preferences, export, delete account
├── tools/ml/               # PyTorch source weights + conversion notes
└── gradle/libs.versions.toml
```

### Layer Rules

1. **feature/** → may depend on **core/** only, never on other features directly.
2. **core/domain** → no Android framework imports except where unavoidable; no Firebase/Room.
3. **core/data** → implements domain repository interfaces; maps entities ↔ domain models.
4. **app** → wires navigation, application class, top-level DI only.
5. Every feature is a separate Gradle module — keep boundaries strict.

---

## Build Environment

```bash
# Required
export ANDROID_HOME=/home/light/Android/Sdk
# JDK 21 (Java 26 breaks AGP — configured in gradle.properties)
org.gradle.java.home=/home/light/.jdks/jdk-21.0.11+10

# Build
cd /home/light/projects/DermoAI
./gradlew :app:assembleDebug

# Output
app/build/outputs/apk/debug/app-debug.apk
```

| Setting | Value |
|---------|-------|
| `minSdk` | 26 |
| `targetSdk` / `compileSdk` | 36 |
| `applicationId` | `com.dermoai` |
| AGP | 8.9.2 |
| Kotlin | 2.1.21 |

After every phase: **build → run → fix compile errors → refactor**. No TODO placeholders. No mock data in production paths.

### Run on emulator

**Fast path** (host GPU on RTX 4050 — do **not** use `swiftshader`):

```bash
# Preferred launcher
/home/light/projects/DermoAI/tools/run-emulator.sh

# Or manually:
export ANDROID_HOME=/home/light/Android/Sdk
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
export QT_QPA_PLATFORM=xcb   # more reliable host GPU under Wayland

emulator -avd DermoAI_Pixel \
  -gpu host -accel on -no-audio -no-boot-anim \
  -cores 8 -memory 4096 &

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dermoai/.MainActivity
```

AVD `DermoAI_Pixel` is tuned for speed: host GPU, 8 cores, 4 GB RAM, 1080×1920@320dpi, no device frame.  
**Never** launch with `-gpu swiftshader_indirect` on this machine — that was the main lag source.

---

## ML Model

**Source repo:** https://github.com/IshanAsati/dermoai-final

| Asset | Location | Notes |
|-------|----------|-------|
| PyTorch checkpoint | `tools/ml/source/ce_ls_best.pth` | ConvNeXt-Base, 339 MB, 12 classes |
| Labels | `app/src/main/assets/ml/labels.txt` | Index-aligned with model output |
| Config | `app/src/main/assets/ml/model_config.json` | 224×224, ImageNet norm, melanoma index 5 |
| TFLite (Phase 6) | `app/src/main/assets/ml/skin_model.tflite` | **FP16, 169 MB — converted from ConvNeXt-Base** |

### 12 Classes

BCC, ACK, NEV, SEK, SCC, MEL, Acne, Hair Loss, Nail Fungus, Fungal, Vascular, Healthy

### Inference Interface

```kotlin
// core/domain — SkinInferenceEngine
suspend fun initialize(): AppResult<Unit>
suspend fun predict(bitmap: Bitmap): AppResult<InferenceResult>
```

Implementation: `core/ml/TfliteSkinInferenceEngine.kt`. Swappable via Hilt binding in `MlModule`.

---

## Firebase

- **Status:** Placeholder `app/google-services.json` — project not yet provisioned.
- `BuildConfig.FIREBASE_CONFIGURED = false` until real config is dropped in.
- Full sync (Firestore + Storage + account delete) is **Phase 13**.
- Auth (email + Google) is **Phase 2**.

---

## Delivery Phases

Work **one phase at a time**. Do not skip ahead.

**Agents: continue from Phase 3.** Mark each phase `DONE` in this table only after build + run gate passes.

| # | Phase | Status |
|---|-------|--------|
| 1 | Project architecture | **DONE** |
| 2 | Authentication | **DONE** |
| 3 | Navigation | **DONE** |
| 4 | Home UI | **DONE** |
| 5 | Scan (camera + capture) | **DONE** |
| 6 | Local AI inference (TFLite conversion) | **DONE** |
| 7 | Photo timeline + voice notes | **DONE** |
| 8 | SkinMind Sync | **DONE** |
| 9 | Correlation engine | **DONE** |
| 10 | Treatment tracker | **DONE** |
| 11 | Environmental alerts | **DONE** |
| 12 | Doctor share PDF | **DONE** |
| 13 | Firebase sync (placeholder) | **DONE** |
| 14 | Analytics dashboard | **DONE** |
| 15 | Wellness + Settings + de-slopification | **DONE** |
| 16 | Testing | **DONE** |

---

## Coding Standards

### Kotlin / Compose

- ViewModels expose `StateFlow<UiState<T>>`; one-off events via `Channel` or `SharedFlow`.
- Use `@HiltViewModel` + constructor injection.
- Repository pattern with interfaces in `core/domain`, implementations in `core/data`.
- Document every public file with a KDoc block.
- Match existing naming: `Dermo*` prefix for app-specific types, `*Entity` for Room, domain models without suffix.

### UI / Design

Target aesthetic: **Google Health + Pixel Weather + Nothing OS + Material You**.  
**Full design system, component inventory, copy deck, and screen layouts:** [`docs/PRD.md`](docs/PRD.md) §7–§12.

Quick rules (non-negotiable):

- Rounded corners (24–28dp cards); glass via `DermoGlassCard`; headers via `GradientHeader`
- `MedicalDisclaimerBar` on all clinically relevant screens (PRD §6)
- Never diagnose: use “possible condition”, “confidence estimate”, “educational information”
- Calm motion; 48dp touch targets; TalkBack; EN + HI strings
- No developer placeholder cards in user-visible paths after Phase 4
- Bottom nav: Home · Timeline · **Scan** (emphasized) · SkinMind · More (PRD §8)

### State & Errors

- Use `AppResult<T>` in domain/data layers.
- Use `UiState<T>` (Idle / Loading / Success / Error) in presentation.
- Fail gracefully with user-facing messages — never crash on missing model or Firebase.

---

## Key Files (Phase 1 baseline)

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/dermoai/DermoAIApplication.kt` | `@HiltAndroidApp` entry |
| `app/src/main/kotlin/com/dermoai/MainActivity.kt` | Single-activity Compose host |
| `app/src/main/kotlin/com/dermoai/navigation/DermoAppRoot.kt` | Root nav (expands Phase 3) |
| `core/ui/theme/DermoTheme.kt` | Material 3 + dynamic color |
| `core/ui/components/MedicalDisclaimerBar.kt` | Legal disclaimer banner |
| `core/database/di/DatabaseModule.kt` | Room + DAO providers |
| `core/data/preferences/UserPreferencesDataStore.kt` | Onboarding, userId, theme, locale |
| `core/ml/TfliteSkinInferenceEngine.kt` | On-device inference (needs TFLite file) |

---

## Agent Do / Don't

### Do

- Read this file and **continue from Phase 3** unless this section says otherwise.
- Keep diffs focused — only modify what the active phase requires.
- Reuse existing abstractions (`AppResult`, `UiState`, `DermoGlassCard`, etc.).
- Run `./gradlew :app:assembleDebug` before marking a phase complete.
- Preserve offline-first: Room writes first, Firebase sync later.
- Add Hindi strings alongside English for user-facing copy.

### Don't

- Don't claim the app diagnoses diseases.
- Don't add TODO placeholders or fake/mock repositories in production code.
- Don't generate all features in one step — follow the phase order.
- Don't use iText for PDFs (licensing) — use `PdfDocument`.
- Don't upgrade to Java 26 for Gradle builds.
- Don't create drive-by refactors or unrelated markdown files.
- Don't add dependencies without updating `gradle/libs.versions.toml`.

---

## Phase 2 Checklist — DONE

- [x] `AuthRepository` interface in `core/domain`
- [x] `FirebaseAuthRepository` in `core/data` (local session fallback for placeholder Firebase)
- [x] `ObserveAuthStateUseCase`, sign-in/up/out + onboarding use cases
- [x] Splash → 3-slide onboarding → email/password + Google Sign-In screens
- [x] Auth navigation guard in `DermoAppRoot` (unauthenticated → auth flow)
- [x] Persist `isOnboarded` + `userId` via `UserPreferencesDataStore`
- [x] Upsert `UserProfileEntity` in Room on successful auth
- [x] Gate `HomePlaceholderScreen` behind authenticated state
- [x] Gate: `./gradlew :app:assembleDebug` passes

### Phase 3 Checklist — DONE

See PRD §10.2 and Appendix A for full acceptance.

- [x] Typed navigation routes (Navigation Compose)
- [x] Bottom bar: Home, Timeline, **Scan** (center emphasized), SkinMind, More
- [x] Hide bottom bar on Scan capture sub-routes
- [x] More hub shells: Treatment, Wellness, Analytics, Reports, Settings
- [x] Wire feature module entry points (even if screens are shells)
- [x] Keep auth guard: unauthenticated users never reach tabs
- [x] Gate: `./gradlew :app:assembleDebug` passes; tabs navigable on device

### Phase 4 Checklist — DONE

See PRD §10.3 for full acceptance.

- [x] Replace `HomePlaceholderScreen` with real dashboard layout
- [x] Greeting header — time-based "Good morning/afternoon/evening, {name}"
- [x] MedicalDisclaimerBar visible without scrolling
- [x] SkinMind completion chip (pending state)
- [x] Primary CTA card — "Scan skin" full-width, teal gradient border → navigates to ScanCaptureRoute
- [x] Latest result card — empty state with educational blurb
- [x] Treatment today — empty "Add a routine" state
- [x] No developer "Architecture Foundation" cards
- [x] Gate: `./gradlew :app:assembleDebug` passes

### Phase 5 Checklist — DONE

See PRD §10.4 for full acceptance.

- [x] Camera permission rationale screen
- [x] CameraX preview use case (back camera) via `CameraCaptureManager`
- [x] Viewfinder with guide overlay ("Center the area of concern")
- [x] Shutter button (72dp, teal ring) with capture via coroutines
- [x] Image capture to app-private storage (`photos/` directory)
- [x] Review screen (image preview + retake/use buttons)
- [x] Flash toggle (auto/on/off cycle) with floating button
- [x] Scan entry screen with permission rationale and denial fallback
- [x] Hide bottom bar on all scan sub-routes
- [x] Gate: `./gradlew :app:assembleDebug` passes; photo capture works on emulator

### Phase 6 Checklist — DONE

- [x] Convert ConvNeXt-Base PyTorch model to TFLite (FP16, 169 MB)
- [x] Copy `skin_model.tflite` to `app/src/main/assets/ml/`
- [x] `TfliteInterpreterHolder` loads model from assets (memory-mapped, 4 threads)
- [x] `ImagePreprocessor` — bitmap → 224×224×3 ByteBuffer with ImageNet normalization
- [x] `ModelConfigLoader` + `ModelLabelsLoader` — parse config JSON + labels from assets
- [x] `SkinInferenceEngine` ↔ `TfliteSkinInferenceEngine` wired via Hilt
- [x] `ScanResultsScreen` — top-k predictions, confidence bars, concern band chips, disclaimer
- [x] `ScanViewModel` injects `SkinInferenceEngine`, runs inference on results screen
- [x] Room entities: `SkinScanEntity` + `ScanPredictionEntity` (DB v2, destructive migration)
- [x] DAOs: `SkinScanDao` + `ScanPredictionDao` with observe/upsert/delete
- [x] Save scan + predictions to Room on "Save to Timeline"
- [x] Gate: `./gradlew :app:assembleDebug` passes; app launches on emulator with model

---

## Room Schema (current)

```
user_profiles (id, email, displayName, createdAt, updatedAt, syncStatus)
skin_scans (id, userId, imagePath, thumbnailPath, capturedAt, note, bodyArea, rotation, voiceNotePath, createdAt, updatedAt, syncStatus)
scan_predictions (id, scanId, label, labelCode, confidence, rank, concernBand, createdAt)
daily_check_ins (id, userId, dateKey, skinFeel, itchDiscomfort, sleepQuality, stressLevel, newProductUsed, newProductNote, notes, voiceNotePath, createdAt, syncStatus)
treatment_routines (id, userId, name, reminderTime, createdAt, updatedAt, syncStatus)
routine_steps (id, routineId, productName, timeOfDay, sortOrder)
step_completions (id, stepId, routineId, completedAt, dateKey)
```

Expands in later phases: `daily_check_ins`, `treatment_routines`, `insight_records`, etc.

---

## Contact / Ownership

- **Model source:** [IshanAsati/dermoai-final](https://github.com/IshanAsati/dermoai-final)
- **Workspace path:** `/home/light/projects/DermoAI`