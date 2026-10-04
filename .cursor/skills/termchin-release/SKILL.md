---
name: termchin-release
description: >-
  Build, test, verify, bump, tag, and publish releases for the TermChin Android
  project. Use when preparing or executing a new release, running local tests,
  assembling APKs, verifying APK version metadata, updating README changelogs,
  pushing tags, and monitoring GitHub Actions releases.
---

# TermChin — Build, Test & Release Workflow

Portable release pipeline for **TermChin** (`ir.courseplanner.app`). Nothing
below assumes a specific machine, OS or checkout path — run every command from
the repository root.

## 1. Prerequisites

- **JDK 17** (release build toolchain; `JAVA_HOME` must point at it)
- **Android SDK** with **Platform 36** and **Build-tools 36.0.0**
  (`ANDROID_HOME` must point at it, or `sdk.dir` in `local.properties`)
- **Gradle Wrapper is checked in** — `gradlew`, `gradlew.bat` and
  `gradle/wrapper/gradle-wrapper.jar` are committed, so no preinstalled Gradle
  is required.

Shell used below:

```bash
# Linux / macOS
./gradlew <task>
```

```powershell
# Windows (PowerShell / cmd)
.\gradlew.bat <task>
```

## 2. Step 1 — Test and build (before committing)

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Both must finish with `BUILD SUCCESSFUL`. A red test suite blocks a release.

Confirm the APK version (Android refuses an install with a duplicate
`versionCode`, so this must be checked every time):

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging \
  app/build/outputs/apk/debug/app-debug.apk | grep -E "versionCode|versionName"
```

### Release build (optional locally, mandatory in CI)

Release signing needs **four environment variables and nothing else** — there
are no `-P`, `gradle.properties` or legacy fallbacks:

```bash
export RELEASE_KEYSTORE_PATH=/path/to/termchin-release.jks
export RELEASE_KEY_ALIAS=<alias>
export RELEASE_KEYSTORE_PASSWORD=<secret>
export RELEASE_KEY_PASSWORD=<secret>

./gradlew assembleRelease
```

If any of the four is missing, `verifyReleaseSigning` fails the build with an
explicit error instead of producing an unsigned or debug-signed APK. It also
validates the values: the keystore must open with `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` must be a private-key entry in it, and `RELEASE_KEY_PASSWORD`
must decrypt that entry. Check just the credentials (no APK) with:

```bash
./gradlew verifyReleaseSigning
```

Add `--no-configuration-cache` right after changing the variables if you want to
be certain the new values (not a cached configuration) were used. The keystore
itself must never live inside the repository (see `docs/SECURITY.md`).

### Windows: `clean` can fail while a daemon holds lint's cache

CI runs `./gradlew clean assembleRelease`. On Windows the same command can fail
with

```
java.io.IOException: Unable to delete directory ...\app\build
Failed to delete some children. This might happen because a process has files
open or has its working directory set in the target directory.
```

because the long-lived Gradle daemon keeps the lint cache
(`app/build/intermediates/lint-cache/**`) open after a lint task runs in it. It
is not a problem with the project — stop the daemon and clean again:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean
```

Linux CI is unaffected: unlinking an open file is allowed there, which is why the
workflow can keep `clean assembleRelease` in one command.

## 3. Step 2 — Version bump and documentation

1. **`app/build.gradle.kts`** — increment `versionCode` by one and update
   `versionName`. This file is the single source of truth; CI reads both from it
   and never hardcodes them.
2. **`README.md`**
   - update the current-version mention to `vX.Y.Z (بیلد N)`,
   - prepend a row to `📋 تاریخچه نسخه‌ها`.
3. **`docs/CHANGELOG.md`** — prepend a `## [X.Y.Z]` section.
4. **`.github/workflows/build-apk.yml`** — no version is hardcoded there; only
   update the Persian release-notes `body:` block with this release's changes.
   Artifact names are derived from the version and are already `TermChin-…`.

## 4. Step 3 — Commit, push and tag

```bash
git status            # nothing sensitive or temporary must be staged
git add -A
git commit -m "feat(scope): vX.Y.Z - short summary"
git push origin main
```

Then tag and push the tag (the tag triggers the release):

```bash
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin vX.Y.Z
```

## 5. Step 4 — CI and release verification

```bash
gh run list --limit 3
gh run view <RUN_ID>
gh release view vX.Y.Z
```

The workflow must pass these gates, in order:

1. `Secret-hygiene gate` — no keystore/base64 file is tracked in git.
2. `Signing pre-flight` — `verifyReleaseSigning` proves the keystore opens, the
   alias is a private-key entry and both passwords are correct (fails fast,
   before the slow steps).
3. `Unit tests` — `testDebugUnitTest` is green.
4. `Build Release APK` — `assembleRelease` with the four `RELEASE_*` variables.
5. `Verify APK carries new version` — `versionCode`/`versionName` match
   `app/build.gradle.kts`.
6. `Network & backup gate` — the APK must request `android.permission.INTERNET`
   and nothing else except the app's own `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
   self-guard; `android:allowBackup` must stay `false`.
7. `Signature gate` — signer SHA-256 equals the current release certificate
   **and** differs from the retired certificate.
8. `Version regression gate` (tags only) — `versionName` equals the tag and
   `versionCode` is greater than the previous tag's.
9. Keystore removed from the runner.

Confirm the published assets exist:

- `TermChin-vX.Y.Z.apk`
- `TermChin-vX.Y.Z-<COMMIT_SHA>.apk` (cache-busting copy)
- Release notes rendered correctly in Persian.
