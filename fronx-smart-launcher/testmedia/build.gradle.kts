plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI-only helper: publishes a real MediaSession so the emulator test can exercise Fronx's media card.
// Not part of the launcher deliverable.
android {
    namespace = "com.fronx.testmedia"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fronx.testmedia"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
