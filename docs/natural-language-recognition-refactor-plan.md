# Natural Language Recognition Refactor Plan

## Purpose

Make YATA's natural language recognition easier to maintain, safer to extend to more languages, and more reliable while preserving current user-facing behavior.

This plan is written for a follow-on implementation agent. Follow the passes in order. Do not combine major passes unless all validation from earlier passes is already green.

## Current Context

Core files:

- `app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt`
- `app/src/main/java/com/mj/yata/util/ParsedQuickAddApplier.kt`
- `app/src/main/java/com/mj/yata/ui/sheets/NewTaskSheet.kt`
- `app/src/main/java/com/mj/yata/ui/screen/taskdetail/TaskDetailScreen.kt`
- `app/src/test/java/com/mj/yata/NaturalLanguageParserTest.kt`

Current parser responsibilities:

- Extract due date, start date, time, recurrence, reminder, priority, flag, project, list, tags, and assignees from a task title.
- Strip recognized phrases from the saved title.
- Return raw-string highlight ranges for recognized phrases.
- Return typed highlight spans for colored UI highlighting.
- Support English plus localized Latin-script aliases for dates, weekdays, months, recurrence units, and entity keywords.

Known validation issue:

- `:app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest` is currently blocked before parser tests run because `AnalyticsUtilsTest.kt` has unrelated compile errors around delegation/entity analytics signatures.
- `:app:compileDebugKotlin` is the minimum required validation until that blocker is fixed.

## Non-Goals

Do not do these in this refactor:

- Do not replace the rule parser with an ML or network-based system.
- Do not change saved task semantics intentionally.
- Do not remove existing aliases unless tests prove they are broken or unsafe.
- Do not expand into broad native-script support in the same pass as the architecture refactor.
- Do not fix unrelated analytics tests unless the user asks or the parser tests cannot be validated any other way.

## Design Principles

- Preserve behavior first, improve structure second.
- Keep raw input ranges stable for highlighting.
- Keep rule ordering explicit and test-covered.
- Make adding a language mostly a data edit, not a parser-code edit.
- Prefer deterministic rule matching over heuristic guessing.
- Avoid ad hoc regex string concatenation where a small builder can escape and order tokens safely.
- Make overlap and conflict behavior visible in code.

## Target Architecture

The end state should have these concepts:

- `ParsedQuickAdd`: final parse result.
- `QuickAddHighlightSpan`: typed range for UI highlighting.
- `QuickAddHighlightType`: enum for due date, time, recurrence, reminder, priority, flag, project, list, tag, assignee, and other.
- `ClaimTracker`: owns claimed ranges, escaped ranges, strip-only ranges, overlap checks, typed claim recording, preposition expansion, and final sorted spans.
- `ParserContext`: raw input, normalized input if introduced, reference date/time, date order, locale data, and claim tracker.
- `ParserRule`: small unit that tries to recognize one family of phrases and mutate a parse state.
- Rule groups:
  - `RecurrenceRules`
  - `ReminderRules`
  - `TimeRules`
  - `StartDateRules`
  - `DueDateRules`
  - `PriorityFlagRules`
  - `EntityRules`
- Language data:
  - Weekday aliases
  - Month aliases
  - Relative day aliases
  - Time-of-day aliases
  - Recurrence keywords and units
  - Priority and flag phrases
  - Entity keywords

## Pass 0: Safety Snapshot

Objective:

Capture current behavior before moving code.

Files to edit:

- `app/src/test/java/com/mj/yata/NaturalLanguageParserTest.kt`

Steps:

1. Add focused tests for representative existing behavior.
2. Cover at least these inputs:
   - `tomorrow 3pm buy milk`
   - `every monday gym`
   - `every 2 weeks haircut`
   - `remind me 30 min before tomorrow call`
   - `starts monday review plan`
   - `+Client =Backlog #urgent @Jane tomorrow 3pm !1 review`
   - Localized Latin examples already supported, such as `morgen`, `domani`, `senin`, `ottobre`, `kasim`, `wrzesnia`.
3. Assert:
   - `title`
   - `due`
   - `startDate`
   - `time`
   - `recurrence`
   - `reminder`
   - `priority`
   - `flag`
   - `projectName`, `listName`, `tagNames`, `assigneeNames`
   - `highlightRanges`
   - `highlightSpans`
4. If unit tests are blocked by `AnalyticsUtilsTest.kt`, still commit the tests only after `:app:compileDebugKotlin` passes. Mention the blocker in the handoff.

Acceptance criteria:

