# DermoAI — Detailed Phase Spec 11–16

Implementation-ready spec for the next 6 phases. Written so a junior engineer or AI agent with zero context can implement each phase by following the exact file paths, signatures, strings, and verification steps below.

**Stack reminder:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, CameraX, TFLite, Navigation Compose. Package roots: `com.dermoai.core.*` and `com.dermoai.feature.*`. Every feature module depends only on `core/*`. Build command: `./gradlew :app:assembleDebug`.

**Conventions enforced throughout:**
- New Room entities/DAOs go in `core/database/src/main/kotlin/com/dermoai/core/database/entity|dao/`
- DAOs always expose `Flow<T>` for reactive reads and `suspend fun` for one-shots
- Add `@Provides fun provideXxxDao(database: DermoDatabase) = database.xxxDao()` to `DatabaseModule.kt`
- Bump `DermoDatabase` version by 1 each phase and keep `fallbackToDestructiveMigration()` in `DatabaseModule`
- Every new Hilt `@Provides`/`@Binds` module is `@InstallIn(SingletonComponent::class)`
- Every user-facing string lives in both `feature/<module>/src/main/res/values/strings.xml` AND `values-hi/strings.xml`
- Every clinical screen renders `MedicalDisclaimerBar()` near the top
- No `TODO`, no `null!!`, no mock data in production paths
- Every public file has a KDoc block

---

## Phase 11 — Environmental skin alerts (GPS weather warnings)

### 11.1 Intent

Passive skin-health guardian. App reads coarse location, fetches UV index / temperature / humidity from a free no-key API, and shows a Home card + system notification when thresholds are crossed.

### 11.2 Acceptance

- [ ] Coarse location permission rationale shown on first alert enable
- [ ] Home card appears when UV ≥ 6, temp ≥ 35°C, or humidity ≥ 85%
- [ ] Notification posted via `NotificationChannel` "Skin Alerts"
- [ ] Settings toggle "Environmental alerts" disables alerts and notifications
- [ ] Works in airplane mode using last cached reading + timestamp
- [ ] No background GPS drain — coarse location only, checked on app foreground or every 3h via WorkManager
- [ ] `./gradlew :app:assembleDebug` passes

### 11.3 Files to create

#### `core/environment/build.gradle.kts`
New Gradle module. Add to `settings.gradle.kts`: `include(":core:environment")`.

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.dermoai.core.environment"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    ksp(libs.hilt.compiler)
}
```

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/WeatherConditions.kt`
```kotlin
package com.dermoai.core.environment

import kotlinx.serialization.Serializable

@Serializable
data class WeatherConditions(
    val uvIndex: Float,
    val temperatureC: Float,
    val humidityPercent: Float,
    val fetchedAt: Long,
    val locationLabel: String = "",
)
```

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/EnvironmentAlertEvaluator.kt`
```kotlin
package com.dermoai.core.environment

/** Pure function — no Android deps. Tests in Phase 16. */
object EnvironmentAlertEvaluator {
    fun evaluate(c: WeatherConditions): EnvironmentAlert? = when {
        c.uvIndex >= 6f && c.temperatureC >= 35f -> EnvironmentAlert.RED_COMBINED
        c.uvIndex >= 6f -> EnvironmentAlert.HIGH_UV
        c.temperatureC >= 35f -> EnvironmentAlert.EXTREME_HEAT
        c.humidityPercent >= 85f -> EnvironmentAlert.HIGH_HUMIDITY
        else -> null
    }
}

enum class EnvironmentAlert {
    HIGH_UV, EXTREME_HEAT, HIGH_HUMIDITY, RED_COMBINED;
    fun messageRes(): String = when (this) {
        HIGH_UV -> "High UV today — wear sunscreen and cover exposed skin"
        EXTREME_HEAT -> "Extreme heat — stay hydrated and avoid prolonged sun exposure"
        HIGH_HUMIDITY -> "High humidity — fungal conditions may flare; keep skin dry"
        RED_COMBINED -> "High-risk skin day — UV + extreme heat combined"
    }
}
```

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/OpenMeteoWeatherApi.kt`
Free, no-API-key endpoint: `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,relative_humidity_2m,uv_index`

