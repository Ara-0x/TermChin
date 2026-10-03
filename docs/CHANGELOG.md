# Changelog

Notable changes to TermChin (Course Planner), newest first.
Versions are written as `versionName (versionCode)` exactly as they appear in
`app/build.gradle.kts` — the single source of truth also checked by CI.

## [2.7.1] — 2026-10-04 byte-level .mht parsing (import fix)

### Data import

- **Picking a Chrome/Edge `.mht` no longer fails with "no courses found".**
  The extractor decoded the whole archive as text; raw binary images inside a
  Blink single-file save are not valid UTF-8, so the charset fallback
  corrupted the Persian HTML and the courses table could not match. Parsing is
  now byte-level: headers/boundaries are scanned on a 1:1 ISO-8859-1 view and
  each part body is decoded by its own transfer encoding + charset.
- **Regression tests.** `raw binary image part does not corrupt the courses
  table` reproduces the on-device failure (with a sanity assertion that
  whole-file UTF-8 decoding of the fixture fails), and the real-save fixture
  test now prints `MHT_FIXTURE_USED` so a silently-skipped run cannot pass
  again. Suite: 126 tests / 18 suites, 0 failures; verified against a real
  portal `.mht` (157 courses parsed).

## [2.7.0] — 2026-10-04 MHTML (.mht) portal import, full-line course names & export copy

### Data import

- **Portal picker accepts Chrome/Edge single-file saves (.mht).**
  `SettingsScreen` now reads the picked portal file as bytes and
  `CoursePlannerViewModel.importPortalBytes` sniffs MHTML vs plain HTML:
  MHTML is unwrapped by the new `MhtHtmlExtractor` (RFC 2046 boundary
  splitting, header folding, quoted-printable/base64 decoding, UTF-8 with a
  Windows-1256 fallback), then parsed by the unchanged `PooyaHtmlParser`.
  HTML files behave exactly as before. Copy-paste flows still use the same
  `importPortalHtml` path.
- **Archive-aware errors.** A menu-only save (login page, no courses table)
  no longer parses as "zero courses found": it reports which archive state
  was found (no HTML part / no courses table / unreadable file).
- **Scored table selection.** `PooyaHtmlParser` now scores every `<table>`
  by its header row (شماره درس + نام درس + ظرفیت/استاد + tooltips) instead
  of taking the first match, so the filter-form table (faculty select) on
  full-page saves can no longer win over the courses list.
- **New tests.** `MhtHtmlExtractorTest` (+9, suite green): frame picking,
  QP/base64 decoding, menu-only and html-less archives, malformed input,
  end-to-end MHT→parser, plus an env-gated (`TERMCHIN_MHT_FIXTURE`) check
  against a real Chrome .mht save. Suite is 125 tests / 18 suites, 0 failures.

### Courses UI

- **Course names own the full card line.** `CourseCard` header is now three
  rows: full-width name (wraps to a second line), a chips `FlowRow` (units +
  documents) sharing a row with the fixed action cluster, then a wrapping
  code/department row — long Persian names like «برنامه نویسی مبتنی بر وب»
  no longer lose characters to same-row chips. `CatalogQuickAddCard` got the
  same treatment (full-width two-line name; the add button keeps its slot).
  No colors, paddings, testTags or behaviour changed.

- **Copy button on the JSON export dialog.** The dialog only previewed the
  JSON; it now has a «کپی» button (testTag `export_json_copy_button`) that
  copies the **full** export text via the shared `copyTextToClipboard`
  choke-point in `TimetableExporter` — the schedule copy on Home is
  unchanged and now funnels through the same function.

## [2.6.0] — 2026-09-29 signing-key migration, data-integrity fixes & UI polish

### Security / build

- **Signing key rotated.** The historical key that signed the released APKs
  (v2.0.0–v2.5.0, certificate SHA-256 `fcced2ea…`, `CN=Android Debug`) is
  retired, because its private key had been committed to this public
  repository. Official APKs are now signed with a dedicated release key —
  `CN=TermChin Release`, SHA-256
  `0d38aa655105b0af6a0c0a1d26b37dbb872d20b6b4ab63fbeb9f88aa195adda0` — generated
  outside the Git working tree and handed to the build only through the
  environment / GitHub Actions secrets.
- **No silent fallbacks.** `assembleRelease` now depends on the
  `verifyReleaseSigning` task, which fails the build with an explicit message
  when the keystore or a password is missing: a release is never unsigned and
  never debug-signed. Debug builds keep AGP's local debug keystore.
