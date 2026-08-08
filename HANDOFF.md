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

**Translation quality fix.** A prior automated pass had expanded locale support to 24 locales and
claimed "0 missing keys" everywhere, but that check only verified `name=` keys existed — not that
the values were actually translated. An audit found 21 of 24 locales were **~75-98% still verbatim
English** with the language folder just tagged on top (e.g. `bn`/`ta`/`pa`/`sw` were ~98%
untranslated; `de`/`hi`/`it`/`nl` ~75%). Only `es`/`fr`/`pt` had genuine translations.

Fixed: retranslated all 21 affected locales (`de`, `nl`, `bn`, `cs`, `gu`, `hi`, `id`, `it`, `kn`,
`ml`, `mr`, `pa`, `pl`, `ro`, `sv`, `sw`, `ta`, `te`, `tl`, `tr`, `vi`) into fluent, native-quality
text via parallel background agents (one per language). Verified after: every locale file is
well-formed XML with exactly 1148 `<string>` + 28 `<plurals>` entries matching the English source
key-for-key, and `./gradlew :app:lintDebug` reports **zero** `MissingTranslation` issues across all
24 locales. The only lint noise left is `MissingQuantity` for `cs`/`pl`/`ro` (those languages have
CLDR `few`/`many` plural categories beyond `one`/`other`) — expected and unfixed on purpose, this
project deliberately keeps every locale to `one`/`other` only (see `TRANSLATION_HANDOFF.md` §5).

**If you're asked to touch locale files again**: don't trust an "0 missing keys" claim at face
value — that only means the `name=` attributes exist. Actually diff a sample of *values* against
the English source (or spot-check a few dozen entries per locale) before believing a locale is
done. The Python snippet in `TRANSLATION_HANDOFF.md` §7 counts missing *keys*; it does not detect
values that are present but untranslated — that gap is exactly what caused this rework.

**Agent-tool gotcha discovered this session**: a general-purpose background agent asked to
translate a ~1260-line locale file sometimes tries to serialize the whole file into a single
`Write` tool call, which can exceed the 64,000-output-token response cap and fail outright partway
through (happened to `kn`, `ml`, `pa` on first attempt — no partial file was corrupted, the agent
just errored out with nothing written). The fix was re-prompting with an explicit instruction to
write via several `Edit` calls in ~100-150-line chunks instead of one `Write`. Also watch for
agents appending a stray `</content>`/`</invoke>` artifact after `</resources>` on large writes
(`ro`, `cs`, `pl` all had this — harmless single-line fix each time, but re-validate XML
well-formedness after any large agent-driven file rewrite, don't just trust the agent's self-report).

Commits landed on `codex/github-sync`, not pushed unless asked. Debug build installed to connected
device (`49261FDAS003Z8`) after the hardcoded-string pass — compiles clean, full unit suite green
throughout.

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

- All 24 locales now have genuine, verified translations (not just present keys) — see §3. New UI
  work will still need translations added to all 24 files going forward; keep the reuse-before-create
  discipline (§4) so that stays manageable.
- Translation *quality* was fixed by AI-generated translations, not native speakers. If the repo
  owner or a native speaker later flags specific phrasing as wrong/unnatural in any of the 24
  locales, that's a spot-fix to the specific `values-<code>/strings.xml` entries, not a full redo.
- No outstanding hardcoded-string work — the pass is done short of `DemoData.kt`.
- Nothing else is currently flagged as in-progress or blocked. Check `git status` and `git log`
  against this file's commit list above to confirm nothing has moved since this was written.
