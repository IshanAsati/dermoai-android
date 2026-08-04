# DermoAI — Bug Hunt & Fix Documentation

Status of every finding below was **verified against the current code** (file:line cited).
Severity: **CRITICAL** (blocks core flow / crash) · **MAJOR** (wrong behavior, data loss) · **MINOR** (dead code, polish) · **A11Y** (accessibility) · **NOTE** (operational).

---

## B-01 · CRITICAL · Missing ML model asset — every scan fails at runtime

- **Location:** `app/src/main/assets/ml/` — contains only `labels.txt` + `model_config.json`; `ml/skin_model.tflite` is absent. Loader: `core/ml/.../TfliteInterpreterHolder.kt:104` (`MODEL_ASSET_PATH = "ml/skin_model.tflite"`), `loadModelFile` at `:67-76` uses `assets.openFd`.
- **Reproduce:** Launch app → Scan → capture/review → Analyze. Inference `initialize()` throws `FileNotFoundException` → UI shows "Model not available" (`ScanScreens.kt` error state).
- **Expected:** model loads and returns predictions. **Actual:** permanent error; scan feature is non-functional on-device.
- **Root cause:** the TFLite conversion from the PyTorch checkpoint (code comment at `TfliteSkinInferenceEngine.kt:19` calls it "Phase 6") was never committed.
- **Fix steps:**
  1. Convert the training checkpoint to TFLite (float32, input 224×224×3, output 12 logits matching `labels.txt` order).
  2. Place it at `app/src/main/assets/ml/skin_model.tflite`.
  3. Confirm `model_config.json` matches (input size, 12 classes, `melanomaIndex: 5`, `melanomaLogitBias: -1.2`).
  4. Until the binary exists, unit/instrumented tests should inject a fake `SkinInferenceEngine` (`core/domain/.../ml/SkinInferenceEngine.kt`) — the DI binding is in `core/ml/.../di/MlModule.kt:14-16`.
- **Verify:** `adb install` → scan a photo → results screen shows predictions; logcat has no `FileNotFoundException`.

---

## B-02 · MINOR · Dead "Play/stop voice" button in Timeline detail

- **Location:** `feature/timeline/src/main/kotlin/com/dermoai/feature/timeline/TimelineScreen.kt:327` — `IconButton(onClick = { /* Play/stop voice */ })`.
- **Reproduce:** Timeline → open a scan with `voiceNotePath != null` → tap the play arrow. Nothing happens.
- **Expected:** plays the recorded voice note (or the button is removed). **Actual:** no-op; TalkBack announces "Play" and nothing plays.
- **Root cause:** voice playback was never implemented; only recording exists.
- **Fix steps:**
  1. Inject a `MediaPlayer`-based player (or ExoPlayer) into `TimelineViewModel`.
  2. `fun togglePlayback(voiceNotePath: String)` — create player, `setDataSource`, `prepareAsync`, toggle `start()`/`pause()`; release in `onCleared()`.
  3. Track `isPlaying` state to swap the icon (`PlayArrow`/`Stop`).
  4. Alternative (cheaper): remove the row entirely if voice notes are out of scope.
- **Verify:** record a voice note → open detail → tap play → audio plays; rotate/leave screen without crash (player released).

---

## B-03 · MINOR · "Steps · Completions" literal placeholder in Treatment

- **Location:** `feature/treatment/src/main/kotlin/com/dermoai/feature/treatment/TreatmentScreen.kt:168` — `Text("Steps · Completions", ...)`.
- **Reproduce:** More → Treatment → any routine card shows the static subtitle.
- **Expected:** real counts, e.g. "3 steps · 5 completions". **Actual:** literal placeholder text.
- **Root cause:** subtitle never wired to `RoutineStepDao` / `StepCompletionDao`.
- **Fix steps:**
  1. In `TreatmentViewModel`, expose per-routine counts: `stepDao.observeByRoutineId(routineId)` count + `stepCompletionDao` count for the routine's step ids (observe as flows, combine).
  2. Render `"${stepCount} steps · ${completionCount} completions"` (add plurals strings).
- **Verify:** create a routine with 2 steps, complete 1 → card reads "2 steps · 1 completion".

---

## B-04 · MINOR · 9 dead `*PlaceholderScreen.kt` files + 4 unused imports

- **Location:** `feature/{analytics,home,reports,scan,settings,skinnmind,timeline,treatment,wellness}/src/main/kotlin/.../*PlaceholderScreen.kt` (9 files, zero call sites — `grep 'PlaceholderScreen('` matches nothing). Unused imports: `app/src/main/kotlin/com/dermoai/navigation/DermoAppRoot.kt:19,26,31,44`.
- **Reproduce:** code navigation shows no references; dead weight in every build.
- **Fix steps:**
  1. `git rm` the 9 files (keep `ScanPlaceholderScreen.kt` only if referenced — it is not).
  2. Remove the 4 imports from `DermoAppRoot.kt`.
  3. Rebuild to confirm no unresolved references.
