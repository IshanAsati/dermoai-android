plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.dermoai.feature.doctor"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    // Cross-device linking: an invite generated on the doctor's phone has to be
    // findable from the patient's. Everything degrades to local-only when the
    // backend is unconfigured or unreachable.
    implementation(project(":core:data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    // QR generation and scanning: `zxing:core` is the pure-Java codec used for
    // both directions. CameraX supplies the live preview and analysis frames
    // for the patient's fallback "scan the doctor's QR" flow — see
    // QrScanScreen.kt. android.permission.CAMERA is already declared in the
    // app manifest for the skin-scan feature.
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // camera-core's ProcessCameraProvider.getInstance() returns a real Guava
    // ListenableFuture. This module also pulls in :core:data, whose Firebase
    // dependencies resolve a real `com.google.guava:guava` on the *runtime*
    // classpath but not the compile classpath, so without this the
    // listenablefuture:1.0 stub gets swapped for Guava's intentionally empty
    // conflict-avoidance jar and ListenableFuture becomes unresolvable at
    // compile time. Pinned to the version Firebase/Firestore already resolves
    // elsewhere in the graph so nothing downstream is bumped.
    implementation("com.google.guava:guava:32.1.3-android")
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(libs.androidx.test.ext.junit)
}
