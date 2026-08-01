# DermoAI — Product Requirements Document (PRD)

| Field | Value |
|-------|--------|
| **Product** | DermoAI |
| **Platform** | Android (Kotlin, Jetpack Compose) |
| **Version** | PRD 2.0 |
| **Status** | Active — source of truth for product & UI |
| **Last updated** | 2026-07-14 |
| **Companion agent guide** | [`../AGENT.md`](../AGENT.md) (implementation phases) |
| **Workspace** | `/home/light/projects/DermoAI` |

---

## 1. Executive summary

DermoAI is a **premium, offline-first dermatology companion** for Android. Users capture skin photos, receive **educational** on-device AI estimates, track change over time, log daily context via voice, receive GPS-based environmental warnings (UV, heat, humidity), follow simple treatment routines, and export **doctor-ready PDF reports**.

It is **not** a diagnostic medical device. Every clinically relevant surface carries a fixed disclaimer and uses careful language (“possible condition”, “confidence estimate”, “educational information”).

**Product north star:** *Feel as calm and trustworthy as Google Health, as glanceable as Pixel Weather, as refined as Nothing OS, and as personal as Material You — while never pretending to be a dermatologist.*

### 1.1 Why this PRD exists

The Phase 1–2 codebase has solid architecture but thin product definition. UI still shows developer placeholders. This document:

1. Locks **what** we build and **why** (product)
2. Specifies **how it should look and feel** (design system + screen inventory)
3. Defines **acceptance criteria** per feature so phases are not ambiguous
4. Aligns agents and humans on **language, safety, and quality bar**

Implementation order remains the phased plan in `AGENT.md`. This PRD expands each phase into implementable requirements.

---

## 2. Problem statement

### 2.1 User problems

| # | Problem | Cost of status quo |
|---|---------|-------------------|
| P1 | People notice skin changes but wait weeks for a dermatology appointment | Anxiety, delayed action, poor photo history for the doctor |
| P2 | Phone cameras are used ad-hoc; photos are scattered and unlabelled | No progression story; doctors get random gallery dumps |
| P3 | Internet “skin checkers” scare users with certainty claims or require cloud upload | Trust collapse; privacy risk for sensitive body images |
| P4 | Daily factors (sleep, stress, new products) are never linked to skin flare patterns | Users miss simple correlations they could act on |
| P5 | Sharing progress with a doctor is friction-heavy | Incomplete visits; repeated storytelling |

### 2.2 Opportunity

Ship an **on-device-first** companion that:

- Keeps photos and inference **local by default**
- Turns scattered photos into a **timeline + report**
- Educates without diagnosing
- Warns users about environmental skin risk factors based on **GPS location** (UV index, extreme heat, humidity)
- Lets users log notes **by voice**, capturing context hands-free during routines
- Uses **auto-flash** for consistent scan quality in low light
- Feels **premium and calm**, not clinical-clinical or gimmicky-AI

---

## 3. Goals & non-goals

### 3.1 Goals (v1 product)

| ID | Goal | Success signal |
|----|------|----------------|
| G1 | First scan → educational result in **under 10 seconds** on mid-range after model warm | p50 inference UX < 10s including capture confirm |
| G2 | User can build a **30-day photo timeline** with compare | ≥1 compare session per active user / month (post-launch metric) |
| G3 | User can complete a **30-second daily SkinMind check-in** | Day-7 retention of check-in habit ≥ 40% among users who completed onboarding |
| G4 | User can export a **PDF doctor report** offline | Report generates without network |
| G5 | App never claims diagnosis; disclaimer always present on clinical surfaces | QA checklist 100% pass |
| G6 | English + Hindi for all user-facing strings in shipped features | No hardcoded EN-only UI strings in features |
| G7 | Offline-first: core flows work with airplane mode | Scan, timeline, check-in, PDF work offline |
| G8 | Environmental skin alerts from GPS | UV, heat, humidity warnings trigger home card + system notification |
| G9 | Voice notes for scan and check-in context | User can record short voice memo instead of typing |

### 3.2 Non-goals (v1)

- Not a regulated diagnostic device / FDA / CDSCO clearance path in v1
- No telemedicine video consults
- No prescription or pharmacy checkout
- No social feed / public sharing of skin photos
- No iOS in v1
- No cloud-required inference (on-device is primary)
- No multi-user profiles on one device in v1 (single active account)
- No AR body mapping

### 3.3 Deferred (post-v1)

- Wearable / sleep API integrations
- Multi-lesion body map
- Clinician portal
- Push-driven treatment adherence coaching beyond local notifications

---

## 4. Personas & jobs-to-be-done

### 4.1 Primary persona — **Asha, 28**

- Urban professional, bilingual (EN/HI), Pixel/Samsung Android
- Mild acne + occasional new moles anxiety after Google rabbit-holes
- Wants privacy; hesitant to upload face/body photos to random apps
- JTBD: *“When I notice a change, help me document it calmly and prepare for my dermatologist — without freaking me out.”*

### 4.2 Secondary persona — **Rahul, 35**

- Managing eczema flares; already has a routine from a doctor
- Wants reminders + “is this better than last month?”
- JTBD: *“Help me stick to treatment and see whether stress weeks match flare weeks.”*

### 4.3 Tertiary persona — **Meera, 52**

