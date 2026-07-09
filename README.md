# YATA — Yet Another Task App

A Material 3 Expressive task manager for Android, built with Jetpack Compose, Room, and Hilt — with a companion Wear OS app that mirrors today's task count as a complication.

> Gradle root project name is `TodoExpressive` (legacy); the actual package/app id is `com.mj.yata`.

## Contents

- [Overview](#overview)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Testing](#testing)
- [Design reference](#design-reference)
- [Status](#status)

## Overview

YATA is a single-user, offline-first task manager. Tasks can optionally be organized under **Projects**, folder-style **Lists**, **Tags**, and **People** (for delegation) — each of those entity types can be hidden entirely via a feature flag if you don't need it. The whole app is backed by one Room database and one large `MainViewModel`; screens are Compose destinations that read from it via `hiltViewModel()`.

## Features

**Task management**
- Natural-language quick add — type a task and it parses out due date/time, priority, and list
- Recurrence rules, subtasks, Markdown-rendered notes, per-task comments
- Priority levels, flags, per-task reminders
- Duplicate-task detection when adding a new task
- Voice quick add, share-to-task (share text/links from other apps straight into a new task)
- Manual drag-and-drop reordering
- Bulk actions (complete / delete / tag / move / assign / duplicate) from a multiselect toolbar
- Delete-with-undo everywhere — soft-deletes to Trash, 30-day retention before permanent purge

**Organization**
- Projects, Lists, Tags, People — each independently toggleable via feature flags
- Starring for quick access from the nav drawer
- A shared accent-color system (16 accent slots) used consistently across custom color/icon pickers

**Views & navigation**
- **Today** — due-today + overdue tasks; progress ring reflects tasks pending as of the *start* of today (a task completed on a previous day no longer inflates the ring — only tasks still open, or completed *today*, count)
- **Next 10 Days** — a flat, date-grouped agenda across a rolling 10-day window (reachable from the drawer and a shortcut button on Today)
- **Upcoming** — 7-day strip or full month calendar, with a per-day agenda
- **Projects / People / Tags** tabs, each with its own detail screen
- Scoped **Search** across all tasks
- **Analytics** — completion trends, streaks, on-time rate, workload share, aging buckets, breakdowns by project/person/tag
- **Trash** — restore or permanently delete
- First-run **welcome tour**

**Home-screen widgets (Glance)**
- Full agenda widget, Single List, Quick Add (with a lightweight config dialog instead of launching the full app), Progress/Stats ring, Upcoming, Team Overdue
- Per-widget corner radius, opacity, Material You dynamic color, accent override, and custom label
- One shared refresh signal keeps every placed widget *and* the Wear OS complication in sync after any task write

**Wear OS companion**
- Today's-task-count complication, pushed from the phone app on every relevant change

**Notifications & reminders**
- Per-task reminders scheduled via `AlarmManager`, rescheduled automatically on device reboot
- Inline notification actions (e.g. mark done) without opening the app
- Scheduled theme window (auto light/dark on a schedule)

**Team / People**
- Assignment and delegation, per-person workload trend (overdue count over the last 7 days), Team Overdue widget

**Backup & export**
- Full JSON backup/restore
- Google Drive cloud backup (Drive `appDataFolder` scope + Google Sign-In), periodic + debounced background upload via WorkManager, optional Wi-Fi-only
- `.ics` calendar export, Markdown export for clipboard/share

**Customization**
- Theme mode (light/dark/system) + dynamic color, scheduled theme window
- App font, text scale, UI scale, FAB position, task-row density (compact/comfortable/spacious)
- Haptics toggle, reduce-motion toggle, start-of-week (Sunday/Monday)
- Independent visibility toggles per tab (Today/Upcoming/Projects/People/Tags)
- Independent "hide completed" state persisted per screen (Today/Project/List/Person)

**Tasker integration**
- A "Create Task" Tasker plugin action that writes directly to the repository, so it works outside the app's own running process

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 Expressive (Compose BOM 2024.02.01) |
| DI | Hilt 2.52 |
| Persistence | Room 2.6.1 (hand-written migrations) + DataStore Preferences |
| Navigation | Navigation-Compose 2.7.7 |
| Home-screen widgets | Glance 1.1.1 (`glance-appwidget`, `glance-material3`) |
| Background work | WorkManager + Hilt-Work (cloud backup uploads) |
| Cloud backup | Google Sign-In + Drive REST v3 over OkHttp |
| Markdown | Markwon |
| Wear sync | `play-services-wearable` |
| Tasker plugin | `taskerpluginlibrary` |
| Build | AGP 8.7.2, KSP, JDK 17 |

## Architecture

Two Gradle modules:
- **`:app`** — the phone app, `com.mj.yata`, minSdk 26, compileSdk/targetSdk 34
- **`:wear`** — companion Wear OS app, `com.mj.yata.wear`, minSdk 30

Layering is one-directional:

```
domain/model            plain Kotlin data classes, no Android/Room dependency
   ↓
data/local/db           Room entities + DAOs
   ↓
data/mapper             Entity ⇄ domain model conversion
   ↓
data/repository         YataRepositoryImpl (implements domain/repository/YataRepository, exposes Flows)
   ↓
ui/screen/main/MainViewModel   single ViewModel for the entire app (StateFlows + imperative methods)
   ↓
ui/screen/*             Compose screens, obtained via hiltViewModel()
```

There is **one `MainViewModel` for the whole app**, not one per screen — every screen reads from and calls methods on the same instance. Room schema changes are hand-written `Migration` objects (no auto-migration); a missing forward migration throws rather than silently wiping data.

Navigation is single-Activity via `AppNavigation.kt` / `Screen.kt` over a `NavHost`. `Screen.Main` is a 5-tab shell (Today, Projects, People, Tags, Upcoming) with its own drawer, FAB, and a shared `SnackbarHostState` for cross-tab bulk actions; detail screens (task/project/list/tag/person, search, settings, trash, analytics, next-10-days) are separate top-level destinations.

## Project structure

```
app/src/main/java/com/mj/yata/
├── domain/model/          Task, Project, YataList, Person, Tag, ... (plain data classes)
├── data/
│   ├── local/db/          Room entities, DAOs, AppDatabase + migrations
│   ├── local/datastore/   UserPreferences (DataStore-backed settings)
│   ├── mapper/            Entity ⇄ domain model conversion
│   └── repository/        YataRepositoryImpl
├── ui/
│   ├── screen/            main/ (tab shell + MainViewModel), task/project/person/tag/list detail,
│   │                        search, settings, analytics, trash, welcome, nextdays
│   ├── navigation/        Screen routes + AppNavigation NavHost
│   ├── widgets/           Reusable Compose widgets (TaskRow, ProgressRing, pickers, ...)
│   ├── sheets/            Bottom sheets (new/edit task, bulk actions, ...)
│   └── theme/             M3 Expressive theme + accent system
├── widget/                Home-screen Glance widgets
├── notification/          Reminder scheduling + delivery
├── tasker/createtask/     Tasker plugin integration
└── util/                  Exporters, recurrence, NLP quick-add parser, analytics, ...

wear/src/main/java/com/mj/yata/wear/   Wear OS companion (today's-count complication)
```

## Getting started

### Prerequisites
- Android Studio (recent stable) or a JDK 17 + Android SDK command-line setup
- compileSdk/targetSdk 34; minSdk 26 (phone), 30 (Wear)

### Build & run

```bash
git clone https://github.com/rjwarrier/yata.git
cd yata

# Compile Kotlin only — fast correctness check, no packaging
./gradlew :app:compileDebugKotlin -q

# Full debug build
./gradlew :app:assembleDebug -q

# Install to a connected device/emulator
./gradlew :app:installDebug -q

# Wear companion
./gradlew :wear:assembleDebug -q
```

On native Windows shells, use `gradlew.bat` in place of `./gradlew`.

## Testing

```bash
# Unit tests (JVM, no device)
./gradlew :app:testDebugUnitTest

# A single test class or method
./gradlew :app:testDebugUnitTest --tests "com.mj.yata.RecurrenceEvaluatorTest"
./gradlew :app:testDebugUnitTest --tests "com.mj.yata.NaturalLanguageParserTest"

# Instrumented tests — Room migrations — require a connected device/emulator
./gradlew :app:connectedDebugAndroidTest --tests "com.mj.yata.data.local.db.AppDatabaseMigrationTest"
```

There is no automated Compose UI test suite; UI-facing changes are verified manually on-device.

## Design reference

`design/` and `design_handoff_yata/` contain the original HTML/JSX design tokens and handoff notes the M3 Expressive theme was built from. They're static references for design intent, not code imported into the build.

## Status

Personal, actively-evolving project — schema and UI can change between commits. Current `versionName` (`app/build.gradle.kts`) is `0.1`.