- App Kotlin compile passes.
- Tests compile as far as the unrelated analytics blocker allows.
- The new tests document current behavior without requiring parser changes.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest --console=plain
git diff --check
```

## Pass 1: Introduce ClaimTracker

Objective:

Move range ownership and typed highlights out of scattered local lists.

Files to edit:

- `app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt`
- Optional new file: `app/src/main/java/com/mj/yata/util/NaturalLanguageClaims.kt`

Steps:

1. Create `ClaimTracker`.
2. Give it:
   - `claim(range: IntRange, type: QuickAddHighlightType)`
   - `claimOther(range: IntRange)`
   - `addEscape(rawBackslashRange: IntRange, protectedRange: IntRange)`
   - `addStripOnly(range: IntRange)`
   - `isFree(range: IntRange): Boolean`
   - `firstFreeMatch(regex: Regex, raw: String): MatchResult?`
   - `firstFreeWord(word: String, raw: String): MatchResult?`
   - `expandedSpans(raw: String, prepositionRegex: Regex): List<QuickAddHighlightSpan>`
   - `stripRanges(raw: String, prepositionRegex: Regex): List<IntRange>`
3. Replace local `claimed`, `escapedRanges`, `stripOnly`, `claim`, `isFree`, `firstFreeMatch`, and `firstFreeWord` with `ClaimTracker`.
4. Keep behavior identical.
5. At first, allow untyped claims to use `QuickAddHighlightType.Other`.

Acceptance criteria:

- No visible behavior changes.
- Existing `highlightRanges` are unchanged for representative tests.
- `highlightSpans.map { it.range } == highlightRanges`.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Risk notes:

- Preposition expansion currently happens late. Preserve that exact timing.
- Escaped ranges must never become highlight spans.
- Strip-only backslashes must still be removed from the saved title.

## Pass 2: Type Claims At Source

Objective:

Remove fragile end-of-parse type inference by labeling every rule when it claims text.

Files to edit:

- `NaturalLanguageParser.kt`
- `NaturalLanguageParserTest.kt`

Steps:

1. Replace recurrence claims with `QuickAddHighlightType.Recurrence`.
2. Replace recurrence end claims, such as `until dec 20` and `for 10 times`, with `Recurrence`.
3. Replace reminder claims with `Reminder`.
4. Replace time claims with `Time`.
5. Replace start-date claims with `StartDate`.
6. Replace due-date claims with `DueDate`.
7. Replace priority claims with `Priority`.
8. Replace flag claims with `Flag`.
9. Replace project/list/tag/assignee claims with their entity types.
10. Remove or greatly reduce end-of-parse type inference once all source claims are typed.
11. Keep `QuickAddHighlightType.Other` only for truly generic cleanup claims.

Acceptance criteria:

- Mixed phrases produce the expected span type for every recognized token.
- All existing highlight-range tests still pass.
- The UI no longer depends on guessing from raw text.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest --console=plain
git diff --check
```

Risk notes:

- Some phrases set more than one field, for example monthly recurrence on a date also sets a due date. The span should usually be `Recurrence` because the phrase describes the repeating rule.
- `starts monday` should be `StartDate`, not `DueDate`, even though the nested parse resolves a due date internally.
- `remind me tomorrow` may contain a due-date phrase and a reminder keyword. Keep the current stripping behavior, but type the final claimed span according to the dominant behavior already used by the parser.

## Pass 3: Extract Shared Highlight UI

Objective:

Stop duplicating quick-add highlight rendering in the new-task and edit-task fields.

Files to edit:

- New file suggested: `app/src/main/java/com/mj/yata/ui/widgets/QuickAddHighlightTransformation.kt`
- `NewTaskSheet.kt`
- `TaskDetailScreen.kt`

Steps:

1. Create a composable helper:

```kotlin
@Composable
fun rememberQuickAddHighlightTransformation(
    spans: List<QuickAddHighlightSpan>,
    enabled: Boolean
): VisualTransformation
```

2. Move the type-to-color mapping into this helper.
3. Use Material colors where possible, but keep categories visually distinct.
4. Return `VisualTransformation.None` or identity transformed text when disabled or spans are empty.
5. Replace inline visual transformation code in:
   - `NewTaskSheet`
   - `TaskDetailScreen`
6. Keep the chip-like style:
   - colored text
   - bold font
   - same color background with low alpha
   - identity offset mapping

Acceptance criteria:

- New-task and edit-task highlighting look and behave the same.
- Adding a highlight type requires editing one UI color map only.
- Cursor position is unchanged because offset mapping remains identity.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

## Pass 4: Create Regex Builders

Objective:

Make aliases safe and predictable by centralizing escaping, sorting, and word-boundary handling.

Files to edit:

- New file suggested: `app/src/main/java/com/mj/yata/util/NaturalLanguageRegex.kt`
- `NaturalLanguageParser.kt`

Steps:

1. Add helpers:

```kotlin
fun alternationOf(tokens: Collection<String>): String
fun wordRegex(token: String): Regex
fun wordAlternationRegex(tokens: Collection<String>): Regex
```