- **Verify:** `./gradlew.bat assembleDebug` green; `grep -r "PlaceholderScreen" app/src` empty.

---

## B-05 · MINOR · `VioletAccent` is literally teal (legacy alias noise)

- **Location:** `core/ui/src/main/kotlin/com/dermoai/core/ui/theme/DermoColors.kt:59` — `val VioletAccent = Color(0xFF0D9488) // was purple, now teal`. Used at `TimelineScreen.kt:323,328`, `ReportScreen.kt:125` (`Open PDF` button).
- **Reproduce:** read-only — confusing naming; a future dev expects purple.
- **Fix steps:**
  1. Replace `DermoColors.VioletAccent` with `DermoColors.TealAccent` at the 3 call sites (add `TealAccent` to the color-mapping `when` at `DermoColors.kt:79` if missing).
  2. Delete the alias.
- **Verify:** `assembleDebug` green; no `VioletAccent` references remain.

---

## B-06 · MINOR · `OnboardingProfileStore` is dead code

- **Location:** `feature/auth/src/main/kotlin/com/dermoai/feature/auth/OnboardingProfileStore.kt` (whole file) — DataStore-backed store with zero callers.
- **Root cause:** onboarding originally collected 18 fields but never persisted them. **This delivery fixed the persistence** by wiring the collected profile into `UserProfileDetailsEntity` (Room) via `OnboardingViewModel.complete(profile)` — so this DataStore store is now redundant.
- **Fix steps:**
  1. Delete `OnboardingProfileStore.kt` and the `Context.onboardingStore` extension.
  2. Grep for `onboardingStore|OnboardingProfileStore|saveField` — confirm zero references.
- **Verify:** `assembleDebug` green; onboarding "Done" still persists to Room (`user_profile_details` table).

---

## B-07 · MINOR · Dead test-time-augmentation config

- **Location:** `core/ml/src/main/kotlin/com/dermoai/core/ml/preprocessing/ImagePreprocessor.kt:36-39` (`horizontalFlip` — no callers) and `app/src/main/assets/ml/model_config.json:11` (`useTestTimeAugmentation: true`).
- **Reproduce:** config promises TTA; inference never uses it.
- **Fix steps (pick one):**
  1. Implement TTA: run inference on original + horizontally flipped input, average the softmax outputs in `TfliteInterpreterHolder.runInference` (flip requires a 90°-rotated bitmap — reuse `ImagePreprocessor.horizontalFlip`).
  2. Or set `useTestTimeAugmentation: false` in the config and delete `horizontalFlip`.
- **Verify:** after (1), same-image predictions are equal-ish before/after TTA; after (2), config matches code.

---

## B-08 · MINOR · `ReportScreen.onBack` parameter is never used

- **Location:** `feature/reports/src/main/kotlin/com/dermoai/feature/reports/ReportScreen.kt:48`; wired at `app/.../DermoAppRoot.kt` (`onBack = { navController.popBackStack() }`).
- **Reproduce:** open Doctor Report — no back affordance rendered (only system back works).
- **Fix steps (pick one):** add a back arrow to the screen (see `GradientHeader` `trailing` slot) and call `onBack`; or drop the parameter and the wiring.
- **Verify:** UI shows a working back control (or signature is clean).

---

## B-09 · MINOR · Two different "retake" semantics in Scan flow

- **Location:** `ScanResultsScreen` "Retake" → `navController.navigate(ScanTab)` with full reset (`DermoAppRoot.kt`, `onBack`); `ScanReviewScreen` "Retake" → `popBackStack()` back to camera capture (`DermoAppRoot.kt`, `onRetake`).
- **Reproduce:** after results, "Retake" drops the photo entirely; system-back from review goes to the camera — users get inconsistent behavior for the same word.
- **Fix steps:** make both go to `ScanCaptureRoute` (pop to it), keeping the photo for a "re-crop" option only on review; or rename results-button to "New scan".
- **Verify:** both buttons land on the camera screen with the same back-stack shape.

---

## B-10 · MINOR · Header inconsistency on scan entry/review

- **Location:** `feature/scan/.../ScanScreens.kt:333` — `ScanEntryScreen` rolls a custom `Row` header (`Text("Scan", ...)`); `ScanReviewScreen` (line 713) has no header at all; only `ScanResultsScreen:834` uses `GradientHeader`. (`TimelineDetailScreen` already uses `GradientHeader`.)
- **Fix steps:** replace the custom header with `GradientHeader("Scan")` (keep the dark camera overlay untouched); give `ScanReviewScreen` a `GradientHeader("Review photo")`.
- **Verify:** visual pass — three scan screens share the same header treatment.