- Parent managing a child’s (or own) chronic skin condition
- Needs large touch targets, clear Hindi, simple PDF for clinic
- JTBD: *“Give me a simple report I can show the doctor without technical jargon.”*

### 4.4 Anti-persona

- Users seeking instant medical certainty or emergency care (redirect copy, not features)
- Clinical users needing full EHR (out of scope)

---

## 5. Product principles

1. **Education over certainty** — Never “you have X”. Always ranked possibilities + confidence + “see a doctor if…”.
2. **Calm over hype** — No red alarm UI for model output unless severity band is high **and** copy still avoids diagnosis.
3. **Private by default** — Photos local; cloud sync explicit and later (Phase 13).
4. **Offline-first** — Room is source of truth; network is enhancement.
5. **Glanceable progress** — Home answers “what should I do today?” in one screen.
6. **Doctor-ready artifacts** — Timeline and PDF are first-class, not afterthoughts.
7. **Accessible premium** — 48dp targets, contrast, TalkBack, dynamic type where feasible.
8. **One job per screen** — No kitchen-sink dashboards; progressive disclosure.

---

## 6. Medical & legal language rules (mandatory)

### 6.1 Fixed disclaimer (verbatim)

> This app is an educational and awareness tool and does not replace a dermatologist.

**Surfaces that must show it:**

- Home (persistent bar or footer chip)
- Scan capture intro + results
- Timeline detail / compare
- Analytics insights that reference conditions
- Doctor report preview + PDF footer
- Onboarding slide 1 (light form)

### 6.2 Banned phrases

| Banned | Use instead |
|--------|-------------|
| diagnosis / diagnosed | possible condition / educational estimate |
| you have … | the model suggests … / may be consistent with … |
| definitely / confirmed | confidence estimate |
| cancer / malignant as fact | elevated-concern educational flag + “consult a clinician” |
| cure | support routine / track progress |

### 6.3 Severity presentation

Model classes map to **UI concern bands** (not medical grades):

| Band | Color token | Meaning in UI |
|------|-------------|----------------|
| Calm | `HealthGreen` | e.g. Healthy / low concern educational |
| Watch | `WarmAmber` | Common conditions; monitor + educate |
| Elevated | `SoftCoral` | Classes with higher clinical stakes (e.g. MEL index) — urgent *consult* CTA, still non-diagnostic |

Melanoma index (config: 5) and other high-stakes labels **never** auto-alarm with sound; use composed elevated card + clear “This is not a diagnosis” line.

---

## 7. Design system — “Dermo Visual Language”

Aesthetic synthesis: **Google Health (trust) + Pixel Weather (layers, motion) + Nothing OS (clarity, bold type) + Material You (dynamic color).**

### 7.1 Brand personality

| Trait | Expression |
|-------|------------|
| Trustworthy | Soft medical teal/violet, never neon “AI purple spam” |
| Calm | Large breathing space, 24–32dp gaps, no harsh red fills |
| Modern | Glass cards, gradient headers, rounded 24–28dp |
| Human | Friendly empty states, illustration-light icons (Material Symbols) |

### 7.2 Color

**Foundation:** Material 3 dynamic color when `dynamicColorEnabled` (default on).

**Brand accents** (from `DermoColors` — keep stable):

| Token | Hex | Usage |
|-------|-----|--------|
| TealAccent | `#2DD4BF` | Primary CTAs, scan FAB ring, progress |
| VioletAccent | `#8B5CF6` | Secondary gradients, SkinMind accent |
| HealthGreen | `#34D399` | Positive trends, healthy band |
| WarmAmber | `#FBBF24` | Watch band, reminders |
| SoftCoral | `#F87171` | Elevated band, errors (soft, not alarm red) |
| Glass overlays | `#66FFFFFF` / `#66000000` | Glass card scrims |

**Rules:**

- Do not pure-black text on pure-white for long clinical body copy — use `onSurface` / `onSurfaceVariant`.
- Elevated concern uses coral **outline + soft container**, not full-screen red.
- Gradient headers: Teal → Violet, bottom corners **32dp**.
- Dark mode: preserve accent luminance; glass opacity increases slightly.

### 7.3 Typography

Use `DermoTypography` scale. Add product-level usage map:

| Role | Style | Example |
|------|-------|---------|
| Hero metric | `displayLarge` | Streak “12” |
| Screen title | `headlineLarge` / `headlineMedium` | “Today” |
| Card title | `titleLarge` | “Latest scan” |
| Body | `bodyLarge` | Educational copy |
| Meta | `bodyMedium` / `labelLarge` | Timestamps, confidence % |

**Nothing-inspired detail:** numeric stats slightly tighter tracking; section titles medium weight, never all-caps.

### 7.4 Spacing & shape

| Token | Value |
|-------|--------|
| Screen horizontal padding | 20–24dp |
| Card corner | 28dp (`DermoGlassCard`) |
| Header bottom radius | 32dp |
| List item gap | 12–16dp |
| Section gap | 24–28dp |
| Min touch target | 48×48dp |
| Bottom nav height | 80dp (incl. safe area) |
| FAB size | 64dp (Scan) |

### 7.5 Elevation & glass

- Prefer **tonal surfaces + 1dp gradient border** over heavy shadows (`DermoGlassCard` pattern).
- Max 2 elevation levels on one screen.
- Scroll content may blur under sticky disclaimer (optional Phase 14).

### 7.6 Motion

