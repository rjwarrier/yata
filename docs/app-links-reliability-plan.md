# App Links — Reliability and Accuracy Plan

Follow-up to `docs/app-links-v2-plan.md` and `docs/app-links-v3-plan.md`, both of which optimised
for **link length**. This one optimises for **not being wrong**: every item below is a way the
import path can currently produce a result the sender did not intend, silently.

Findings are from reading the shipped code, not from design review. Line references are against
`app/src/main/java/com/mj/yata/util/export/TaskTransferLink.kt` at the time of writing.

## 0. The links are not clickable — the feature's main path is broken

Reported from real use, and confirmed by design: **`yata://` is a custom scheme, and messaging
apps, email clients and SMS do not linkify custom schemes.** They linkify `http`/`https` (and
sometimes `mailto`/`tel`) and render everything else as inert text. So the shared text presents a
link the recipient cannot tap.

This is not a defect in link construction — the URI is well-formed and works when actually
opened. The manifest already anticipated it:

```xml
<!-- Custom scheme rather than an https App Link, which
     would need a verified assetlinks.json on a domain this app doesn't have. -->
<intent-filter android:autoVerify="false">
    <data android:scheme="yata" />
</intent-filter>
```

**That premise is now false.** The app ships a domain of its own —
`SettingsScreen.kt:4105`, `YATA_WEBSITE_URL = "https://ranjithj.in/yata/"`. Android App Links are
therefore available, and everything below assumes publishing rights to that host.

Note the existing filter declares `scheme="yata"` with **no host**, so it matches any host. The
v2/v3 change from `yata://import/tasks` to `yata://i` did not break matching.

### What clickability actually requires

Two separate things, often conflated:

1. **Being tappable at all** comes from the `https` scheme. Any `https` URL gets linkified.
2. **Opening YATA instead of a browser** comes from verification — `assetlinks.json` plus
   `autoVerify="true"`. On Android 12+ an *unverified* https link goes straight to the browser,
   with no chooser, so verification is not optional here.

### Required changes

- Serve `https://ranjithj.in/.well-known/assetlinks.json`. It must be at the **host root**, not
  under `/yata/` — Android only fetches the root path.
- Add an `https` intent-filter (`host="ranjithj.in"`, `pathPrefix="/yata/i"`,
  `autoVerify="true"`). **Keep the `yata://` filter**: links already shared use it, and Tasker and
  automation entry points depend on the custom scheme.
- Emit `https` links from `buildTaskTransferLink`.
- Serve a landing page at that path for recipients without the app installed.

### The privacy trap this introduces, and the fix

Today the payload never leaves the device — nothing resolves a `yata://` URI over the network.
Move to `https` naively and that stops being true: **if the recipient does not have YATA
installed, their browser sends the whole URL to the web server**, so task titles and notes end up
in access logs of the site. That is a real regression against a feature whose selling point is
being offline and private.

**Put the payload in the URL fragment.** Fragments are never transmitted to the server:

```text
https://ranjithj.in/yata/i#e=<payload>
```

The app receives the fragment intact and can reuse the existing query-parameter parsing with
`Uri.parse("?" + uri.fragment)`, so `getQueryParameter("e")` keeps working unchanged. A landing
page can still read `location.hash` client-side to say "install YATA to open this task" without
the payload ever reaching the server.

### Cost, and things to verify on-device

Size: `https://ranjithj.in/yata/i#e=` is 29 characters against `yata://i?e=` at 11 — **+18 per
link**. That is the right trade when the alternative is a link nobody can tap, and it is the
reliability-for-size exchange `docs/app-links-v3-plan.md` flagged as acceptable if ever needed.
Shortening the path to `/y` recovers 5 of those characters.

Must be confirmed on a device rather than assumed:

- **Do linkifiers include the `#fragment` in the detected link?** Generally yes, but a linkifier
  that stops at `#` would silently truncate the payload — the exact silent-truncation failure
  mode described in item 7 below, so ship the checksum before or alongside this.
- **Debug builds have a different signing certificate than release.** `assetlinks.json` accepts an
  array of fingerprints; include both, or App Links will not verify on the debug build used for
  testing and the change will look broken when it isn't.
- Verification is asynchronous after install and can take a moment; a fresh install tapping a link
  immediately may still go to the browser once.

This supersedes the "custom `yata://` links vs verified HTTPS App Links" open decision carried in
`docs/app-links-handoff.md`, and it is the highest-priority item in this document: the other
defects degrade the feature, this one prevents most recipients from using it at all.

## Confirmed defects

### 1. `sortOrder` truncates to a negative number, identically for every imported task