```kotlin
interface OpenMeteoWeatherApi {
    @GET("v1/forecast")
    suspend fun getCurrent(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,uv_index",
    ): OpenMeteoResponse
}
@Serializable data class OpenMeteoResponse(val current: CurrentData)
@Serializable data class CurrentData(
    val temperature_2m: Float,
    val relative_humidity_2m: Float,
    val uv_index: Float,
)
```

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/EnvironmentRepository.kt`
```kotlin
interface EnvironmentRepository {
    suspend fun fetchCurrent(lat: Double, lon: Double): AppResult<WeatherConditions>
    fun cachedConditions(): WeatherConditions?
    fun cache(conditions: WeatherConditions)
}
```

Implement `OpenMeteoEnvironmentRepository` in same module. Cache writes to DataStore (use `UserPreferencesDataStore` pattern; add new keys `KEY_ENV_CACHE_JSON`, `KEY_ENV_ALERTS_ENABLED`).

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/di/EnvironmentModule.kt`
```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class EnvironmentModule {
    @Binds @Singleton
    abstract fun bindEnvironmentRepository(impl: OpenMeteoEnvironmentRepository): EnvironmentRepository
}
```

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/EnvironmentWorker.kt`
WorkManager `CoroutineWorker` — fetches every 3 hours when alerts enabled. Posts notification via `NotificationManager` channel "Skin Alerts" (channel created in `DermoAIApplication`).

#### `core/environment/src/main/kotlin/com/dermoai/core/environment/LocationProvider.kt`
```kotlin
class LocationProvider @Inject constructor(@ApplicationContext val context: Context) {
    suspend fun lastCoarseLocation(): Pair<Double, Double>?  // uses LocationManager fused last-known
}
```

### 11.4 Files to modify

**`core/data/.../UserPreferencesDataStore.kt`** — add:
```kotlin
val envAlertsEnabled: Flow<Boolean> // default true
suspend fun setEnvAlertsEnabled(enabled: Boolean)
val envCacheJson: Flow<String?>
suspend fun setEnvCacheJson(json: String)
```

**`feature/home/.../HomeViewModel.kt`** — inject `EnvironmentRepository`, expose `currentAlert: StateFlow<EnvironmentAlert?>`, call `refresh` to read cache first then optionally fetch.

**`feature/home/.../HomeDashboardScreen.kt`** — between `ScanCtaCard` and `LatestScanCard`, insert `if (alert != null) EnvironmentAlertCard(alert)`. New composable:
```kotlin
@Composable private fun EnvironmentAlertCard(alert: EnvironmentAlert)
```
Teal-amber gradient glass (red for `RED_COMBINED`), icon + one-line message, tap shows expandable detail with all three metrics + educational blurb.

**`app/.../DermoAIApplication.kt`** — create `NotificationChannel("skin_alerts", "Skin Alerts", IMPORTANCE_LOW)` in `onCreate`.

**`app/build.gradle.kts`** — add `implementation(project(":core:environment"))` and WorkManager dependency `implementation("androidx.work:work-runtime-ktx:2.10.0")`.

**`AndroidManifest.xml`** — add permissions:
```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 11.5 Strings to add (EN + HI) — feature/home and feature/settings

| Key | EN | HI |
|-----|----|----|
| env_alerts_title | Environmental alerts | पर्यावरण अलर्ट |
| env_alerts_toggle | Environmental alerts | पर्यावरण अलर्ट |
| env_alerts_subtitle | Skin-health warnings based on your area | आपके क्षेत्र के आधार पर त्वचा स्वास्थ्य चेतावनी |
| env_high_uv | High UV today — wear sunscreen and cover exposed skin | आज उच्च UV है — सनस्क्रीन लगाएं और त्वचा ढकें |
| env_extreme_heat | Extreme heat — stay hydrated and avoid prolonged sun exposure | अत्यधिक गर्मी — हाइड्रेटेड रहें, लंबे समय तक धूप से बचें |
| env_high_humidity | High humidity — fungal conditions may flare; keep skin dry | उच्च आर्द्रता — फंगल समस्या बढ़ सकती है, त्वचा सूखी रखें |
| env_red_combined | High-risk skin day — UV + extreme heat combined | उच्च जोखिम दिन — UV + अत्यधिक गर्मी संयुक्त |

### 11.6 Verification

1. `./gradlew :app:assembleDebug` passes
2. On emulator with mock location (San Diego 32.7, -117.1) + forced UV 7, the Home card appears
3. Toggle off in Settings → card disappears, no WorkManager runs
4. Airplane mode on → card shows cached reading with timestamp
5. No `SecurityException` for coarse location when permission denied

---

## Phase 12 — Doctor share PDF reports

### 12.1 Intent

Generate a clinic-ready PDF using only `android.graphics.pdf.PdfDocument` (no iText, no third-party libs). Includes scan history with images + predictions + SkinMind summary + env data + page numbers + disclaimer on every page.

### 12.2 Acceptance

- [ ] Report builder screen: range chips (7d / 30d / 90d / custom), section toggles (Images / Predictions / SkinMind / Env)
- [ ] PDF has cover header, summary, per-scan entries with thumbnail + top-3 predictions, footer disclaimer + page numbers
- [ ] Opens in Drive/Files via system share sheet
- [ ] Works offline (no Firebase required)
- [ ] PDF file saved to `app-private files/reports/`
- [ ] `./gradlew :app:assembleDebug` passes

### 12.3 Files to create

#### `core/reports/build.gradle.kts`
New module. Add to `settings.gradle.kts`. Dependencies: `:core:common`, `:core:database`, `:core:domain`, `:core:environment`. No third-party PDF lib.