| Interaction | Motion |
|-------------|--------|
| Tab switch | Fade + slight vertical slide (150–200ms) |
| Card appear | Staggered fade-up 40ms delay |
| Scan shutter | Scale pulse on shutter button |
| Inference progress | Determinate ring if possible; else calm indeterminate |
| Success | Soft check morph, no confetti for elevated results |
| Onboarding pager | Horizontal shared-axis |

Use `MaterialTheme.motionScheme` when available; no spring bounce on clinical results.

### 7.7 Iconography

- Material Symbols **rounded / outlined** for nav and actions
- Filled only for selected bottom-nav item
- Scan: custom ringed camera glyph optional later; v1 Material `photo_camera`

### 7.8 Illustration & empty states

Every major empty state needs:

1. One line title  
2. One line supportive body  
3. One primary CTA  
4. Optional secondary text link  

Tone: warm, never shaming (“No scans yet” → “Start your skin journal”).

### 7.9 Component inventory (build / reuse)

| Component | Module | Spec |
|-----------|--------|------|
| `DermoGlassCard` | core:ui | 28dp radius, gradient border, 20dp pad |
| `GradientHeader` | core:ui | Teal→Violet, optional trailing actions |
| `MedicalDisclaimerBar` | core:ui | Full-width, readable, not dismissible on clinical screens |
| `DermoPrimaryButton` | core:ui *(add)* | 52dp height, full-width option, loading state |
| `DermoSecondaryButton` | core:ui *(add)* | Outlined 52dp |
| `ConfidenceBar` | core:ui *(add)* | Horizontal % bar + label |
| `ConcernBandChip` | core:ui *(add)* | Calm / Watch / Elevated |
| `ScanThumbnail` | core:ui *(add)* | 72–96dp rounded 20dp, Coil |
| `SectionHeader` | core:ui *(add)* | Title + optional “See all” |
| `EmptyState` | core:ui *(add)* | Icon + title + body + CTA |
| `StickyDisclaimerScaffold` | core:ui *(add)* | Content + disclaimer slot |
| `DermoBottomBar` | app/nav *(Phase 3)* | 5 destinations, center Scan emphasis |
| `InsightCard` | feature surfaces | Title, body, optional chart sparkline |

### 7.10 Accessibility

- Content descriptions on all icon-only buttons  
- Contrast ≥ WCAG AA for text  
- Don’t rely on color alone for concern band (icon + text)  
- Font scale 1.3: cards wrap; no clipped CTAs  
- Reduce motion: respect system setting when practical (Phase 14)

### 7.11 Localization

- EN + HI for every user-facing string  
- No concatenated sentences that break Hindi grammar  
- PDF: language follows app locale  
- RTL not required v1 (HI is LTR)

---

## 8. Information architecture

### 8.1 App graph (conceptual)

```
Splash
  ├─ Onboarding (first run only)
  └─ Auth (if signed out)
       └─ Main (authenticated)
            ├─ Home
            ├─ Scan (tab root → capture → preview → results → save)
            ├─ Timeline (list → detail → compare)
            ├─ SkinMind (daily check-in)
            └─ More
                 ├─ Treatment
                 ├─ Wellness
                 ├─ Analytics
                 ├─ Doctor report
                 └─ Settings
```

### 8.2 Bottom navigation (Phase 3)

| Tab | Route | Icon | Label EN | Label HI |
|-----|-------|------|----------|----------|
| 1 | `home` | home | Home | होम |
| 2 | `timeline` | timeline | Timeline | टाइमलाइन |
| 3 | `scan` | camera (emphasized) | Scan | स्कैन |
| 4 | `skinnmind` | favorite / psychology | SkinMind | स्किनमाइंड |
| 5 | `more` | menu | More | और |

**Scan tab behavior:** selecting Scan always enters capture flow root (or last incomplete step with resume dialog). Not a static hub.

**More:** single scrollable hub with glass rows (not another bottom bar).

### 8.3 Auth guard

- Unauthenticated → never Main  
- Not onboarded → Onboarding before Auth  
- Sign-out → clear session, land Sign-in  

---

## 9. User journeys

### 9.1 First open (happy path)

1. Splash (brand, 400–800ms or until session ready)  
2. Onboarding 3 slides (scan / track / share doctor)  
3. Create account (email or Google when Firebase ready)  
4. Land Home empty state with “Take first scan” CTA  

### 9.2 First scan

1. Home → Scan CTA or Scan tab  
2. Permission rationale → system camera permission  
3. Viewfinder with **guide overlay** (lesion circle + lighting tip)  
4. Capture → review (retake / use photo)  
5. Inference progress (educational microcopy, not “diagnosing…”)  
6. Results: top-3 classes, confidence, concern band, disclaimer  
7. Save to timeline + optional note  
8. Optional “Add today’s SkinMind” soft prompt  

### 9.3 Weekly habit

1. Notification (local) “30-second skin check-in”  
2. SkinMind 3–5 quick chips + mood slider  
3. Home shows streak + correlation teaser when enough data  

### 9.4 Clinic visit

1. More → Doctor report  
2. Date range + include photos toggle  
3. Preview with disclaimer  
4. Share PDF via system sheet  

---

## 10. Feature requirements (detailed)

Each feature: **intent · screens · functional requirements · UI requirements · acceptance · phase**.

---

### 10.1 Authentication & onboarding — **Phase 2 (DONE)**

**Intent:** Establish identity + education framing.

