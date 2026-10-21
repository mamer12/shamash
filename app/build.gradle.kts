import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.shamash"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.shamash"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Your own release key from local.properties (never committed); without one, release uses the debug key so a
    // fork can build and install at once.
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }
    // API keys from local.properties (or the environment, for CI), baked into BuildConfig; never in the source or the UI.
    fun key(name: String, env: String): String =
        (local.getProperty(name) ?: System.getenv(env) ?: "").trim().replace("\\", "\\\\").replace("\"", "\\\"")
    defaultConfig {
        buildConfigField("String", "TMDB_KEY", "\"${key("tmdb.key", "SHAMASH_TMDB_KEY")}\"")
        buildConfigField("String", "YOUTUBE_CLIENT_ID", "\"${key("youtube.clientId", "SHAMASH_YOUTUBE_CLIENT_ID")}\"")
        buildConfigField("String", "YOUTUBE_CLIENT_SECRET", "\"${key("youtube.clientSecret", "SHAMASH_YOUTUBE_CLIENT_SECRET")}\"")
    }

    val ownKey = local.getProperty("signing.storeFile")?.let { path ->
        signingConfigs.create("own") {
            storeFile = file(path)
            storePassword = local.getProperty("signing.storePassword")
            keyAlias = local.getProperty("signing.keyAlias")
            keyPassword = local.getProperty("signing.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = ownKey ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}


dependencies {
    val compose = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(compose)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
