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

// Release signing, in priority order: environment (CI secrets, see
// .github/workflows/release.yml) → the checked-in release-signing.keystore.
// Committing the keystore is a deliberate convenience for this personal,
// sideloaded app: it keeps the release signature stable so updates install
// over each other with zero setup. Tradeoff: anyone with repo access can
// sign app-compatible APKs — switch to CI secrets if that ever matters.
fun env(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

// The built-in credentials belong to the checked-in keystore ONLY. When a
// custom keystore comes from the environment, its passwords must too — a
// partial secret set should fail loudly, not fall back to the defaults and
// produce a baffling "keystore password was incorrect".
val envKeystorePath = env("RELEASE_KEYSTORE_FILE")
fun requiredSigningEnv(name: String): String =
    env(name) ?: error("RELEASE_KEYSTORE_FILE is set but $name is missing")

val releaseStoreFile =
    if (envKeystorePath != null) file(envKeystorePath)
    else rootProject.file("release-signing.keystore").takeIf { it.exists() }
val releaseStorePassword =
    if (envKeystorePath != null) requiredSigningEnv("RELEASE_KEYSTORE_PASSWORD") else "outlander-hub"
val releaseKeyAlias =
    if (envKeystorePath != null) requiredSigningEnv("RELEASE_KEY_ALIAS") else "outlander"
val releaseKeyPassword =
    if (envKeystorePath != null) requiredSigningEnv("RELEASE_KEY_PASSWORD") else "outlander-hub"

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

    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
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
