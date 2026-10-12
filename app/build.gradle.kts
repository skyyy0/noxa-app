import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

// Fallback signing credentials for the repository's own keystore
// (`my-upload-key.jks`). This is a personal, privately hosted app: committing
// the keystore + password is what lets every CI build produce APKs with ONE
// stable signature, so each new version installs as an in-place UPDATE instead
// of requiring an uninstall. Environment variables still take precedence.
val COMMITTED_KEYSTORE_PASSWORD = "noxa-local-signing"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.noxa.music"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "1.9.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Signing configuration.
  //
  // AI Studio ships the release keystore at ${rootDir}/my-upload-key.jks and passes
  // KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD. Codemagic instead exports
  // CM_KEYSTORE_PATH / CM_KEYSTORE_PASSWORD / CM_KEY_ALIAS / CM_KEY_PASSWORD through
  // its `android_signing` integration. Both are supported here.
  //
  // A signing config is only registered when its keystore actually exists, so the
  // project can also be compiled on CI without any keystore (the release APK is then
  // simply left unsigned instead of failing the build).
  signingConfigs {
    val releaseKeystoreFile = file(
      System.getenv("KEYSTORE_PATH")
        ?: System.getenv("CM_KEYSTORE_PATH")
        ?: "${rootDir}/my-upload-key.jks"
    )
    if (releaseKeystoreFile.exists()) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
          ?: System.getenv("CM_KEYSTORE_PASSWORD")
          ?: COMMITTED_KEYSTORE_PASSWORD
        keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("CM_KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
          ?: System.getenv("CM_KEY_PASSWORD")
          ?: COMMITTED_KEYSTORE_PASSWORD
      }
    }

    val debugKeystoreFile = file("${rootDir}/debug.keystore")
    if (debugKeystoreFile.exists()) {
      create("debugConfig") {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Signed when a release keystore is available, otherwise left unsigned.
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    debug {
      // Uses the AI Studio debug keystore when present, otherwise falls back to the
      // standard auto-generated Android debug keystore.
      signingConfigs.findByName("debugConfig")?.let { signingConfig = it }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    // NewPipeExtractor uses java.nio.file APIs that need core-library
    // desugaring on minSdk < 33 (per the library's README).
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.firebase.ai)
  // firebase-firestore removed in v1.9.1: it is unused in app code and its
  // transitive protolite-well-known-types duplicates protobuf classes that
  // NewPipeExtractor needs at a newer version (release builds failed the
  // duplicate-class check). Auth is unaffected.
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  // On-device YouTube audio extraction: full-length streams without any
  // Piped/Invidious instance. License GPL-3.0 — fine for this private app.
  implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.1")
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
