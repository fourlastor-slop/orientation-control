plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.fourlastor.orientation"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fourlastor.orientation"
        minSdk = 26
        targetSdk = 36
        // Android only installs an update with a higher number than the one it has
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = "1.${versionCode}"
    }

    // The organization's release keystore, handed over by the publish workflow
    val keystore = System.getenv("ANDROID_KEYSTORE_PATH")
    if (keystore != null) {
        signingConfigs.create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
}
