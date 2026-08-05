# Localization

To add another language:

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<language-code>/strings.xml`.
2. Translate every `<string>` and `<plurals>` entry. Keep placeholders like `%1$s`, `%2$d`, and escaped apostrophes intact.
3. Add search-box aliases in `searchFilterPhrases` in `app/src/main/java/com/mj/yata/ui/screen/search/SearchScreen.kt`.
4. Add quick-add natural-language aliases in `app/src/main/java/com/mj/yata/util/NaturalLanguageParser.kt`.
5. Add parser tests for the new language in `NaturalLanguageParserTest` and `SearchQueryParserTest`.

Quick validation:

```powershell
$base=[xml](Get-Content app/src/main/res/values/strings.xml -Raw)
$lang=[xml](Get-Content app/src/main/res/values-<language-code>/strings.xml -Raw)
$baseNames=@($base.resources.string | % {$_.name}) + @($base.resources.plurals | % {$_.name})
$langNames=@($lang.resources.string | % {$_.name}) + @($lang.resources.plurals | % {$_.name})
$baseNames | ? { $_ -notin $langNames }
```

That command should print nothing. Then run:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