**Screens:** Splash, Onboarding (3), Sign-in, Sign-up.

**Functional**

- Email/password sign-up & sign-in  
- Google Sign-In when Firebase OAuth configured; graceful error otherwise  
- Local session fallback while `google-services.json` is placeholder  
- Persist `isOnboarded`, `userId`; upsert Room `user_profiles`  

**UI**

- Onboarding: large icon in glass card, pager dots, Skip + Next / Get started  
- Auth forms: 52dp fields, password visibility toggle, local-mode banner  
- Keep GradientHeader + MedicalDisclaimerBar on auth forms  

**Acceptance**

- [x] Cold start → onboarding once → auth → home only when authenticated  
- [x] Sign-out returns to sign-in  

---

### 10.2 Navigation shell — **Phase 3 (ACTIVE)**

**Intent:** Stable IA for all features.

**Functional**

- Typed routes (kotlinx.serialization or type-safe Navigation Compose)  
- Bottom bar 5 tabs; More stack for secondary features  
- Auth guard preserved  
- Feature modules expose entry composables even if shell content  

**UI**

- Bottom bar: surface container, selected = primary + filled icon  
- Center Scan: slightly raised / larger icon or floating pill  
- Scaffold handles IME + system bars (edge-to-edge)  
- No dual headers (tab screens own top content; avoid stacking GradientHeader + large app bar everywhere)  

**Home/Timeline/SkinMind:** content under optional compact top bar or immersive content.  
**Scan:** immersive camera (no bottom bar chrome over viewfinder — hide bottom bar on capture sub-routes).

**Acceptance**

- [ ] All 5 tabs reachable when authenticated  
- [ ] Unauthenticated user cannot open tabs  
- [ ] Scan sub-flow hides bottom bar  
- [ ] `assembleDebug` + navigable on emulator  

---

### 10.3 Home dashboard — **Phase 4**

**Intent:** Answer “What matters today?” in one calm scroll.

**Layout (top → bottom)**

1. **Greeting header** — time-based “Good morning, {name}” + small settings/profile affordance  
2. **MedicalDisclaimerBar**  
3. **Today strip** — SkinMind completion chip + streak fire metric  
4. **Primary CTA card** — “Scan skin” full-width glass, teal gradient border, camera icon  
5. **Latest result card** — thumbnail, date, top label, confidence, concern chip → opens detail  
6. **Insights teaser** (if ≥ N data points) — 1 insight line + “See analytics”  
7. **Treatment today** — next routine step or empty “Add routine”  
8. **Footer** — soft disclaimer repeat optional; version hidden  

**Empty states**

- No scans: hero CTA only + short educational blurb  
- No check-in today: outlined “2-minute check-in”  

**Functional**

- Read latest scan, streak, next treatment from Room  
- No mock data  

**Acceptance**

- [ ] No developer “Architecture Foundation” cards  
- [ ] Disclaimer visible without scrolling on default phone size  
- [ ] CTA opens Scan  
- [ ] Latest card opens timeline detail when data exists  

---

### 10.4 Scan — camera & capture — **Phase 5**

**Intent:** High-quality photo capture with guidance.

**Screens**

| Screen | Purpose |
|--------|---------|
| Permission | Why camera is needed |
| Viewfinder | Live preview + overlay |
| Review | Confirm / retake |
| (Later Phase 6) Results | Model output |
| (Phase 7) Notes | Voice memo recording + playback |

**Viewfinder UI**

- Full-bleed camera preview  
- Top: close, flash toggle (auto/on/off cycle), facing (if front enabled later — v1 back only)
- Center: translucent oval/circle guide; “Fill the circle with the area of concern”  
- Bottom tip chips: “Even lighting”, “Hold steady”, “No blur”  
- Shutter 72dp, teal ring; disabled while capturing  
- Hide system nav immersively where possible  

**Functional**

- CameraX use cases: preview + image capture
- **Auto-flash**: detect ambient light via sensor, toggle flash automatically when below threshold
- Save full-res to app-private storage; store URI path in pending scan
- EXIF orientation handled
- Fail gracefully if camera in use

**Acceptance**

- [ ] Capture produces viewable image in review  
- [ ] Retake discards previous buffer  
- [ ] Denied permission shows recovery UI (settings link)
- [ ] Auto-flash engages in low light (ambient sensor < 50 lux)
- [ ] Voice memo recording available after capture — tap mic, record, save; playback inline

---

### 10.5 On-device AI inference — **Phase 6**

**Intent:** Educational top-k predictions offline.

**Model**

- Source: ConvNeXt-Base PyTorch `ce_ls_best.pth` → TFLite `skin_model.tflite`  
- 12 classes (labels.txt), 224×224, ImageNet norm  
- Melanoma index 5 → elevated band treatment  

**Results UI**

1. Hero image (rounded 24dp)  
2. Disclaimer bar  
3. “Educational estimates” title  
4. Ranked list (top 3–5): label, confidence bar, short plain-language blurb (from local strings map)  
5. Concern band chip + “What this means” expandable  
6. “When to see a doctor” static educational bullets (not personalized diagnosis)  
7. Actions: Save to timeline, Retake, Share (share only after save; share sheet for image optional — prefer report later)  

**Microcopy during inference**

- “Analyzing patterns for education…”  
- Never “Diagnosing…”

**Functional**

- `SkinInferenceEngine.initialize()` / `predict()`  
- Persist scan + predictions in Room  
- Model missing → friendly error, no crash  

