<div align="center">

# YATA

**Yet Another To-Do App** — pronounced *"YAH-tuh"*

A Material 3 Expressive task manager for Android, built with Jetpack Compose, Room, and Hilt.

[![Latest release](https://img.shields.io/github/v/release/rjwarrier/yata?label=release&color=E8735A)](https://github.com/rjwarrier/yata/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

[**Download APK**](https://github.com/rjwarrier/yata/releases/latest/download/app-release.apk) ·
[User guide](docs/USER_GUIDE.md) ·
[Feature reference](docs/FEATURES.md) ·
[Sync setup](docs/SYNC_SETUP.md) ·
[Changelog](CHANGELOG.md) ·
[Releases](https://github.com/rjwarrier/yata/releases)

</div>

## About

YATA is a single-user, offline-first task manager. Tasks can be organized under **Projects**, folder-style **Lists**, **Tags**, and **People** (for delegation) — and any of those can be switched off entirely if you don't need them. Everything lives on-device; sync and backup are opt-in and go to storage you control.

Capture a thought in natural language, turn it into a plan, and review both the work and the
effort it takes. Start with Today and a few tasks; enable more organization as you need it.
People and team analytics help you track your own assignments and delegated work. Shared links
let someone import a copy; they do not create a shared live workspace.

## Documentation

| Start here | What you will find |
| --- | --- |
| [User guide](docs/USER_GUIDE.md) | First task, daily planning, recurrence, organization, time tracking, imports, widgets, settings and troubleshooting |
| [Feature reference](docs/FEATURES.md) | Supported behavior, boundaries and links to the implementation |
| [Sync setup](docs/SYNC_SETUP.md) | GitHub and SFTP/FTPS/FTP setup, adding a device, encryption, recovery and common errors |
| [Developer guide](docs/DEVELOPMENT.md) | Toolchain, project structure, builds, checks, data migrations and contribution workflow |
| [Documentation index](docs/README.md) | Maintained guides and engineering notes, with design plans identified separately |

## Screenshots

<table>
  <tr>
    <td width="33%" align="center"><a href="docs/USER_GUIDE.md#daily-planning"><img src="docs/assets/screenshots/01-today.png" alt="Today with overdue work, priority filters, estimates and quick add" width="280"></a><br><sub><strong>Today</strong> — choose what needs attention.</sub></td>
    <td width="33%" align="center"><a href="docs/USER_GUIDE.md#projects-lists-and-groups"><img src="docs/assets/screenshots/02-project-groups.png" alt="Projects organized into collapsible groups with completion progress" width="280"></a><br><sub><strong>Projects</strong> — group outcomes and track progress.</sub></td>
    <td width="33%" align="center"><a href="docs/USER_GUIDE.md#people-and-delegation"><img src="docs/assets/screenshots/03-people.png" alt="People with assignment, completion and overdue counts" width="280"></a><br><sub><strong>People</strong> — review assignments and workload.</sub></td>
  </tr>
  <tr>
    <td align="center"><a href="docs/USER_GUIDE.md#daily-planning"><img src="docs/assets/screenshots/05-upcoming.png" alt="Month calendar with task markers and a selected-day agenda" width="280"></a><br><sub><strong>Calendar</strong> — plan beyond today.</sub></td>
    <td align="center"><a href="docs/USER_GUIDE.md#estimates-and-focus-timer"><img src="docs/assets/screenshots/06-task-details.png" alt="Task details with tracked time, timer, project sections and nested subtasks" width="280"></a><br><sub><strong>Task details</strong> — break down work and record effort.</sub></td>
    <td align="center"><a href="docs/USER_GUIDE.md#analytics-and-reviews"><img src="docs/assets/screenshots/07-analytics.png" alt="Analytics showing task health, planned effort, completion and daily activity" width="280"></a><br><sub><strong>Analytics</strong> — review progress and capacity.</sub></td>
  </tr>
</table>

Screenshots show sample data in YATA 0.96.2, captured on 27 October 2026. Colors, fonts,
navigation and visible features depend on your settings. [Screenshot details](docs/assets/screenshots/README.md).

## Quick start

1. Install the APK from the [latest release](https://github.com/rjwarrier/yata/releases/latest).
2. Open YATA and finish the welcome screens.
3. Tap **New task**, enter `Buy milk tomorrow 5pm`, and review the detected date and time.
4. Save, then open **Upcoming** to see the task on its due date.
5. Tap a task to add notes, subtasks, a reminder or an estimate; tap its completion circle when done.
6. Create a file backup under **Settings → Backup & Sync** before setting up optional sync.

Android 8.0 or newer is required. For APK installation, allow installation from your browser or
file manager when Android asks. Keep the installed app and its data when upgrading; install
the matching signed release over it. See the [full walkthrough](docs/USER_GUIDE.md#quick-start).

## Highlights

- **Natural-language quick add** — type or dictate *"Gym every monday 7am !1 #health"* and get the date, time, recurrence, priority, and tags parsed out, highlighted as you type.
- **Today, Next 10 Days, Upcoming** — focused daily view, rolling agenda, and a week strip / month calendar.
- **Recurring tasks, subtasks, start dates** — with streaks, deferral ("not before"), follow-ups, and estimates.
- **Focus timer and recorded effort** — time your work and compare tracked minutes with estimates.
- **Calendar planning** — show device events alongside tasks, or import shared events and calendar files.
- **Home-screen widgets** — six Glance widgets plus a Quick Settings tile for capture from anywhere.
- **Analytics** — completion trends, on-time rate, aging, postponement tracking, and per-person workload.
- **Your data, your storage** — JSON backup, encrypted sync via GitHub, SFTP, FTPS, or FTP. No account, no server of ours.
- **English plus 24 translated locales** and adaptive layouts for tablets and foldables.

## Features

<details>
<summary><b>Tasks</b></summary>

- Natural-language quick add (typed or voice, on-device recognition) with a "smart add understood this as" preview
- Recurrence rules, subtasks, Markdown notes, per-task comments
- Priorities, flags, reminders, start dates, follow-ups, time estimates
- Recurring-task streaks and postponement tracking with per-priority warnings
- Optional warning when a task is rescheduled onto a weekend
- Drag-and-drop reordering and project sections
- Bulk complete / delete / tag / move / assign / duplicate / reschedule
- Delete-with-undo everywhere; Trash with configurable retention; Archive for shelving without deleting
- Share tasks, projects, or lists as a link — the receiver previews and imports them

</details>

<details>
<summary><b>Views</b></summary>

- **Today** — due and overdue tasks with a progress ring and tappable stat filters
- **Next 10 Days** — flat, date-grouped agenda
- **Upcoming** — 7-day strip or month calendar with per-day agenda
- **Projects / People / Tags** tabs, each with a detail screen
- **Search** across live, archived, and trashed tasks
- **Command palette** with saved presets (My Work, Focus Mode, Morning/Evening Review, Stale Nudges, Task Health)
- **Analytics**, per-person and team analytics

</details>

<details>
<summary><b>Widgets & integrations</b></summary>

- Agenda, Single List, Quick Add, Progress, Upcoming, and Team Overdue widgets
- Per-widget corner radius, opacity, Material You color, accent override, custom label
- Quick Settings tile for quick add
- Share-to-task from any app
- Tasker "Create Task" plugin action (off by default)

</details>

<details>
<summary><b>Backup, sync & export</b></summary>

- Full JSON backup/restore (including profile photos), CSV import/export
- Multi-device sync over **GitHub**, **SFTP**, **FTPS**, or **FTP** — three-way merge, deletion propagation, required passphrase encryption for remote snapshots, automatic recovery backups
- `.ics` calendar export, Markdown export and checklist import into any project or list
- Branded PDF / image export for any project, tag, or person

</details>

<details>
<summary><b>Customization & privacy</b></summary>

- Light / dark / system / AMOLED themes, dynamic color, custom seed colors
- App font, text scale, UI scale, row density, FAB position, start of week
- Per-tab visibility and per-screen "hide completed"
- Haptics and reduce-motion toggles
- Optional App Lock (PIN + biometric) that also hides content from the recents screen

</details>

## Requirements

- Android 8.0 (API 26) or newer
- To build: JDK 17 and the Android SDK (compileSdk 35), or a recent Android Studio

## Importing Markdown checklists

Open **Import Markdown** from a project/list's overflow menu or **Settings → Data Management**.
Choose a UTF-8 `.md` file, select a project, a list, or both, then review and select tasks before
tapping **Import**. Each main task has a selection checkbox; **Select all** and **Select none**
change the whole selection. Only selected main tasks and their subtasks are added. Checked tasks
are shown in the preview but are initially unselected.

- Checkboxes become tasks and nested subtasks. Without checkboxes, bulleted and numbered lists
  are supported too, with indented items becoming subtasks. Plain task-per-line text also works.
  In checklist documents, ordinary descriptive bullets stay in notes.
- A Markdown table with a `Task` or `Title` column can supply `Due`, `Start`, `Time`, `Tags`,
  `Person`, `Project`, `List`, `Priority`, `Repeat`, `Reminder`, `Status`, and `Notes` columns.
  Indented properties such as `Due: tomorrow` and `Project: Work` are supported as well.
  Reference tables and fenced code examples do not become tasks.
- Every task uses the individual-task smart-add parser: dates (including your numeric date order
  and custom aliases), times, start dates, recurrence, reminders, priority, flags, `#tags`,
  `@people`, `+projects`, and `=lists`. Names resolve against existing local items with the same
  matching and feature settings. Unmatched references are flagged and retained in source notes.
- Headings provide defaults; task-level values override them. An `Updated: 27 Oct 2026` line
  anchors relative dates; otherwise the current date is used. Selecting a project or list in the
  picker overrides detected destinations for all selected tasks. Leave both unset to use detected
  destinations. Review the detected fields before importing, especially dates mentioned in prose.
- Source text, descriptive bullets, links, and reference sections are retained in notes. URLs and
  inline code stay literal. **Use detected schedules** can turn off all imported scheduling fields.
  Import saves the selected preview values without parsing them a second time.

## Building

See the [developer guide](docs/DEVELOPMENT.md) for setup, verification, migration requirements
and safe device use.

```bash
git clone https://github.com/rjwarrier/yata.git
cd yata
./gradlew :app:assembleDebug
```

Install on a connected device with `./gradlew :app:installDebug`. On Windows, use `gradlew.bat`.

<details>
<summary><b>Tests</b></summary>

```bash
# JVM unit tests
./gradlew :app:testDebugUnitTest

# A single class
./gradlew :app:testDebugUnitTest --tests "com.mj.yata.NaturalLanguageParserTest"
```

Instrumented tests (Room migrations, Compose smoke tests) reinstall the app and write to its database, so they refuse to run without `-PdisposableDevice`. **Use an emulator or spare device — never a phone with real data.**

```bash
./gradlew :app:connectedDebugAndroidTest -PdisposableDevice
```

</details>

## Tech stack

| | |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose, Material 3 Expressive (Compose BOM 2025.12) |
| **DI** | Hilt |
| **Storage** | Room (hand-written migrations), DataStore |
| **Background** | WorkManager, AlarmManager |
| **Widgets** | Glance |
| **Other** | Markwon, sshj, Apache Commons Net, Tasker plugin library |

## Architecture

Single-activity app with two Gradle modules: `:app` and `:baselineprofile`. Layering is one-directional:

```
domain/model  →  data/local/db  →  data/mapper  →  data/repository  →  MainViewModel  →  ui/screen/*
```

One `MainViewModel` backs the whole app; heavier logic lives in injected use cases (`TaskOperations`, `BackupOperations`). See [`CLAUDE.md`](CLAUDE.md) for a detailed tour of the codebase.

<details>
<summary><b>Project structure</b></summary>

```
app/src/main/java/com/mj/yata/
├── domain/          models, repository interface, use cases
├── data/
│   ├── local/       Room database, DataStore preferences, crash log
│   ├── repository/  YataRepositoryImpl
│   ├── sync/        snapshot merge engine
│   ├── github/      GitHub sync transport
│   ├── sftp/ ftp/   SFTP / FTP(S) transports
│   └── voice/       on-device speech recognition
├── ui/
│   ├── screen/      one package per destination
│   ├── widgets/     shared composables
│   ├── sheets/      bottom sheets
│   ├── navigation/  routes + NavHost
│   └── theme/       M3 Expressive theme + accent system
├── widget/          home-screen widgets
├── notification/    reminders, agenda, overdue nudges
├── tasker/          Tasker plugin
└── util/            natural-language parser, exporters, recurrence
```

</details>

## Support

For missing tasks, late reminders, import problems or sync failures, start with
[Troubleshooting](docs/USER_GUIDE.md#troubleshooting). Include the app version, Android version,
steps to reproduce and the exact error in a bug report. Review logs and screenshots before
sharing; leave out tokens, passwords, passphrases and private task content.

YATA is a personal project, built in spare time. If it's useful to you:

<a href="https://www.buymeacoffee.com/ranjithj"><img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="40" /></a>

Bug reports and ideas are welcome in [Issues](https://github.com/rjwarrier/yata/issues).
