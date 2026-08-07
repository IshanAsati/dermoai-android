import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ── Appwrite backend sync configuration ──────────────────────────────────────
// Read from `local.properties` (gitignored) with EMPTY defaults, so a fresh
// clone with no backend provisioned still builds and runs — it just falls back
// to local-only mode. This mirrors how `FirebaseAuthRepository` treats the
// placeholder Firebase project.
//
// SECURITY: only the endpoint, project id and database id live here. All three
// are public in every Appwrite *client* app by design — the Appwrite security
// model assumes the client knows them and enforces authorisation server-side
// from the user's session. An Appwrite **API key** must NEVER be added here,
// to BuildConfig, or to any committed file: everything in an APK is recoverable
// with `unzip` + `strings`. Key-holding setup lives in
// `tools/appwrite/setup_collections.py`, which reads APPWRITE_API_KEY from the
// environment and runs on the developer's machine only.
//
// Duplicated (rather than shared) with `app/build.gradle.kts` because this
// module is where `AppwriteConfig` reads BuildConfig from — an Android library
// gets its own BuildConfig class and cannot see the application module's.
val appwriteProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun appwriteProperty(key: String): String =
    (appwriteProperties.getProperty(key) ?: "").trim()

android {
    namespace = "com.dermoai.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "APPWRITE_ENDPOINT", "\"${appwriteProperty("APPWRITE_ENDPOINT")}\"")
        buildConfigField("String", "APPWRITE_PROJECT_ID", "\"${appwriteProperty("APPWRITE_PROJECT_ID")}\"")
        buildConfigField("String", "APPWRITE_DATABASE_ID", "\"${appwriteProperty("APPWRITE_DATABASE_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.appwrite.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(libs.androidx.test.ext.junit)
}