**Acceptance**

- [ ] Offline inference succeeds with converted model  
- [ ] Results language audit passes banned-phrase list  
- [ ] Elevated band for MEL shows consult CTA  

---

### 10.6 Photo timeline — **Phase 7**

**Intent:** Chronological skin journal + compare.

**Screens**

- Timeline list (reverse chronological, sticky month headers)
- Detail (image, predictions, **voice notes**, tags)
- Compare (two-up slider or side-by-side)

**UI**

- Grid 2-column on phone; cards with date + top label chip
- Compare: drag handles, “Before / After” labels, shared scale
- Detail: voice note waveform bar — record inline via mic button, playback inline

**Functional**

- CRUD notes (text + voice); delete scan with confirm
- Voice recording: tap mic, record ≤60s, auto-attach to scan; playback from detail
- Auto-flash based on ambient light sensor reading during capture
- Filter by label / date range (nice-to-have if time; at least date sort)

**Acceptance**

- [ ] New scans appear after save without restart
- [ ] Compare two scans
- [ ] Delete removes files + DB rows
- [ ] Voice notes record and play back
- [ ] Flash auto-engages in low-light scenes

---

### 10.7 SkinMind Sync — **Phase 8**

**Intent:** ≤30s daily context check-in.

**Fields (v1)**

| Field | Control |
|-------|---------|
| Overall skin feel | 1–5 emoji or segmented |
| Itch / discomfort | 0–10 slider |
| Sleep quality | 1–5 |
| Stress | 1–5 |
| New product used? | Yes/No + optional text |
| Notes | Optional 140 chars |

**UI**

- Single scroll page, one primary “Save check-in”  
- Celebration micro-interaction on streak +1 (subtle)  
- History week strip on top  

**Acceptance**

- [ ] One check-in per local day (edit allowed)  
- [ ] Completes in ≤30s happy path  
- [ ] Streak updates on Home  
- [ ] Voice notes: tap mic on notes field to record ≤30s instead of typing

---

### 10.8 Environmental skin alerts (GPS) — **Phase 11+**

**Intent:** Passive skin health guardian using coarse device location.

**How it works**

- App periodically reads coarse location (no background GPS drain — uses last-known location + geocoder)  
- Fetches UV index, temperature, and humidity for the user's area from a lightweight weather API or on-device estimation  
- When thresholds are crossed, posts a system notification and a Home dashboard card  

**Trigger thresholds**

| Metric | Warning threshold | Message tone |
|--------|-------------------|-------------|
| UV Index | ≥ 6 (High) | "High UV today — wear sunscreen and cover exposed skin" |
| Temperature | ≥ 35°C (95°F) | "Extreme heat — stay hydrated and avoid prolonged sun exposure" |
| Humidity | ≥ 85% | "High humidity — fungal conditions may flare; keep skin dry" |
| Combined (UV + heat) | UV ≥ 6 **and** temp ≥ 35°C | Red alert card: "High-risk skin day — UV + heat" |

**UI**

- Home dashboard card: teal-amber gradient glass with icon + one-line message  
- Tap opens expandable detail with all three metrics + educational blurb  
- System notification (non-intrusive, channels: "Skin Alerts")  
- Settings toggle: "Environmental alerts" on/off under Settings → Notifications  

**Functional**

- `LocationManager` with coarse permission only (no fine GPS required)  
- Weather data from free API (e.g., Open-Meteo, no API key needed) or cached offline estimate  
- Check frequency: once every 3 hours or on app foreground (whichever is less frequent)  
- Offline fallback: if no network, show last-cached reading with timestamp  

**Acceptance**

- [ ] Coarse location permission rationale on first alert enable  
- [ ] Home card appears when UV ≥ 6, temp ≥ 35°C, or humidity ≥ 85%  
- [ ] Notification posted via "Skin Alerts" channel  
- [ ] Toggle in Settings disables alerts completely  
- [ ] Works with airplane mode (cached last reading)  
- [ ] No background GPS drain (coarse location only, periodic)

---

### 10.9 Correlation / insights engine — **Phase 9**

**Intent:** Rule-based insights (ML-swappable later).

**Examples**

- “Itch scores were higher on days you logged poor sleep (last 14 days).”  
- “You scanned more during high-stress weeks.”  

**UI**

- Insight cards on Home (max 1–2) and Analytics  
- Always include “Possible pattern — not medical advice”  

**Functional**

- `InsightsEngine` in domain; `RuleBasedInsightsEngine` implementation  
- Needs minimum N check-ins + scans before emit  

**Acceptance**

- [ ] No insights with insufficient data  
- [ ] Insights offline  

---

### 10.10 Treatment tracker — **Phase 10**

**Intent:** Simple routines + reminders.

**Entities**

- Routine (name, schedule)  
- Steps (product/name, time of day)  
- Completion log  

**UI**

- List of routines; detail with checklist for today  
- Time picker; WorkManager/AlarmManager local notifications  

**Acceptance**

- [ ] Create routine, tick steps, notification fires (or scheduled)  
- [ ] Completions visible on Home “Treatment today”  

---

### 10.11 Analytics dashboard — **Phase 11**

**Intent:** Charts for personal progress (not clinical stats theater).

**Charts (v1)**

- Check-in itch/stress over time (line)  
- Scan frequency (bar by week)  
- Concern band distribution (simple)  

