import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.firebase.crashlytics)
}

// ── Appwrite backend sync configuration ──────────────────────────────────────
// Endpoint / project id / database id, read from `local.properties` (gitignored)
// with EMPTY defaults so a clone with no backend provisioned still builds and
// runs — it degrades to local-only mode, the same way the app degrades when the
// Firebase config is the placeholder project.
//
// These three values are NOT secrets: every Appwrite client app ships them and
// Appwrite's security model assumes the client knows them, enforcing access
// server-side from the user's session plus document permissions. An Appwrite
// **API key** must NEVER appear here or anywhere else in the app — an APK is
// readable with `unzip` + `strings`. Server-side provisioning that needs a key
// is `tools/appwrite/setup_collections.py`, which reads APPWRITE_API_KEY from
// the environment and never writes it to disk.
//
// `core/data/build.gradle.kts` repeats this block because the sync layer lives
// there and an Android library sees only its own BuildConfig, never the app's.
// The copy here exists so app-level code can read the same values without
// reaching across modules.
val appwriteProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun appwriteProperty(key: String): String =
    (appwriteProperties.getProperty(key) ?: "").trim()

android {
    namespace = "com.dermoai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dermoai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", "false")
        buildConfigField("String", "APPWRITE_ENDPOINT", "\"${appwriteProperty("APPWRITE_ENDPOINT")}\"")
        buildConfigField("String", "APPWRITE_PROJECT_ID", "\"${appwriteProperty("APPWRITE_PROJECT_ID")}\"")
        buildConfigField("String", "APPWRITE_DATABASE_ID", "\"${appwriteProperty("APPWRITE_DATABASE_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":core:camera"))
    implementation(project(":core:ml"))
    implementation(project(":core:analytics-engine"))
    implementation(project(":core:reports"))
    implementation(project(":core:environment"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:skinnmind"))
    implementation(project(":feature:treatment"))
    implementation(project(":feature:wellness"))
    implementation(project(":feature:analytics"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:faq"))
    implementation(project(":feature:finder"))
    implementation(project(":feature:doctor"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// Firebase config (google-services.json) is developer-provided and gitignored.
// Only apply the plugin when the file is present, so local builds work without it.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}