2. `alternationOf` must:
   - remove blanks
   - de-duplicate case-insensitively
   - sort longest first
   - apply `Regex.escape`
3. Replace direct `keys.joinToString("|")` use with the helper.
4. Replace manually built alternations where safe.
5. Do not change complex capture regexes in this pass unless they are clearly pure token lists.

Acceptance criteria:

- Multi-word aliases, such as Vietnamese month names, still match correctly.
- Short tokens do not win before long tokens.
- Regex metacharacters inside aliases cannot accidentally change parser behavior.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Risk notes:

- Some current regex constants intentionally include regex syntax, such as optional plurals. Do not pass those through `Regex.escape`.
- Only use the helper for literal alias tokens.

## Pass 5: Move Language Data Into Data Objects

Objective:

Make adding/editing languages a data change.

Files to add:

- `app/src/main/java/com/mj/yata/util/nl/NaturalLanguageLexicon.kt`
- `app/src/main/java/com/mj/yata/util/nl/LanguagePack.kt`
- `app/src/main/java/com/mj/yata/util/nl/LatinLanguagePacks.kt`

Suggested model:

```kotlin
data class LanguagePack(
    val id: String,
    val weekdays: Map<String, DayOfWeek> = emptyMap(),
    val months: Map<String, Int> = emptyMap(),
    val relativeDays: Map<String, RelativeDay> = emptyMap(),
    val recurrenceEveryWords: Set<String> = emptySet(),
    val dayUnits: Set<String> = emptySet(),
    val weekUnits: Set<String> = emptySet(),
    val monthUnits: Set<String> = emptySet(),
    val yearUnits: Set<String> = emptySet(),
    val priorityPhrases: List<Pair<String, String>> = emptyList(),
    val flagPhrases: Set<String> = emptySet()
)
```

Steps:

1. Create English as the baseline pack.
2. Move existing Spanish, Portuguese, French data into packs.
3. Move added Latin-script language data into packs.
4. Create `NaturalLanguageLexicon` that merges packs into:
   - `weekdayNames`
   - `monthNames`
   - `bareDateWords`
   - recurrence unit sets
   - priority phrase list
   - flag phrase set
5. Preserve current duplicate aliases by de-duplicating only when values agree.
6. If two languages use the same token with conflicting meaning, add it to a conflict list and handle explicitly.

Acceptance criteria:

- Adding a new weekday/month alias does not require editing parser logic.
- Existing localized tests still pass.
- Conflict handling is documented in code comments.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Risk notes:

- Tokens like `morgen` can mean different things in different languages. If the current behavior already chooses one meaning, preserve it and document the conflict.
- Some aliases are voice fallbacks rather than formal spellings. Keep them, but mark them as speech-friendly aliases in comments or data grouping.

## Pass 6: Split Rule Groups

Objective:

Reduce `NaturalLanguageParser.kt` size and make rule ownership clearer.

Files to add:

- `app/src/main/java/com/mj/yata/util/nl/ParserContext.kt`
- `app/src/main/java/com/mj/yata/util/nl/ParseState.kt`
- `app/src/main/java/com/mj/yata/util/nl/RecurrenceRules.kt`
- `app/src/main/java/com/mj/yata/util/nl/ReminderRules.kt`
- `app/src/main/java/com/mj/yata/util/nl/TimeRules.kt`
- `app/src/main/java/com/mj/yata/util/nl/DateRules.kt`
- `app/src/main/java/com/mj/yata/util/nl/PriorityFlagRules.kt`
- `app/src/main/java/com/mj/yata/util/nl/EntityRules.kt`

Steps:

1. Create `ParseState` holding mutable parse outputs:
   - due
   - dueRange
   - startDate
   - time
   - recurrence
   - reminder
   - priority
   - flag
   - project/list/tag/assignee data
2. Create `ParserContext` holding:
   - raw input
   - reference date
   - reference time
   - day-first setting
   - lexicon
   - claim tracker
3. Move one rule group at a time.
4. Preserve the top-level order exactly:
   - escape setup
   - recurrence
   - recurrence ending
   - reminder
   - time
   - start date
   - due date
   - fallback time-of-day
   - priority
   - flag
   - entities
   - title cleanup
5. After moving each group, compile before moving the next.

Acceptance criteria:

- `NaturalLanguageParser.parse` reads as an orchestration function.
- Each rule group has a clear public `apply(context, state)` or similar entry point.
- No rule group directly edits UI or task models.

Validation after each group:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Risk notes:

- Do not reorder rules unless there is a failing test that proves the current order is wrong.
- Recurrence and due date rules intentionally interact. Move them carefully.

## Pass 7: Normalize Input With Raw Range Mapping

Objective:

Improve voice and multilingual robustness without breaking highlight ranges.

Only start this pass after previous passes are stable.

Files to add:

- `app/src/main/java/com/mj/yata/util/nl/NormalizedInput.kt`

