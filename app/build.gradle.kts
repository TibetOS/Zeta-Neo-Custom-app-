plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Set by the release workflow from the git tag / run number; local builds
// fall back to a dev version.
val appVersionName = providers.environmentVariable("APP_VERSION_NAME").orNull
    ?.removePrefix("v") ?: "0.1.0-dev"
val appVersionCode = providers.environmentVariable("APP_VERSION_CODE").orNull
    ?.toIntOrNull() ?: 1

// Release signing is optional: configured entirely from the environment
// (see .github/workflows/release.yml) so no credential lives in the repo.
// Without it, assembleRelease produces an unsigned APK.
fun env(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = env("RELEASE_KEYSTORE_FILE")
val releaseStorePassword = env("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = env("RELEASE_KEY_ALIAS")
val releaseKeyPassword = env("RELEASE_KEY_PASSWORD")

android {
    namespace = "com.traffko.outlanderhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.traffko.outlanderhub"
        // Zeta Neo units ship with Android 10+; keep minSdk low enough for older firmware.
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.accompanist.drawablepainter)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
