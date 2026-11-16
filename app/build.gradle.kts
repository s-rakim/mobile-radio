plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Spotify's App Remote SDK only ships as an .aar on their GitHub releases page, so it isn't in
// the repo. CI downloads it into app/libs before building (see .github/workflows/build.yml, or
// run scripts/fetch-spotify-sdk.sh locally). Without it the app still builds and simply plays
// everything from Spitify's own sources.
val spotifySdk: File? = file("libs").listFiles()
    ?.firstOrNull { it.extension == "aar" && it.name.contains("app-remote") }

android {
    namespace = "com.srakim.spitify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.srakim.spitify"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Shared Spitify server for accounts, friends and messages. Everyone who wants to be
        // friends in the app needs the same one. Set the SPITIFY_SERVER_URL Actions variable.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"${(System.getenv("SPITIFY_SERVER_URL") ?: "").replace("\"", "")}\"")
        // Client id of a Spotify developer app, for importing playlists. Not a secret.
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${(System.getenv("SPOTIFY_CLIENT_ID") ?: "").replace("\"", "")}\"")
    }

    // The release key never lives in the repo. CI writes it from secrets (or makes a
    // throwaway one) and passes the path in SPITIFY_KEYSTORE. Without it, release builds
    // fall back to the debug key so a local assembleRelease still works.
    val releaseStore = System.getenv("SPITIFY_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("SPITIFY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SPITIFY_KEY_ALIAS")
                keyPassword = System.getenv("SPITIFY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(if (spotifySdk != null) "src/spotify/java" else "src/nospotify/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    // Some radio stations only stream as HLS (.m3u8).
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR codes for Jam invites. Core only, no camera or Android parts.
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Only for the screen tour that screenshots the app on an emulator in CI.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    if (spotifySdk != null) {
        implementation(files(spotifySdk))
        // App Remote talks to the Spotify app in JSON and expects Gson on the classpath.
        implementation("com.google.code.gson:gson:2.11.0")
    }
}