- **The signing guard validates values, not just their presence.**
  `verifyReleaseSigning` now opens the keystore with
  `RELEASE_KEYSTORE_PASSWORD`, requires `RELEASE_KEY_ALIAS` to be a private-key
  entry in it, and requires `RELEASE_KEY_PASSWORD` to decrypt that entry. A
  typo'd or rotated secret therefore fails in seconds, naming the broken value,
  instead of failing later during packaging (or worse, producing an artifact
  nobody expected). All four failure modes were exercised: no variables, wrong
  alias, wrong keystore password, wrong key password.
- **CI builds the release variant.** `.github/workflows/build-apk.yml` restores
  `RELEASE_KEYSTORE_BASE64` into `$RUNNER_TEMP` (outside the workspace), removes
  it after the job, aborts when any signing secret is missing, assembles
  `assembleRelease`, and its signature gate now pins the NEW fingerprint — a
  debug-signed or otherwise wrong APK fails the run.
- **Stricter CI gates and least privilege.** The workflow requests `contents:
  read` by default and grants `contents: write` only to a separate `release`
  job that runs on `v*` tags and depends on the build job, so a build from a
  branch can never publish a release. A signing pre-flight step runs
  `verifyReleaseSigning` before the test/build steps, the signature gate also
  asserts that the retired fingerprint never appears, and a new **Offline &
  backup gate** inspects the built APK for `android.permission.INTERNET` and
  `android:allowBackup=false`. Both assets (plain and cache-busting name) travel
  to the publish job in one artifact, which is what the release job asserts.
- **Signing interface is deterministic.** Release signing reads exactly the four
  `RELEASE_*` environment variables: the legacy `STORE_PASSWORD` /
  `KEY_PASSWORD` names, the non-secret `gradle.properties` values
  (`KEYSTORE_PATH` / `KEY_ALIAS`) and every `-P` fallback are gone, and an unset
  keystore path resolves to a non-existent placeholder instead of a plausible
  file in the project root.
- **History cleanup.** The compromised blob is removed from every reachable
  branch and tag with `git filter-repo` plus a force-push, so the old commit is
  no longer reachable from repository refs.
- **Breaking change for installs.** Because the certificate changed, Android
  refuses an update over v2.0.0–v2.5.0
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): users must uninstall once and then
  install the new APK. Details and remaining risks: `docs/SECURITY.md`.

### UI / UX polish

- **One card radius app-wide.** Cards, section surfaces and dialogs were using
  14, 16, 18 and 20 dp interchangeably for the same job. They now all use the
  16 dp that the majority already used (Settings sections, the Courses search
  surface, the conflict banner, the FAB). Geometry, spacing and layout are
  untouched — only the corner radius moved.
- **Form labels share one size.** The add/edit course and add/edit group dialogs
  mixed 11 sp and 12 sp field labels; every label now uses the same token
  (`labelMedium`, 11.5 sp) that the filter chips already used.
- **Result metrics follow the type scale.** The “why this schedule?” tiles mixed
  hard-coded 9.5 / 10.5 / 14.5 sp sizes; they now use the typography tokens, so
  the smallest line is no longer below the app's smallest text size.
- **The generator no longer looks frozen.** `runScheduleGenerator` reports a
  busy flag: while it searches, the button is disabled and reads
  «در حال محاسبهٔ برنامه…», and a second tap cannot queue another run. The flag
  is cleared on completion, failure and cancellation, so it can never stick.
- **Dark-mode accents are theme-aware.** The bookmark star and the five
  document-category colours were hard-coded; they now have light/dark variants
  (new tokens in `Color.kt`) so they keep their contrast on both surfaces. The
  “is this palette dark?” rule that the conflict banner used inline moved into a
  shared `isDarkTheme()` helper and is now used by all three call sites.

### Privacy