#### `core/reports/src/main/kotlin/com/dermoai/core/reports/ReportInput.kt`
```kotlin
data class ReportInput(
    val patientName: String,
    val dateRangeStart: Long,
    val dateRangeEnd: Long,
    val includeImages: Boolean,
    val includePredictions: Boolean,
    val includeSkinMind: Boolean,
    val includeEnv: Boolean,
    val scans: List<ScanWithPrediction>,
    val checkIns: List<DailyCheckInEntity>,
    val envConditions: WeatherConditions?,
)
data class ScanWithPrediction(
    val scan: SkinScanEntity,
    val predictions: List<ScanPredictionEntity>,
)
```

#### `core/reports/src/main/kotlin/com/dermoai/core/reports/PdfReportGenerator.kt`
```kotlin
@Singleton
class PdfReportGenerator @Inject constructor() {
    fun generate(input: ReportInput, outputDir: File): File
}
```
Implementation details:
- A4 portrait: 595×842 points
- 36pt margin on all sides
- Title: "DermoAI Doctor Report" — 24pt bold, TealAccent `#2DD4BF`
- Patient name + date range below in 11pt
- Per-scan entry: 72×72pt thumbnail (decoded from `scan.imagePath` via `BitmapFactory`, scaled down), date + body area below, top-3 predictions as a 3-row mini table with label + confidence %
- Footer on every page: "Educational only — not a medical diagnosis" centered 8pt gray
- Page numbers: "Page X of Y" bottom right 8pt
- When page would overflow, `startPage()` new page and continue
- Output file: `report_<timestamp>_<range>.pdf` in `outputDir`

#### `core/reports/src/main/kotlin/com/dermoai/core/reports/di/ReportsModule.kt`
```kotlin
@Module @InstallIn(SingletonComponent::class)
object ReportsModule {
    @Provides @Singleton fun providePdfReportGenerator() = PdfReportGenerator()
}
```

### 12.4 Files to modify

**`feature/reports/.../ReportBuilderScreen.kt`** (replace `ReportsPlaceholderScreen`):
- Range chips: 7d / 30d / 90d / Custom (date picker dialog)
- Toggles: Images / Predictions / SkinMind / Env (default all on)
- "Generate PDF" button (DermoPrimaryButton style)
- Progress dialog while generating
- "Share" button after generation — uses `FileProvider` + `Intent.ACTION_SEND`

**`feature/reports/build.gradle.kts`** — add deps `:core:reports`, `:core:database`, `:core:environment`, `:core:ui`.

**`feature/reports/.../ReportViewModel.kt`** — inject `PdfReportGenerator`, `SkinScanDao`, `ScanPredictionDao`, `DailyCheckInDao`, `EnvironmentRepository`. `generateReport(userId, range)` builds `ReportInput` and calls generator on `Dispatchers.IO`.

**`app/src/main/AndroidManifest.xml`** — add `<provider android:name="androidx.core.content.FileProvider"` with `android:authorities="com.dermoai.fileprovider"` and `res/xml/file_paths.xml`.

**`app/src/main/res/xml/file_paths.xml`**:
```xml
<paths>
  <files-path name="reports" path="reports/" />
</paths>
```

### 12.5 Strings (EN + HI) — feature/reports

| Key | EN | HI |
|-----|----|----|
| report_title | Doctor report | डॉक्टर रिपोर्ट |
| report_range_7d | Last 7 days | पिछले 7 दिन |
| report_range_30d | Last 30 days | पिछले 30 दिन |
| report_range_90d | Last 90 days | पिछले 90 दिन |
| report_range_custom | Custom range | कस्टम अवधि |
| report_section_images | Include images | छवियां शामिल करें |
| report_section_predictions | Include predictions | पूर्वानुमान शामिल करें |
| report_section_skinmind | Include SkinMind summary | स्किनमाइंड सारांश शामिल करें |
| report_section_env | Include environmental data | पर्यावरण डेटा शामिल करें |
| report_generate | Generate PDF | PDF बनाएं |
| report_generating | Generating… | बना रहे हैं… |
| report_share | Share report | रिपोर्ट साझा करें |
| report_footer_pdf | Educational only — not a medical diagnosis | केवल शैक्षिक — चिकित्सा निदान नहीं |

### 12.6 Verification

1. Build passes
2. With ≥1 saved scan, choose "Last 30 days" + all toggles on → PDF generates, opens in Drive
3. PDF opens in Drive/Files/any PDF reader without corruption
4. Each page has the disclaimer footer + page numbers
5. Scans without image file path still render (placeholder box)
6. No `OutOfMemoryError` for 50+ scans (downsample thumbnails)

---

## Phase 13 — Firebase sync (optional backup)

### 13.1 Intent

Optional backup when user provisions real Firebase. Firestore stores metadata (profiles, scans, check-ins), Cloud Storage stores encrypted photos only when "Cloud backup" toggle is on. Account delete wipes remote + local.

