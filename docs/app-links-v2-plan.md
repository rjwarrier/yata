# App Links v2 — Shortening Plan

Follow-up to `docs/app-links-handoff.md`. Goal: make `yata://import/tasks` links markedly
shorter without narrowing what they can carry. Nothing here drops a field the importer
actually reads, and nothing here requires a backend.

All sizes below are measured, not estimated — real gzip / raw-DEFLATE / URL-safe Base64 runs
over representative payloads (a single unstructured task, and a 10-task set with a list,
project, tag, person, and subtasks).

## Headline

| Case | Current share text | Achievable | Factor |
| --- | --- | --- | --- |
| 1 task, no structure | 346 chars (2 links x 173) | ~39 | ~9x |
| 10 tasks + structure | ~1388 chars (2 x 694) | ~371 | ~3.7x |

Ranked by payoff per unit of effort:

| # | Change | Saving | Cost |
| --- | --- | --- | --- |
| 1 | Emit one link instead of two | 2x the whole share text | UX decision |
| 2 | v2 compact payload (dictionaries, index refs, positional arrays) | 694 -> 395 | ~1 day |
| 3 | gzip -> raw DEFLATE (`nowrap = true`) | flat -24 chars | 3 lines |
| 4 | Short URI form `yata://i?d=` | -15 chars per link | trivial |
| 5 | Plaintext fast path for a simple single task | 50 -> 30, and readable | small |

## 1. Emit one link, not two

`TaskTransferLinks.asShareText` prints both `inboxOnly` and `withStructure`. Both embed the
**identical** `d` blob; only `s=0` / `s=1` differs. The share text is therefore literally twice
the size it needs to be, and this is the single largest win available.

There is a correctness wrinkle alongside the size one. `TaskDetailScreen.kt` (and the four
entity screens) pass `includeStructure = !options.privacyMode`. In privacy mode the payload
contains no structure at all — yet the `s=1` "add with missing lists, projects, tags, and
people" link is still emitted. It advertises something the blob cannot deliver.

Two ways out:

- **Option A (cheap).** The sender already chooses privacy mode at export time. Let that one
  choice decide, and emit a single link. No new UI. Pick this if the goal is to land the size
  win quickly.
- **Option B (better).** One link, plus a receiver-side import preview sheet carrying an
  "also create missing lists, projects, tags, and people" checkbox. Same 2x saving, and it
  resolves the open decision about an import confirmation screen at the same time.

Both deliver the 2x immediately. B is the better end state; A is a valid stepping stone
because the wire format is identical either way — only who flips the flag changes.

## 2. Dead weight in the current payload

These cost bytes on every share and are read by nothing. Removing them loses no data.

- **Structure `id` UUIDs** (~40 chars each, across four dictionaries). They exist only so task
  rows can reference a dictionary entry, and are then discarded — `importLists` /
  `importProjects` / `importTags` / `importPeople` all match by normalized name, never by the
  transported id. Replace with an array index.
- **`subs[].p`** (`parentSubtaskId`). `TaskTransferLink.kt:249` hardcodes `parentSubtaskId = null`.
  Written, never read.
- **`subs[].d`** (subtask `done`). `TaskTransferLink.kt:248` hardcodes `done = false`.
  Written, never read.
- **`subs[].o`** (`sortOrder`). `TaskTransferLink.kt:250` already falls back to the array index.
  Array position carries the ordering.
- **Empty `tags` / `people` / `subs` arrays.** `Task.toTransferJson` emits all three
  unconditionally (`TaskTransferLink.kt:139-145`), including when structure is excluded.
- **`"f": false` and `"p": "none"`.** Emitted on every task even at their defaults.

### Related bug, not just size

`Task.effectiveTransferTagIds` (`TaskTransferLink.kt:157`) folds `project.commonTagIds` into
the exported tag dictionary. But `importProjects` (`TaskTransferLink.kt:302`) constructs
`Project(id, name, color, icon)` and never sets `commonTagIds`. Those tags are therefore
created on the receiving device referenced by nothing — orphan rows, plus wasted payload.

Fix by choosing one: import `commonTagIds` onto the created project, or stop exporting
project common tags. Importing them is the behaviour that matches user expectation, since the
receiver otherwise loses tags that were visibly on the shared tasks.

## 3. v2 payload shape

Top-level becomes a positional array with no key names:

```text
[2, title, lists[], projects[], tags[], people[], tasks[]]
```

A task row uses fixed positions, **ordered by descending likelihood of being set**, with
trailing defaults truncated:

```text
[title, priority, flag, notes, listIdx, projIdx, tagIdx[], personIdx[], subs[]]
```

Trailing truncation is what makes the common case tiny — `["Pay electricity bill", 3]` is a
complete task row. Sparse middles use zero placeholders: `["Title", 0, 0, "notes"]`.

Other shape changes:

- `priority` becomes an ordinal `0..3` rather than `"none"` / `"low"` / `"med"` / `"high"`.
  The importer must still validate the range and fall back to `0`.
- Dictionary entries drop their `id` and are referenced by array index from task rows.
- Subtasks collapse to `[title]`, since done/parent/order are all discarded on import.

Worked example, 10 tasks with a list, project, tag, person and subtasks:

```text
v1 JSON              3185 bytes   ->  gzip + base64  694 chars
v2 JSON               615 bytes   ->  raw   + base64  371 chars
```

