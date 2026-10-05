# YATA — Translation Handoff Package

For an LLM (or human) picking up localization work with no other context on this repo.

## 1. What this app is

YATA ("Yet Another Task App", pronounced **"YAH-tuh"** — *ya* as in *yard*, *ta* as in *factory*) is an Android task manager, Kotlin + Jetpack Compose. Package id `com.mj.yata`. Repo root: `D:\AntiGravity\yata`.

## 2. Where translations live

```
app/src/main/res/
  values/strings.xml          <- SOURCE OF TRUTH. English (en-US). Never mark this "translated".
  values-es/strings.xml       <- Spanish
  values-fr/strings.xml       <- French
  values-pt/strings.xml       <- Portuguese
  values-de/strings.xml       <- German
  values-it/strings.xml       <- Italian
  values-nl/strings.xml       <- Dutch
  values-id/strings.xml       <- Indonesian
  values-tr/strings.xml       <- Turkish
  values-vi/strings.xml       <- Vietnamese
  values-tl/strings.xml       <- Tagalog / Filipino
  values-pl/strings.xml       <- Polish
  values-sv/strings.xml       <- Swedish
  values-ro/strings.xml       <- Romanian
  values-cs/strings.xml       <- Czech
  values-sw/strings.xml       <- Swahili
  values-hi/strings.xml       <- Hindi
  values-bn/strings.xml       <- Bengali
  values-mr/strings.xml       <- Marathi
  values-te/strings.xml       <- Telugu
  values-ta/strings.xml       <- Tamil
  values-gu/strings.xml       <- Gujarati
  values-kn/strings.xml       <- Kannada
  values-ml/strings.xml       <- Malayalam
  values-pa/strings.xml       <- Punjabi
```

Twenty-four localized folders exist today. `app/src/main/res/resources.properties` declares `unqualifiedResLocale=en-US`, telling the build system the unqualified `values/` folder *is* the English source (not "the fallback for everything").

`androidResources.generateLocaleConfig = true` in `app/build.gradle.kts` means the Android per-app language picker is generated automatically from whichever `values-<code>/` folders exist. **You never need to register a new locale anywhere else** — just create the folder and file, matching the pattern above, and it's picked up.

## 3. Current state (as of this handoff)

Base `values/strings.xml`: **1148 `<string>` entries + 28 `<plurals>` blocks** (1176 total translatable resources).

All 24 supported locales (`es`, `fr`, `pt`, `de`, `it`, `nl`, `id`, `tr`, `vi`, `tl`, `pl`, `sv`, `ro`, `cs`, `sw`, `hi`, `bn`, `mr`, `te`, `ta`, `gu`, `kn`, `ml`, `pa`) have **0 missing keys** (every `name=` present in every locale) **and** — as of the most recent pass — genuine translated *values*, not just present keys. See §3a for why that distinction matters and got a dedicated fix.

**The regeneration script in §7 can be used to re-verify or regenerate missing keys whenever new features add strings to `values/strings.xml`.** It only checks key presence — see §3a for a value-level check.

## 3a. Important: "0 missing keys" is not the same as "translated" — read this before trusting any completeness claim

An earlier pass claimed all 24 locales were "100% complete" based solely on the §7 script, which only checks whether a `name=` key exists in each locale file — not whether its *value* differs from the English source. That earlier pass had actually only added the *keys* (copying the English text as a placeholder value) for 21 of the 24 locales, then never came back to translate them. A later audit found:

- `es`, `fr`, `pt` — genuinely translated (~15-19% of entries incidentally identical to English, mostly brand names/technical terms — normal).
- `de`, `hi`, `it`, `nl` — ~75% of entries were still verbatim English.
- The other 17 locales (`bn`, `cs`, `gu`, `id`, `kn`, `ml`, `mr`, `pa`, `pl`, `ro`, `sv`, `sw`, `ta`, `te`, `tl`, `tr`, `vi`) — ~98% of entries were still verbatim English. The app in those locales was, in practice, still almost entirely English with a language tag on the folder.

This was fixed by retranslating all 21 affected locales (see `HANDOFF.md` for the session-by-session detail). **If you're asked to verify or extend translation coverage, don't trust a "0 missing keys" report by itself.** Spot-check actual values, or run this value-diff check (counts entries whose value is suspiciously identical to English — a translated file should show a small number here, mostly brand names/technical tokens like `YATA`, `SFTP`, `#RRGGBB`, not hundreds):

```bash
python3 - <<'PYEOF'
import re, glob

def extract(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    return dict(re.findall(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', content, re.S))

base = extract("app/src/main/res/values/strings.xml")
for path in sorted(glob.glob("app/src/main/res/values-*/strings.xml")):
    loc = path.split("values-")[1].split("/")[0]
    if loc in ("night", "night-v31", "v31"): continue
    locd = extract(path)
    same = [k for k in locd if k in base and locd[k].strip() == base[k].strip() and len(base[k].strip()) > 3]
    flag = "  <-- LIKELY UNTRANSLATED" if len(same) > 100 else ""
    print(f"{loc:4}: {len(same):4} entries identical to English{flag}")
PYEOF
```

## 4. The job for new UI strings

When new UI features add new `<string>` or `<plurals>` entries to the base `values/strings.xml`:

