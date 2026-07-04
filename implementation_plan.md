# Implementation Plan: Material 3 Expressive Todo App

The goal is to build a fully offline, no-login Android Todo app using Kotlin, Jetpack Compose, Room, and the Material 3 Expressive design system.

## User Review Required
> [!IMPORTANT]
> Since this is a massive greenfield project, please review the proposed architecture, database schema, and technical stack.
> Key decisions:
> - **Build System**: Gradle Version Catalogs and KSP
> - **Database**: Room with FTS4 for search
> - **Dependency Injection**: Hilt
> - **Architecture**: Clean Architecture (MVVM)
> - **UI**: Jetpack Compose (latest) with M3 Expressive (dynamic colors, squircles)
> - **Background**: foreground services for Pomodoro, AlarmManager for reminders.

## Proposed Changes

### [Setup & Configuration]
#### [NEW] `gradle/libs.versions.toml`
Define dependencies: Compose, Hilt, Room, KSP, Coroutines, DataStore, Coil, Turbine, etc.
#### [MODIFY] `build.gradle.kts` (Project & App)
Setup plugins (KSP, Hilt, Compose compiler) and apply versions catalog.

### [Data Layer: Room & DataStore]
#### [NEW] `app/data/local/db/AppDatabase.kt`
Room database setup with entities and DAOs.
#### [NEW] `app/data/local/db/entity/TaskEntity.kt`
Room entity representing a task, including repeat rules and pomodoro tracking.
#### [NEW] `app/data/local/db/entity/ProjectEntity.kt`
Room entity for projects/lists.
#### [NEW] `app/data/local/db/entity/LabelEntity.kt`
Room entity and cross-refs for tags/labels.
#### [NEW] `app/data/local/db/entity/PomodoroSessionEntity.kt`
History of focus and break sessions.
#### [NEW] `app/data/local/datastore/UserPreferences.kt`
DataStore to hold app settings (Theme, Pomodoro lengths, dynamic color toggle).

### [Domain Layer: Models & Repositories]
#### [NEW] `app/domain/model/Task.kt`
Clean domain models.
#### [NEW] `app/domain/repository/TaskRepository.kt`
Interface and implementation mapping Flow<List<TaskEntity>> to Flow<List<Task>>.
#### [NEW] `app/domain/usecase/...`
Single-responsibility classes for Clean Architecture (e.g., GetTasksUseCase, UpdateTaskUseCase).

### [UI Layer: Theme & Navigation]
#### [NEW] `app/ui/theme/Theme.kt`
Material 3 Expressive dynamic and static color palettes.
#### [NEW] `app/ui/theme/Shape.kt`
Expressive squircle shapes (e.g., extra-large 28dp corners).
#### [NEW] `app/ui/theme/Type.kt`
Responsive Android typography.
#### [NEW] `app/ui/navigation/AppNavigation.kt`
Compose Navigation graph routing (Home, TaskDetail, Pomodoro, Settings).

### [Features: App Screens & ViewModels]
#### [NEW] `app/ui/screen/home/HomeScreen.kt`
Sidebar base with HomeViewModel to fetch smart lists and projects.
#### [NEW] `app/ui/screen/tasklist/TaskListScreen.kt`
Shows tasks, allows quick-add, sorting, grouping.
#### [NEW] `app/ui/screen/taskdetail/TaskDetailScreen.kt`
Rich editing, repeats, reminders, labels, subtasks, markdown notes.
#### [NEW] `app/ui/screen/pomodoro/PomodoroScreen.kt`
Canvas animations for timer ring, statistics integration.
#### [NEW] `app/ui/screen/settings/SettingsScreen.kt`
Theme toggles, JSON import/export, Pomodoro settings.

### [Background & Notifications]
#### [NEW] `app/pomodoro/PomodoroService.kt`
Foreground service to keep timer running and update persistent notification.
#### [NEW] `app/notification/ReminderScheduler.kt`
AlarmManager exact alarms for time-sensitive task reminders.

## Verification Plan
1. **Automated Tests**:
   - Write JUnit 5 tests for domain UseCases and mapping logic.
   - Use Turbine to test Repositories and `Flow` emissions.
   - Basic Compose UI testing for the core TaskRow interactions.
2. **Manual Verification**:
   - Install the APK on an Android emulator (API 35) or physical device.
   - Verify dynamic colors by changing the device wallpaper.
   - Verify Pomodoro timer runs accurately and survives app backgrounding (Foreground Service behavior).
   - Test offline search mechanism using FTS4.
   - Test JSON export/import data consistency.
