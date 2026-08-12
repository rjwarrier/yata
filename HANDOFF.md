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

**Android tag grouping parity with the web UI.** The repo owner said tag grouping was added in the
web UI and asked for the Android app to match it. Android already had the data model/Room table
(`tag_groups`), sync/export support, and some UI display/edit support, but it was incomplete and
buggy in practice:

- Tags tab can now multi-select tags and move them into an existing or newly-created group.
- The Tags tab now shows tag groups inside both Open and Closed sections, instead of flattening
  closed tags. Group headers are alphabetized, collapsible, and deletable; deleting a group
  ungroups its tags.
- The tag editor now lists existing groups, keeps newly-created inline groups visible immediately,
  and treats a typed new-group draft as the intended group when the user taps the main Save/Create
  button. Before this, typing a group name and pressing Save did nothing unless the tiny inline
  checkmark was tapped first.
- Persistence was hardened at the repository layer: creating a tag group and assigning tag(s) now
  happens in Room transactions (`YataRepository.upsertTags(..., pendingGroup)` and
  `YataRepository.setTagsGroup(tagIds, groupId, pendingGroup)`). Existing-group assignment uses a
  direct DAO `UPDATE tags SET groupId = :groupId WHERE id IN (:tagIds)`, so it does not re-upsert
  placeholder groups or accidentally overwrite group names.
- Added localized plurals for tag group assignment title and tag open-task counts across all
  locale files.

Important bug trail: the owner tried assigning group `STPs` to tag `tolydas`/`TolyDas` multiple
times and it still showed "no group". A read-only DB pull from both connected debug installs showed
`tag_groups` empty and `tolydas`/`TolyDas` with `groupId = NULL`, confirming the assignment had not
persisted. The root causes fixed were (1) inline new-group draft not being applied on Save, and
(2) group creation and tag assignment being separate async writes instead of one transactional
operation. The latest installed/pushed commit for this is `4829d37 Persist tag group assignments
transactionally`; earlier related commits are `6df5ac7`, `c0e0453`, and `c1a57c0`.

For updating the web UI/backend: mirror the Android contract, not the earlier broken flow. A tag
group row must exist before a tag references it, and "create group + assign one/many tags" should be
atomic/transactional. Pressing the main Save/Create button in a tag editor should apply any typed
new-group draft; do not require a separate tiny confirm affordance. If a typed group name matches an
existing group, reuse the existing group instead of creating a duplicate. Bulk assignment should
update tag `groupId`s directly and durably. After saving, a reopened tag editor should show the
assigned group selected, and the tags list should render that tag under the group in both open and
closed/inactive sections.

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

Update after the Android tag-group work: commits `6df5ac7`, `c0e0453`, `c1a57c0`, and `4829d37`
were pushed to `codex/github-sync`. Debug builds were installed on both connected devices
(`49261FDAS003Z8`, `IRGUROPJP7TCPFRW`). `./gradlew :app:compileDebugKotlin -q` passed. Full
`lintDebug` was attempted earlier and hung with no output, so it was stopped; targeted XML/new-key
checks passed for the new resources. There is still an untracked `yata-debug.apk` in the repo root;
leave it alone unless the owner asks.

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

- Web UI follow-up: update the web implementation to match Android's final tag-group contract in
  section 3. In particular, Save/Create must apply a typed group draft, existing group names should
  be reused, and group creation plus tag assignment should be atomic/transactional so assigned
  groups stick to tags.

- All 24 locales now have genuine, verified translations (not just present keys) — see §3. New UI
  work will still need translations added to all 24 files going forward; keep the reuse-before-create
  discipline (§4) so that stays manageable.
- Translation *quality* was fixed by AI-generated translations, not native speakers. If the repo
  owner or a native speaker later flags specific phrasing as wrong/unnatural in any of the 24
  locales, that's a spot-fix to the specific `values-<code>/strings.xml` entries, not a full redo.
- No outstanding hardcoded-string work — the pass is done short of `DemoData.kt`.
- Nothing else is currently flagged as in-progress or blocked. Check `git status` and `git log`
  against this file's commit list above to confirm nothing has moved since this was written.