### Version lives in the URI

Move the version from inside the compressed blob to a URI parameter (`v=2`), so the parser can
branch before spending work on decompression. `TaskTransferImporter.importFrom` currently reads
`payload.optInt("v")` after decoding. Keep the `v=1` path intact for links already shared.

## 4. Compression

Measured crossover between "just Base64 the JSON" and "DEFLATE then Base64":

| tasks | raw JSON | base64 only | deflate + base64 |
| --- | --- | --- | --- |
| 1 | 47 | 63 | 66 |
| 2 | 83 | 111 | **76** |
| 10 | ~370 | 493 | **124** |
| 40 | 1481 | 1975 | **228** |

Compression wins from two tasks upward, and ties at one. So: keep compressing always, but
switch `GZIPOutputStream` / `GZIPInputStream` for `Deflater` / `Inflater` with `nowrap = true`.
That drops gzip's 10-byte header and 8-byte trailer, worth a flat 24 Base64 characters on every
link regardless of payload size. The 3-char difference at n=1 does not justify a second code
path on its own — the plaintext fast path in section 5 covers that case better.

Keep `MAX_DECODED_BYTES` enforced on the v2 decode path. It is the zip-bomb guard, and the
streaming byte count in `decodeTransferPayload` is the right shape for it.

### Rejected alternatives, with numbers

- **Preset DEFLATE dictionary** (`Deflater.setDictionary`): 371 -> 355 chars, about 4%. It
  welds a byte-exact dictionary to the format version permanently — any later edit to the
  dictionary is a breaking change. Not worth it.
- **Binary / varint encoding instead of JSON**: JSON's structural punctuation is highly
  repetitive, so DEFLATE already removes most of it. The remaining gain is marginal and costs
  all debuggability of the payload. Not worth it.
- **Base66 / Base85 alphabets**: log2(66)/6 is a 0.7% gain, and stepping outside the Base64URL
  alphabet risks breaking link auto-detection in messaging apps, which is the constraint that
  actually matters here. Not worth it.

## 5. Optional plaintext fast path

When a share is one task with no structure, no notes and no subtasks — by far the most common
case, and what `TaskDetailScreen` produces — skip Base64 entirely:

```text
yata://i?t=Pay%20electricity%20bill&p=3
```

30 characters against 50 for the encoded equivalent, and readable, so the receiver can see what
they are about to import before tapping. That readability is a real trust benefit, not just a
size one. Fall back to the `d=` blob the moment the share does not fit the shape.

## 6. Link length budget

`MAX_DECODED_BYTES = 256_000` currently permits links no messaging app will ever linkify. The
decode guard should stay where it is (it defends against hostile input), but share-time needs a
separate, much smaller budget:

- Soft target: **<= ~500 chars** for reliable auto-linking across SMS, WhatsApp, and email.
- Warn the sender past **~1000 chars** that some apps may not turn it into a tappable link.

This is the open decision about warning on very long links, and it is worth doing regardless of
how much of the rest lands — a silently unusable link is worse than a warned-about one.

Note that long notes are the one thing no encoding change helps with. Truncating them would
narrow the data scope, so the answer there is the warning, not a cap.

## Execution order

1. **Phase 0 — tests first.** Write `app/src/test/java/com/mj/yata/TaskTransferLinkTest.kt`
   against the **v1** format. None exists today. Cover round-trip build -> parse -> import for:
   single task, multi-task, structure on and off, privacy mode, malformed blob, oversized blob.
   This is the safety net for every phase after it, and it pins current behaviour before the
   format moves. Unit tests touching the payload need the `org.json` JVM dependency — the
   `android.jar` stubs throw, which turns every assertion into an opaque `RuntimeException`.
2. **Phase 1 — one link, short URI, raw DEFLATE, drop dead fields.** Decide Option A or B
   first. Fold the URI and compression changes into the `v=2` bump rather than versioning them
   separately.
3. **Phase 2 — v2 payload shape.** Dictionaries with index refs, positional task rows, trailing
   truncation. The importer keeps its `v=1` branch.
4. **Phase 3 — fix the `commonTagIds` orphan.** Either import it onto the project or stop
   exporting it.
5. **Phase 4 — plaintext fast path.**
6. **Phase 5 — share-time length budget and warning.**

## Carried over from the first pass

Unrelated to shortening, still outstanding from commit `7db50e9`:

- `task_transfer_imported` (plurals) and `task_transfer_import_failed` were added only to
  `res/values/strings.xml`. All **24** translated locale folders are missing them, so lint's
  `MissingTranslation` will flag them and non-English users see English.
- The `require()` messages in `TaskTransferLink.kt:203-262` ("Not a YATA task import link.",
  "Unsupported YATA task link.", "No tasks found in shared link.", "Shared task link is too
  large.") are hardcoded English. `MainActivity` surfaces them through
  `error.message ?: getString(R.string.task_transfer_import_failed)`, so the localized fallback
  is unreachable in practice and every failure path shows untranslated text.

## Open decisions unchanged by this plan

- Whether custom `yata://` links are enough, or whether YATA should add verified HTTPS App
  Links later. Note that HTTPS App Links are strictly **longer** than `yata://i`, so this trades
  size for reliability of handling.
- Whether to embed clickable links inside generated PDFs in addition to share-sheet text.