### 13.2 Acceptance

- [ ] With placeholder `google-services.json`: no crash, Settings shows "Sync not configured"
- [ ] With real `google-services.json`: sign-in + sync path works
- [ ] Cloud backup toggle off by default; turning on requires explicit user confirmation
- [ ] Account delete wipes Firestore + Storage + local Room + DataStore
- [ ] No Firebase calls during normal flow when not configured
- [ ] Build passes with both placeholder and real config

### 13.3 Files to create

#### `core/data/.../sync/FirebaseSyncManager.kt`
```kotlin
@Singleton
class FirebaseSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,
) {
    suspend fun syncScans(userId: String, scans: List<SkinScanEntity>): AppResult<Unit>
    suspend fun syncCheckIns(userId: String, checkIns: List<DailyCheckInEntity>): AppResult<Unit>
    suspend fun uploadPhoto(scanId: String, localPath: String): AppResult<String>
    suspend fun deleteAllUserData(userId: String): AppResult<Unit>
    val isConfigured: Boolean  // checks BuildConfig.FIREBASE_CONFIGURED
}
```

#### `core/data/.../sync/SyncQueueProcessor.kt`
WorkManager worker that drains a `sync_queue` table (or DataStore list) of pending operations.

#### `core/database/.../entity/SyncQueueEntity.kt`
```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val entityType: String,  // "scan", "checkin", "routine"
    val entityId: String,
    val operation: String,   // "upsert", "delete"
    val payload: String,     // JSON
    val createdAt: Long,
    val attempts: Int = 0,
)
```
Plus DAO with `observePending`, `markProcessed`, `incrementAttempts`.

### 13.4 Files to modify

**`app/.../BuildConfigFlags.kt`** — `FIREBASE_CONFIGURED = false` until real `google-services.json` dropped in. Detected via `try { FirebaseApp.initializeApp() } catch`.

**`feature/settings/.../SettingsScreen.kt`** — add sections: Sync status card (last synced timestamp, error if any), Cloud backup toggle (off by default, confirmation dialog), Delete account button (with type-name-to-confirm).

**`core/data/.../di/DataModule.kt`** — bind `FirebaseSyncManager` only if `BuildConfig.FIREBASE_CONFIGURED`. Use `@Qualifier` annotation `@FirebaseConfigured`.

### 13.5 Strings (EN + HI)

| Key | EN | HI |
|-----|----|----|
| settings_sync_status | Sync status | सिंक स्थिति |
| settings_sync_not_configured | Sync not configured | सिंक कॉन्फ़िगर नहीं है |
| settings_sync_last | Last synced: %1$s | अंतिम सिंक: %1$s |
| settings_cloud_backup | Cloud backup | क्लाउड बैकअप |
| settings_cloud_backup_body | Upload photos to Firebase Storage (encrypted) | फोटो को Firebase Storage पर अपलोड करें (एन्क्रिप्टेड) |
| settings_cloud_backup_confirm | Enable cloud backup? Photos will be uploaded encrypted-at-rest. | क्लाउड बैकअप सक्षम करें? फोटो एन्क्रिप्टेड अपलोड होंगे। |
| settings_delete_account | Delete account | खाता हटाएं |
| settings_delete_warning | This will permanently delete your account, all scans, check-ins, and cloud data. | यह आपका खाता, सभी स्कैन, चेक-इन और क्लाउड डेटा स्थायी रूप से हटा देगा। |
| settings_delete_type_to_confirm | Type DELETE to confirm | पुष्टि करने के लिए DELETE टाइप करें |

### 13.6 Verification

1. Placeholder config: app launches, Settings shows "Sync not configured", no Firebase errors in logcat
2. Real config (test with Firebase emulator): sign-in works, scans sync to Firestore
3. Cloud backup toggle on → photos upload to Storage under `users/{uid}/scans/{scanId}.jpg`
4. Delete account with "DELETE" typed → Firestore + Storage + local Room + DataStore all cleared
5. Airplane mode → sync queue accumulates; reconnect → drains

---

## Phase 14 — Analytics dashboard (charts)

### 14.1 Intent

Personal-progress charts, not clinical stats theater. Lightweight Compose Canvas — no webviews, no heavy chart libs.

### 14.2 Acceptance

- [ ] Check-in itch/stress over time (line chart)
- [ ] Scan frequency (bar by week)
- [ ] Concern band distribution (donut or stacked bar)
- [ ] Empty state until data exists
- [ ] Content descriptions on chart series for TalkBack
- [ ] Build passes

### 14.3 Files to create

