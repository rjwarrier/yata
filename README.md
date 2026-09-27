<div align="center">

# YATA

**Yet Another To-Do App** — pronounced *"YAH-tuh"*

A Material 3 Expressive task manager for Android, built with Jetpack Compose, Room, and Hilt.

[![Latest release](https://img.shields.io/github/v/release/rjwarrier/yata?label=release&color=E8735A)](https://github.com/rjwarrier/yata/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

[**Download APK**](https://github.com/rjwarrier/yata/releases/latest/download/app-release.apk) ·
[Changelog](CHANGELOG.md) ·
[Releases](https://github.com/rjwarrier/yata/releases)

<br>

<img src="https://github.com/user-attachments/assets/f23751e7-6288-424b-bddd-628c8d037061" width="19%" alt="YATA screenshot 1" />
<img src="https://github.com/user-attachments/assets/04997998-3970-4d53-b86c-e925f0f3a134" width="19%" alt="YATA screenshot 2" />
<img src="https://github.com/user-attachments/assets/1dbfe773-84ea-4a46-8db7-d6e5a99f654a" width="19%" alt="YATA screenshot 3" />
<img src="https://github.com/user-attachments/assets/f9dfd14f-5592-425a-b538-151c706dac2c" width="19%" alt="YATA screenshot 4" />
<img src="https://github.com/user-attachments/assets/07d877b7-c0f1-48dd-a8d0-e8d16903cf37" width="19%" alt="YATA screenshot 5" />

</div>

## About

YATA is a single-user, offline-first task manager. Tasks can be organized under **Projects**, folder-style **Lists**, **Tags**, and **People** (for delegation) — and any of those can be switched off entirely if you don't need them. Everything lives on-device; sync and backup are opt-in and go to storage you control.

## Highlights

- **Natural-language quick add** — type or dictate *"Gym every monday 7am !1 #health"* and get the date, time, recurrence, priority, and tags parsed out, highlighted as you type.
- **Today, Next 10 Days, Upcoming** — focused daily view, rolling agenda, and a week strip / month calendar.
- **Recurring tasks, subtasks, start dates** — with streaks, deferral ("not before"), follow-ups, and estimates.
- **Home-screen widgets** — six Glance widgets plus a Quick Settings tile for capture from anywhere.
- **Analytics** — completion trends, on-time rate, aging, postponement tracking, and per-person workload.
- **Your data, your storage** — JSON backup, encrypted sync via GitHub, SFTP, FTPS, or FTP. No account, no server of ours.
- **24 languages** and adaptive layouts for tablets and foldables.

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
- Multi-device sync over **GitHub**, **SFTP**, **FTPS**, or **FTP** — three-way merge, deletion propagation, optional end-to-end encryption, automatic recovery backups
- `.ics` calendar export, Markdown export
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

## Building

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
| **Other** | Markwon, sshj, Apache Commons Net, pdfbox-android, Tasker plugin library |

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

YATA is a personal project, built in spare time. If it's useful to you:

<a href="https://www.buymeacoffee.com/ranjithj"><img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="40" /></a>

Bug reports and ideas are welcome in [Issues](https://github.com/rjwarrier/yata/issues).
