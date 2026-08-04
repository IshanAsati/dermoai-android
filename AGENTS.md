# DermoAI — UI/UX Analysis (condensed)

## Design system (core/ui) — neumorphic (Soft UI)
- Neumorphic recipe: mid-tone base `Canvas #DDE1E6`, raised fill `CardWhite #EAECF0` (darker than base — depth comes from dual shadows, not brightness), inset well `TintSweep #F4F6F8`.
- Dual shadows on every raised element: `ShadowHi #FFFFFF` (top-left light) + `ShadowLo #B5B9C0` (bottom-right dark); pressed/inset state swaps to inner shadow + `TintSweep` fill at 150ms.
- `DermoColors`: teal `#0D9488` accent, slate/ink neutrals, coral/sage/amber semantic, dark-mode set. Violet deliberately removed.
- Dark mode keeps slate surfaces but adds soft depth: `DarkShadowHi #26324B`, `DarkShadowLo #0A101F`, `DarkCard #232F45` raised.
- `DermoTypography`: full M3 scale, SansSerif, no inline `sp` anywhere — strong discipline. (Future polish: Plus Jakarta Sans, not bundled.)
- `DermoSpacing` (`Sp.*`): 8dp tokens, documented as mandatory — **zero usage in `feature/`** (verified by grep). Dead contract.
- `DermoTheme`: light/dark + dynamic color (Android 12+), user overrides in Settings; now also passes `Shapes` (medium = 14dp neumorphic radius).
- Primitives: `NeuSurface` (raised/inset), `NeuButton`/`NeuIconButton`/`NeuFloatingActionButton`, `NeuShadow` modifiers (`neumorphElevated` / `neumorphInset` / `innerShadow`). Shared components reworked on top: `DermoGlassCard` → raised `NeuSurface`; `GradientHeader` (teal accent bar, carved bottom edge); `MedicalDisclaimerBar` (inset well).
- Shell: 5-tab bottom bar with center-emphasized teal Scan tab; typed routes; auth guard; bar hidden on camera/detail.

## Neumorphism guardrails
- Raised = `CardWhite` fill + dual shadows; pressed/inset = `TintSweep` + inner shadow. Camera/crop overlays and the Splash gradient stay dark/unchanged.
- Contrast is the style's known weakness — the retheme must not reintroduce the contrast bugs below:
  - Body text `Ink #0F172A` on base ≈ 11:1 ✓; `Slate #475569` ≈ 5.3:1 ✓; `Muted #94A3B8` ≈ 2.3:1 ✗ — never on the base, bump captions to `Slate`.
  - Small semantic labels (teal `labelSmall`, coral) only on filled backgrounds with dark-enough tone.
- Semantics preserved in all `Neu*` components (`Role`, `enabled`, `onClick`) so TalkBack behavior is unchanged.

## Strengths
- Disclaimers everywhere; typography discipline; mostly no hardcoded colors; touch targets mostly ≥48dp; scan crop UX thoughtful; state machines in scan + reports.

## Critical dead-ends — ALL FIXED (historical; see Priorities #3)
1. ~~Home "Latest scan" card always shows empty~~ — fixed: `HomeViewModel` + `SkinScanDao`, thumbnail/date/click → `TimelineDetailRoute`.
2. ~~Gallery upload: copies file, swallows exceptions, never navigates~~ — fixed: `ScanEntryScreen.onPhotoPicked` → `ScanReviewRoute` + inline error.
3. ~~Scan "Analyzing…" state renders text only~~ — fixed: spinner present.
4. ~~Camera capture failure and permission denial are silent~~ — fixed: capture-failure overlay + permission-denied + gallery-error messages.

## Missing state handling
- Home/Timeline/Treatment/Analytics/Settings: no loading/error/empty states; flash-of-empty on first load; no shimmer skeletons (PRD mandates them).
- Timeline delete swallows errors, still navigates back; Treatment CRUD has no failure path; Home env fetch races and reads stale cache.
- Timeline detail renders broken UI for a null/deleted scan — Delete/Record still enabled.
- Report PDF `Ready` never reset on range/toggle change → stale report shown as current.

## Accessibility
- Contrast: teal ~3.3:1, coral ~3.1:1 on white (fail AA, used at `labelSmall`); pervasive `onSurfaceVariant.copy(alpha = 0.6-0.7f)` on small text.
- Unlabeled `Switch` rows (reports/settings); report date-range selection conveyed by color only.
- Breathing circle has no accessible/live-region alternative; SkinMind mood selector FIXED (unselected uses plain `onSurfaceVariant` since retheme; icons now TalkBack-labeled with selected state).
- `NudgeButton` is 48dp (meets target — earlier "36dp" note was stale); onboarding Back/Next ~40dp; flash toggle says only "Flash" to TalkBack.

