plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.offlinevault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offlinevault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Foldable & Adaptive layouts for Samsung Galaxy Fold
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background workers & Settings
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Video & Audio Downloader (yt-dlp, FFmpeg, aria2c)
    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)

    // RSS / Podcasts
    implementation(libs.rssparser)
    implementation(libs.coil.compose)

    // Web Scraping & Deep Site Downloader
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation("org.json:json:20240303")
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
}
