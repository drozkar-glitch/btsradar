import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
    }
}

android {
    namespace = "cz.mares.btsradar"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.btsradar"
        minSdk = 24
        targetSdk = 36
        val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        val appVer = System.getenv("APP_VERSION")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
        versionCode = runNum + 100
        versionName = appVer
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("CM_KEYSTORE_PATH")
                ?: System.getenv("ANDROID_KEYSTORE_PATH")
                ?: System.getenv("KEYSTORE_FILE")
                ?: System.getenv("STORE_FILE")
            val ksPass = System.getenv("CM_KEYSTORE_PASSWORD")
                ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                ?: System.getenv("STORE_PASSWORD")
            val kAlias = System.getenv("CM_KEY_ALIAS")
                ?: System.getenv("ANDROID_KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val kPass = System.getenv("CM_KEY_PASSWORD")
                ?: System.getenv("ANDROID_KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")
                ?: ksPass

            if (!ksPath.isNullOrBlank() && File(ksPath).exists() && !ksPass.isNullOrBlank() && !kAlias.isNullOrBlank()) {
                storeFile = File(ksPath)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val relSigning = signingConfigs.getByName("release")
            signingConfig = if (relSigning.storeFile != null) relSigning else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
