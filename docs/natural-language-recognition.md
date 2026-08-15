# Natural Language Recognition

YATA's quick-add parser is rule based. It extracts schedule fields and entities from task titles, strips recognized phrases from the saved title, and returns typed highlight spans for the UI.

## Main Files

- Parser orchestration: `app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt`
- Claim tracking: `app/src/main/java/com/mj/yata/util/NaturalLanguageClaims.kt`
- Regex helpers: `app/src/main/java/com/mj/yata/util/NaturalLanguageRegex.kt`
- Language packs: `app/src/main/java/com/mj/yata/util/nl/LanguagePack.kt`
- Merged lexicon: `app/src/main/java/com/mj/yata/util/nl/NaturalLanguageLexicon.kt`
- Input normalization: `app/src/main/java/com/mj/yata/util/nl/NaturalLanguageInputNormalizer.kt`
- Title cleanup: `app/src/main/java/com/mj/yata/util/nl/NaturalLanguageTitleCleaner.kt`
- Shared highlight UI: `app/src/main/java/com/mj/yata/ui/widgets/QuickAddHighlightTransformation.kt`

## Adding A Latin-Script Language

Prefer adding aliases in `NaturalLanguageLexicon.kt` through a `LanguagePack`. Avoid editing parser regexes unless the language needs a new grammar shape.

Add:

- `weekdays`: aliases to `DayOfWeek`
- `months`: aliases to month numbers `1..12`
- `relativeDateWords`: words such as today, tomorrow, yesterday

Keep aliases literal. Regex escaping and longest-first ordering are handled by `literalAlternation`.

## Accent And Voice Aliases

Include both accented and ASCII forms when users may type or dictate either form.

Examples:

- `kasım` and `kasim`
- `września` and `wrzesnia`
- `tháng mười một` can be represented by a Latin fallback such as `thang muoi mot`

Voice-friendly aliases are allowed, but keep them close to the language pack entry they support.

## Ambiguity

Some aliases conflict across languages. Preserve existing behavior unless product direction says otherwise.

Examples:

- `morgen` is tomorrow in German and Dutch, but morning in other contexts.
- Short tokens such as `mar` can mean March or Tuesday depending on language.

When adding a conflicting alias, add a test documenting the chosen behavior.

## Highlight Types

Parser rules should claim spans with the correct `QuickAddHighlightType` at the rule site:

- `DueDate`
- `StartDate`
- `Time`
- `Recurrence`
- `Reminder`
- `Priority`
- `Flag`
- `Project`
- `List`
- `Tag`
- `Assignee`
- `Other`

Do not add end-of-parse guessing for highlight colors. The rule that recognizes text should own its type.

## Required Tests

For every new language, add coverage for at least:

- One relative date
- One weekday
- One month date
- One recurrence phrase if recurrence words/units were added
- Any known ambiguous alias

Use:

- `app/src/test/java/com/mj/yata/NaturalLanguageParserTest.kt`
- `app/src/test/java/com/mj/yata/NaturalLanguageLexiconTest.kt`

## Validation

Minimum validation:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
```

Parser tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageParserTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests com.mj.yata.NaturalLanguageLexiconTest --console=plain
```

At the time this guide was added, the targeted unit-test commands were blocked by unrelated compile errors in `AnalyticsUtilsTest.kt`. Once those are fixed, run the parser and lexicon tests for every natural-language change.
