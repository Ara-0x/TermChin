# Security Policy — TermChin

> Applies to repository `Ara-0x/TermChin` and to APKs distributed through its
> GitHub Releases page.

## Supported Versions

| Version | Supported |
|---|---|
| 2.6.x – 2.7.x (signed with the current release key) | ✅ |
| 2.0.0 – 2.5.0 (signed with the retired key) | ⚠️ installable, but must be replaced — see [Migration impact](#migration-impact) |
| older | ❌ |

Only APKs downloaded from this repository's official Releases page are
supported. Builds from forks or third-party mirrors are not.

## Reporting a Vulnerability

Please **do not** open a public issue for a security problem.

- Use GitHub's private vulnerability reporting for this repository
  (Security → Report a vulnerability), or
- contact the maintainer directly.

Include: affected version, steps to reproduce, impact, and any suggested fix.
You should receive an acknowledgement within a few days. Once a fix is
released, the report can be discussed publicly.

Never include in a report: private keys, keystore files, passwords, tokens, or
any real user data.

## Release Signing

| Item | Value |
|---|---|
| Certificate owner | `CN=TermChin Release, O=TermChin, C=IR` |
| Key | 4096-bit RSA; certificate signed with `SHA384withRSA` |
| Validity | 2026-09-29 → 2056-09-21 |
| **SHA-256 fingerprint (current)** | `0d38aa655105b0af6a0c0a1d26b37dbb872d20b6b4ab63fbeb9f88aa195adda0` |
| **SHA-256 fingerprint (retired)** | `fcced2ea0574ba6c7b9536c44846c2b697e1f841507af9e4ed00ed910bbe10ff` |

Verify an APK before installing it:

```bash
apksigner verify --print-certs TermChin-vX.Y.Z.apk | grep 'SHA-256 digest'
```

On Windows both tools come from the Android SDK's `build-tools` folder, where
they are named `apksigner.bat` and `aapt.exe`
(`%ANDROID_HOME%\build-tools\36.0.0\`); add that directory to `PATH` or call
them with their full path. `apksigner` is a wrapper around the JDK, so a `java`
binary must also be available (`JAVA_HOME` or `PATH`).

The digest must equal the **current** fingerprint. The retired fingerprint is
only expected on releases up to 2.5.0 — seeing it on a newer build, or any other
digest, means the APK was not produced by this project.

Rules enforced by the build:

1. **Four environment variables, no fallbacks.** Release signing reads exactly
   `RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_KEYSTORE_PASSWORD`,
   `RELEASE_KEY_PASSWORD`. Legacy names and `-P` overrides are not honoured, and
   there is no default keystore.
2. **Fails closed when a value is missing.** `assembleRelease` depends on the
   `verifyReleaseSigning` Gradle task, which aborts with an explicit error if any
   of the four is unset. A release artifact can never be unsigned or
   debug-signed.
3. **Fails closed when a value is wrong.** The same task then proves the four
   values work together: it opens the keystore with `RELEASE_KEYSTORE_PASSWORD`,
   requires `RELEASE_KEY_ALIAS` to be a private-key entry in it, and requires
   `RELEASE_KEY_PASSWORD` to decrypt that entry. A wrong password, a wrong
   alias, or a keystore that is not really a keystore stops the build before
   packaging begins — with the failure named — instead of failing late or
   publishing something signed by an unexpected key. CI runs this as its own
   **Signing pre-flight** step, before the tests and the build, so a rotated or
   typo'd secret fails in seconds; locally, `./gradlew verifyReleaseSigning`
   performs the same check without producing an APK.
4. **The keystore is never in Git.** It lives outside the working tree and is
   supplied to CI only as a repository secret, restored into the runner's temp
   directory for the job and deleted afterwards.
5. **CI verifies the certificate both ways.** Every build compares the built
   APK's signer SHA-256 against the current fingerprint and separately asserts
   that the retired certificate never appears. Either comparison failing stops
   the run, so a debug-signed or accidentally re-keyed APK cannot be published.
6. **No signing material may be tracked.** A CI gate fails the build if any
   `*.jks`, `*.keystore`, `*.b64`, `*.base64` or `debug.keystore` file ever
   becomes a tracked file again.
7. **A release cannot be a downgrade.** On `v*` tags, CI requires `versionName`
   to equal the tag and `versionCode` to be strictly greater than the previous
   tag's, so an older, weaker build can never be published as an update.

Debug builds use the ordinary local Android debug keystore so a fresh clone
builds with zero setup. The debug key never signs a release.

## Historical Signing-Key Incident

TermChin's early releases were signed with a certificate that was later
**committed to this public repository**, which means the private key must be
treated as permanently compromised. That key is **retired**: it no longer signs
anything, and all history reachable from the repository's branches and tags has
been cleaned so the material is gone. **2.6.0 is the first release signed with
the current key**; everything before it (2.0.0 – 2.5.0) carries the retired
certificate.

Two consequences remain, and neither can be undone retroactively:

- Copies may still exist in older clones, forks, forks' caches and mirrors.
  Rewriting this repository's history does not reach those.
- APKs signed with the retired certificate (v2.0.0–v2.5.0) remain installable
  from the Releases page, and whoever holds the retired key could still sign
  an APK that installs as an update over *those* builds only.

### Migration impact

**One-time reinstall.** Because the signing identity changed, Android refuses
to install 2.6.0 (or any later build) over a release signed with the retired key
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The upgrade requires: export a backup
from the app, uninstall, install the new APK, re-import the backup. Builds
signed with the *current* key update normally afterwards.

**This is not "100% secure".** Rotation removes the ongoing exposure for future
releases; it cannot un-publish a key that was already public.

## Data & Privacy

- **One narrow, documented network request — nothing else.** TermChin asks
  `https://api.github.com/repos/Ara-0x/TermChin/releases/latest` whether a newer
  release exists. It is an unauthenticated HTTPS `GET`: no account, no token, no
  cookie, no analytics SDK, and **no user data** (courses, schedule, documents or
  preferences are never part of the request). A CI gate re-checks this on the
  **built APK** (`aapt dump permissions`) and fails if *any* permission other than
  `android.permission.INTERNET` is present — so the update check cannot silently
  become a broader network surface. No location, storage, contacts or
  `REQUEST_INSTALL_PACKAGES` permission exists; TermChin neither downloads nor
  installs an APK itself (the browser and Android's system installer do that).
  Every other feature of the app is offline.
- **OS backup is disabled.** The manifest sets `android:allowBackup="false"`,
  and the `dataExtractionRules` / `fullBackupContent` attributes together with
  their `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`
  files are gone, so nothing can re-point the OS at an archive. The same CI
  gate inspects the **built APK** and fails unless the merged manifest still
  contains `android:allowBackup="false"`. Android Auto Backup and
  device-to-device transfer therefore never copy the app's data to a cloud
  account or another device; the only backup mechanism is the app's own
  **Settings → Export JSON**, saved wherever the user chooses.
- **What is stored locally:** course catalog and enrollments, weekly schedule,
  documents metadata and preferences (Room + DataStore, both app-private
  internal storage).
- **Attached files are not copied by TermChin.** A document attachment points
  at a file the user selected through Android's storage picker; the file stays
  where the user put it.
- **No data is deleted automatically.** The database is only cleared through
  the explicit "حذف همهٔ اطلاعات" action in Settings. Room schema changes always
  ship a migration; destructive migrations are forbidden.
- **Restoring a backup is additive.** Re-importing the same JSON/CSV updates
  existing rows instead of duplicating them, so a restore cannot silently
  multiply data.

## Verifying a release APK

The checks CI runs on every build — usable on any APK you downloaded:

```bash
# 1) signer must be the current TermChin key (see the fingerprints above)
apksigner verify --print-certs TermChin-vX.Y.Z.apk | grep 'SHA-256 digest'

# 2) version must match what the release claims
aapt dump badging TermChin-vX.Y.Z.apk | grep -E "versionCode|versionName"

# 3) exactly one permission (INTERNET, for the update check) and no OS backup
aapt dump permissions TermChin-vX.Y.Z.apk | grep -v INTERNET
aapt dump xmltree TermChin-vX.Y.Z.apk AndroidManifest.xml | grep allowBackup

# 4) no signing material may ever be tracked in this repository
git ls-files | grep -Ei '\.(jks|keystore|b64|base64)$|(^|/)debug\.keystore'
```

Expected: (1) `0d38aa65…adda0`; (2) a `versionName` matching the release tag;
(3) the `grep -v INTERNET` command prints **nothing** (no permission other than
the update check's `INTERNET`) and the `allowBackup` line ends with
`(type 0x12)0x0`; (4) prints **nothing**. The `sha256:…` digest GitHub
shows for a release asset must also equal `sha256sum <file>.apk`.

## What must never be committed

- Keystores (`*.jks`, `*.keystore`), their base64 dumps (`*.b64`,
  `*.base64`), keystores named `debug.keystore`, password files (`*.pass`).
- Private keys, passwords, tokens, API keys — in any file, commit or issue.
- `local.properties`, `.env`, or anything under `.ci-secrets/`.

Rule 6 and the CI **secret-hygiene gate** enforce the first point mechanically;
the rest relies on review.