**UI**

- Use lightweight Compose charts (existing stack or Canvas) — avoid heavy webviews  
- Empty state until data  

**Acceptance**

- [ ] Renders with sample real Room data  
- [ ] Accessible content descriptions for series summary  

---

### 10.12 Doctor share PDF — **Phase 12**

**Intent:** Clinic-ready artifact with full scan history, images, and predictions.

**PDF content (in order)**

1. **Cover header** — DermoAI branding, generated date, patient display name  
2. **Disclaimer** — fixed educational-only notice (every page footer)  
3. **Summary section** — total scans in date range, top concern bands distribution, SkinMind check-in streak  
4. **Scan timeline** — one entry per scan:
   - Date + body area tag  
   - **Thumbnail image** (compressed JPEG embedded inline)  
   - **Top prediction** with confidence bar  
   - **Top-3 predictions** ranked with label, code, and confidence %  
   - Concern band chip (Low / Medium / High / Critical)  
   - User notes (text + voice note transcribed or "Voice note attached" indicator)  
5. **Environmental context** (if available) — weather conditions at time of each scan (UV, temp, humidity)  
6. **SkinMind summary** — avg sleep/stress/itch scores over date range (line chart or table)  
7. **Educational footer** — “Prepared with DermoAI — educational only, not a medical diagnosis”  
8. **Page numbers** — "Page X of Y" on every page  

**UI**

- Builder screen: range chips (7d/30d/90d/custom), section toggles (include images, include predictions, include SkinMind, include env data)  
- Preview (Compose approximation) + generate + share via system share sheet  

**Tech:** `android.graphics.pdf.PdfDocument` only (no iText).

**Acceptance**

- [ ] PDF includes at least one thumbnail image per scan (compressed, readable)  
- [ ] Top-3 predictions with confidence bars rendered per scan  
- [ ] Full scan history within selected date range  
- [ ] PDF opens in Drive/Files  
- [ ] Works offline  
- [ ] Disclaimer on every page  
- [ ] Page numbers on every page

---

### 10.13 Firebase sync — **Phase 13**

**Intent:** Optional backup when user provisions Firebase.

**Scope**

- Auth already real Firebase when configured  
- Firestore: profiles, metadata (not raw pixels unless opted in)  
- Storage: encrypted-at-rest photos if user enables “Cloud backup”  
- Account delete: wipe remote + local  

**UI**

- Settings: sync status, last synced, toggle backup, delete account  

**Acceptance**

- [ ] With real `google-services.json`, sign-in + sync path works  
- [ ] Placeholder config: no crash; clear status  

---

### 10.14 Wellness, settings, polish — **Phase 14**

**Wellness**

- Confidence journal (text + mood)  
- Box breathing 1-min exercise  
- Streak celebration screen  

**Settings**

- Theme: system / light / dark  
- Dynamic color toggle  
- Language EN/HI  
- Export data  
- Sign out / delete account  
- Open-source / model credits  
- Disclaimer full page  

**Polish**

- Motion reduce  
- Hindi QA pass  
- Iconography consistency  
- Remove any remaining placeholder copy  

---

### 10.15 Testing — **Phase 15**

- Unit: use cases, insights rules, mappers  
- Instrumented: Room, DataStore  
- Compose UI tests: auth guard, tab nav, disclaimer presence  
- Manual checklist: language audit, offline airplane mode, scan flow  

---

## 11. Screen inventory (complete map)

| ID | Screen | Phase | Primary components |
|----|--------|-------|--------------------|
| A0 | Splash | 2 | Brand gradient, logo, progress |
| A1 | Onboarding | 2 | Pager, glass cards |
| A2 | Sign-in | 2 | Form, Google button |
| A3 | Sign-up | 2 | Form |
| H0 | Home | 4 | Greeting, CTA, latest, streak |
| N0 | Bottom nav host | 3 | 5 tabs |
| S0 | Scan permission | 5 | Rationale |
| S1 | Viewfinder | 5 | CameraX, guide |
| S2 | Review | 5 | Image, retake/use |
| S3 | Inferencing | 6 | Progress |
| S4 | Results | 6 | Ranked list, band |
| T0 | Timeline list | 7 | Grid |
| T1 | Timeline detail | 7 | Image + preds |
| T2 | Compare | 7 | Two-up |
| M0 | SkinMind today | 8 | Form, **voice notes** |
| ENV0 | Environmental alerts | 11 | Dashboard card, notification |
| ENV1 | Alert detail | 11 | Metrics + education |
| M1 | SkinMind history | 8 | Week strip |
| R0 | Treatment list | 10 | Routines |
| R1 | Treatment detail | 10 | Checklist |
| W0 | Wellness hub | 14 | Cards |
| W1 | Breathing | 14 | Animation |
| W2 | Journal | 14 | Editor |
| C0 | Analytics | 11 | Charts |
| P0 | Report builder | 12 | Toggles |
| P1 | Report preview | 12 | Share |
| Z0 | More hub | 3 | Nav rows |
| Z1 | Settings | 14 | Prefs |

---

## 12. Copy deck (core EN — HI to mirror)