## Performance
- `BreathingScreen.kt:67` — unbounded `while(true)` ~15Hz state emissions, full Canvas recompose per tick; no cap/auto-stop/onDispose. Battery drain.
- Analytics charts: 9sp inline text (`TextStyle(fontSize = 9.sp)`), generic contentDescriptions, no data.

## Consistency / correctness
- Two "retake" semantics (results → Scan tab vs system-back → crop); duplicate rotate affordances.
- `deleteStep` FIXED (deletes only the step, renumbers survivors — no orphaned `StepCompletion` rows); move-up/down no-op at boundaries; "Steps · Completions" literal placeholder remains.
- Timeline feature now localized (`feature/timeline/res/values/strings.xml`, 21 strings); streak string pluralized in Home + SkinMind.
- Header inconsistency: scan entry/review + timeline detail roll custom headers instead of `GradientHeader`.
- Legacy alias noise: `VioletAccent` is literally teal (DermoColors.kt) but used as SkinMind brand accent.
- Dead code: 8 unreachable `*PlaceholderScreen.kt` + unused imports in `DermoAppRoot`; `ReportScreen.onBack` unused (no back affordance); dead no-op Play button in TimelineDetail. `toggleDynamicColor()` NOW surfaced in Settings ("Dynamic color (Android 12+)" row).

## Priorities — status (updated after last task run)
1. **Neumorphic retheme** — tokens + `Neu*` primitives + feature sweep. **IMPLEMENTED + compiles (`assembleDebug` green).** Visual verification on emulator: attempted headless (AVD `dermoai_test`, `-gpu swiftshader_indirect` and `-gpu host`) — app installs, launches, and draws frames (HWUI logs completed frames, no crash), but `screencap`/`emu screenrecord` return black/primary-RGB garbage on this machine (gfxstream capture broken headless). **NOW VISUALLY VERIFIED on-device (windowed emulator, `-gpu host`):** splash, onboarding, auth, Home light + dark all render the neumorphic palette correctly — Canvas #DDE1E6 base (~92% onboarding, 31% Home), raised CardWhite cards, TintSweep inset wells, teal accents, dual shadows, dark mode DarkCanvas/DarkCard/DarkShadowHi/Lo all confirmed via pixel analysis of screencaps. Env alert card + 5-tab bottom bar confirmed.
   - Emulator gotcha on this machine: cold-boot ANR storms are caused by the guest's `android.hardware.sensors-service.multihal` spinning at ~95% CPU (known emulator bug), NOT by the app. **FIX (verified): DO NOT kill/`ctl.stop` the HAL — on this API 36 image `system_server` hard-depends on `android.hardware.sensors.ISensors`; if the HAL is gone it retries forever and never registers the `activity` service (boot hangs if stopped pre-boot; framework dead if stopped post-boot). Instead contain it:** `adb root && adb shell 'PID=$(pidof android.hardware.sensors-service.multihal); taskset -p 1 $PID; renice -n 19 -p $PID'` (pin to CPU 0 + lowest priority; the spin then settles to ~10% of one core after boot). Use `tools/run-emulator-win.sh` (Windows launcher, does this automatically). Also: quickboot snapshots get corrupted when an emulator is killed mid-boot — the launcher uses `-no-snapshot-load -no-snapshot-save`; MSYS path-mangles `/sdcard/...` in `adb pull`/`screencap` — prefix with `MSYS_NO_PATHCONV=1`.
