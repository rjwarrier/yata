# App Links Handoff

## Current status

YATA task image/PDF exports now include app deep links in the share text. A receiver can tap a `yata://import/tasks...` link to import the shared task or task set into their Inbox.

Two links are generated for each share:

- Inbox-only import: adds the task(s) to Inbox without copying list, project, tag, or people structure.
- Inbox + structure import: adds the task(s) to Inbox and creates/reuses missing lists, projects, tags, and people by name.

Privacy mode keeps the transfer payload leaner and safer by omitting notes and structure.

## Main files

- `app/src/main/java/com/mj/yata/util/export/TaskTransferLink.kt`
  - Builds compressed transfer links.
  - Parses transfer links.
  - Imports tasks and optionally creates missing structure.
- `app/src/main/java/com/mj/yata/MainActivity.kt`
  - Handles `yata://import/tasks` deep links.
  - Imports tasks, shows result toast, and navigates to Inbox.
- `app/src/main/java/com/mj/yata/util/export/ExportFileUtils.kt`
  - Adds optional share text alongside exported files.
- `app/src/main/java/com/mj/yata/util/export/TaskReportExport.kt`
  - Passes transfer text through task image/PDF export.
- `app/src/main/java/com/mj/yata/util/export/EntityReportExport.kt`
  - Passes transfer text through list/project/tag/person task image/PDF export.
- Export call sites:
  - `app/src/main/java/com/mj/yata/ui/screen/taskdetail/TaskDetailScreen.kt`
  - `app/src/main/java/com/mj/yata/ui/screen/project/ProjectDetailScreen.kt`
  - `app/src/main/java/com/mj/yata/ui/screen/list/ListDetailScreen.kt`
  - `app/src/main/java/com/mj/yata/ui/screen/tag/TagDetailScreen.kt`
  - `app/src/main/java/com/mj/yata/ui/screen/person/PersonDetailScreen.kt`

## Current link format

Links use the custom app scheme:

```text
yata://import/tasks?s=0&d=...
yata://import/tasks?s=1&d=...
```

Where:

- `s=0` means Inbox-only.
- `s=1` means copy missing structure.
- `d` is a URL-safe Base64 encoded, gzip-compressed JSON payload.

This is not an Android verified HTTPS App Link yet. It is an app-specific deep link through the existing `yata` scheme.

## Import behavior

Imported tasks are intentionally normalized so they land in Inbox:

- New task IDs are always generated.
- Tasks are imported as not done.
- Due date, start date, reminder, recurrence, time, section, estimate, archive, and delete state are cleared.
- Title, priority, flag, optional notes, and subtasks are preserved.
- Tags, list, project, and people are preserved only when the receiver chooses the structure-copy link.
- Missing structure is matched case-insensitively by name before creating new rows.

## Verification already run

Before commit `7db50e9 Add task import links to exports`, these passed:

```text
:app:compileDebugKotlin
:app:assembleDebug
git diff --check
```

The debug APK was also installed successfully on the connected device.

## Link shortening opportunities

The current payload is compressed but still uses readable JSON. The best next optimization is a compact payload version while keeping everything offline and private.

Recommended next step:

1. Add payload version `v=2`.
2. Replace verbose JSON object keys with compact keys.
3. Omit all default/empty values.
4. Store structure as dictionaries once, then reference dictionary indexes from tasks.
5. Store tasks/subtasks as fixed-position arrays rather than nested objects.
6. Keep the existing importer compatible with `v=1` links.

Example direction:

```json
{
  "v": 2,
  "n": "Shared task set",
  "x": {
    "l": ["Work"],
    "p": ["Launch"],
    "g": ["Urgent"],
    "u": ["Ranjith"]
  },
  "t": [
    ["Pay bill", "high", 1, "notes", [0], 0, 0, [0], [["Check amount"]]]
  ]
}
```

Further shortening would require server-backed short links, for example `yata://import/tasks?id=abc123`, but that introduces backend storage, expiry, privacy, sync, and abuse-handling decisions.

## Open decisions

- Whether custom `yata://` links are enough, or whether YATA should add verified HTTPS App Links later.
- Whether to embed clickable links inside generated PDFs in addition to share-sheet text.
- Whether to show a confirmation/import preview screen before creating tasks.
- Whether to warn users when a large task set produces a very long link that some apps may not auto-link.
