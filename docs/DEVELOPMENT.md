# YATA developer guide

YATA is a Kotlin/Jetpack Compose Android app. This guide covers the current source tree and
safe contribution workflow. For feature behavior, use [FEATURES.md](FEATURES.md); for operation,
use [USER_GUIDE.md](USER_GUIDE.md). [CLAUDE.md](../CLAUDE.md) contains deeper repository notes
and agent-specific working guidance.

## Toolchain and modules

| Component | Current repository configuration |
| --- | --- |
| Runtime support | Android 8.0+, minSdk 26 |
| Compile / target SDK | 36 / 36 |
| Build JDK | 17 |
| Kotlin | 2.0.21 |
| Android Gradle plugin | 8.13.0, via the wrapper-managed build |
| Compose BOM | 2025.12.00 |
| Persistence | Room 2.6.1 and DataStore |
| Dependency injection | Hilt 2.52 |
| App module | `:app`, application ID `com.mj.yata` |
| Profile module | `:baselineprofile`, Android test module |

The Gradle root name `TodoExpressive` is historical. Version catalogs live in
[gradle/libs.versions.toml](../gradle/libs.versions.toml); the current app version and SDK levels
live in [app/build.gradle.kts](../app/build.gradle.kts). Read those files rather than treating
this table as a permanent version constraint.

## Set up and build

Install JDK 17 and Android SDK platform 36, platform tools and the build tools requested by
Gradle, or open the repository in a compatible Android Studio installation.

```bash
git clone https://github.com/rjwarrier/yata.git
cd yata
./gradlew :app:assembleDebug
```

On Windows, run from PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

Configure the SDK through Android Studio or an untracked `local.properties` with `sdk.dir`.
The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Signing and installation

If a root `debug.keystore` is available, the debug configuration uses that shared project key;
otherwise it falls back to the machine's Android debug key. Release credentials come from an
untracked `keystore.properties` and keystore. Without them, release output is unsigned.
Never commit either key material or release credentials.

For a compatible, intentionally selected test installation:

```powershell
.\gradlew.bat :app:installDebug
```

Do not resolve a signing mismatch by uninstalling an app that contains useful data. Confirm
the intended package/key and retain a portable backup before changing installations. `installDebug`
does not by itself authorize clearing storage or changing the user's installed signing identity.

## Architecture and source map

The app has one main activity and a shared MainViewModel. Screens collect state from that
ViewModel; heavier work is delegated to injected operations/use cases.

```text
Compose screens
    ↓ actions / ↑ StateFlow
MainViewModel + injected operations
    ↓
YataRepository interface / implementation
    ↓
Room DAOs, entities and DataStore

Domain models remain independent of Android/Room storage types.
```

| Location under `app/src/main/java/com/mj/yata/` | Responsibility |
| --- | --- |
| `domain/model` | Task, organization, timer and related plain models |
| `domain/repository` | Repository contract |
| `domain/usecase` | Task operations, backup operations and focus timer |
| `data/local/db` | Room database, entities, DAOs and migrations |
| `data/local/datastore` | Preferences and device-local state |
| `data/mapper` | Storage/domain conversions |
| `data/repository` | Repository implementation and streams |
| `data/sync` | Snapshot normalization, merge/apply and baselines |
| `data/github`, `data/sftp`, `data/ftp` | Provider transports |
| `data/demo` | Sample repository and transfer safety gate |
| `data/calendar`, `data/voice` | Device integrations |
| `ui/screen`, `ui/sheets`, `ui/widgets` | Destinations, capture sheets and shared composables |
| `ui/navigation`, `ui/theme` | Routing, adaptive navigation and Material 3 appearance |
| `widget`, `notification`, `tasker` | Launcher, reminder/background and automation integrations |
| `util` | Parsing, recurrence, import/export and supporting utilities |

Design prototypes in `design/` are references, not executable app components. Prefer existing
shared composables and accent tokens when extending screens.

## Verification

