# Todo App Implementation Tasks

## Phase 1: Planning & Setup
- [x] Write implementation plan and get user approval
- [x] Initialize Android project with Compose, Hilt, Room, Navigation, etc. dependencies
- [x] Setup Gradle Version Catalogs and KSP

## Phase 2: Data & Domain Layer
- [x] Create Core Domain Models (Task, Project, Label, PomodoroSession)
- [x] Create Room Entities, DAOs, and Type Converters
- [x] Create AppDatabase and Room setup
- [ ] Implement Repositories (TaskRepository, ProjectRepository, etc.)
- [ ] Implement DataStore for UserPreferences
- [ ] Create Use Cases (CRUD for Tasks, Projects, Pomodoro)
- [x] Setup Hilt Dependency Injection modules (Database, Repository, UseCase)

## Phase 3: M3 Expressive Design System & Theming
- [x] Setup Color Scheme (Dynamic Color + Fallback palettes)
- [x] Setup Typography (M3 Expressive specs)
- [x] Setup Shapes (Extra-large squircles)
- [x] Create common Compose UI components (TaskRow, PrioritySelector, etc.)

## Phase 4: Core Features - Tasks & Projects
- [x] Implement Navigation graph
- [ ] Build Home / Sidebar Navigation (Projects, Smart Lists)
- [x] Build Task List Screen (with sticky quick-add bar)
- [x] Build Task Detail / Edit Screen (BottomSheet / Full Screen)
- [ ] Implement Task CRUD logic in ViewModels
- [ ] Implement Project creation and management

## Phase 5: Reminders & Notifications
- [x] Implement AlarmManager scheduler for task reminders
- [x] Create BroadcastReceiver for alarms and boot completion
- [x] Show local notifications for reminders

## Phase 6: Pomodoro Timer
- [x] Implement Pomodoro count-down engine
- [x] Create Foreground Service for running timer
- [x] Build Pomodoro Focus Screen (Canvas ring, stats, controls)
- [x] Link Pomodoro sessions to specific tasks

## Phase 7: Additional Features
- [x] Implement Search & Filters
- [x] Build Settings Screen (Theme, Pomodoro defaults, Export/Import)
- [x] Implement Data Export/Import to JSON
- [x] Build App Widgets (Glance)

## Phase 8: Polish & Verification
- [ ] Test interactions and animations
- [x] Write Unit Tests (JUnit/Turbine)
- [ ] Verify offline full functionalityence and FTS4 search
- [ ] Fix any UI/UX issues