- **OS backup and device-to-device transfer are disabled.**
  `android:allowBackup="false"`, and the `dataExtractionRules` /
  `fullBackupContent` attributes are removed together with
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`, so Android
  can no longer copy the database or preferences to a cloud account or another
  device. The app's own JSON export remains the only backup path. Documented in
  `README.md` and `docs/SECURITY.md`, and enforced by the CI gate above.

### Fixed (data integrity)

- **Importing a backup twice no longer duplicates data.** JSON/CSV restore wrote
  rows with `insertAll`; it now writes through `syncCourse`, so a course that
  already exists (matched through `normalizeCode`) is updated in place, section
  codes are matched the same way, and enrollment / generator ticks are
  preserved. `RepositoryImportTest` covers re-import, normalized matching and
  tick preservation — all three fail on the previous implementation.

## [2.5.0] — 2026-09-28 integrity pass

### Fixed (critical data safety)

- **Startup database wipe removed.** `CoursePlannerViewModel.init` used to run
  `repository.clearAllData()` whenever the DataStore marker
  `release_clean_courses_v1` was absent — and the marker defaults to *false*, so
  one missing flag (fresh/restored DataStore) erased courses, groups, sessions,
  enrollments and documents on the next launch. The whole mechanism (marker
  API + `init` block) is gone; nothing may delete user data automatically, and
  `StartupDataPreservationTest` now guards that invariant (existing data +
  missing marker + app start ⇒ data intact).
- **Incomplete schedules can no longer be applied.**
  `ScheduleSearchResult.isComplete` / `GenerationState.isComplete` /
  `canApplyCurrent` express the state; the Apply button is disabled with an
  explanatory note and `applyCurrentGeneratedSchedule()` refuses the write, while
  `CourseRepository.applySchedule` independently refuses any candidate that does
  not cover every course selected for generation (`ApplyScheduleResult.Incomplete`
  names the missing courses). The current enrollments are never destroyed by a
  partial plan. `skippedCourses` kept its role (informational report).
- **Duplicate course codes are impossible at the data layer.** Code uniqueness
  is now checked with one canonical normalization (`trim()` + upper-case, see
  `normalizeCode`) in `addManualCourse` **and** `updateCourseDetails`, so
  "MATH101", " math101 " and "Math101" are one course. Refusals return typed
  results the UI turns into messages (`AddCourseResult` /
  `UpdateCourseResult.BlankCode`).
- **Duplicate group codes are impossible inside a course.** Same normalized
  uniqueness per course for `addSectionToCourse` / `updateSectionDetails`; the
  same group number under a *different* course stays valid.
- **Overlapping sessions rejected before persistence.** New
  `engine/SectionSessionValidator` (single implementation shared by the add/edit
  dialogs and the repository) rejects same-day, same-parity, time-overlapping
  sessions of one group, honouring the existing parity rule
  (EVERY×EVERY/ODD/EVEN and ODD×ODD/EVEN×EVEN clash, ODD×EVEN does not) plus
  unparseable/reversed times. Imported catalog data is not run through it (the
  parsers own their format and the import path is unchanged).
- **No synthesized identifiers.** The `CRS-${timestamp%10000}` course code and
  the `ifBlank { "01" }` group code fallbacks are gone: a blank code is an
  explicit validation error in the dialogs and in the repository.
- **Import I/O failures are no longer fake-empty files.**
  `readImportText` now throws when the stream cannot be opened (it used to
  return `""`), and Settings reports four distinct outcomes: file read failed →
  "خواندن فایل ناموفق", file empty → "فایل خالی", read-but-no-courses → the
  parser's message, success → the parser's summary. JSON/CSV pickers got the
  same treatment. Parser behavior untouched.
- **Remaining raw `viewModelScope.launch` writes** (course/generator toggles,
  section enrollment, document bookmarks, manual course/group creation) now go
  through `launchDbWrite`, so a failed write always produces a logged, visible
  error instead of a UI that pretends the write succeeded.

### Changed (scheduler scoring)

- **Parity-aware scoring metrics.** `weeklyActiveDays` /
  `weeklyEarlyMorningCount` charge a biweekly-only day/session 0.5 (the class
  meets every other week) instead of 1.0; the integer `activeDaysCount` /
  `earlyMorningClassCount` stay the *union* view used for display ("days you
  must keep free"). Both rules are documented at their definition and covered
  by tests.
- **Comments now match the weights.** "each day above 3 drops 5 points" was
  wrong (the code always used 6); all weights are named constants with the
  table documented next to `evaluateSchedule`. Scores are unchanged except for
  the intentional parity averaging above.
- **Score breakdown always equals the displayed score.** `ScoredSchedule` gained
  `rawScore`, and when the 0..100 clamp bites, an explicit
  "محدودسازی امتیاز به بازهٔ ۰ تا ۱۰۰" row is added so the rows sum to the
  shown number (tests cover in-range, below-zero and the max case).

### Removed

- `feature/portal/PortalWebViewScreen.kt`: an unreferenced WebView screen with
  no `INTERNET` permission in the manifest (dead code from the abandoned
  in-app-browser idea). The official import workflow remains "save the page as
  HTML → import the file → process locally"; no scraping was added.

### Security / repository hygiene

- Verified (fingerprint compared offline, nothing printed): the distribution
  keystore blob was committed in `e0eb70e` and deleted in `7b2b5a1`, but **at
  that time** it was still in the **public** repository's history and it was the
  very key that signed releases (`fcced2ea…`). Findings and impact are
  documented in `docs/SECURITY.md`; a CI **secret-hygiene gate** fails any
  build that tracks a keystore-like file again. Both remediation options have
  since been carried out — see the `[unreleased]` section at the top.
- README rewritten for accuracy: TermChin repo/URLs, honest offline wording
  (no `INTERNET` permission at all), Top-K + `maxLeaves` truncation instead of
  "all combinations", the 4-step HTML-file import workflow (no automatic portal
  login/scraping), MVVM + Repository (no "Clean Architecture" claim), removed
  the deleted WebView entry, and documented the version history through 2.5.0.

## [2.4.0] — 2026-09-28 audit pass

### Added

- **Global error snackbar.** Repository/DB write failures no longer die
  silently in `viewModelScope`: every write goes through `launchDbWrite`,
  which logs the exception and shows a Persian error snackbar hosted in
  `MainActivity` (info vs. error styling, auto-dismiss).
- **Preference re-run.** Changing the optimization preference now re-runs the
  full schedule search instead of re-ranking only the previous top-12
  retained combinations — the previously "impossible to recover" better
  schedule can win again.
- **Deterministic ranking.** `ScheduleEngine.rankingComparator`
  (score → gaps → active days → early classes → section-id) used by top-K
  insertion, final ordering and `rankSchedules`; covered by the new
  `RankingDeterminismTest`.
- **Gradle wrapper** (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar`) pinned to Gradle 9.3.1, so any machine
  and CI build the same way without a preinstalled Gradle.