#### `feature/analytics/.../AnalyticsViewModel.kt`
```kotlin
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val checkInDao: DailyCheckInDao,
    private val skinScanDao: SkinScanDao,
    private val predictionDao: ScanPredictionDao,
    private val insightsEngine: InsightsEngine,
) : ViewModel() {
    val itchOverTime: StateFlow<List<DataPoint>>
    val stressOverTime: StateFlow<List<DataPoint>>
    val scansByWeek: StateFlow<List<WeekBucket>>
    val concernBandDistribution: StateFlow<Map<String, Int>>
    val insights: StateFlow<List<SkinInsight>>
    fun refresh(userId: String)
}
data class DataPoint(val dateKey: String, val value: Float)
data class WeekBucket(val weekStart: String, val count: Int)
```

#### `feature/analytics/.../charts/LineChart.kt`, `BarChart.kt`, `DonutChart.kt`
All `@Composable private fun` using `Canvas` with `Modifier.fillMaxWidth().height(180.dp)`. Pure Compose — no third-party deps. Colors from `DermoColors`. Axis labels in `labelSmall` style. `contentDescription` parameter required on each for accessibility.

### 14.4 Files to modify

**`feature/analytics/.../AnalyticsScreen.kt`** — replace `AnalyticsPlaceholderScreen`:
- `GradientHeader("Analytics")`
- `MedicalDisclaimerBar`
- Scrollable Column with chart sections, each preceded by `SectionHeader(title, subtitle?)`
- Insight cards at bottom (reuse from Home)

**`feature/analytics/build.gradle.kts`** — add `:core:database`, `:core:domain`, `:core:analytics-engine`, `:core:ui`, lifecycle runtime compose, coroutines.

### 14.5 Strings (EN + HI)

| Key | EN | HI |
|-----|----|----|
| analytics_title | Analytics | एनालिटिक्स |
| analytics_itch_over_time | Itch over time | समय के साथ खुजली |
| analytics_stress_over_time | Stress over time | समय के साथ तनाव |
| analytics_scans_by_week | Scans per week | प्रति सप्ताह स्कैन |
| analytics_concern_distribution | Concern band distribution | चिंता बैंड वितरण |
| analytics_empty | No data yet. Complete check-ins and scans to see your trends. | अभी कोई डेटा नहीं। रुझान देखने के लिए चेक-इन और स्कैन करें। |

### 14.6 Verification

1. Build passes
2. Empty state renders with copy above when no check-ins/scans
3. With 5 check-ins over 5 days: itch line chart shows trend, x-axis dates, y-axis 0–10
4. With 3 scans across 2 weeks: bar chart shows 2 bars with correct counts
5. TalkBack reads chart series summary (content description set)

---

## Phase 15 — Wellness + Settings + de-slopification polish

### 15.1 Intent

Two things in this phase:
1. Build the wellness hub (breathing exercise, confidence journal) and full settings screen
2. **De-slopification pass** — make the app look professional, not like generic AI output

### 15.2 Wellness acceptance

- [ ] Wellness hub screen with 3 cards: Breathing, Journal, Streaks
- [ ] Breathing: 1-minute box breathing animation (4s in, 4s hold, 4s out, 4s hold), circle scales 0.4→1.0 with text cue
- [ ] Journal: text + mood emoji entry, saved to Room `journal_entries` table
- [ ] Streaks: celebration screen with fire animation on streak milestone
- [ ] Settings: theme (system/light/dark), dynamic color toggle, language EN/HI, export data, sign out, delete account (if Phase 13 done), env alerts toggle (Phase 11)

### 15.3 Settings acceptance

- [ ] Theme selection persists and applies on next app start
- [ ] Dynamic color toggle works (Android 12+)
- [ ] Language toggle switches EN ↔ HI live (recomposition)
- [ ] Export data: writes JSON to Downloads via SAF
- [ ] Sign out clears session, returns to sign-in
- [ ] Delete account available only if Phase 13 done

### 15.4 Files to create (Wellness)

#### `core/database/.../entity/JournalEntryEntity.kt`
```kotlin
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val mood: Int,           // 1–5
    val bodyText: String,
    val createdAt: Long,
)
```
Plus DAO + DatabaseModule provider + DB version bump.

#### `feature/wellness/.../WellnessScreen.kt`, `BreathingExerciseScreen.kt`, `JournalScreen.kt`, `StreaksScreen.kt`
Each with `GradientHeader` + `MedicalDisclaimerBar` where applicable.

#### `feature/wellness/.../BreathingViewModel.kt`
```kotlin
@HiltViewModel
class BreathingViewModel @Inject constructor() : ViewModel() {
    val phase: StateFlow<BreathingPhase>  // INHALE, HOLD_IN, EXHALE, HOLD_OUT
    val scale: StateFlow<Float>           // 0.4 → 1.0
    fun start(); fun stop()
}
enum class BreathingPhase(val label: String, val durationMs: Long) {
    INHALE("Breathe in", 4000),
    HOLD_IN("Hold", 4000),
    EXHALE("Breathe out", 4000),
    HOLD_OUT("Hold", 4000),
}
```

### 15.5 Files to create (Settings)

