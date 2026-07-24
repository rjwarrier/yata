# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

YATA ("Yet Another Task App") — a Material 3 Expressive task manager for Android, built with Jetpack Compose, Room, and Hilt. Gradle root project name is `TodoExpressive` (legacy); package/app id is `com.mj.yata`. Two modules:
- `:app` — the phone app (`com.mj.yata`, minSdk 26, compileSdk/targetSdk 34).
- `:wear` — a companion Wear OS app (`com.mj.yata.wear`, minSdk 30) that shows a today's-task-count complication and receives sync pushes from the phone.

## Commands

All commands run from the repo root using the Gradle wrapper (`./gradlew` on Bash, `gradlew.bat` on native Windows shells).

```bash
# Compile Kotlin only (fast correctness check, no packaging) — use this while iterating
./gradlew :app:compileDebugKotlin -q

# Full debug build
./gradlew :app:assembleDebug -q

# Install to a connected/emulated device
./gradlew :app:installDebug -q

# Unit tests (JVM, no device) — e.g. RecurrenceEvaluatorTest, NaturalLanguageParserTest
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "com.mj.yata.RecurrenceEvaluatorTest"
./gradlew :app:testDebugUnitTest --tests "com.mj.yata.RecurrenceEvaluatorTest.testWeeklyRecurrence"

# Instrumented tests (require a connected device/emulator) — Room migration tests live here
./gradlew :app:connectedDebugAndroidTest --tests "com.mj.yata.data.local.db.AppDatabaseMigrationTest"

# Wear module
./gradlew :wear:assembleDebug -q
```

After changing anything under `app/src/main/java`, the fast loop is `compileDebugKotlin` to catch errors, then `installDebug` before manually verifying in the UI — there is no automated Compose UI test suite, so behavior changes need a manual pass on-device.

## Architecture

**Layering:** `domain/model` (plain data classes, no Android/Room deps) → `data/local/db` (Room entities/DAOs) → `data/mapper` (Entity ⇄ domain model conversion) → `data/repository/YataRepositoryImpl` (implements `domain/repository/YataRepository`, exposes `Flow`s) → `ui/screen/main/MainViewModel` (single large ViewModel backing the whole app, exposes `StateFlow`s and imperative methods like `deleteTask`, `bulkDeleteTasks`, `toggleTaskDone`) → Compose screens under `ui/screen/*`. Screens read from `MainViewModel` (obtained via `hiltViewModel()`) rather than each having their own ViewModel — there is one ViewModel per app, not per screen.

**Database (Room, `data/local/db/AppDatabase.kt`):** currently at version 23. Every schema change is a hand-written `Migration` object added to the `companion object` and registered in `di/DatabaseModule.kt`'s `.addMigrations(...)` call — there's no auto-migration. `fallbackToDestructiveMigrationOnDowngrade()` only fires on a genuine version *downgrade* (e.g. reinstalling an older APK); a missing forward migration throws instead of silently wiping data. When adding a column/table: bump the version in `@Database`, add a `MIGRATION_N_N1` object, register it in `DatabaseModule`, and add a migration test to `AppDatabaseMigrationTest`. Row-scoped booleans/soft-deletes follow existing conventions — e.g. `tasks.deletedAt` (non-null = in Trash, not hard-deleted) and `projects/lists.excludeFromToday`.

**Navigation:** single-Activity, `ui/navigation/AppNavigation.kt` defines routes (`Screen.kt`) over a `NavHost`. `Screen.Main` is a 5-tab shell (`MainScreen.kt` → `CustomBottomNav` with ids 0=Today, 1=Projects, 2=People, 3=Tags, 4=Upcoming, fixed regardless of which tabs are hidden by feature flags) with its own drawer, FAB, and — as of the delete-undo work — a shared `SnackbarHostState` at the `MainScreen` Scaffold level for cross-tab bulk actions. Detail screens (task/project/list/tag/person, search, settings, trash, analytics, help/about, next-days, welcome) are separate top-level destinations, each usually with its own `Scaffold`/`SnackbarHostState` when they need one (see `SearchScreen.kt`). `Screen.HelpAbout` and `Screen.Settings` were split apart (help/about used to be a card inside Settings); `Screen.NextDays` is a standalone 10-day lookahead reachable from the drawer's Tools section. The drawer's secondary entries (Next 10 Days, Command palette, My Work, Focus Mode, Morning/Evening Review, Stale Nudges, Task Health) live under a collapsible "Tools" header in `MainScreen.kt`, collapsed by default with state kept via `rememberSaveable` — add new drawer-only surfaces there rather than growing the top-level drawer list.

**Feature flags:** People/Tags/Projects are optional and can be hidden entirely via `UserPreferences` (`peopleFeatureEnabledFlow`, `tagsFeatureEnabledFlow`, `projectsFeatureEnabledFlow`, backed by DataStore in `data/local/datastore/UserPreferences.kt`). Any new screen/tab/bulk-action surface touching these entities should gate on the corresponding `*FeatureEnabled` `StateFlow` from `MainViewModel`, matching existing tabs.