- **Audit trail:** `docs/AUDIT.md` (+ parts 2–3) and this changelog.

### Changed

- **Courses screen split & de-scanned.** `CoursesScreen.kt` 1550 → 900
  lines; behavior-identical extractions into
  `ui/screens/courses/{CourseCard,CatalogQuickAdd,CoursesEmptyStates}.kt`.
  O(docs) render-path scans replaced by ViewModel-computed maps
  (`sectionsByCourse`, `documentCountByCourse`, `departmentNames`,
  `catalogOnlyCourseCount`).
- **`CoursePlannerViewModel` decomposition** without public-API changes:
  derived `StateFlow`s for the Courses-screen aggregates, shared
  `showInfo/showError/launchDbWrite` error plumbing, single-pass grouping in
  the generator.
- **Score honesty.** `testRealScore_noFakeHundredAndExplainableBreakdown` now
  asserts the real invariant (`sum(deltas) == score`, one positive base row)
  — the engine always scored 60, the old test double-counted the +100 base.
- **Dependency hygiene.** `gradle/libs.versions.toml` dropped ~15 unused
  libraries/plugins (camera, location, Retrofit/Moshi/OkHttp, Coil,
  accompanist, navigation-compose, Firebase, credentials, secrets &
  google-services plugins) and `app/build.gradle.kts` dropped the
  commented-out stubs. The legacy web prototype was out of the Android build
  and is deleted in the "Removed" section below.

### Removed

- **Legacy web prototype deleted.** The pre-Android React/Vite prototype
  (`src/App.tsx`, `src/main.tsx`, `src/index.css`,
  `src/utils/{defaultData,parser}.ts`, `index.html`, `package.json`,
  `tsconfig.json`, `vite.config.ts`, `metadata.json` — ~130 KB) never
  participated in the Gradle build (`settings.gradle.kts` includes only
  `:app`, and no workflow, build script or source file referenced it), so it
  was pure dead weight that made every clone look like a mixed JS/Android
  project. It stays available in git history if it is ever needed again.

### Security / build

- **Keystore hygiene.** `debug.keystore.base64` untracked; `*.jks`,
  `*.keystore`, `*.base64` and `.ci-secrets/` gitignored. Local debug builds
  use AGP's standard auto-generated keystore (fresh clones build with zero
  setup). Release signing reads only non-secret values from
  `gradle.properties` (`KEYSTORE_PATH`, `KEY_ALIAS`) and passwords **only**
  from the environment (`STORE_PASSWORD`/`KEY_PASSWORD`) or `-P` — no
  defaults, no committed file; without them the release APK is unsigned by
  design. Because the released APKs are debug-signed, the debug key *is* the
  distribution key: CI restores it from the `DEBUG_KEYSTORE_BASE64`
  repository secret into `.ci-secrets/debug.keystore` and hands it to the
  build explicitly through `CI_KEYSTORE_PATH` (relying on AGP's implicit
  `~/.android/debug.keystore` silently produced a throwaway key and an APK
  that could not update installed builds).
- **Signature gate.** `build-apk.yml` verifies the built APK's signer
  certificate against the public SHA-256 fingerprint of the historical
  release key and fails the run on any mismatch, so a signature-breaking
  release can never be published again.
- **CI gates.** `build-apk.yml` now (1) runs `testDebugUnitTest` before
  assembling, (2) derives `versionCode`/`versionName` from
  `app/build.gradle.kts` instead of hardcoding them, (3) on `v*` tags fails
  the release when `versionName != tag` or `versionCode` is not strictly
  greater than the previous tag's code.

### Fixed

- Baseline unit-test failure (`expected:<60> but was:<100>`) — see
  "Score honesty" above; `testDebugUnitTest` is green (0 failures).
- `init` cleanup logs instead of crashing; import DB failures surface as an
  error snackbar instead of vanishing.