import java.security.KeyStore
import java.time.Instant
import java.util.Collections

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "ir.courseplanner.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "ir.courseplanner.app"
    minSdk = 24
    targetSdk = 36
    // NOTE: versionCode MUST be bumped on every user-facing APK release.
    // Android refuses to install an "update" with the same versionCode,
    // which is exactly why latest changes looked "missing" on the APK.
    versionCode = 20
    versionName = "2.7.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Embed git SHA + build time so any installed APK is verifiable
    // from Settings screen (no more guessing which build is on device).
    val gitShaProvider = providers.exec {
      commandLine("git", "rev-parse", "--short=7", "HEAD")
      isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifBlank { "local" } }
    val gitSha = try { gitShaProvider.get() } catch (_: Exception) { "local" }
    buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
  }

  // Release signing: the private keystore lives OUTSIDE this repository and is
  // never committed. The interface is deterministic — exactly four environment
  // variables and nothing else:
  //
  //   RELEASE_KEYSTORE_PATH      path to the release keystore
  //   RELEASE_KEY_ALIAS          key alias inside that keystore
  //   RELEASE_KEYSTORE_PASSWORD  keystore password
  //   RELEASE_KEY_PASSWORD       key password
  //
  // There are NO fallbacks: not the legacy STORE_PASSWORD / KEY_PASSWORD, not
  // -P overrides, not gradle.properties values, and never the debug keystore.
  // A release build missing any of the four fails loudly in `verifyReleaseSigning`
  // below instead of falling back to a debug-signed or unsigned artifact.
  //
  // The distribution key was ROTATED in 2026-09 after the historical debug key
  // leaked into public git history; the old certificate must not sign anything
  // again. See docs/SECURITY.md for the current fingerprint and the policy.
  signingConfigs {
    create("release") {
      // An unset path must not resolve to a plausible file: point it somewhere
      // that can never exist so the guard reports a missing keystore rather
      // than attempting to sign with whatever happens to be in the project root.
      val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")?.trim().orEmpty()
      storeFile =
        if (keystorePath.isEmpty()) {
          rootProject.file("build/absent-release-keystore.jks")
        } else {
          rootProject.file(keystorePath)
        }
      keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim()
      storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")?.trim()
      keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.trim()
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Official releases are ALWAYS signed with the release keystore. There is
      // deliberately no fallback to the debug keystore and no unsigned
      // artifact: when credentials are missing, the `verifyReleaseSigning` task
      // registered below fails the build loudly.
      signingConfig = signingConfigs.getByName("release")
    }
    // debug: AGP's own debug keystore (~/.android/debug.keystore, auto-created
    // on first build) — a fresh clone still builds with zero setup, and the
    // debug key is never used to sign a released APK.
  }

  // --- Release signing guard -------------------------------------------------
  // A machine without credentials must never produce a release artifact, and a
  // misconfigured CI run must never publish a debug-signed or unsigned APK.
  // Every release packaging task therefore depends on this explicit check.
  val guardKeystoreFile: File? = signingConfigs.getByName("release").storeFile
  val guardAlias: String? = signingConfigs.getByName("release").keyAlias
  val guardStorePassword: String? = signingConfigs.getByName("release").storePassword
  val guardKeyPassword: String? = signingConfigs.getByName("release").keyPassword

  // The container may be JKS or PKCS12; pick the engine from the file extension
  // and fall back to the other one so either format works with no extra config.
  fun openReleaseKeyStore(file: File, password: CharArray): KeyStore {
    val preferred =
      when (file.extension.lowercase()) {
        "jks", "keystore" -> "JKS"
        "p12", "pfx", "pkcs12" -> "PKCS12"
        else -> KeyStore.getDefaultType()
      }
    var firstFailure: Exception? = null
    for (type in listOf(preferred, "JKS", "PKCS12").distinct()) {
      try {
        val keyStore = KeyStore.getInstance(type)
        file.inputStream().use { stream -> keyStore.load(stream, password) }
        return keyStore
      } catch (e: Exception) {
        if (firstFailure == null) firstFailure = e
      }
    }
    throw firstFailure ?: IllegalStateException("keystore ${file.name} could not be read")
  }

  val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description =
      "Fails when the release keystore is missing, unreadable, or has the wrong alias/passwords."
    doLast {
      val missing = listOfNotNull(
        "keystore file $guardKeystoreFile (set RELEASE_KEYSTORE_PATH)".takeIf {
          guardKeystoreFile?.exists() != true
        },
        "key alias (set RELEASE_KEY_ALIAS)".takeIf { guardAlias.isNullOrBlank() },
        "store password (set RELEASE_KEYSTORE_PASSWORD)".takeIf {
          guardStorePassword.isNullOrBlank()
        },
        "key password (set RELEASE_KEY_PASSWORD)".takeIf {
          guardKeyPassword.isNullOrBlank()
        },
      )
      if (missing.isNotEmpty()) {
        throw GradleException(
          "Release signing is not configured — refusing to produce an unsigned release APK.\n" +
            "Missing: ${missing.joinToString("; ")}\n" +
            "Set exactly these four environment variables (CI: GitHub Actions secrets): " +
            "RELEASE_KEYSTORE_PATH, RELEASE_KEY_ALIAS, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_PASSWORD. " +
            "There are no fallbacks (-P and legacy STORE_PASSWORD/KEY_PASSWORD are ignored). " +
            "See docs/SECURITY.md for the signing policy.",
        )
      }

      // All four variables are present, so the remaining misconfiguration modes
      // are wrong VALUES: a wrong store password, a wrong alias, or a wrong key
      // password. Catching them here means a broken CI secret fails in seconds
      // with a fixable message instead of silently degrading the artifact.
      val alias = guardAlias ?: return@doLast
      val keystoreFile = guardKeystoreFile ?: return@doLast
      val keyStore =
        try {
          openReleaseKeyStore(keystoreFile, guardStorePassword!!.toCharArray())
        } catch (e: Exception) {
          throw GradleException(
            "Release signing is misconfigured — refusing to produce a release APK.\n" +
              "Could not open keystore ${keystoreFile.absolutePath} " +
              "(${e::class.java.simpleName}: ${e.message}).\n" +
              "Usually RELEASE_KEYSTORE_PASSWORD is wrong, or the file is not a real keystore " +
              "(e.g. a truncated base64 decode). " +
              "See docs/SECURITY.md for the signing policy.",
          )
        }
      if (!keyStore.isKeyEntry(alias)) {
        val available =
          Collections.list(keyStore.aliases()).sorted().joinToString(", ").ifEmpty { "<none>" }
        throw GradleException(
          "Release signing is misconfigured — refusing to produce a release APK.\n" +
            "RELEASE_KEY_ALIAS \"$alias\" is not a private-key entry in " +
            "${keystoreFile.absolutePath}.\n" +
            "Aliases present in that keystore: $available. " +
            "See docs/SECURITY.md for the signing policy.",
        )
      }
      try {
        keyStore.getKey(alias, guardKeyPassword!!.toCharArray())
      } catch (e: Exception) {
        throw GradleException(
          "Release signing is misconfigured — refusing to produce a release APK.\n" +
            "RELEASE_KEY_PASSWORD does not unlock alias \"$alias\" in " +
            "${keystoreFile.name} (${e::class.java.simpleName}). " +
            "See docs/SECURITY.md for the signing policy.",
        )
      }
      logger.lifecycle(
        "Release signing verified: alias \"$alias\" in ${keystoreFile.name} is readable " +
          "and the key password is correct.",
      )
    }
  }
  tasks.matching {
    it.name == "assembleRelease" || it.name == "packageRelease" || it.name == "bundleRelease"
  }.configureEach { dependsOn(verifyReleaseSigning) }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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

// NOTE (phase-0): Firebase / Gemini / network deps were removed because the app
// is offline-first and none of the Kotlin sources referenced them.
// gradle/libs.versions.toml only lists dependencies this module really uses —
// add the library there first when you actually need one.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.hilt.compiler)
}

// Room schema export: schemas are versioned in git so every future
// version bump MUST ship a Migration (see AppDatabase).
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