1. Run the script in §7 to update `missing_translation_keys.txt`.
2. For every `<string name="...">English text</string>` line, add a matching line to all `values-<code>/strings.xml` files with the same `name` and translated `value`.
3. For every `<plurals name="...">...</plurals>` block, add the same block with translated `<item quantity="...">` text. **Do not drop or add quantity categories** — match exactly what the English source has for that key (typically `one`/`other`; check each block, don't assume).
4. Appending new entries before `</resources>` closing tag is fine.

## 5. Hard rules — read before starting

- **App Name Pronunciation**: YATA is pronounced **"YAH-tuh"** (*ya* as in *yard*, *ta* as in *factory*). In Indic scripts (Devanagari, Bengali, Telugu, Tamil, etc.), phonetic transliteration matches this pronunciation (e.g. Hindi/Marathi: **याटा**, Bengali: **যাটা**, Telugu: **యాటా**, Tamil: **யாடா**).
- **Never translate the `name="..."` attribute.** Only the text between `<string name="x">` and `</string>` changes. The `name` is the programmatic key; changing it breaks the build (missing resource) with no compile-time error until runtime.
- **Preserve every placeholder exactly**, including its position marker: `%1$s`, `%2$d`, `%1$s` etc. These are positional (the `1$`/`2$` part), which lets a translation reorder them relative to English *if the target language's grammar requires it* — e.g. English `"%1$s of %2$s completed"` could become `"%2$s completado, %1$s en total"` in a language that wants that order, as long as both placeholders still appear once each with matching types (`s`=string, `d`=integer — don't swap `%1$s` for `%1$d` or vice versa).
- **Escape apostrophes as `\'`** inside string values — Android's XML string format requires this (a literal `'` inside an unescaped string is a build error under some lint configs, and silently mis-renders under others). Example from the existing base file: `Tasks here never show on the Today screen... you\'ll schedule later.`
- **`&gt;`/`&lt;`/`&amp;`** — a few strings contain literal `>` `<` `&` and are XML-escaped in the source (e.g. `1. Open GitHub &gt; Settings &gt; ...`). Keep the same escaping in translations.
- **`<plurals>` quantity rules are per-language.** All existing locale files currently use `one`/`other` — same two categories as the English source. Use `one`/`other` for new plurals too.
- **Emoji and literal symbols stay as-is** unless there's a genuine cultural-localization reason to change them (e.g. `🇮🇳` flag in `settings_about_made_in` — that's a factual "made in India" credit).
- **Brand/product name "yata"/"YATA"** — preserve capitalization or phonetic transliteration per language convention.
- **Don't touch `app/src/main/java/com/mj/yata/data/demo/DemoData.kt`.** It contains hardcoded fictional task/person/project names used for demo-mode screenshots. It is explicitly out of scope.
- **Don't touch anything under `app/src/androidTest/` or `app/src/test/`.**
- **Don't touch `values-night/`, `values-night-v31/`, `values-v31/`** — those are dark-mode/API-level color resource folders, unrelated to language.

## 6. Naming convention

Existing keys follow `action_*` (buttons/menu labels), `cd_*` (`contentDescription` for accessibility), and `<feature>_*` (screen/feature-scoped, e.g. `settings_*`, `analytics_*`, `entity_editors_*`, `export_*`, `remote_sync_*`, `voice_task_overlay_*`).

## 7. How to verify and audit missing keys

From the repo root:

```bash
python3 - <<'PYEOF'
import re, glob

def extract(path):
    with open(path, encoding="utf-8") as f:
        content = f.read()
    strings = dict(re.findall(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', content, re.S))
    plurals = {}
    for m in re.finditer(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', content, re.S):
        plurals[m.group(1)] = m.group(2).strip()
    return strings, plurals

base_s, base_p = extract("app/src/main/res/values/strings.xml")
locales = [f.split("values-")[-1].split("/")[0] for f in glob.glob("app/src/main/res/values-*/strings.xml")]

all_missing = {}
for loc in locales:
    loc_s, loc_p = extract(f"app/src/main/res/values-{loc}/strings.xml")
    m_s = {k: v for k, v in base_s.items() if k not in loc_s}
    m_p = {k: v for k, v in base_p.items() if k not in loc_p}
    print(f"Locale {loc:4}: missing {len(m_s)} strings, {len(m_p)} plurals")
    if m_s or m_p:
        all_missing[loc] = (m_s, m_p)

with open("missing_translation_keys.txt", "w", encoding="utf-8") as out:
    if not all_missing:
        out.write("=== ALL 24 LOCALES ARE 100% COMPLETE (0 MISSING KEYS) ===\n")
    else:
        for loc, (m_s, m_p) in all_missing.items():
            out.write(f"=== MISSING FOR {loc} ===\n")
            for k, v in m_s.items():
                out.write(f'<string name="{k}">{v}</string>\n')
            for k, v in m_p.items():
                out.write(f'<plurals name="{k}">\n{v}\n</plurals>\n')
PYEOF
```

## 8. Current Deliverables State

All 24 supported locale files in `app/src/main/res/values-<code>/strings.xml` are 100% populated (0 missing keys) and synchronized with the English base source (`1148` strings + `28` plurals each), **and** — per the fix described in §3a — actually translated, not just key-complete. `./gradlew :app:lintDebug` reports zero `MissingTranslation` issues across all 24 locales. The only remaining lint noise is `MissingQuantity` for `cs`/`pl`/`ro` (those languages have CLDR `few`/`many` plural categories beyond `one`/`other`), which is expected per §5's `one`/`other`-only convention — not a bug.

Translation quality is AI-generated, not native-speaker-reviewed. If a native speaker flags specific phrasing in any locale, that's a targeted fix to the flagged entries, not a reason to distrust the whole file.

## 9. Hardcoded String Extraction Status

The hardcoded string extraction pass is **done** (103 lint hits remain, all either the deliberately-excluded `DemoData.kt` [28] or confirmed non-UI false positives like Compose animation debug labels).