**Task list screens share structural patterns**, not a shared composable hierarchy — each of Today/Project/List/Tag/Person independently splits its tasks into "Pending"/"Completed" sections (header via `ui/widgets/TaskSectionHeader.kt`, hidden when empty or when completed tasks are toggled off) and independently wires an eye-icon `hideCompleted` toggle. `ui/widgets/DragDropReorderableColumn.kt` is the shared long-press drag-to-reorder `LazyColumn` used by screens with manual ordering (Project/List detail); it supports non-draggable `header`/`footer` `LazyListScope` slots (e.g. the Pending/Completed headers and the static Completed list) via a `headerItemCount` offset that keeps its internal drag index math in the same "global" index space as `LazyListState.layoutInfo`.

**Delete-with-undo pattern:** deleting a task (single, from `TaskDetailScreen`, or bulk, from Today/Upcoming/Search's multiselect toolbar) does not delete immediately — it shows a `Snackbar` with `actionLabel = "Undo"` and only calls the repository delete if the snackbar result is `Dismissed` (i.e. it timed out without the user tapping Undo). The custom countdown rendering (`ui/widgets/DeleteUndoSnackbar.kt`) is picked in each `SnackbarHost`'s content lambda by checking `data.visuals.actionLabel == "Undo"`. Any new delete flow should follow this same shape rather than deleting synchronously.

**Home-screen widgets (Glance, `widget/`):** each widget (`YataAppWidget`, `SingleListWidget`, `QuickAddWidget`, `ProgressStatsWidget`, `UpcomingWidget`, `TeamOverdueWidget`) is instantiated directly by Android (`new SomeWidget()`), not through Hilt, so it can't get constructor injection — instead it reaches app dependencies via `WidgetEntryPoint`, a `@EntryPoint` Hilt interface exposing `repository()`/`userPreferences()`. `WidgetUpdater.notifyTasksChanged()` is the single hook called after any task write; it refreshes all placed widget instances via `WidgetRefresher` *and* pushes to the paired Wear OS watch via `WearSyncUpdater` — both home-screen widgets and the watch complication ride the same "something changed" signal. All six widgets share one configure Activity, `WidgetCustomizerConfigActivity` (`Theme.Yata.Transparent`), for corner radius / custom label / M3-colors toggle / opacity / accent-color override; Single List and Quick Add additionally get a source picker (list/project/tag) from the same screen. Each widget's `provideGlance` must explicitly read+apply every `WIDGET_*_KEY` it wants to support — the config screen doesn't know which keys a given widget type actually renders, so a widget that's supposed to honor a shared option but doesn't read it fails silently (this bit `TeamOverdueWidget` once; see `supportsM3Colors` in `WidgetCustomizerConfigActivity` for how an unsupported option gets hidden instead of silently ignored).

**Reminders/notifications (`notification/`):** `ReminderScheduler`/`TaskReminderScheduler` schedule via `AlarmManager`, delivered by `ReminderReceiver`; `BootReceiver` reschedules everything on device reboot; `NotificationActionReceiver` handles notification-inline actions (e.g. mark done) without opening the app. `DailyAgendaWorker` and `OverdueEscalationWorker` are WorkManager jobs (not `AlarmManager`) for, respectively, a daily agenda summary notification and escalating overdue-task nudges.

**Cloud backup (`data/cloud/`):** `CloudBackupManager` drives Google Drive backup/restore of the same JSON format as `JsonExporter`; `CloudBackupWorker` is the WorkManager job for scheduled/background backups. Independent of the local `Trash`/undo path — this is off-device redundancy, not soft-delete.

**Archiving:** Projects, Lists, and People each support archive (distinct from Trash's soft-delete) — a hide-without-deleting state with dedicated DAO-backed archive streams, surfaced via each entity's detail/list screens. Don't conflate with `deletedAt`/Trash; archived rows stay fully intact and excluded only from default listing queries.

**Tasker integration (`tasker/createtask/`):** exposes a "Create Task" Tasker plugin action (`com.joaomgcd:taskerpluginlibrary`) — `CreateTaskConfigActivity` is the Tasker-facing config UI, `CreateTaskRunner` executes the action against the repository directly (not through `MainViewModel`, since it runs outside the app's Activity).

**Theming:** `ui/theme/` implements M3 Expressive with a warm coral palette by default (see `design/README.md` / `design_handoff_yata/README.md` for the original design tokens/handoff — these HTML/JSX files are non-executable design references, not code to import from). Accent colors for lists/projects/tags/people are named `accentA`..`accentP` (plus a literal `"error"` for tags) resolved through `LocalYataAccents`, not raw `Color` values, so accent pickers (`ColorPicker`, `IconPicker` in `ui/widgets/`) work uniformly across entity types.

**Export/import (`util/`):** `JsonExporter` (full backup/restore), `IcsExporter` (calendar `.ics`), `MarkdownExporter` (plain-text task list for clipboard/share) all operate on the same domain models, independent of the DB layer.