---

## B-11 · A11Y · `labelSmall` semantic colors fail AA contrast on light fills

- **Location:** `ScanScreens.kt` `ConcernBandChip` (teal/amber/coral text at ~10% tint fills), `TimelineScreen.kt:310-312` (band text via `labelSmall`), `AnalyticsScreen.kt:140` (label color).
- **Measured:** teal ≈3.3:1, coral ≈3.1:1 on white — fails AA for small text.
- **Fix steps:**
  1. Use the dark text tokens that already exist — `CoralText`, `AmberText`, `SageText` (TimelineDetail already does this) — and/or render the band as a **filled** chip: text in `onPrimary` on the full-strength semantic color.
  2. Sweep remaining `onSurfaceVariant.copy(alpha = 0.6-0.7f)` caption uses to `DermoColors.Slate`.
- **Verify:** run a contrast check (e.g. axe/skia goldens) on the results screen in light + dark.

---

## B-12 · A11Y · Report date-range selection conveyed only by style

- **Location:** `feature/reports/.../ReportScreen.kt:64-76` — selected range uses `NeuSurfaceStyle.Inset` + tint; no `selected` semantics.
- **Fix steps:** add `Modifier.selectable(selected = rangeDays == days, role = Role.RadioButton)` (or `semantics { selected = ... }`) to each chip.
- **Verify:** TalkBack announces "30d, selected".

---

## B-13 · NOTE · Location privacy for the dermatologist finder

- **Location:** `feature/finder/.../data/DermatologistRepository.kt` — user's coarse lat/lon is POSTed to `https://overpass-api.de/api/interpreter`; OSM tile servers observe the map viewport.
- **Action:** disclose in the privacy policy ("third-party geo lookup via OpenStreetMap/Overpass, no account data shared"), and keep the runtime permission prompt as the only entry (already the case — `FinderScreen`).
- **Rate limits:** Overpass tolerates light use; keep one query per search (already the case: no polling/auto-refresh).

---

## B-14 · NOTE · Emulator ANR/lag is environmental, not the app

- **Observation:** cold-boot ANR storms on this machine are caused by the guest `android.hardware.sensors-service.multihal` spinning at ~95% CPU (known emulator bug).
- **Fix recipe:** `adb root && kill -9 $(pidof android.hardware.sensors-service.multihal)` after boot (`tools/run-emulator.sh` does this). App itself renders smooth.

---

## Previously reported findings — VERIFIED FIXED (kept for the record)

| Finding | Where fixed |
|---|---|
| Home "Latest scan" card always empty | `HomeViewModel` + `SkinScanDao` |
| Gallery upload swallowed errors / never navigated | `ScanEntryScreen.onPhotoPicked` → `ScanReviewRoute` |
| "Analyzing…" text-only state | spinner rendered (`ScanScreens.kt`) |
| Camera failure / permission denial silent | overlay + inline messages |
| Timeline delete swallows errors & navigates back | `TimelineViewModel.deleteScan` returns Boolean; failure shown, stays (`TimelineScreen.kt:256-261,356-364`) |
| Timeline detail broken on null/deleted scan | "not found" state + early return; buttons gated on `scan != null` (`TimelineScreen.kt:281-291,367`) |
| Report PDF `Ready` never reset on range/toggle change | `ReportScreen.kt:72,87` call `viewModel.reset()` |
| Breathing unbounded `while(true)` | capped `for (cycle in 1..MAX_CYCLES)` (`BreathingScreen.kt:78`) + auto-stop + liveRegion |
| Analytics 9sp inline text | `labelSmall` (`AnalyticsScreen.kt:140`) |
| Unlabeled Settings/Reports Switch rows | `contentDescription` semantics |
| Onboarding demographics collected but discarded | **fixed in this delivery** — `OnboardingViewModel.complete(profile)` → `UserProfileDetailsEntity` (Room) |
| `DermoSpacing` dead token contract | removed |
| Env-fetch race / stale cache | fixed in `HomeViewModel` |
| Icon centering in wells (15 sites) | `Box(Modifier.fillMaxSize(), contentAlignment = Center)` |
| White-halo shadow inversion (`NeuShadow.kt`) | shadow directions swapped + softened |

---

## Suggested fix order

1. **B-01** (unblocks the core scan feature end-to-end).
2. **B-11, B-12** (accessibility, cheap, high value).
3. **B-02, B-03** (visible dead UI).
4. **B-04 – B-10** (cleanup; low risk).
5. **B-13** (privacy wording — do before any store release).