2. **Bug hunt** — code-level fixes done (see below). Remaining: on-device visual pass (dual shadows, pressed/inset states, dark mode) when a windowed emulator is available. This machine: JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`, SDK at `C:\Android\Sdk`, run with `set JAVA_HOME=...&& gradlew.bat assembleDebug`.
3. **DONE** — gallery → review wired (`ScanEntryScreen.onPhotoPicked` → `ScanReviewRoute`); Home "Latest scan" card real (HomeViewModel + `SkinScanDao`, thumbnail/date/click → `TimelineDetailRoute`, loading placeholder); analyzing spinner verified present; capture-failure overlay + camera-permission-denied + gallery-error inline messages added.
4. **DONE** — `Sp.*` tokens deleted (only 2 real usages in `DermoGlassCard`, inlined to 20.dp; `DermoSpacing.kt` removed).
5. **DONE** — loading states for Home (placeholder card), Timeline (spinner + "Scan not found" detail state + delete failure surfaced), Treatment (spinner), Analytics (spinner); empty states already existed in Timeline/Treatment/Analytics.
6. **DONE (partial)** — Timeline + Report disclaimers no longer use 0.6-alpha `onSurfaceVariant`; Analytics 9sp inline label → `labelSmall`. Remaining alpha uses are decorative or dark camera overlays. Teal/coral small-label contrast on light fills still fails AA (design-token issue, not yet resolved).
7. **DONE** — breathing capped at 5 cycles (~80s) with auto-stop + "Session complete" state; phase label is a `liveRegion` (Polite) with phase + cycle announcement.
8. **DONE** — Timeline localized (new `res/values/strings.xml`); streak plurals in Home + SkinMind (`home_streak_days`, `skinnmind_streak_days`); Switch rows labeled via `contentDescription` semantics in Settings (3) and Reports (3).

**Resume here — DESIGN DIRECTION (user request, settled):** keep **neumorphism**, fix the colors. Final scheme: **"Pine & Cream" neumorphism** — warm sand base `Canvas #EAE4DA`, warm cream raised fill `CardWhite #F4EFE7`, warm sand inset wells `TintSweep #E2DCD1`, warm white highlight + warm taupe shadow (`#FFFFFF` / `#C9C1B4`), accent pine `Teal #1E6E5C` (alias `TealAccent`/`VioletAccent` → pine, 6.1:1 on white), pale pine `TealLight #D9EDE4`, deep pine text `TealText #123F33`, warm ink `#202B26`, secondary `Slate #55645C` (≥4.9:1), semantic coral/sage/amber + `CoralText/AmberText/SageText` unchanged. **Typography (per ui-ux-pro-max skill):** serif display roles (displayLarge→headlineSmall = `FontFamily.Serif`, editorial wellness personality; future polish: bundle Lora variable font) + SansSerif body/UI. **ICON-CENTERING BUG FIXED:** `Box(contentAlignment = Center)` without `Modifier.fillMaxSize()` places children top-left in a `BoxScope` — fixed all 15 wells (Home 9, Scan 4, Timeline 1, Journal 1) to `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)` + bottom-bar Scan chip icon `Modifier.align(Alignment.Center)`; verified on-device (avatar letter center offset 0,1px; hero camera icon center exact). **WHITE-HALO BUG FIXED (NeuShadow.kt):** `neumorphElevated` had INVERTED shadow directions — `translate(+e,+e)` sweeps the stroke along the top/left interior (dark rim there) and `translate(-e,-e)` put the white highlight along the bottom/right interior, so every raised element (cards, buttons, FABs) showed a white bottom-right rim ("weird white thing"). Swapped so light = top/left interior, dark = bottom/right interior; also softened: elevation 8→5dp, stroke e*1.4→e*1.1, alphas 0.55/0.45→0.35/0.30 (full-strength white strokes read as a harsh halo on warm cream); `neumorphInset` depth 2→1.5dp, alphas capped 0.55. Verified on-device: top-edge rim flipped from dark (−34) to light (+28). Skill-validated: neumorphism = best-fit style for health/wellness; design system persisted at `design-system/dermoai/MASTER.md` (+ FINAL DECISIONS overrides). Home = neumorphic template (verified on emulator): GradientHeader w/ avatar monogram (top-right, inset pale-pine circle), pale-pine raised ScanHeroCard w/ inset pine well, raised cards for latest-scan/alert(+time label)/SkinMind(🔥 pill)/insight/treatment, inset-well disclaimer + bottom bar with raised pine Scan chip. `DermoTheme.LightColorScheme` remapped to warm neumorphic tokens; `ShimmerBox` = inset-fill bars on raised cards. **Not yet converted to warm palette** (still old beige-gray neumorphic): Timeline, Treatment, Analytics, SkinMind, Scan, Reports, Settings, auth — they pick up the new accent via `DermoColors` but their `NeuSurface` fills come from the theme, so they're already warm; verify visually. Dark scheme untouched (slate neumorphic). Dynamic color defaults `false`. Env-fetch race FIXED. Shimmer skeletons DONE. Small-label contrast DONE (`textOnLight`). **Lag fix:** emulator ANR/lag storms = `sensors-service.multihal` CPU spin — contained by pinning to CPU 0 + renice (see emulator gotcha above); Windows launcher `tools/run-emulator-win.sh` does it automatically; app itself renders smooth (gfxinfo unreliable on this AVD).

Full verbose analysis: desktop `readme.md`. Original project AGENTS.md: `AGENTS.md.bak`.