Choose checks according to what changed. Documentation and screenshot edits need link/anchor,
image and diff checks; they do not need an APK rebuild. Kotlin changes should first pass:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Run targeted JVM tests for logic changes; the complete unit-test graph is heavier than a
compile check even when filtered:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest --console=plain
```

Replace the test class with the one relevant to the change. Parser/normalization work should
also follow the checks in [natural-language-recognition.md](natural-language-recognition.md).
For a packaging change, use `:app:assembleDebug`. Locale checks are described below.

### Instrumentation is for disposable devices

`connectedDebugAndroidTest` reinstalls the app and tests create, modify and delete database
records. The build requires the explicit `-PdisposableDevice` opt-in. Never pass it for a
daily-use phone, even if the phone currently displays demo data. The real installation still
contains its own database.

On an intentionally disposable emulator/spare installation only:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PdisposableDevice
```

For a single instrumentation class, use the runner argument rather than JVM `--tests`:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PdisposableDevice -Pandroid.testInstrumentationRunnerArguments.class=com.mj.yata.data.local.db.AppDatabaseMigrationTest
```

Do not bypass the guards or rely on a test leaving data untouched. Baseline profile generation
also needs a suitably configured emulator or rooted/userdebug device:

```powershell
.\gradlew.bat :baselineprofile:generateBaselineProfile
```

## Database and backup changes

Room schema **36** reconciles historical branch variants with direct migrations from 33, 34
and 35. Do not reuse a database version independently or replace a historical exported schema
to satisfy a test. Follow [database integration notes](database-36-integration.md).

For a schema change:

1. Start from current shared history and coordinate the next version.
2. Implement a handwritten migration and register it in DatabaseModule.
3. Export the schema and retain historical schemas/fixtures.
4. Verify data, foreign keys, cross-references, subtasks, comments and FTS survive migration.
5. Run the relevant migration contract/tests on suitable test infrastructure.

Missing migrations and downgrades fail rather than wiping the database. Preserve this behavior.
Current portable backup version is **5**, with project groups and their memberships. Read/write
compatibility, photo bytes, tracked-time normalization and sync canonicalization must stay
consistent when the format changes.

Portable settings use their setting name as identity; most other synced records use an ID.
Keep credentials, connection/checkpoint state, app-lock material and demo/timer state out of
portable preference sync. Before adding a transfer entry point, hold
`UserPreferences.withRealData`/`realDataResult` for the entire operation so demo transitions
cannot interleave with real-data writes.

## UI text and localization

English strings are the source. Add resource-backed UI text and reuse existing shared action
labels where possible. Maintain all 24 translated locale files for new keys. Use plural
resources for counts and positional format arguments for inserted values.

```powershell
.\gradlew.bat :app:lintLocaleParity
.\gradlew.bat :app:lintHardcodedStrings
```

Parity validates key presence, not language quality. The hardcoded-string task catches common
shapes rather than every literal. Debug pseudolocales en-XA and ar-XB help check expansion and
right-to-left layouts. See [localization.md](localization.md) for details.

## Documentation and screenshots

Keep the README as the project entrance, USER_GUIDE as the workflow guide, FEATURES as the
behavior/source reference, and SYNC_SETUP as the setup/recovery procedure. Link detailed
engineering work from [docs/README.md](README.md) and label design plans as plans.

Use repository-local screenshots with meaningful alt text and explicit display widths so
GitHub renders them predictably. Record capture date, app version, demo state and any image
processing. Use demo data and explicit user authorization for device capture; do not expose
private task content or change the app's data merely to illustrate documentation.

The current screenshot set is documented in [assets/screenshots/README.md](assets/screenshots/README.md).
When changing headings, verify inbound README links and table-of-contents fragments too.

## Contribution and release checklist

1. Read repository guidance and inspect existing changes before editing.
2. Make the smallest coherent change and preserve unrelated work.
3. Add tests when changed logic needs them, and run the relevant checks.
4. Update user documentation and add user-visible behavior changes under **Unreleased** in
   [CHANGELOG.md](../CHANGELOG.md).
5. Inspect the diff for credentials, private data, accidental generated files and broken links.

At release time, update versionName/versionCode, date and rename the Unreleased section, open
a new Unreleased section, and use the changelog section as release notes. Signing and publishing
require the maintainer's release setup; a successful local build is not a published release.
