# App Links v3 — Further Shortening: Scope and Plan

Follow-up to `docs/app-links-v2-plan.md`, whose phases 0–5 have all landed. This round is smaller
in ambition but contains one genuine **regression fix**: the plaintext fast path shipped in v2
phase 4 makes links *longer* — up to 2x — for every non-Latin script YATA ships.

As before, every number here is measured (real DEFLATE / Base64URL / Android `Uri.encode`
percent-encoding), not estimated.

## Headline

| # | Change | Effect | Priority |
| --- | --- | --- | --- |
| A | Choose encoding by measured size, not by shape | Fixes a 1.6–2.0x regression for Indic scripts; also wins up to n=8 for Latin | **Bug fix** |
| B | Drop the payload `title` field — written every time, never read | −16 to −24 chars | High |
| C | Extend the plaintext grammar (multi-task, notes, subtasks) | Gives A a richer candidate to pick from | Medium |
| D | Omit `s=0` when structure is absent | −4 chars | Trivial |

## A. The regression: shape-based fast-path selection

`buildTaskTransferLink` currently picks the plaintext form by **shape** — one task, no structure,
no notes, no subtasks. It never checks whether the result is actually shorter.

Percent-encoding costs 3 characters per UTF-8 byte, and Indic scripts are 3 bytes per character —
so 9 characters each. Base64 costs ~4 characters per 3 bytes regardless. Measured, single task,
title only:

| Locale | plaintext (shipped today) | deflate + Base64 | verdict |
| --- | --- | --- | --- |
| English | 39 | 67 | fine |
| German | 39 | 70 | fine |
| Swahili | 39 | 65 | fine |
| Hindi | 150 | 95 | **+55 (1.6x worse)** |
| Bengali | 183 | 105 | **+78 (1.7x worse)** |
| Malayalam | 201 | 113 | **+88 (1.8x worse)** |
| Tamil | 219 | 119 | **+100 (1.8x worse)** |
| Telugu | 228 | 114 | **+114 (2.0x worse)** |

YATA ships nine Indic locales (`bn gu hi kn ml mr pa ta te`). For those users the v2 work made
single-task sharing worse, not better, and it does so silently.

The same gate also *costs* Latin-script users, from the other direction — plaintext keeps winning
well past one task, but the shape check refuses to consider it:

| tasks | plaintext | deflate + Base64 | winner |
| --- | --- | --- | --- |
| 1 | 35 | 61 | plaintext |
| 2 | 57 | 86 | plaintext |
| 3 | 80 | 110 | plaintext |
| 5 | 129 | 154 | plaintext |
| 8 | 190 | 197 | plaintext (barely) |

**Fix:** build both candidates and emit the shorter one. This is strictly optimal, deletes the
arbitrary shape gate, fixes the Indic regression automatically, and needs no locale detection —
size is the thing we actually care about, so measure it directly rather than proxying it through
a rule about task shape.

The cost is one extra encode per share. That is microseconds against an export that is already
rendering a PDF or bitmap, so it does not need guarding.

## B. Drop the payload `title`

`buildTaskTransferLink` writes the share title at payload index 1. `importV2` reads indices 0
(version), 2–5 (dictionaries) and 6 (tasks). **Index 1 is never read.** Every compressed link has
been carrying it for nothing.

| tasks | with title | without | saved |
| --- | --- | --- | --- |
| 1 | 72 | 48 | 24 |
| 3 | 123 | 104 | 19 |
| 10 | 252 | 236 | 16 |

Dropping it shifts indices 2–6 down to 1–5, which is a payload-format change — see the versioning
note below. If a future import-preview screen wants a heading, it should take it from the task
titles it is already about to show, not from a duplicate field: the share *text* around the link
carries the title in plain sight already.

## C. Extend the plaintext grammar

Today the plaintext form expresses only `t` (title), `p` (priority), `f` (flag). With size-based
selection in place, teaching it more shapes directly widens the set of shares that can take the
cheaper encoding:

- **Multiple tasks** — repeat `t`; `Uri.getQueryParameters("t")` returns them in order.
- **Notes** — `n`, one per task, positionally aligned with `t`.
- **Subtasks** — needs a delimiter within one parameter, since per-task grouping must survive.

Careful with parameter names: **`s` is already the structure flag** and must not be reused for
subtasks. Suggest `b` for subtasks. Structure (lists/projects/tags/people) should stay
compressed-only — expressing an index-referenced dictionary in query parameters costs more than it
saves, and it is exactly the case where content volume favours DEFLATE.

Per-task alignment across repeated parameters is the one real correctness risk here: a task with
no notes still needs a placeholder so `n[i]` lines up with `t[i]`. Tests must cover a middle task
having empty notes, not just the leading and trailing cases.

## D. Omit `s=0`

When structure is not included, `s=0` is four characters stating the default. Absence already
means the same thing — `getQueryParameter("s") == "1"` is the existing check, so a missing `s`
reads as false with no code change. Emit `s` only when it is `1`.

## Rejected, with numbers

- **Flattening subtask rows** from `[["a"],["b"]]` to `["a","b"]`: measured **−2 to −3 chars, i.e.
  worse**. This was my hypothesis and it was wrong: the nested form's repeated `"],["` punctuation
  is highly compressible, and DEFLATE exploits that better than it does the varied content of a
  flat array. Left alone.
- **Server-backed short links** (`yata://i?id=abc123`): still the only route to a dramatic further
  cut, and still out of scope — it introduces backend storage, expiry, privacy, and abuse handling,
  trading the feature's offline/private property for length.
- **Verified HTTPS App Links**: strictly *longer* than `yata://i`. Worth doing for handling
  reliability if ever, but it is a size cost, not a saving.
- **Preset DEFLATE dictionary / binary encoding / Base66**: re-confirmed as rejected in
  `docs/app-links-v2-plan.md` §4; nothing has changed to revisit them.

## Versioning

B changes payload indices, so it needs a marker. The cheapest one that costs zero characters is a
**new query parameter name for the payload**, keeping the single `yata://i` host:

- `d=…` — v2 compressed payload (keep decoding; links may already exist from installed builds)
- `e=…` — v3 compressed payload
- `t=…` present — plaintext form (any version)

This avoids both a `v=3` parameter (4 wasted characters) and a second host letter (cryptic, and
`isTaskTransferUri` would need to know every historical letter forever).

`yata://import/tasks` v1 decode stays exactly as-is.

## Execution order

1. **Phase A — size-based selection.** Pure refactor of the choice in `buildTaskTransferLink`; no
   format change, no version bump. Land first and alone: it is the bug fix, and it is verifiable
   against the existing suite plus new non-Latin cases.
2. **Phase B — drop the title, introduce `e=`.** Payload format change; keep the `d=` v2 decode
   path.
3. **Phase D — omit `s=0`.** One line, fold into B.
4. **Phase C — extend the plaintext grammar.** Largest new surface, lowest risk of the four,
   because A already guarantees it is only used when it actually wins.

## Test additions required

The existing `TaskTransferLinkTest` (32 tests) covers v1/v2 round trips but has **no non-Latin
case at all** — which is precisely why the regression got through review, a full test suite, and a
device install. Additions:

- Non-Latin round trip (Tamil or Hindi title) asserting the *encoding chosen*, not just that the
  import succeeds.
- A direct assertion that the emitted link is never longer than the alternative encoding — the
  invariant Phase A establishes, stated once so no future shape heuristic can quietly break it.
- Per-task alignment for phase C: three tasks where only the middle one has notes.
- `s` absent behaves identically to `s=0` on import.

## Expected end state

Single ASCII task: 39 → ~31 characters. Single Tamil task: 219 → ~119 (the regression erased).
Ten-task ASCII set with structure: 371 → ~355. The remaining bulk is user content — titles and
notes — which no encoding change can compress away without discarding data.
