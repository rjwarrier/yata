# YATA — Translation Handoff Package

For an LLM (or human) picking up localization work with no other context on this repo.

## 1. What this app is

YATA ("Yet Another Task App") is an Android task manager, Kotlin + Jetpack Compose. Package id `com.mj.yata`. Repo root: `D:\AntiGravity\yata`.

## 2. Where translations live

```
app/src/main/res/
  values/strings.xml          <- SOURCE OF TRUTH. English (en-US). Never mark this "translated".
  values-es/strings.xml       <- Spanish
  values-fr/strings.xml       <- French
  values-pt/strings.xml       <- Portuguese
```

That's it — three locales exist today. No other `values-<code>/` folders. `app/src/main/res/resources.properties` declares `unqualifiedResLocale=en-US`, telling the build system the unqualified `values/` folder *is* the English source (not "the fallback for everything").

`androidResources.generateLocaleConfig = true` in `app/build.gradle.kts` means the Android per-app language picker is generated automatically from whichever `values-<code>/` folders exist. **You never need to register a new locale anywhere else** — just create the folder and file, matching the pattern above, and it's picked up.

## 3. Current state (as of this handoff)

Base `values/strings.xml`: **1148 `<string>` entries + 28 `<plurals>` blocks** (1176 total translatable resources).

Each of `es`, `fr`, `pt` is missing the **same 350 strings and 10 plurals** — these are recent additions (an ongoing hardcoded-string-extraction pass has pulled ~200 literal strings out of Kotlin UI code into `strings.xml` across several sessions; the translated locale files were never updated to match). No stale/orphaned keys exist in the translated files (nothing to *remove*), only things to *add*. The gap is still growing — extraction is not finished (see §9) — so if you're picking this up some time after this handoff was written, **regenerate the diff before starting** (§7 has the one-liner) rather than trusting these exact counts.

**The exact list of missing keys, with their English source values, is in `missing_translation_keys.txt` next to this file** — that's the actual work list. It's organized as two sections: `<string>` entries to add, then `<plurals>` blocks to add. Every key in that file needs a corresponding entry added to **all three** locale files (`values-es`, `values-fr`, `values-pt`) with translated values.

## 4. The job

For each of the three locale files:

1. Open `missing_translation_keys.txt`.
2. For every `<string name="...">English text</string>` line, add a matching line to `values-{es,fr,pt}/strings.xml` with the same `name`, translated `value`.
3. For every `<plurals name="...">...</plurals>` block, add the same block with translated `<item quantity="...">` text. **Do not drop or add quantity categories** — match exactly what the English source has for that key (typically `one`/`other`; check each block, don't assume).
4. Insertion position within the file doesn't matter functionally (Android resolves by `name`, not order) — existing files are *roughly* grouped by feature but not strictly sorted. Appending new entries in a sensible spot (or in one contiguous block at the end) is fine; don't reorder existing entries.

## 5. Hard rules — read before starting

- **Never translate the `name="..."` attribute.** Only the text between `<string name="x">` and `</string>` changes. The `name` is the programmatic key; changing it breaks the build (missing resource) with no compile-time error until runtime.
- **Preserve every placeholder exactly**, including its position marker: `%1$s`, `%2$d`, `%1$s` etc. These are positional (the `1$`/`2$` part), which lets a translation reorder them relative to English *if the target language's grammar requires it* — e.g. English `"%1$s of %2$s completed"` could become `"%2$s completado, %1$s en total"` in a language that wants that order, as long as both placeholders still appear once each with matching types (`s`=string, `d`=integer — don't swap `%1$s` for `%1$d` or vice versa).
- **Escape apostrophes as `\'`** inside string values — Android's XML string format requires this (a literal `'` inside an unescaped string is a build error under some lint configs, and silently mis-renders under others). Example from the existing base file: `Tasks here never show on the Today screen... you\'ll schedule later.` Look at how the existing `es`/`fr`/`pt` files already handle this (they do it correctly throughout) and match that pattern.
- **`&gt;`/`&lt;`/`&amp;`** — a few strings contain literal `>` `<` `&` and are XML-escaped in the source (e.g. `1. Open GitHub &gt; Settings &gt; ...`). Keep the same escaping in translations; don't unescape to a raw `>` because it happens to render the same in most cases — stay consistent with the source file's convention.
- **`<plurals>` quantity rules are per-language.** Checked all three existing locale files: `es`, `fr`, and `pt` currently only ever use `one`/`other` (verified via `grep quantity= values-fr/strings.xml` etc.) — same two categories as the English source. Use `one`/`other` for the new plurals too; don't introduce a category (e.g. `zero`, `many`) that isn't already present anywhere in that locale's file.
- **Emoji and literal symbols stay as-is** unless there's a genuine cultural-localization reason to change them (e.g. `🇮🇳` flag in `settings_about_made_in` — that's a factual "made in India" credit, not a translatable phrase in the usual sense; the surrounding words translate, the flag emoji doesn't change).
- **Brand/product name "yata"/"YATA" is deliberately inconsistent in casing across contexts** (lowercase wordmark in the About card via `app_name`, uppercase `"YATA"` in export-card branding via `export_yata_wordmark`) — this is intentional visual design, not a typo. Don't "fix" the casing; translate any *surrounding* words only, and check existing `values-fr/strings.xml` / `values-pt/strings.xml` for `app_name` — they already deliberately keep it as `"YATA"` (capitalized) in fr/pt while `es` keeps it lowercase `"yata"`, matching what each locale's translator apparently decided. Follow suit for consistency within each locale file rather than picking one style for all three.
- **Don't touch `app/src/main/java/com/mj/yata/data/demo/DemoData.kt`.** It contains hardcoded fictional task/person/project names used for demo-mode screenshots. It is explicitly out of scope — not part of this translation task, and the repo owner has asked for it to be left alone entirely (personal use, not app UI).
- **Don't touch anything under `app/src/androidTest/` or `app/src/test/`.** Not localizable content.
- **Don't touch `values-night/`, `values-night-v31/`, `values-v31/`** — those are dark-mode/API-level color resource folders, unrelated to language.

