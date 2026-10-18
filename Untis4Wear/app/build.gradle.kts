import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.untis4wear"
    // Wear OS 6 (One UI 8 Watch) is based on Android 16 = API level 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.untis4wear"
        // Wear OS 3 (API 30) is the lowest Wear OS version that is still in use.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The app intentionally uses only the Android platform APIs (no AndroidX / Compose),
// so there are no third-party dependencies to keep in sync.