#### `feature/settings/.../SettingsScreen.kt`, `SettingsViewModel.kt`
ViewModel injects `UserPreferencesDataStore` + `AuthRepository` (sign-out) + (optional) `FirebaseSyncManager` (delete account).

### 15.6 **De-slopification pass** (the anti-AI-slop checklist)

Apply these changes across all screens. This is the most important part of Phase 15:

#### 15.6.1 Typography overhaul — `core/ui/.../Typography.kt`

Replace default Roboto with **Inter** font (download from Google Fonts, place in `core/ui/src/main/res/font/`). Use `FontFamily(Font(R.font.inter), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold))`.

**Tracking tweaks:**
- Hero numbers (streak, confidence %): `letterSpacing = (-1.5).sp` — tight, premium
- Section titles: medium weight, never all-caps
- Body: 1.4 line height for readability

#### 15.6.2 Iconography — replace generic Material icons with custom SVGs

The current app uses default `Icons.Outlined.*` everywhere — instant AI-slop tell. Replace with **custom vector drawables** in `core/ui/src/main/res/drawable/`:

| Custom icon | Replaces | Notes |
|---|---|---|
| `ic_scan_ring.xml` | `Icons.Outlined.PhotoCamera` | Stylized camera with teal ring, 2dp stroke |
| `ic_timeline_grid.xml` | `Icons.Outlined.List` | 2x3 grid with rounded corners |
| `ic_skinnmind_lotus.xml` | `Icons.Outlined.Psychology` | Lotus glyph (calm, not brain) |
| `ic_more_dots.xml` | `Icons.Outlined.Menu` | Three horizontal dots, 6dp |
| `ic_check_ring.xml` | `Icons.Outlined.CheckCircle` | Soft check with circular outline |
| `ic_uv_sun.xml` | `Icons.Outlined.Warning` | Sun with rays (UV alert) |
| `ic_thermometer.xml` | `Icons.Outlined.Warning` | Thermometer glyph (heat alert) |
| `ic_drop.xml` | `Icons.Outlined.Warning` | Water drop (humidity alert) |
| `ic_breath_circle.xml` | `Icons.Outlined.SelfImprovement` | Concentric circles (breathing) |

Use `painterResource(R.drawable.ic_xxx)` instead of ` Icons.Outlined.X`. Keep Material icons only for truly generic actions (back arrow, close, delete).

#### 15.6.3 Spacing rhythm — enforce 8dp grid

Audit every screen. All paddings/margins must be multiples of 4 or 8:
- 4dp, 8dp, 12dp, 16dp, 20dp, 24dp, 32dp, 40dp, 56dp
- Remove ad-hoc values like 14dp, 18dp, 22dp

Add a `Spacing.kt` object in `core/ui/theme/`:
```kotlin
object Spacing {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
    val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val huge = 40.dp
}
```
Use `Spacing.lg` not `16.dp` literally.

#### 15.6.4 Empty states — hand-crafted, not generic

Every empty state currently shows an outlined Material icon centered + headline + body. This screams template. Replace with:

1. **Custom illustration** — simple geometric composition in `core/ui/src/main/res/drawable/`:
   - `illust_empty_scans.xml` — single photo frame with dashed outline + small sparkle
   - `illust_empty_timeline.xml` — vertical stack of 3 fading cards
   - `illust_empty_analytics.xml` — bar chart with one bar + dotted line
   - `illust_empty_treatment.xml` — pill bottle outline + check
2. Headline in `headlineSmall` (not `headlineMedium` — too big)
3. Body in `bodyMedium` with `onSurfaceVariant` at 0.7 alpha
4. CTA button inline below, not centered-spaced
5. Limit width to 280dp so it doesn't sprawl on tablets

#### 15.6.5 Loading states — replace spinners with skeletons

`CircularProgressIndicator` everywhere = AI slop. Replace with **shimmer skeletons**:

Create `core/ui/.../components/ShimmerBox.kt`:
```kotlin
@Composable
fun ShimmerBox(modifier: Modifier, shape: Shape = RoundedCornerShape(16.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -300f, targetValue = 300f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmerX"
    )
    Box(modifier.fillMaxWidth().clip(shape).background(
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            start = Offset(translate, 0f), end = Offset(translate + 300f, 0f),
        )
    ))
}
```
Use for: timeline cards, scan results predictions list, analytics charts. Not for actions — buttons stay enabled with disabled state.

#### 15.6.6 Motion — spring animations on cards, not just fades

Currently tab switches are instant. Add subtle motion:
- Card appear: `AnimatedVisibility(enter = fadeIn() + slideInVertically(initialOffsetY = { it / 20 }))`
- Button tap: `Modifier.pointerInput { detectTapGestures(onPress = { /* scale 0.97f */ }) }`
- Streak fire: `animateFloatAsState(targetValue = 1f, animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))`
- Tab content: `Crossfade(targetState = currentTab, animationSpec = tween(200))`

