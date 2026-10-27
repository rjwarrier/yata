# Unified database release

App version: **0.96.0**, Android version code **29**, Room database **36**, JSON backup format **5**.

The integration combines `origin/master` at `1682301` and `origin/codex/github-sync`
at `ff18597`. Both histories and their features are retained in the merge commit.

## Why a direct migration is required

Two incompatible schemas shipped as database version 34. The master layout contains
non-null tracked time. The other layout contains project groups, and its version 35
adds nullable tracked time. A version number alone cannot distinguish those databases.

| Installed layout | Upgrade |
| --- | --- |
| Version 32 and earlier supported versions | Existing registered chain to 33, then directly to 36 |
| Version 33 | Add groups, membership and tracked time |
| Master version 34 | Add groups and membership; preserve tracked time |
| Groups version 34 | Preserve groups and membership; add tracked time |
| Version 35 | Preserve groups; convert null tracked time to zero |

`UnifiedDatabaseMigration` examines the actual columns before changing them. Projects
receive a nullable foreign-key column through `ALTER TABLE`, avoiding a project-table
drop that could cascade into tasks. When tasks require rebuilding, temporary copies
preserve dependent comments, nested subtasks and junction rows. Original rowids and
indices are retained, the search index is rebuilt, and foreign keys are checked.
Room executes the migration transactionally and validates the resulting schema.

The production registry is `AppDatabase.MIGRATIONS`. The historical master 33-to-34
migration remains only for its existing regression test and is not registered.
Both historical version-34 layouts have immutable fixtures under
`app/src/androidTest/assets/legacy-schemas`; the canonical exported `34.json` is master's.

## Backup and sync compatibility

Project groups and project membership are exported, validated, imported in foreign-key
order, merged by ID and reconciled on deletion. Deleting a group clears its membership
without deleting projects or tasks. Missing, null and zero tracked time all normalize
to zero; recorded time is preserved.

Format 5 makes older app builds reject these snapshots instead of silently stripping
project groups. Update every syncing device to this release before continuing sync.
Backups in formats 1 through 4 remain importable. Existing first-sync, recovery-backup,
empty-device and rewritten-GitHub-history protections remain in place.

## Verification

`UnifiedDatabaseMigrationContract` runs the production migration registry against
historical schemas with foreign keys enabled and Room's full schema validation. It
covers versions 32 and 33, both version-34 layouts, version 35 with null and recorded
time, and fresh installation. It checks task relationships, groups, tracked time,
original rowids, foreign-key integrity, and search before and after a subsequent edit.

The same contract runs under Robolectric native SQLite and, when explicitly approved,
on a disposable emulator through `UnifiedDatabaseMigrationDeviceTest`. Never run the
instrumentation suite on a daily-use phone.

`UnifiedBackupRoundTripTest` uses real Room repositories and `JsonExporter` to verify
backup/import, repeated sync application, invalid-group-reference rejection, and
older backups containing missing or null tracked time. Snapshot merge regression
tests cover concurrent group creation and deletion as well as tracked-time normalization.

Validation on 2026-10-27: **742 JVM tests passed**, including eight migration cases;
`compileDebugAndroidTestKotlin`, `assembleDebug` and `lintLocaleParity` passed. All
1,555 resource keys are present across 24 translated locales. The debug APK was checked
against the connected Pixel's signing certificate and installed in place after saving
an integrity-checked local app-data archive. Instrumented tests were not run on the phone.

## Future schema changes

Start new feature branches from current master. Agree on the next database version
before making schema changes, preserve old exported schemas, add and register a
migration, and extend migration tests. Avoid installing older APKs over the unified
release: old builds contain their own downgrade behavior. Keep the previous branches
and device backup until the release has been verified in normal use.
