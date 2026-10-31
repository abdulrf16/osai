import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Secrets stay out of source control: read from git-ignored local files or the environment.
fun loadProps(name: String): Properties = Properties().apply {
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { load(it) }
}
val localProps = loadProps("local.properties")
val keystoreProps = loadProps("keystore.properties")
fun secret(env: String, props: Properties, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: props.getProperty(key)?.takeIf { it.isNotBlank() }

val weatherKey = secret("FRONX_WEATHER_API_KEY", localProps, "weather.apiKey") ?: ""
val ksFile = secret("FRONX_KEYSTORE_FILE", keystoreProps, "storeFile")
val ksPass = secret("FRONX_KEYSTORE_PASSWORD", keystoreProps, "storePassword")
val ksAlias = secret("FRONX_KEY_ALIAS", keystoreProps, "keyAlias")
val ksKeyPass = secret("FRONX_KEY_PASSWORD", keystoreProps, "keyPassword")
val hasReleaseSigning = ksFile != null && ksPass != null && ksAlias != null && ksKeyPass != null

android {
    namespace = "com.fronx.smartlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fronx.smartlauncher"
        minSdk = 29
        targetSdk = 33 // head unit reports Android 13
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "WEATHER_API_KEY", "\"$weatherKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Same applicationId so the debug build can be tried before the release one.
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
