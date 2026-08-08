# YATA — Session Handoff

Written for a fresh LLM (or a human) picking up this repo with zero prior context. Repo root: `D:\AntiGravity\yata`. Branch: `codex/github-sync`.

## 1. Read this first

`CLAUDE.md` in this repo root is the primary reference — architecture, module layout, commands,
conventions (changelog, localization, DB migrations, error handling, etc.). This file only covers
*session state*: what was just done and what's next. Don't duplicate CLAUDE.md's content here; go
read it.

## 2. What this app is

YATA ("Yet Another Task App", pronounced **"YAH-tuh"** — *ya* as in *yard*, *ta* as in *factory*) — Material 3 Expressive task manager, Android, Kotlin + Jetpack
Compose + Room + Hilt. Package id `com.mj.yata`. Two Gradle modules: `:app` (the phone app),
`:baselineprofile` (ART baseline profile generator).

## 3. What just happened (most recent work, newest first)

**Hardcoded-string extraction pass — now effectively complete.** Over several sessions, hardcoded
UI string literals in Compose code were moved into `app/src/main/res/values/strings.xml` as
`stringResource`/`pluralStringResource` calls. Started at 351 flagged hits
(`./gradlew :app:lintHardcodedStrings`), now down to **103**, of which:

- **28** are in `app/src/main/java/com/mj/yata/data/demo/DemoData.kt` — fictional demo-mode seed
  content, **permanently out of scope**. The repo owner has explicitly asked for this file to never
  be touched by any cleanup/localization pass ("that's for my use only").
- **75** are confirmed non-UI false positives the lint regex can't distinguish: Compose animation
  API debug labels (`animateFloatAsState(label = "...")`, `AnimatedContent(label = "...")`,
  `rememberInfiniteTransition(label = "...")` — these are dev-tooling identifiers, never rendered),
  internal validation labels in `JsonExporter.kt` (not UI), and one dynamic `@`-prefix
  concatenation in `ExportChips.kt` that carries no literal text worth extracting.

Treat `lintHardcodedStrings`'s count as a **floor, not a ceiling** if you re-run it — the regex
misses `text =` parameters split across multiple lines inside a `Text(...)` call, so future UI
work can silently reintroduce hardcoded strings the tool won't flag. Spot-check new/changed
composables by eye too.

**Translation handoff package** (`TRANSLATION_HANDOFF.md` + `missing_translation_keys.txt`, both
at repo root) — localized support now covers 24 locales (`es`, `fr`, `pt`, `de`, `it`, `nl`, `id`, `tr`, `vi`, `tl`, `pl`, `sv`, `ro`, `cs`, `sw`, `hi`, `bn`, `mr`, `te`, `ta`, `gu`, `kn`, `ml`, `pa`). Base `strings.xml` has 1148 `<string>` + 28 `<plurals>`; all 24 locales have 0 missing strings and 0 missing plurals. `missing_translation_keys.txt` is clear. All Settings screen sections, headers, and UI strings are 100% localized into native vocabulary. App language picker enum `AppLanguage.kt` updated with all 25 language entries. `TRANSLATION_HANDOFF.md` §7 has the exact Python one-liner to audit/regenerate if new UI features are added.

Commits pushed to `codex/github-sync`. Debug build installed to connected device (`49261FDAS003Z8`) — compiles clean, full unit suite (22/22) green throughout.

**Earlier in the branch** (not this session, see `git log` for full history): moved Remote Sync
config from a dialog to a dedicated `RemoteSyncScreen`, fixed GitHub sync bugs (secondary
rate-limit misclassification, swallowed error causes), added the real GitHub icon, and overhauled
the Analytics screen (drill-through navigation, created-vs-completed chart, trend arrows,
capacity/weekday-pattern insights).

## 4. Standing workflow conventions for this repo (established this session, likely to continue)

- Verification loop after any batch of changes: `./gradlew :app:compileDebugKotlin -q` →
  `./gradlew :app:testDebugUnitTest -q` (check
  `grep -l 'failures="[1-9]\|errors="[1-9]' app/build/test-results/testDebugUnitTest/*.xml`
  reports nothing) → `adb devices` + `./gradlew :app:installDebug -q` if a device is connected →
  commit. Push only when explicitly asked.
- Commit messages: heredoc, ending `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Reuse-before-create for string resources: grep `strings.xml` for an exact-text match before
  adding a new key, to avoid duplicate/drifting translations across the three locale files.
- `DemoData.kt` is off-limits for any cleanup/localization/refactor sweep — see §3.
- **Never** run `connectedAndroidTest`/instrumented tests against the user's real device — see the
  prominent warning in `CLAUDE.md`. It wipes real data. Emulator or spare device only, and ask
  first.
- **Never** drive the device via `adb shell input`/`screencap`/`uiautomator` to "see" the UI —
  build, install, and describe; visual verification is the user's.

## 5. Open threads / plausible next steps

- Translation pass completed — all 350 missing strings and 10 plurals have been translated into Spanish (`es`), French (`fr`), and Portuguese (`pt`). 0 missing keys remain.
- No outstanding hardcoded-string work — the pass is done short of `DemoData.kt`.
- Nothing else is currently flagged as in-progress or blocked. Check `git status` and `git log`
  against this file's commit list above to confirm nothing has moved since this was written.