All three import paths set:

```kotlin
sortOrder = System.currentTimeMillis().toInt()   // lines 380, 452, 596
```

`System.currentTimeMillis()` is ~1.79e12. `Int.MAX_VALUE` is 2.15e9, so `.toInt()` keeps the low
32 bits: **-706,395,136** at time of writing. Two consequences:

- Every imported task sorts far below any normally-created task, wherever manual order is used.
- Every task in a single import receives the *same* value, so the order the sender chose is
  discarded and the resulting order is whatever the database returns.

The sibling bulk importer already does this correctly — `PlainTextImporter.kt:99,129` uses
`baseSortOrder = existingTasks.size` then `baseSortOrder + index`. Adopt that verbatim.

Pre-existing since `7db50e9`; carried into the v3 and plaintext paths by later work rather than
introduced by it.

### 2. Blank structure names are no longer rejected

The v1 legacy importers guard every dictionary row:

```kotlin
if (oldId.isBlank() || name.isBlank()) return@forEach   // lines 616, 638, 660, 682
```

The current `importListsV2` / `importProjectsV2` / `importTagsV2` / `importPeopleV2` have **no such
check**. A blank or whitespace-only dictionary name creates a real list/project/tag/person named
`""` on the receiver's device.

This is a regression introduced by the v2 rewrite (mine), not present in the original.

Note the fix is not simply "skip blank rows": task rows reference dictionary entries **by array
index**, so dropping one silently reassigns every later entry. The row must be retained as an
un-importable placeholder that tasks referencing it resolve to null.

### 3. Structure is written before the task list is validated

```kotlin
val tagMap     = if (copyStructure) importTagsV2(...)      // line 414  — writes to the DB
val listMap    = if (copyStructure) importListsV2(...)     // line 415  — writes
val projectMap = if (copyStructure) importProjectsV2(...)  // line 416  — writes
val personMap  = if (copyStructure) importPeopleV2(...)    // line 417  — writes

val tasks = ...
require(tasks.isNotEmpty()) { "No tasks found in shared link." }   // throws *after* all of that
```

A structure-carrying link with no importable task therefore creates lists, projects, tags and
people, then fails with "Could not import this YATA task link." The user is told nothing happened
while their workspace has just gained entities they never asked for and cannot easily trace.

Validate the task list — decode, parse, check non-empty — **before** the first write.

### 4. Import is not atomic

Even with (3) fixed, structure entities are written one repository call at a time and the tasks are
written last. Any failure in between (full disk, `SQLiteDiskIOException`, a concurrently deleted
row) leaves the receiver with orphan entities and no tasks, and no indication which entities came
from the failed import.

This is the exact failure mode `CLAUDE.md` documents for tag groups — *"Creating a group and
assigning tags to it is one transactional repository call … never two separate async writes"* —
because the non-transactional version silently dropped data on-device. The share-link importer
reintroduces the pattern that rule exists to prevent.

Needs a repository entry point that commits structure and tasks in one transaction, mirroring
`upsertTags(tags, pendingGroup)`.

## Reliability gaps

### 5. No confirmation before writing

Tapping a link writes to the database immediately. There is no preview, no undo, and — with
`s=1` — no warning that lists, projects, tags and people are about to be created. This was left as
an open decision in the original handoff; items 6 and 8 below make it materially more urgent, and
it is the single change that most improves trust in the feature.

### 6. The plaintext path has no size guard — done

`MAX_TASKS_PER_LINK = 200` is now enforced once, in `parseTransferLink`, applying uniformly to
every format rather than duplicated per-parser. `MAX_SUBTASKS_PER_TASK = 100` guards the same way
per task, across all three formats. The task cap rejects outright (`IllegalArgumentException`);
the subtask cap truncates rather than rejects, since an oversized subtask list on one task isn't
a sign of a hostile link the way hundreds of top-level tasks is.

The "surface N tasks through the confirmation" half of this item is still open — it wants the
confirmation dialog from item 5, which hasn't landed yet.

### 7. A truncated plaintext link imports silently wrong data — done

Implemented as proposed: a `c=` parameter carrying an 8-hex-char CRC32, computed over a
**canonical** (sorted-by-key) form of the other parameters rather than their literal encoded
order — so a link-processing step in transit that reorders query parameters doesn't false-positive
as truncation, while a link that's actually lost or altered data still fails to verify. Links built
before this existed simply have no `c=` parameter and are imported without a check, rather than
being rejected retroactively.

The "measure before committing" concern turned out not to bind: the fixed ~11-character overhead
(`&c=` + 8 hex digits) is small relative to the sizes already in play post-v3, and correctness
against silent data loss outweighs it.

