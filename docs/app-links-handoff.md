# App Links Handoff

## Current Status

YATA task image/PDF exports can include one import link in the system share text. A receiver taps
the link to add the shared task or task set to their Inbox.

Generated human-facing links are verified HTTPS App Links:

```text
https://ranjithj.in/yata/i#...
```

The task payload lives in the URL fragment after `#`. Fragments are not sent to the web server or
to link-preview crawlers, so task titles and notes do not leak through preview fetches. Older
`yata://` links remain accepted for compatibility, automation, and links already shared before the
HTTPS transport landed.

## Main Files

- `app/src/main/java/com/mj/yata/util/export/TaskTransferLink.kt`
  - Builds transfer links.
  - Chooses the shorter of readable plaintext parameters and compressed payloads.
  - Parses legacy and current links.
  - Imports confirmed multi-task links.
- `app/src/main/java/com/mj/yata/MainActivity.kt`
  - Detects task import links before ordinary deep-link routing.
  - Parses links without writing and routes valid task links to the shared import screen.
- `app/src/main/java/com/mj/yata/ui/screen/sharedimport/SharedTaskImportScreen.kt`
  - Shows a single shared task in the normal task editor before saving.
  - Shows multi-task links in a preview screen with Import/Cancel confirmation.
  - Asks before creating missing list/project/tag structure.
- `app/src/main/java/com/mj/yata/ui/screen/crashlog/CrashLogScreen.kt`
  - Shows a task-link diagnostic card under Settings > Diagnostics.
  - Reads Android 12+'s domain verification state for `ranjithj.in` and opens system link settings.
- `app/src/main/java/com/mj/yata/ui/sheets/NewTaskSheet.kt`
  - Contains `resolveAgainstLocalData`, the single-task bridge from parsed shared task to draft.
- `app/src/main/java/com/mj/yata/util/export/ExportFileUtils.kt`
  - Adds optional `Intent.EXTRA_TEXT` alongside shared image/PDF files.
- `app/src/main/java/com/mj/yata/util/export/LongTaskLinkWarning.kt`
  - Shows a sender-side confirmation when an import link exceeds the practical auto-link budget.
- `app/src/main/java/com/mj/yata/util/export/TaskReportExport.kt`
  - Passes import-link share text through task image/PDF export.
- `app/src/main/java/com/mj/yata/util/export/EntityReportExport.kt`
  - Passes import-link share text through list/project/tag/person report export.

Export call sites:

- `app/src/main/java/com/mj/yata/ui/screen/taskdetail/TaskDetailScreen.kt`
- `app/src/main/java/com/mj/yata/ui/screen/project/ProjectDetailScreen.kt`
- `app/src/main/java/com/mj/yata/ui/screen/list/ListDetailScreen.kt`
- `app/src/main/java/com/mj/yata/ui/screen/tag/TagDetailScreen.kt`
- `app/src/main/java/com/mj/yata/ui/screen/person/PersonDetailScreen.kt`

## Link Formats

Current generated formats:

- `https://ranjithj.in/yata/i#t=...&c=...`
  - Plaintext form, repeated `t` per task.
  - Used only when it is shorter than the compressed form.
  - Carries a CRC32 checksum in `c` so truncated readable links are rejected.
- `https://ranjithj.in/yata/i#e=...`
  - Current compressed form.
  - Raw DEFLATE plus URL-safe Base64.
  - Current compressed payload version is `4`.

Decode-only compatibility formats:

- `yata://i?...`
- `yata://import/tasks?s=...&d=...`
- v2 compressed payloads under `d=...`
- legacy v1 gzip/verbose JSON links

## Shared Fields

Shared tasks preserve fields that describe the work:

- title
- priority
- flag
- notes, only when export options include notes and privacy mode is off
- subtasks, flattened and reopened
- due date
- start date
- time
- estimate minutes
- recurrence
- list/project/tags, only when structure copy is included

Shared tasks deliberately do not preserve sender-local or identity fields:

- reminder
- section
- completed/done state
- follow-up
- archive/trash state
- people assignments

People are intentionally not copied or assigned. Lists, projects, and tags are shared vocabulary
that can be matched by name; people are local identity records and are left behind.

## Import Behavior

Single-task links:

- Parse without writing.
- Open the shared task in `NewTaskSheet`.
- Let the receiver edit before saving.
- Ask before creating missing lists/projects/tags.

Multi-task links:

- Parse fully before any write.
- Show a preview/confirmation screen before importing.
- Create new task IDs.
- Import tasks as open, unarchived, and not deleted.
- Continue local `sortOrder` from the existing task count.
- If structure copy is enabled, reuse or create missing lists/projects/tags by case-insensitive
  name.
- Never create people and never assign imported tasks to sender people.

## Hardening

Current guards:

- Max decoded compressed payload: `256_000` bytes.
- Max tasks per link: `200`.
- Max subtasks per task: `100`.
- Unsupported payload versions rejected.
- Blank task titles skipped; links with no usable tasks rejected.
- Blank entity names ignored.
- Plaintext checksum catches clipped links.
- Compressed payload corruption fails before writing.
- Parser failures are surfaced as `TaskTransferLinkException` with a typed
  `TaskTransferLinkError` reason, so UI code should switch on the reason rather than matching
  exception message text.

Tests live in:

- `app/src/test/java/com/mj/yata/TaskTransferLinkTest.kt`
- `app/src/test/java/com/mj/yata/ui/sheets/SharedTaskResolverTest.kt`

## Open Follow-Ups

- Broaden App Link and import-flow coverage with UI/Robolectric tests that run under workspace
  `user.home`.