Create `core/ui/.../Motion.kt` with reusable specs:
```kotlin
object DermoMotion {
    val cardEnter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 20 }
    val tabSwitch = tween<Float>(200)
    val springBounce = spring<Float>(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
}
```

#### 15.6.7 Color discipline — semantic tokens, no random accent

Audit every `DermoColors.X.copy(alpha = 0.06f)` — there are too many arbitrary alpha values. Define semantic tokens:

```kotlin
object DermoSemanticColors {
    val surfaceContainerLow = Color(0xFFF7F8FA)        // light card bg
    val surfaceContainerHigh = Color(0xFFFFFFFF)        // elevated card bg
    val accentSoftTint = DermoColors.TealAccent.copy(alpha = 0.08f)  // CTA bg
    val successSoftTint = DermoColors.HealthGreen.copy(alpha = 0.08f)
    val warningSoftTint = DermoColors.WarmAmber.copy(alpha = 0.08f)
    val dangerSoftTint = DermoColors.SoftCoral.copy(alpha = 0.08f)
}
```

Dark mode equivalents in same file. Replace every ad-hoc alpha in feature code with these tokens.

#### 15.6.8 Edge-to-edge + insets

`enableEdgeToEdge()` already in MainActivity. Audit every screen: `Modifier.padding(scaffoldPadding)` must include status bar + nav bar insets. Bottom nav content must not hide behind system nav bar.

#### 15.6.9 Microcopy audit