| Key | English |
|-----|---------|
| disclaimer | This app is an educational and awareness tool and does not replace a dermatologist. |
| home_cta_scan | Scan your skin |
| home_empty_title | Start your skin journal |
| home_empty_body | Take a photo to track changes over time. Results are educational estimates only. |
| scan_guide | Center the area of concern inside the guide |
| scan_analyzing | Analyzing patterns for education… |
| results_title | Educational estimates |
| results_subtitle | Not a diagnosis. Confidence is a model estimate. |
| elevated_cta | Consider consulting a dermatologist |
| skinnmind_title | Daily SkinMind |
| report_title | Doctor report |
| report_footer | Educational only — not a medical diagnosis |
| env_uv_high | High UV today — wear sunscreen and cover exposed skin |
| env_heat_warning | Extreme heat — stay hydrated and avoid prolonged sun exposure |
| env_humidity_warning | High humidity — fungal conditions may flare; keep skin dry |
| env_combined_alert | High-risk skin day — UV + extreme heat combined |
| env_settings_toggle | Environmental alerts |
| voice_note_placeholder | Tap mic to record a voice note |
| voice_note_recording | Recording… tap to stop |
| flash_auto_engaged | Auto-flash active in low light |

All keys live in `values` / `values-hi`; no hardcoded UI strings in feature code.

---

## 13. Data model (product-level)

### 13.1 Current

```
user_profiles(id, email, displayName, createdAt, updatedAt, syncStatus)
```

### 13.2 Target schema (phased)

```
skin_scans(
  id, userId, imagePath, thumbnailPath, capturedAt,
  note, bodyArea?, createdAt, updatedAt, syncStatus
)

scan_predictions(
  id, scanId, label, labelCode, confidence, rank, concernBand
)

daily_check_ins(
  id, userId, localDate, skinFeel, itch, sleep, stress,
  newProduct, notes, createdAt
)

treatment_routines(id, userId, name, scheduleJson, createdAt)
treatment_steps(id, routineId, title, sortOrder, timeOfDay)
treatment_completions(id, stepId, localDate, completedAt)

insight_records(
  id, userId, type, title, body, generatedAt, dismissed
)

wellness_journal(id, userId, mood, entry, createdAt)

sync_queue(id, entityType, entityId, op, payload, updatedAt)  // Phase 13
```

**Rules:** soft-delete optional; hard-delete for account wipe; image files deleted with scan rows.

---

## 14. ML product requirements

| Req | Detail |
|-----|--------|
| On-device only for v1 inference | No forced cloud path |
| Input | RGB bitmap → preprocess per `model_config.json` |
| Output | Softmax vector → top-k domain models |
| Failure | User-visible error; offer retake |
| Versioning | Store model version string with each scan |
| Safety | Elevated band list configurable; MEL included |

**Class display names** (user-facing, not raw codes): map BCC, ACK, NEV, SEK, SCC, MEL, Acne, Hair Loss, Nail Fungus, Fungal, Vascular, Healthy to readable EN/HI strings + 1-line education.

---

## 15. Non-functional requirements

| Area | Requirement |
|------|-------------|
| Performance | App cold start to interactive Home < 3s on emulator host-GPU config after warm install |
| Scan UX | Capture + show review < 1s after shutter returns buffer |
| Inference | Target p50 < 5s on mid-range once TFLite optimized; show progress immediately |
| Storage | Compress thumbnails; full images app-private |
| Security | No logging of image bitmaps; no third-party analytics of pixel data in v1 beyond Crashlytics IDs |
| Reliability | Never crash on missing model / Firebase placeholder |
| Battery | Camera unbind on leave Scan; no permanent wake locks |
| Emulator dev | Host GPU launch script; no swiftshader default |

---

## 16. Metrics (product analytics — when Firebase Analytics live)

| Event | Props |
|-------|-------|
| `onboarding_complete` | — |
| `sign_up` / `sign_in` | method |
| `scan_captured` | — |
| `scan_inference_success` | top_label, latency_ms |
| `scan_inference_fail` | reason |
| `checkin_saved` | streak |
| `report_generated` | range_days |
| `disclaimer_impression` | screen |

Respect privacy: no raw images in analytics.

---

## 17. Delivery roadmap (aligned with AGENT.md)

| Phase | Theme | PRD sections | Status |
|------:|-------|--------------|--------|
| 1 | Architecture | — | **DONE** |
| 2 | Auth | 10.1 | **DONE** |
| 3 | Navigation | 10.2, §8 | **DONE** |
| 4 | Home UI | 10.3, §7 | **DONE** |
| 5 | Scan capture | 10.4 | **DONE** |
| 6 | TFLite inference | 10.5, §14 | **DONE** |
| 7 | Timeline + voice notes | 10.6 | Pending |
| 8 | SkinMind | 10.7 | Pending |
| 9 | Insights | 10.9 | Pending |
| 10 | Treatment | 10.10 | Pending |
| 11 | Environmental alerts + analytics | 10.8, 10.11 | Pending |
| 12 | PDF report | 10.12 | Pending |
| 13 | Firebase sync | 10.13 | Pending |
| 14 | Wellness + settings + polish | 10.14 | Pending |
| 15 | Testing | 10.15 | Pending |

**Rule:** One phase at a time. Phase gate = build green + acceptance checks for that phase.

---

