# Changelog

All notable changes to YATA, newest first.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Version numbers are the
app's `versionName`, with `versionCode` noted alongside since Android upgrades key off that.

**Working convention:** every user-visible change goes under `[Unreleased]` in the same commit that
makes it. At release time that section is renamed to the new version, dated, and a fresh empty
`[Unreleased]` is opened above it — so the GitHub release notes are a copy of the section rather
than an archaeology exercise over the commit log.

Entries describe what changed for someone using the app. Internal refactors, build plumbing and
test-only changes belong in the commit message, not here, unless they change behaviour.

## [Unreleased]

### Added

- **Sort people and tags by open work.** A "Most open tasks" option on the People and Tags tabs,
  ranking by how many tasks are still unfinished. The existing "Most tasks" counts everything ever
  associated with someone, so whoever has the longest history came out on top regardless of what
  they currently have left — and on the Tags tab it disagreed with the "N open" figure printed on
  every row. Both original task-count options are unchanged.
- **Open and Closed sections on the Tags tab.** Tags with unfinished work sit under Open; tags whose
  tasks are all done, and tags nothing points at any more, collapse into Closed, shut by default.
  Groups still work as before inside Open.
- **Auto-assign new tasks to you** (Settings → Task Defaults). New tasks were always assigned to
  you with no way to change it; turn this off and they start unassigned instead. You can still pick
  an assignee on any task either way. Applies to the widget's quick-add as well as the app, and
  adding a task from someone's own screen still assigns it to them regardless.
- Backups now include your settings — theme, feature flags, task defaults, notification
  preferences, sort orders and the rest. Previously a restore rebuilt every task and left you on
  defaults, which was most obvious after reinstalling. The app-lock PIN and the cloud account are
  deliberately excluded: a backup file is no place for a credential, and the cloud grant is
  per-device.

### Changed

- The task title in the New Task sheet is now the clear focal point of the screen: a tonal
  container with the largest rounding and type on the sheet, and a focus ring, instead of a thin
  underline that read as the least important control there. The mic button sits inside it.
- Notes, comment and subtask fields are filled tonal surfaces with generous rounding instead of
  outlined boxes, which sat oddly against the tonal cards around them. The project, list, tag and
  person editor sheets now match.
- "Create another" in the New Task sheet is a switch rather than a checkbox, and the whole row is
  one target: it's a mode that takes effect immediately, not something submitted with the task.
- The top bar buttons on a person's and a tag's own screen now sit in the same circular containers
  as the ones on the main tabs, and hide-completed fills in while it's on rather than only swapping
  its icon. Project and List detail still use the older plain buttons.

### Removed

- The dashed "New tag" and "Add person" rows at the bottom of the Tags and People tabs. Each
  duplicated the button already floating over the same screen. Note that if you have the quick-add
  button set to Hidden, these two tabs no longer offer any way to create a tag or a person.

### Fixed

- The list name under a task could render one letter per line — "Work" as a vertical `W o r k`.
  When a row carried enough detail to run out of width, everything after that point was squeezed
  to nothing and wrapped per character. The details now wrap onto a second line instead. Affected
  Today, Upcoming, Next 10 Days, Search, and the project, list, tag and person screens.
- A long person name on the People tab could squeeze the "YOU" badge beside it into the same
  one-letter-per-line state.
- The "Create another" label and its box behaved as two separate controls, and screen readers
  announced them separately.
- The FAB on the five main tabs sat too far above the bottom navigation bar, most noticeably with
  the floating panel. It was counting the system navigation inset twice — worth about 24dp on
  gesture navigation and 48dp with the three-button bar.
- Three buttons were too small to reliably tap — the group-delete buttons on the People and Tags
  tabs, and the dismiss on the New Task reminder warning. All three now meet the 48dp minimum
  while the icons stay the same size.

## [0.85 beta] - 2026-07-30

`versionCode 7`. Upgrades in place over 0.7 beta; the database migrates from 23 to 26 with existing
tasks preserved.

### Added

- **Start dates.** A task can be marked "not actionable before" a date. Deferred tasks drop out of
  Today but stay in their project or list, badged with the date they become available, and return
  on their own when it arrives. Understood at capture too — "draft proposal starts monday",
  "chase invoice not before next week", "defer until tomorrow".
- **Crash logs.** Reports are saved on the device automatically and readable from
  Settings → Crash Logs: full stack trace, copy, share, delete. Both hard crashes and failures the
  app recovered from are recorded, the latter having previously been invisible.
- **Manual cloud sync.** A sync button in the Today top bar when cloud backup is enabled, with
  progress and a result message.
- Profile name and email are included in backups. Previously a backup carried the avatar but not
  the name beside it, so a restore rebuilt the picture onto a blank identity.

### Fixed

- Adding a task or a person could crash the app. The immediate cause was a toolchain bug (D8
  emitting unverifiable dex for the task sheet); a follow-up scan found and fixed four systemic
  crash paths behind it — unhandled database failures on every write, an unguarded startup
  coroutine, corrupt-preferences handling, and unresolvable settings deep links.
- Long or pasted task titles could not be edited: text scrolled off to the right with the cursor
  pinned at the end. Titles now wrap.
- An unset profile name could never be set — the row was an invisible, untappable target.
- Recoverable database failures show a message instead of taking the app down.
- The release build could not be produced at all; R8 failed on a missing optional PDF codec.

### Changed

- M3 pass over the top bars on all five tabs: circular tonal containers, hide-completed as a real
  toggle, correct touch targets and screen-reader labels on the profile avatar.
- Settings search rebuilt to the M3 search-field spec.
- Notes and comment fields: send action moved inside the field, markdown hint moved to supporting
  text, consistent shapes and bounded height.

## [0.7 beta] - 2026-07-25

`versionCode 5`.

### Added

- On-device voice input: continuous recognition via `SpeechRecognizer`, working offline.
- Natural-language parser handles spoken time (12h AM/PM, written-out hours, quarter/half past),
  preposition expansion and trailing-punctuation cleanup.

### Changed

- M3 Expressive voice UI: layered waveform animation, pulsing record aura, bouncy entity chips,
  squircle mic button.

## [0.6 beta] - 2026-07-24

### Added

- Sort menu on the Tags and People tabs (name, task count, starred first); the same ordering
  applies to the assignee/tag pickers and to `@`/`#` mention autocomplete. People's drag-to-reorder
  was dropped in favour of it.
- Export a single task as PDF or image, with options to include notes and comments.

### Fixed

- Missing `%` label on the Project/Person/List/Tag hero progress ring.
- Home-screen widget "Configure" changes not applying reliably: the Team Overdue widget now honours
  its label and accent settings, refreshes are serialised and retried per instance, and a refresh
  can no longer be cut short by the configure screen closing early.

## [0.5] - 2026-07-23

First signed release build.

### Added

- Branded PDF/image export for Project/Tag/Person/List: accent letterhead, stat chips, grouping,
  tag chips, assignee names, include/exclude completed with an age cutoff, strike-off toggle, and
  Compact/Relaxed layouts.
- Natural-language quick-add gained word-based priority, flag detection, and more relative-date and
  recurrence words.

### Changed

- Redesigned priority indicator (dots plus a coloured edge stripe).
- Equal-width hero stat cards.

[Unreleased]: https://github.com/rjwarrier/yata/compare/v0.85-beta...HEAD
[0.85 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.85-beta
[0.7 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.7-beta
[0.6 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.6-beta
[0.5]: https://github.com/rjwarrier/yata/releases/tag/v0.5