Replace generic copy:
- "Loading…" → context-specific ("Analyzing patterns for education…", "Preparing your timeline…")
- "Error" → "Couldn't load X. Pull to retry."
- "Success" → silent (no toast) or contextual ("Saved to timeline")
- "Coming soon" → remove entirely (don't ship placeholders)

#### 15.6.10 Bottom nav — custom indicator

Replace default M3 `NavigationBar` indicator pill with a custom **dot + active icon tint** style:
- Selected: filled icon in TealAccent + 4dp dot below + label in TealAccent
- Unselected: outlined icon in onSurfaceVariant
- No pill background

#### 15.6.11 Splash screen — proper branded splash

Use `androidx.core:core-splashscreen`. Create `styles.xml` splash theme with DermoAI logo on `TealAccent` → `VioletAccent` gradient background. No text on splash (logo only).

#### 15.6.12 Cards — gradient borders, not flat surfaces

Replace generic `Surface(color = surfaceContainerHigh)` cards with `DermoGlassCard` (gradient border 1dp teal→violet, 28dp radius, 20dp padding). Already exists in `core/ui` — use it everywhere.

### 15.7 Strings (EN + HI) — wellness + settings

| Key | EN | HI |
|-----|----|----|
| wellness_title | Wellness | कल्याण |
| wellness_breathing | Box breathing | बॉक्स ब्रीदिंग |
| wellness_breathing_body | 1-minute calming exercise | 1-मिनट शांत व्यायाम |
| wellness_journal | Confidence journal | आत्मविश्वास जर्नल |
| wellness_journal_body | Track how you feel | अपनी भावनाएं ट्रैक करें |
| wellness_streaks | Streaks | स्ट्रीक |
| breathing_inhale | Breathe in | अंदर सांस लें |
| breathing_hold | Hold | रोकें |
| breathing_exhale | Breathe out | बाहर सांस छोड़ें |
| settings_title | Settings | सेटिंग्स |
| settings_theme | Theme | थीम |
| settings_theme_system | System | सिस्टम |
| settings_theme_light | Light | लाइट |
| settings_theme_dark | Dark | डार्क |
| settings_dynamic_color | Dynamic color | डायनामिक कलर |
| settings_language | Language | भाषा |
| settings_export | Export my data | मेरा डेटा निर्यात करें |
| settings_sign_out | Sign out | साइन आउट |
| settings_about | About | बारे में |
| settings_disclaimer | Full disclaimer | पूरा अस्वीकरण |

### 15.8 Verification

1. Build passes
2. App cold-starts with branded splash (logo + gradient, no white flash)
3. Bottom nav shows custom dot indicator, no pill
4. Every empty state uses a custom illustration, not a Material icon
5. Loading states use shimmer skeletons, no `CircularProgressIndicator` on cards
6. Hindi QA pass: switch language, every screen renders HI without overflow
7. Inter font visible (compare to Roboto — Inter has tighter lowercase 'a', 'g')
8. Tap any card → subtle scale animation (0.97f) + fade
9. Settings → theme dark → entire app switches with no white flashes
10. Settings → export → JSON file saved to Downloads

---

## Phase 16 — Testing + final quality audit

### 16.1 Intent

Comprehensive test suite covering domain logic, Room DAOs, ViewModels, Compose UI. Final language audit + manual checklist.

### 16.2 Acceptance

- [ ] Unit tests: insights rules, environment evaluator, streak computation, PDF input builder
- [ ] Instrumented tests: Room DAOs (SkinScanDao, DailyCheckInDao, TreatmentRoutineDao)
- [ ] Compose UI tests: auth guard, tab nav, disclaimer presence, scan flow
- [ ] Manual checklist: language audit EN+HI, airplane mode, scan flow, PDF generation
- [ ] No `TODO`, no `null!!`, no hardcoded EN-only strings
- [ ] Coverage ≥ 60% on `core/*` modules
- [ ] Build passes with all tests green

### 16.3 Files to create

#### `core/analytics-engine/src/test/.../RuleBasedInsightsEngineTest.kt`
Test each of the 6 rules:
- `when fewer than 2 scans and 3 check-ins → emits NO_PATTERN`
- `when 5+ scans → emits scan frequency insight`
- `when 2+ CRITICAL/HIGH predictions → emits elevated concern insight`
- `when 3+ low-sleep + high-itch days → emits SLEEP_CORRELATION`
- `when 3+ high-stress days → emits STRESS_CORRELATION`
- `when 3+ new-product days → emits ADHERENCE_IMPROVEMENT`

Use fakes for DAOs (return canned Flow).

#### `core/environment/src/test/.../EnvironmentAlertEvaluatorTest.kt`
Pure function tests:
- `uv=7, temp=20, humidity=50 → HIGH_UV`
- `uv=2, temp=36, humidity=50 → EXTREME_HEAT`
- `uv=2, temp=20, humidity=90 → HIGH_HUMIDITY`
- `uv=7, temp=36, humidity=50 → RED_COMBINED`
- `uv=2, temp=20, humidity=50 → null`

#### `core/database/src/androidTest/.../DermoDatabaseTest.kt`
In-memory Room database. Test each DAO:
- Insert scan + predictions, query by userId → returns enriched
- Insert check-in, query by date → returns entity
- Upsert + delete → confirms row gone

#### `feature/home/src/androidTest/.../HomeDashboardScreenTest.kt`
- Shows disclaimer bar
- Empty state renders when no scans
- Scan CTA clickable → invokes callback
- SkinMind chip shows streak when completed

#### `app/src/androidTest/.../AuthGuardTest.kt`
- Unauthenticated → navigates to sign-in
- Authenticated → navigates to Home

#### `app/src/androidTest/.../TabNavTest.kt`
- All 5 tabs reachable when authenticated
- Scan sub-flow hides bottom bar

### 16.4 Manual checklist (create `docs/QA-Checklist.md`)

```
[ ] Cold start → branded splash → onboarding (first run)
[ ] Sign in → Home loads with greeting
[ ] Each tab navigates without crash
[ ] Scan capture → review → results → save → appears in Timeline
[ ] SkinMind check-in → streak updates on Home
[ ] Treatment routine create → check steps → completion persists
[ ] Analytics renders with 5+ data points
[ ] Doctor PDF generates and opens in Drive
[ ] Settings: theme toggle works, language toggle works
[ ] Airplane mode: scan, timeline, check-in all work offline
[ ] Hindi: every screen renders HI without overflow
[ ] No banned phrases ("diagnosis", "you have", "cancer as fact")
[ ] Disclaimer on: Home, Scan results, Timeline detail, Analytics, PDF preview
```

### 16.5 Verification

1. `./gradlew test` — all unit tests green
2. `./gradlew connectedAndroidTest` — all instrumented tests green
3. `./gradlew :app:assembleDebug` — build green
4. Manual QA checklist all checked
5. `grep -r "TODO" feature/ core/` returns nothing
6. `grep -rn '"[A-Z][a-z]' feature/ core/ | grep -v strings.xml` finds no hardcoded English strings in Kotlin

---

## Appendix — De-slopification quick reference

**The 10 instant tells of AI-generated Android UI (and how DermoAI avoids them):**

| AI slop tell | DermoAI fix |
|---|---|
| Default Roboto font everywhere | Inter font, tight tracking on numbers |
| `Icons.Outlined.*` for every action | Custom SVG drawables for brand-relevant glyphs |
| `CircularProgressIndicator` centered | Shimmer skeletons matching content shape |
| Generic empty state: icon + headline + body + centered button | Custom illustration + inline CTA, capped width |
| Flat `Surface(color = surfaceContainerHigh)` cards | `DermoGlassCard` with 1dp gradient border |
| `MaterialTheme.colorScheme.primary` everywhere | `DermoSemanticColors` tokens with deliberate alpha |
| Default `NavigationBar` pill indicator | Custom dot + active icon tint, no pill |
| Ad-hoc padding values (14dp, 18dp, 22dp) | `Spacing` object on 8dp grid |
| Instant tab switches, no motion | `Crossfade` + spring on cards, subtle scale on tap |
| White splash screen | Branded gradient splash with logo via SplashScreen API |

If a screen has 3+ of these tells, it gets flagged for rework before Phase 16 sign-off.