## 18. Key decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Diagnostic stance | Educational only | Safety, trust, regulatory simplicity for v1 |
| Inference locus | On-device TFLite | Privacy + offline |
| UI kit | Compose M3 + custom glass/gradient | Premium health feel without custom game engine |
| Nav | 5-tab + More hub | Fits core loops without 8-tab overload |
| Source of truth | Room first | Offline-first; Firebase later |
| PDF library | Platform PdfDocument | License simplicity |
| i18n | EN + HI from day of feature | India-first audience |
| Auth without Firebase project | Local session fallback | Unblocks device testing |
| Design density | Calm spacing, fewer cards | Reduces anxiety vs dense medical dashboards |
| Scan in bottom nav | Center emphasized tab | Primary action always one tap |

---

## 19. Open questions

| # | Question | Default if unanswered |
|---|----------|----------------------|
| Q1 | Front camera / body areas taxonomy in v1? | Back camera only; free-text note for area |
| Q2 | Cloud photo backup default on or off? | **Off** until explicit opt-in |
| Q3 | Min age / parental gate? | Soft copy only in v1; no hard gate until legal review |
| Q4 | Chart library? | Custom Canvas / lightweight Compose first |
| Q5 | Expand class education content source? | Static strings in app for v1 |

---

## 20. PR / implementation plan (incremental)

Use as epic breakdown; maps to phases. Each PR should be independently reviewable.

| PR | Title | Depends on | Scope |
|----|-------|------------|-------|
| PR-01 | Typed navigation + bottom bar shell | Phase 2 | Routes, `DermoBottomBar`, feature entry stubs, hide bar on scan sub-graph |
| PR-02 | Design system components pack | PR-01 optional | Buttons, EmptyState, ConfidenceBar, ConcernBandChip, SectionHeader |
| PR-03 | Home dashboard real UI | PR-01, PR-02 | Replace placeholder; disclaimer; CTAs; empty/latest layouts |
| PR-04 | CameraX scan capture flow | PR-01 | Permission, viewfinder, review, file store |
| PR-05 | TFLite conversion tooling + assets | — | Convert pth→tflite, ship assets, version meta |
| PR-06 | Inference + results UI | PR-04, PR-05 | Engine wire-up, Room scan/prediction, results screen |
| PR-07 | Timeline list/detail/compare + voice notes | PR-06 | Grid, detail, compare, voice recording |
| PR-08 | SkinMind check-in | PR-01 | Form, streak, Home binding |
| PR-09 | Rule-based insights | PR-07, PR-08 | InsightsEngine + cards |
| PR-10 | Treatment routines + notifications | PR-01 | CRUD, today checklist, WorkManager |
| PR-11 | Environmental alerts + analytics | PR-07, PR-08 | GPS alerts, charts |
| PR-12 | Doctor PDF export | PR-07 | Builder, PdfDocument, share |
| PR-13 | Firebase provisioning + sync | Auth | Real google-services, Firestore/Storage, delete |
| PR-14 | Wellness + settings + i18n QA | Many | Polish |
| PR-15 | Test suite + language audit | All | Phase 15 |

---

## 21. Quality bar (definition of done — product)

A feature is not done when it “renders”. It is done when:

1. Acceptance criteria in this PRD are checked  
2. EN + HI strings exist  
3. Disclaimer rules satisfied if clinical  
4. Empty, loading, error states exist  
5. Airplane mode path verified if offline-critical  
6. `./gradlew :app:assembleDebug` passes  
7. No banned medical language  
8. No developer placeholder copy in user-visible paths  

---

## 22. Competitive positioning (concise)

| Type | Typical gap | DermoAI stance |
|------|-------------|----------------|
| Web “skin checkers” | Scare copy, cloud upload | On-device, calm education |
| Gallery + notes | No structure | Timeline + PDF |
| Clinical EHR apps | Too heavy | Consumer companion |
| Generic habit apps | No skin context | SkinMind + scans linked |

---

## 23. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Users misread estimates as diagnosis | Fixed disclaimer, banned phrases, elevated copy review |
| Model quality / bias | Educational framing; versioning; future eval set |
| Large model size | TFLite conversion + quantization in Phase 6 |
| Firebase delay | Local auth + offline core |
| Scope creep | Phase lock in AGENT.md |
| UI anxiety | Soft coral, no alarm sounds, calm motion |

---

## 24. Appendix A — Phase 3 acceptance (next up)

- [ ] Typed routes for Home, Timeline, Scan, SkinMind, More  
- [ ] Bottom bar matches §8.2 labels (EN/HI)  
- [ ] Scan nested graph hides bottom bar  
- [ ] More hub lists Treatment, Wellness, Analytics, Reports, Settings (shell OK)  
- [ ] Auth guard unchanged  
- [ ] Emulator: install, sign-in, tap all tabs  

## 25. Appendix B — Visual reference notes for implementers

- Prefer **one accent gradient per screen** (header or CTA), not both competing  
- Cards: max ~3 primary cards above fold on Home  
- Results list: rank number in circle; confidence as bar not only percent text  
- Timeline: date in `bodyMedium`; photo dominant  
- More hub: 56dp rows, 12dp icon containers with soft tint  
- Settings: standard M3 list, no glass overload  

## 26. Appendix C — Document control

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | (implicit) | AGENT.md phase list only |
| **2.0** | 2026-07-13 | Full PRD: product, UI system, screens, acceptance, PR plan |

**Ownership:** Product requirements live here. **Implementation sequencing and agent rules live in `AGENT.md`.** When they conflict on *product intent*, update this PRD; when they conflict on *phase order*, `AGENT.md` wins until this PRD’s roadmap table is edited to match.

---

*End of PRD 2.0*