## 6. Naming convention (for context, not required reading to do the task)

Existing keys follow `action_*` (buttons/menu labels), `cd_*` (`contentDescription` for accessibility), and `<feature>_*` (screen/feature-scoped, e.g. `settings_*`, `analytics_*`, `entity_editors_*`, `export_*`, `remote_sync_*`, `voice_task_overlay_*`). You won't be creating new keys for this task — just filling in translations for keys that already exist in the English source — so this is background only.

## 7. How to verify the work

From the repo root (Windows, PowerShell or the Bash-compatible shell already in use for this repo):

```bash
cd D:/AntiGravity/yata
./gradlew :app:lintDebug
```

`MissingTranslation` and `MissingQuantity` lint checks are **enabled** (not suppressed) in `app/build.gradle.kts` — once a second locale exists (which it does), any string/plural present in the base file but absent from a `values-<code>/` file gets flagged. Running this after finishing a locale is the fastest way to confirm nothing was missed — it should report zero `MissingTranslation`/`MissingQuantity` issues in `values-es`, `values-fr`, `values-pt` when done. (It may report unrelated pre-existing lint findings in other categories — those aren't part of this task, ignore them unless they're specifically about these three locale files.)

There's also a debug-only pseudolocale mode (`isPseudoLocalesEnabled`) that adds `en-XA` (accented/padded, to catch clipped layouts from longer translated text) and `ar-XB` (right-to-left) test locales automatically — no translation content needed for those, they're generated from English at build time. Not part of this task, just context on how translation-readiness gets tested elsewhere in this project.

If you want to regenerate `missing_translation_keys.txt` yourself rather than trust the copy handed to you (recommended if any time has passed, per §3) — from the repo root:

```bash
python3 - <<'PYEOF'
import re

def extract(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    strings = dict(re.findall(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', content, re.S))
    plurals = {}
    for m in re.finditer(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', content, re.S):
        plurals[m.group(1)] = m.group(2).strip()
    return strings, plurals

base_s, base_p = extract("app/src/main/res/values/strings.xml")
es_s, es_p = extract("app/src/main/res/values-es/strings.xml")
missing_s = {k: v for k, v in base_s.items() if k not in es_s}
missing_p = {k: v for k, v in base_p.items() if k not in es_p}
print(f"Missing: {len(missing_s)} strings, {len(missing_p)} plurals")

with open("missing_translation_keys.txt", "w", encoding="utf-8") as out:
    out.write("=== MISSING <string> ENTRIES ===\n\n")
    for k, v in missing_s.items():
        out.write(f'<string name="{k}">{v}</string>\n')
    out.write("\n=== MISSING <plurals> ENTRIES ===\n\n")
    for k, v in missing_p.items():
        out.write(f'<plurals name="{k}">\n{v}\n</plurals>\n\n')
PYEOF
```

This assumes `es`/`fr`/`pt` still have identical missing-key sets (true as of this handoff — verify with the same script swapping `values-es` for `values-fr`/`values-pt` if you want to be sure before relying on one shared list for all three).

## 8. Deliverable

Three modified files:

- `app/src/main/res/values-es/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-pt/strings.xml`

Each with the strings + plurals from `missing_translation_keys.txt` (350 strings + 10 plurals as of this handoff — regenerate to confirm, see §7) added, translated, following the rules in §5. No other files should change.

## 9. Context: this is a moving target

Separately from translation, another pass is incrementally extracting hardcoded string literals out of Kotlin Compose UI code and into `values/strings.xml` (tracked via `./gradlew :app:lintHardcodedStrings`). That pass is now effectively **done** — 103 hits remain, all either the deliberately-excluded `DemoData.kt` (28) or confirmed non-UI false positives (`animateFloatAsState`/`AnimatedContent` debug `label=` params, internal validation labels in `JsonExporter.kt`, a dynamic `@`-prefix concatenation in `ExportChips.kt` that carries no literal text). Every string that pass added to the English source became a new translation gap the moment it landed — that's what happened between this handoff's first draft (284 missing) and this revision (350 missing), with zero translation work done in between. With extraction now settled, the gap should stop growing from that source; the remaining variable is translation work itself.

**Practical implication:** if extraction work is still landing in parallel with translation work, `missing_translation_keys.txt` can go stale within the same day. Regenerate it (§7) immediately before starting a translation session rather than assuming the copy you were handed is current — and if you're doing both jobs across sessions, translate *after* a given extraction pass settles, not concurrently with it, to avoid translating a list that's already grown.
