# Localization gaps

Snapshot as of 2026-08-13. Two different things get conflated under "missing translations" —
this doc covers both, separately, since the fixes for each are different.

1. **Missing locale keys** — a string exists in `values/strings.xml` but one or more of the 24
   `values-<code>/` files never got it. Caught by `./gradlew :app:lintLocaleParity`.
2. **Hardcoded strings** — UI text still written as a Kotlin literal instead of going through
   `stringResource`/`getString`, so it can never be translated at all. Caught (approximately) by
   `./gradlew :app:lintHardcodedStrings`.

## 1. Missing locale keys — found 6, now fixed

`lintLocaleParity` reported **0** missing keys across "23 locale(s)" — but that count itself was
wrong. Its directory filter excluded `values-v*` to skip API-level qualifier folders like
`values-v31`, and `values-vi` (Vietnamese) matches that same prefix. Vietnamese was silently
skipped by the check on every run. Fixed in `app/build.gradle.kts` (`lintLocaleParity`) by
requiring a digit after `values-v` instead of a bare prefix match.

With that fixed, a full key diff (`values/strings.xml` against all 24 locales) found 6 keys
present in English only, in **every** locale — the Welcome tour's "Make it yours" profile step,
added after the original 24-locale translation pass and never caught up:

- `welcome_next`, `welcome_get_started`
- `welcome_title_profile`, `welcome_profile_description`, `welcome_profile_email_hint`,
  `welcome_profile_avatar_or_icon`

**Status: fixed.** Translated into all 24 locales. `lintLocaleParity` now reports 0 missing across
24 locales.

## 2. Hardcoded strings — 79 raw matches, most are false positives

`lintHardcodedStrings`'s pattern (`Text("...")`, `text = "..."`, `contentDescription = "..."`,
`label/placeholder/title = "..."`) reports 107 across 38 files. 28 of those are in `DemoData.kt`,
which is off-limits per project convention (fictional seed content, not real UI copy) — that
leaves 79 across 37 files.

Manually verifying all 79: **most of them are animation `label = "..."` parameters** —
`rememberInfiniteTransition(label = "...")`, `animateFloatAsState(label = "...")` — a debug/
profiling identifier for Android Studio's animation inspector, not user-facing text. The pattern
can't tell those apart from a real `label =` slot on a UI element. Of 37 flagged files, files like
`LockScreen.kt`, `ProgressRing.kt`, `WelcomeScreen.kt`, `YataSelectChip.kt`, and most of the
"1-2 hits" files are **100% animation labels — nothing to fix**.

Two more matches are internal diagnostic tags, not UI text: `JsonExporter.kt`'s
`label = "project commonTagIds"` / `label = "settings[$index]"` are validation-error labels for
backup-import error messages, not translated strings. `AnalyticsScreen.kt:655`
(`text = "${...}$unit"`) is just number formatting — the actual literal is `trendUnit = "pp"` at
line 260, a percentage-points abbreviation, which (like "kg" or "km/h") isn't normally translated
either.

### What's actually real (11 items across 8 files)

**A. Same "preview panel empty state" message, worded differently per screen — 4 files.** All
four pass a literal string into `TaskPreviewPane`'s `emptySubtitle` param instead of a shared
`stringResource`:
- `ui/screen/main/tabs/UpcomingTab.kt:651` — `"Preview the selected day's tasks without leaving the planner."`
- `ui/screen/main/tabs/TodayTab.kt:804` — `"Preview today's tasks without leaving the day list."`
- `ui/screen/list/ListDetailScreen.kt:618` — `"Preview list tasks without leaving this list."`
- `ui/screen/project/ProjectDetailScreen.kt:737` — `"Preview project tasks without leaving this project."`

**B. `ExportOptionsDialog.kt:171`** — `"$doneCount completed task(s) available before filters."`
Also uses the `"(s)"` pattern the project's own strings.xml comment calls out as wrong
("untranslatable into languages with dual/few/many forms") — needs a `<plurals>`, not just a
`stringResource`.

**C. `ui/screen/crashlog/CrashLogScreen.kt:384`** — `"Shareable GitHub sync log"` (section label).

**D. `ui/widgets/TaskPreviewPane.kt:131,137`** — two, only one caught by the pattern:
`Text(if (task.done) "Reopen" else "Complete")` (missed — the `if` breaks the regex) and
`Text("Open")` (caught).

**E. `util/export/TaskReportExport.kt:127-131`** — PDF export metadata, lower priority since it's
document properties (visible in a PDF viewer's "Properties" panel) rather than in-app UI:
`title = "$title — YATA Task"`, `subject = "YATA task export: $title"`,
`keywords = "YATA, task, $title"`, and the share-intent chooser title `"Share $title"` (not
caught by the pattern at all — no matching parameter name).

**F. Glance home-screen widgets — 2 files.** Different rendering system from the Compose screens,
same underlying issue:
- `widget/UpcomingWidget.kt:154` — `"Nothing coming up."`
- `widget/WidgetComponents.kt:212` — `"Stale"` (staleness badge)

### Suggested order

B first (it's a real plurals bug, not just missing translation). Then A (one shared string with
per-screen substitution, or 4 separate resources — either works, four short strings). Then
C/D/F (quick, independent). E last (lowest visibility, touches PDF export code rather than a
screen).