### 8. Re-tapping a link silently duplicates everything

Task ids are freshly minted on every import with no duplicate detection, so opening the same link
twice — a completely ordinary thing to do from a chat thread — produces two copies of every task.

`PlainTextImporter` already solves this: it calls `findSimilarTask(row.title, allTasksSoFar)` and
reports a `skippedDuplicates` count (`PlainTextImporter.kt:101-107`). Reuse that helper and report
the skipped count in the result toast, rather than inventing a second notion of "same task".

## Accuracy gaps — silent, undisclosed data loss

These are deliberate normalisations (a shared task should land in the receiver's Inbox, not
reproduce the sender's schedule), and the *behaviour* is probably right. The problem is that
neither side is ever told.

### 9. Dropped fields are invisible to both parties

Discarded on import: `due`, `startDate`, `time`, `reminder`, `recurrence`, `estimateMinutes`,
`section`, `followUpAt`, `done`, `completedAt`. A sender sharing a task with a due date and a
reminder reasonably believes those travelled. Surface it: the confirmation screen from item 5
should say what will and will not be carried across.

### 10. Subtask hierarchy is flattened and completion is reset

`Subtask.parentSubtaskId` is a real feature, but every import path hardcodes
`parentSubtaskId = null` and `done = false`. Nested subtasks silently become a flat list.

Unlike the schedule fields, there is no "belongs to the receiver's context" argument for
flattening hierarchy — a checklist's shape is part of its content. Either carry
`parentSubtaskId` (as an index reference into the same task's subtask array, mirroring how
dictionaries already work) or state the limitation explicitly. Carrying it is the better answer;
completion state should stay reset, since the receiver has not done the work.

## Execution order

Ordered so each phase is independently verifiable, defects before features.

0. **Phase 0 — HTTPS App Links (item 0).** First, because until this lands most recipients cannot
   use the feature at all. Split into: (a) publish `assetlinks.json` and the landing page —
   external to this repo; (b) add the https intent-filter and emit fragment-based https links;
   (c) verify on a real device across WhatsApp, Gmail and SMS. Step (c) is the acceptance
   criterion — the others are not done until a tapped link opens YATA from a chat.

1. **Phase 1 — defects 1, 2, 3.** Self-contained, no format change, no UI. Each is a small fix with
   a directly matching test. Land as one commit per defect so a bisect can isolate them.
2. **Phase 2 — defect 4, atomic import.** Needs a new `YataRepository` method and therefore a fake
   update in tests; keep separate from phase 1 so the repository change is reviewable on its own.
3. **Phase 3 — item 8, duplicate detection.** Reuses `findSimilarTask`; behaviour change, needs the
   result type to carry a skipped count.
4. **Phase 4 — item 5, the confirmation screen.** The largest piece, and it subsumes the
   user-visible half of items 6, 9 and 10 (task count, what is dropped, what will be created).
5. **Phase 5 — items 6 and 7, plaintext cap and checksum.** Do after phase 4 so the cap has a
   sensible place to report from.
6. **Phase 6 — item 10, subtask hierarchy.** Payload change; sequence last so it is not blocked
   behind any of the above.

## Test additions required

The current 40 tests cover round trips and encoding selection well but almost nothing about
failure. Needed:

- `sortOrder` is positive, distinct per task, and preserves payload order.
- A blank dictionary name creates nothing, and a task referencing that index imports with a null
  list/project rather than a phantom one.
- A structure-carrying payload with zero valid tasks writes **nothing** — asserted against every
  repository collection, not just tasks.
- A repository whose task write throws leaves no structure behind (needs a failure mode on the
  existing fake).
- Importing the same link twice yields one copy and reports one skipped.
- A plaintext link truncated mid-title is rejected rather than imported short.
- A plaintext link with an absurd task count is rejected.
- Nested subtasks survive a round trip once phase 6 lands.

## Explicitly out of scope

- **Server-backed short links** (`?id=abc123`, payload stored remotely) — still out of scope, and
  note it is a different thing from item 0. Item 0 keeps the payload in the URL fragment so it
  never reaches a server; a short-link service would deliberately send it there, taking on
  storage, expiry, privacy and abuse handling.
- **Encryption or sender authentication.** These links are meant to be pasteable into any chat;
  anyone holding one can import it. That is the design, not a defect, and the confirmation screen
  from item 5 is the right mitigation rather than cryptography.
- **Further size optimisation.** After v3 the remaining bulk is user content. Note that items 7 and
  10 both *add* characters — that is the correct trade at this point, and any future size work
  should not undo them.