## Delivery status (IN PROGRESS — user stopped mid-verification)
**DONE (code complete; BUILD-VERIFIED end-to-end as of this session):**
- **Phase 1 severity messaging:** `SeverityMessageEngine` (`core/domain/.../severity/`) — tier→guidance, confidence %, runner-up ≥15%; `SeveritySummaryLine` in `ScanScreens.kt`; templates in `feature/scan/res/values/strings.xml`. Unit tests pass.
- **Phase 2 rule-based filter:** `RuleBasedFilterEngine` (`core/domain/.../rules/`, `@Inject`) — 8 escalate-or-annotate rules, never downgrades, transparent `RuleAdjustment`s; storage wired (`UserProfileDetailsDao` bound; onboarding persists; Settings "Skin profile" dialog); applied in `ScanViewModel.runInference(photoPath, userId)` → `ruleAdjustments` + `referralFlagged`. Unit tests pass (24 total in core/domain incl. severity).
- **Phase 3 FAQ:** `feature/faq` module — bundled JSON (60 entries, 9 categories) at `assets/faq/faq_content.json`, `FaqRepository` (token search), `FaqViewModel` (exposes `search`), `FaqScreen` (search + category chips + expandable cards + empty/error states), `FaqRoute` + More-hub row.
- **Phase 4 finder:** `feature/finder` module — osmdroid + OkHttp + Overpass (`DermatologistRepository`: dermatology query → broad `amenity=doctors` fallback, haversine distances, rate-limit-safe), `FinderScreen` (map, markers, list, detail card w/ sanitized tel:/geo: intents, permission flow), `FindDermatologistRoute`, More-hub row, **consult CTA** on scan results when `referralFlagged`.
- **Phase 5 bug hunt:** `docs/BUGS.md` written — 14 verified findings (B-01 critical missing model asset … B-14 emulator note) + "previously reported, now FIXED" table + fix order. Built-in `review` + `security-review` passes run on the diff; all findings fixed: `FaqViewModel.search` delegate, `@Inject` on rule engine, MapView `onDetach()` + cacheDir tile cache + center-once, auth-race-safe profile persistence (`withTimeoutOrNull` + `first { it != null }`), `runCatching` around onboarding persist, `Uri.encode`/phone sanitization.
- **Build env fixes:** `app/build.gradle.kts` — google-services plugin now applied conditionally (json is gitignored/absent locally); `local.properties` created (`sdk.dir=C\:\\Android\\Sdk`).
- **Stale AGENTS.md findings re-verified:** Report `Ready` reset, Timeline null-scan + delete failure, breathing cap, Analytics 9sp, Switch labels, placeholders → see FIXED table in `docs/BUGS.md`.

**NOT DONE / NEXT STEPS:**
- **Final verification build CONFIRMED ✅** (this session): `export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot" && ./gradlew.bat :core:domain:testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL in 1m 28s** (742 tasks: 13 executed, 729 up-to-date); 24/24 core/domain unit tests pass (14 `RuleBasedFilterEngine` + 10 `SeverityMessageEngine`); `app-debug.apk` produced. Only remaining warning: pre-existing `Locale(String)` deprecation in `MainActivity.kt:61`.
- Lint pass: **DONE ✅** (this session) — `./gradlew.bat lintDebug` → **BUILD SUCCESSFUL**, 0 errors. Fixed: `LocationProvider.kt` `MissingPermission` false positive (guard moved inside `withContext` lambda + `@SuppressLint` — lint can't trace the runtime `checkSelfPermission` through the lambda); MissingTranslation for `home_latest_view`/`home_latest_thumb_desc`/`home_streak_days` (hi/mr/bn) and `more_faq`/`more_find_dermatologist` (hi/te/mr/bn/ta). 98 remaining warnings are all informational (dep version bumps, AppBundleLocaleChanges).
- Emulator pass: **DONE ✅** (this session) — AVD `dermoai_test` (API 36, `-gpu host`, NVIDIA RTX 4050): APK installs, launches, 0 FATAL/ANR. Smoke-tested Home (warm Pine & Cream palette confirmed via screencap pixel analysis), More hub, FAQ (search+expand), Finder (graceful no-location state — emulator GPS `geo fix` doesn't propagate on API 36, environmental), Scan entry/camera/crop, B-01 graceful "Model not available", Timeline, SkinMind. Screenshots in `verification/`. Details + not-verified list in `docs/BUGS.md` "Emulator pass" section. **Not verified on-device:** Overpass map fetch (needs real GPS fix), dark-mode toggle visual.
- `app/src/main/assets/ml/skin_model.tflite` MISSING (B-01) — inference code complete, scan fails at runtime until the binary is added; blocks live scan demo.