Steps:

1. Create a `NormalizedInput` model:

```kotlin
data class NormalizedInput(
    val raw: String,
    val normalized: String,
    val normalizedIndexToRawIndex: IntArray
)
```

2. Centralize:
   - whitespace collapse
   - common voice substitutions, such as `to day` to `today`
   - accent folding where safe
   - punctuation normalization
3. Keep all final highlight ranges in raw coordinates.
4. Convert normalized match ranges back to raw ranges through the index map.
5. Start by using normalized matching only for low-risk alias lookup. Do not convert every regex at once.

Acceptance criteria:

- Existing highlights still point to exact raw substrings.
- Accented and unaccented aliases both match where expected.
- Voice substitutions still behave as before.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Risk notes:

- This is the highest-risk pass because it can break cursor highlighting.
- Do not normalize destructively unless you can map every matched character back to raw text.

## Pass 8: Language-Pack Contract Tests

Objective:

Make future language edits safe.

Files to add:

- `app/src/test/java/com/mj/yata/NaturalLanguageLexiconTest.kt`

Steps:

1. Test every language pack has:
   - unique `id`
   - valid weekday values
   - month numbers in `1..12`
   - no blank aliases
   - no unescaped regex assumptions in literal alias lists
2. Test merged lexicon:
   - longest aliases sort before shorter aliases
   - duplicate aliases either map to the same value or are listed as known conflicts
3. Add example parse tests for each supported language:
   - one relative day
   - one weekday
   - one month date
   - one recurrence unit

Acceptance criteria:

- A future language addition fails tests if it is malformed.
- Conflicts are explicit rather than accidental.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageLexiconTest --console=plain
git diff --check
```

## Pass 9: Parser Reliability Tests

Objective:

Catch regressions in overlap handling, escaping, malformed input, and performance.

Files to edit:

- `NaturalLanguageParserTest.kt`

Steps:

1. Add overlap tests:
   - `every monday` should not also create a one-off due date from `monday`.
   - `starts monday` should not set due date.
   - `remind me 30 min before tomorrow` should keep reminder and due date distinct.
2. Add escape tests:
   - `\today`
   - `every \monday`
   - escaped entity tokens.
3. Add malformed input tests:
   - many `#`, `@`, `+`, `=`
   - repeated keywords
   - very long plain text
   - invalid dates
4. Add performance guard:
   - parse a 500-word string under a reasonable threshold.
   - Avoid flaky timing thresholds on CI; prefer a broad threshold or repeated local-only benchmark if the project already has one.

Acceptance criteria:

- Parser does not throw for malformed input.
- Parser does not produce overlapping highlight spans.
- Escaped phrases stay literal and unhighlighted.

Validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest --console=plain
git diff --check
```

## Pass 10: Documentation For Adding A Language

Objective:

Make future language additions straightforward.

Files to add or edit:

- `docs/natural-language-recognition.md`

Include:

1. Where language packs live.
2. How to add:
   - weekdays
   - months
   - relative days
   - recurrence units
   - priority phrases
   - voice-friendly aliases
3. How to handle accent variants.
4. How to handle alias conflicts.
5. Required tests for a new language.
6. Validation commands.

Acceptance criteria:

- A developer can add a Latin-script language by editing one language-pack file and one test file.
- The docs mention known ambiguity examples.

## Implementation Checklist

Use this checklist across all passes:

- Before editing, run `git status --short`.
- Do not revert unrelated changes.
- Keep parser behavior unchanged unless a test and user request justify the change.
- Add or update tests in the same pass as parser behavior changes.
- Run `:app:compileDebugKotlin` after every pass.
- Run parser tests when the analytics test compile blocker is fixed.
- Run `git diff --check` before handoff.
- Mention any blocked validation clearly.

## Suggested Commit Boundaries

Use one commit per pass when possible:

1. `test: capture natural language parser behavior`
2. `refactor: introduce quick add claim tracker`
3. `refactor: type natural language highlight claims`
4. `refactor: share quick add highlight transformation`
5. `refactor: add natural language regex builders`
6. `refactor: move natural language aliases into language packs`
7. `refactor: split natural language parser rule groups`
8. `feat: normalize natural language input with raw range mapping`
9. `test: add natural language lexicon contracts`
10. `docs: document natural language language packs`

## Final Acceptance Criteria

The refactor is complete when:

- New task and edit task screens still recognize and highlight the same phrases.
- Highlight colors are typed and consistent across screens.
- Adding a Latin-script language is mostly a data-pack change.
- Parser rule order is documented and encoded in orchestration.
- Claim overlap behavior is centralized.
- Escaped phrases remain stable.
- Existing parser behavior is covered by tests.
- `:app:compileDebugKotlin` passes.
- Parser unit tests pass once unrelated analytics test compilation is repaired.
