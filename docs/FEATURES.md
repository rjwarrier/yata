# YATA feature reference

This reference describes the current 0.96.x implementation. For instructions, use the
[user guide](USER_GUIDE.md); for provider configuration and recovery, use [sync setup](SYNC_SETUP.md).
Source links below are implementation entry points, not a promise that every option appears
on every screen or when its feature is disabled.

## Contents

- [Capture and parsing](#capture-and-parsing)
- [Task model and lifecycle](#task-model-and-lifecycle)
- [Views and navigation](#views-and-navigation)
- [Organization and responsibility](#organization-and-responsibility)
- [Time and analytics](#time-and-analytics)
- [Imports, exports and sharing](#imports-exports-and-sharing)
- [Notifications and integrations](#notifications-and-integrations)
- [Backup and sync](#backup-and-sync)
- [Appearance, language and privacy](#appearance-language-and-privacy)
- [Demo data isolation](#demo-data-isolation)

## Capture and parsing

| Capability | Behavior and boundaries | Implementation |
| --- | --- | --- |
| Quick add | Typed task capture with highlighted schedule/entity spans and a parsed-field preview | [Task sheet](../app/src/main/java/com/mj/yata/ui/sheets/NewTaskSheet.kt) |
| Natural-language parsing | Rule-based extraction of due/start dates, time, recurrence, reminders, priority, flags and organization references; local rather than a remote language model | [Parser](../app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt), [engineering notes](natural-language-recognition.md) |
| Literal escapes | A backslash protects words/markers such as `\tomorrow` and `\#urgent` from becoming fields | [Parser](../app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt) |
| Localization of recognition | Language packs, normalization and date aliases supply recognized phrases; coverage is separate from translated UI coverage | [Language rules](../app/src/main/java/com/mj/yata/util/nl) |
| Voice capture | Device speech recognition feeds capture; engine/language support varies by device | [Voice integration](../app/src/main/java/com/mj/yata/data/voice) |
| Android sharing | Supported incoming text/links/events can open task creation or import previews | [Manifest](../app/src/main/AndroidManifest.xml), [activity](../app/src/main/java/com/mj/yata/MainActivity.kt) |

## Task model and lifecycle

Tasks carry scheduling, priority, flag, recurrence, assignment, organization, estimate, tracked
minutes and history fields. Notes, comments, nested subtasks and tag/person cross-references
provide structured context. The owner is the first assigned person; additional people are
collaborators.

| Action/state | Meaning |
| --- | --- |
| Complete | Marks work done; recurring work advances according to recurrence logic |
| Reschedule/postpone | Changes scheduling and tracks postponement; does not complete work |
| Defer/start date | Keeps work from becoming actionable before the selected date |
| Waiting-on follow-up | Defers delegated work from Today until its follow-up date |
| Archive | Shelves an intact record outside normal active listings |
| Delete | Soft-deletes into Trash, with undo/restore until permanent removal |
| Retention purge | Removes eligible old Trash records based on configured retention; Forever disables automatic expiry |
| Auto-archive | Moves eligible completed work according to the configured age; skips missing completion timestamps |

Task data flows through [domain models](../app/src/main/java/com/mj/yata/domain/model/DomainModels.kt),
the [repository](../app/src/main/java/com/mj/yata/data/repository/YataRepositoryImpl.kt) and
[task operations](../app/src/main/java/com/mj/yata/domain/usecase/TaskOperations.kt).
The [task detail screen](../app/src/main/java/com/mj/yata/ui/screen/taskdetail/TaskDetailScreen.kt)
provides the editing workflow. Deletion and archive are distinct states; hiding completed rows
changes presentation rather than deleting records.

## Views and navigation

| View/tool | Behavior |
| --- | --- |
| Today | Due/overdue tasks, baseline-based progress, planned effort and quick stat/assignment filters |
| Next 10 Days | Rolling date-grouped agenda |
| Upcoming | Week strip or month calendar plus day agenda |
| Projects/Lists | Container task review, ordering, sections for projects, completion visibility and exports |
| People/Tags | Cross-cutting review by responsibility or label |
| Search | Text and smart filters with live/archive/Trash scope controls and saved views |
| Command palette | Navigation, recent tasks, custom views and built-in review presets |
| Archive | Shelved task review and unarchive with undo |
| Trash | Deleted task review, restore and permanent removal |
| Recurring tasks | A dedicated review destination for recurring work |

Navigation is defined in [routes](../app/src/main/java/com/mj/yata/ui/navigation/Screen.kt).
Screens live under [ui/screen](../app/src/main/java/com/mj/yata/ui/screen); the shared
[MainViewModel](../app/src/main/java/com/mj/yata/ui/screen/main/MainViewModel.kt) exposes their state.
Adaptive layout, feature visibility, filters and per-screen hide-completed choices can change
which destinations and records appear.

Search recognizes phrases including `high priority`, `overdue`, `flagged`, `due today`,
`assigned to me` and `no due date`, leaving remaining words as text. The behavior is covered by
[search query tests](../app/src/test/java/com/mj/yata/SearchQueryParserTest.kt).

## Organization and responsibility

- **Projects** collect work toward an outcome and support user-defined sections.
- **Project groups** organize projects under collapsible headings; membership survives backup/sync.
- **Lists** are reusable task buckets. A task can have both project and list context.
- **Tags** label work across containers; tag groups organize the tag browser.
- **People** track owners/collaborators and delegated work; person groups organize the directory.
- **Stars** keep frequently used items easy to reach.
- **Bulk selection** supports complete/delete/tag/move/assign/duplicate/reschedule on supported screens.

All these are local organization records. People do not automatically receive invitations or
notifications. A shared task/project/list link is a preview/import workflow, not a continuously
shared workspace. Containers can be archived or excluded from Today without deleting their tasks.

See [domain models](../app/src/main/java/com/mj/yata/domain/model/DomainModels.kt) and the
[project](../app/src/main/java/com/mj/yata/ui/screen/project),
[list](../app/src/main/java/com/mj/yata/ui/screen/list),
[person](../app/src/main/java/com/mj/yata/ui/screen/person) and
[tag](../app/src/main/java/com/mj/yata/ui/screen/tag) screens.

## Time and analytics

Estimates describe planned minutes; tracked minutes describe recorded effort. Task detail
offers estimate presets, editable recorded time and a focus timer. Only one timer runs per
device; starting another stops the previous one and logs its elapsed minutes. The running
session is device-local, while recorded minutes belong to the task. Timer stop does not complete
the task. A task no longer live when the session ends may not receive those minutes.

Analytics includes completion trends, streaks, on-time rate, aging, overdue/upcoming counts,
postponement and planned effort. Project/tag/person breakdowns and team/person analytics aid
workload review. On-time calculations require completed tasks with due dates. Missing estimates
or unrecorded effort limit what totals can tell you.

Entry points: [focus timer operations](../app/src/main/java/com/mj/yata/domain/usecase/FocusTimerOperations.kt),
[analytics](../app/src/main/java/com/mj/yata/ui/screen/analytics/AnalyticsScreen.kt),
[person analytics](../app/src/main/java/com/mj/yata/ui/screen/person/PersonAnalyticsScreen.kt)
and [team analytics](../app/src/main/java/com/mj/yata/ui/screen/person/StaffAnalyticsScreen.kt).

## Imports, exports and sharing

| Path | Supported behavior | Important boundary |
| --- | --- | --- |
| Markdown import | Checklists, nested subtasks, ordinary lists/plain lines, task tables, heading/property defaults, smart fields, selection and editable preview | Source structure affects detection; review dates and references |
| Markdown duplicate handling | Skip, separate import or update, bulk choices, field previews and batch undo | Updates use merge rules and preserve existing task context/history |
| Reference mapping | Map/create unresolved references and optionally remember mappings | Mappings are device-local and stale targets require review |
| Full JSON | Task/organization data, comments, subtasks, portable settings and actual photo bytes | Credentials, app lock and sync checkpoints remain local |
| CSV | Task exchange with spreadsheet/text workflows | Not a full app backup |
| Calendar import | Shared events and `.ics`/`.vcs`, including multi-event selection | Creates task copies rather than a continuing calendar sync |
| Calendar export | Dated tasks as `.ics` | Snapshot export rather than a live feed |
| Markdown export | Readable task lists/checklists for sharing | Does not replace a full JSON backup |
| PDF/image export | Readable project/tag/person reports | Presentation output rather than round-trip app state |
| Shared links | Preview/import tasks, projects or lists | Changes after import remain independent |

The reviewed Markdown preview is saved without another parse. Descriptive bullets, source text,
links and references are retained in notes; code fences and reference tables do not become tasks.
The document's Updated line can anchor relative dates. Selected destination overrides apply
across the batch. Batch undo preserves fields edited after the import.

Entry points: [Markdown parser](../app/src/main/java/com/mj/yata/util/MarkdownTaskParser.kt),
[import screen](../app/src/main/java/com/mj/yata/ui/screen/markdownimport/MarkdownImportScreen.kt),
[JSON exporter](../app/src/main/java/com/mj/yata/util/JsonExporter.kt),
[text/CSV importer](../app/src/main/java/com/mj/yata/util/PlainTextImporter.kt),
[export utilities](../app/src/main/java/com/mj/yata/util) and
[shared-link import](../app/src/main/java/com/mj/yata/ui/screen/sharedimport/SharedTaskImportScreen.kt).

## Notifications and integrations

- Task reminders use Android alarms and are re-established after reboot. Notification actions
  offer complete/snooze. Exact-alarm access, notification permissions and power restrictions matter.
- Daily agenda and overdue nudges are configurable notification features.
- Six Glance widgets cover Agenda, Single List, Quick Add, Progress, Upcoming and Team Overdue.
  Widget styling supports opacity, corners, labels, dynamic color and accents.
- The Quick Settings tile opens capture from Android's notification shade.
- Tasker's **Create Task** plugin is off by default. Enabling it exposes the automation action
  through the Locale/Tasker plugin mechanism; it is not an authenticated team API.
- Device calendar integration reads visible events into Upcoming/Next 10 Days. It requires
  read permission and does not write back or include provider events in task snapshot sync.

Entry points: [notifications](../app/src/main/java/com/mj/yata/notification),
[widgets](../app/src/main/java/com/mj/yata/widget),
[Tasker](../app/src/main/java/com/mj/yata/tasker) and
[calendar provider](../app/src/main/java/com/mj/yata/data/calendar/DeviceCalendar.kt).

## Backup and sync

| Area | Current behavior |
| --- | --- |
| Portable schema | Backup version 5 includes project groups; versions 1–4 remain importable |
| Database | Room version 36 unifies historical branch schemas through explicit migrations |
| Local backup | Device-bound Keystore encryption; not a portable key |
| Remote providers | GitHub, SFTP, FTPS and FTP |
| Remote encryption | Required passphrase-based AES-256-GCM envelope with `YATAENC1` header |
| Merge | Baseline-aware three-way merge, deletion propagation and remote preference for direct conflicts |
| First join | Review/confirmation when joining existing remote data |
| Unexpected empty local state | Stops for restore-versus-intentional-deletion review |
| Recovery | Recovery copy before remote restore/apply paths; restore points from provider history |
| GitHub concurrency | Checked branch updates/retries rather than a remote lock |
| GitHub history | Ancestry check detects rewound/force-pushed history; bounded recovery search can find older valid snapshots |
| SFTP/FTP concurrency | Remote lock plus rotated backup files |
| Scheduling | After-change and periodic behavior; unchanged scheduled data can skip writes |
| Config transfer | Password-encrypted GitHub setup transfer, separate from ordinary backups |

Portable settings deliberately exclude credentials, sync destinations/checkpoints, local-backup
state, app-lock material and install-specific photo paths. Photos are carried as bytes instead
of relying only on paths that fail on another device. Running timer and demo-mode flags remain
device-local. Sync is not a substitute for an independent backup.

Entry points: [backup operations](../app/src/main/java/com/mj/yata/domain/usecase/BackupOperations.kt),
[snapshot engine](../app/src/main/java/com/mj/yata/data/sync/SnapshotSync.kt),
[GitHub](../app/src/main/java/com/mj/yata/data/github),
[SFTP](../app/src/main/java/com/mj/yata/data/sftp),
[FTP](../app/src/main/java/com/mj/yata/data/ftp),
[encryption](../app/src/main/java/com/mj/yata/util/BackupCrypto.kt),
[scheduled worker](../app/src/main/java/com/mj/yata/data/backup/UnifiedBackupWorker.kt).
See [sync setup](SYNC_SETUP.md) for the user-facing procedures.

## Appearance, language and privacy

System/light/dark/AMOLED modes, dynamic colors, custom seeds, fonts, text/UI scaling, row density,
motion controls and theme presets support different reading preferences. AMOLED uses true-black
app surfaces, with separate widget treatment. Settings are searchable and grouped by purpose.

English is the source locale; 24 translated locale folders are maintained alongside it. Android
13+ can expose per-app language selection. Debug builds provide en-XA/ar-XB pseudolocales for
layout checks. Locale parity checks verify key presence, not translation quality or parser coverage.

App Lock supports a PIN and biometrics and hides recents content. It does not replace remote
backup encryption. Logs and exported readable files can contain sensitive context; review them
before sharing. Core storage, search and parsing are local. Remote sync and explicitly chosen
share/export actions move data to their selected destinations.

Entry points: [settings](../app/src/main/java/com/mj/yata/ui/screen/settings/SettingsScreen.kt),
[theme](../app/src/main/java/com/mj/yata/ui/theme),
[lock screen](../app/src/main/java/com/mj/yata/ui/screen/lock/LockScreen.kt),
[localization guide](localization.md).

## Demo data isolation

Demo mode swaps the visible repository to sample data without treating it as the real database.
Its safety gate coordinates mode changes with full backup/sync/restore/export operations, so
queued transfers recheck mode before side effects. Automatic runs skip without changing backup
timestamps or canonical hashes. The mode flag is device-local.

Screenshots in the guides use this sample data; they do not establish that current real-data
sync was tested. Any new transfer entry point must hold the gate for its entire operation.

Entry points: [demo repository](../app/src/main/java/com/mj/yata/data/demo/DemoRepository.kt),
[safety gate](../app/src/main/java/com/mj/yata/data/demo/DemoDataSafetyGate.kt).
