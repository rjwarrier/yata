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

### Fixed

- **Backup and restore no longer lose "waiting on" dates or a project's sections.** Both shipped in
  0.88 beta as database fields but were never written to the backup file, so restoring a backup
  silently dropped them. Backups taken with 0.88 beta are missing this data and cannot recover it;
  backups taken from here on carry both. Older backups restore as before.
- **Home-screen widgets no longer show tasks the app has hidden from Today.** The Today, Progress
  and Upcoming widgets, and the daily agenda notification, ignored start dates entirely — a task
  deferred to next week still sat on the launcher — and the same gap applied to the new "waiting
  on" dates. The Upcoming/Calendar tab and Next 10 Days still show deferred tasks on their due
  date, which is intended: a start date only keeps work out of the day view.

## [0.88 beta] - 2026-08-01

`versionCode 10`. Upgrades in place over 0.87 beta; the database migrates from 27 to 29 with
existing tasks preserved.

### Added

- **Multiselect and bulk actions on Project/List/Tag/Person detail screens.** Long-press a task to
  select it, then complete, tag, assign, move, reschedule, duplicate or delete the whole selection
  at once — previously only available on Today, Upcoming and Search.
- **Workload hints when bulk-assigning.** The bulk "Assign" sheet now shows each person's open and
  overdue count, so assigning doesn't mean guessing who's already underwater.
- **Owner vs. collaborator on multi-assignee tasks.** The first person assigned to a task is its
  owner (rendered larger in the assignee stack, labeled "Owner" on the task detail screen);
  Analytics' delegation stats now key off ownership rather than mere presence in the assignee list.
- **"Waiting on" follow-up dates.** A task delegated to someone else can be given a follow-up
  date from its detail screen; until that date arrives it's hidden from Today (it stays fully live
  everywhere else) and reappears on its own once it does.
- **Saved views reachable from the command palette.** Filter combinations saved from Search (
  previously only listed in the drawer's Custom Views section) now also show up in the command
  palette, searchable alongside the built-in presets.
- **User-defined sections inside a project.** Add headings like "Design" or "Backend" from a
  project's "Manage sections" menu; tasks are grouped under them (with an implicit "No section"
  bucket) and reassigned from the task detail screen. Projects without sections keep the existing
  flat, drag-to-reorder Pending/Completed list.
- **Cloud backup retention slider.** Settings → Cloud lets you choose how many cloud backups to
  keep (2–15, default 5) — older ones are pruned automatically after each backup, same as before,
  just at a count you control instead of a fixed 5.
- **Simplified "Backup frequency" to a 4-option picker.** Right after any change in a task, or
  every 30 / 60 / 120 minutes — replaces the old free-form number + Minutes/Hours/Days entry.
- **New "Search & Saved Views" section in Help & About**, plus updated Projects/Lists, People and
  Backup & Export sections covering everything above.
- Help & About's build line now includes the exact build timestamp
  (`Build <versionCode>.<DDMMYYYYHHmm>`), not just the version code.

### Changed

- The Calendar month heading is bigger, bold, and sits in a pill.

### Fixed

- The "yata" wordmark in Help & About now actually renders in Bodoni Moda's bold, high-contrast
  display cut — it was silently falling back to the variable font's plain default instance since
  nothing told it which `wght`/`opsz` axis values to use.
- The backup-frequency picker's selection indicator is animated again (was a static checkmark that
  just popped in and out).

## [0.87 beta] - 2026-08-01

`versionCode 9`. Upgrades in place over 0.86 beta; the database migrates from 26 to 27 with
existing tasks preserved.

### Added

- **Analytics rebuilt around delegated work.** A new Delegation card shows how open work splits
  between people you've handed it to, your own, and nothing assigned yet — plus median turnaround,
  the age of your oldest open task, and how much open work has no due date at all. A Per Assignee
  section ranks everyone by what's late rather than by volume, showing what each person holds, how
  much they finished this period, their on-time rate and how fast work typically moves through
  them. Unlike the existing By Person breakdown it doesn't hide someone whose work all falls
  outside the selected period — a teammate sitting on old undated tasks is exactly who you need
  to see.
- **Insight callouts at the top of Analytics** — at most four, worst first: who has the most
  overdue, anyone carrying well above the team median, how much is unassigned, the least-finished
  group across projects *and* tags *and* lists, and anything open for more than 30 days.
- **A By List breakdown in Analytics.** Lists were the one organising axis with no analytics at
  all, despite sitting beside projects and tags everywhere else.
- **Task age and turnaround.** Tasks now record when they were created, so Analytics can show how
  long work takes from creation to completion and how long open work has been sitting. Tasks that
  existed before this update have no creation date and are left out of those figures rather than
  being counted as new.
- **Subtask progress on task rows.** A task with a checklist now shows "3/5" wherever it's listed
  — Today, Upcoming, Project, List, Tag, Person, Search and Next 10 Days — and the count turns the
  list's colour once everything is ticked. Tasks without subtasks look exactly as before.

### Changed

- **Analytics periods now mean "what happened", not "what was due".** 7 Days and 30 Days used to
  keep only tasks whose *due date* landed in the window, so a task with no due date appeared under
  All Time and nowhere else, and work finished this week but due last month didn't register as
  work done. They now cover anything completed, due or created in the window, plus everything
  still open. Expect the figures to read higher than before — the previous ones were leaving out
  most undated work.
- **Changing month in the calendar is smoother.** The grid used to empty and refill one cell at a
  time, and going back a month looked identical to going forward. The whole grid now slides as one
  piece in the direction you're travelling, and the card resizes with it instead of snapping when
  a month needs a different number of rows.
- **The drawer's "Tools" section is gone**, and with it a collapsible header hiding eight more
  rows. The six views that lived there — My Work, Focus Mode, Morning Review, Evening Review,
  Stale Nudges and Task Health — are now in the command palette, where you can type "stale" or
  "overdue" to reach them instead of remembering where they sat in a menu. Next 10 Days was
  already on Today's top bar. The drawer now ends at a single Command palette entry.
- **Task lists redraw less.** Ticking one task off used to cause every task visible on screen to be
  rebuilt, not just the one that changed; the progress rings on the People, Tags and Projects tabs
  also animated a decorative wave continuously, at sizes too small for it to be visible, rebuilding
  their shape from scratch on every frame. Both are fixed, so long lists scroll better and the app
  is easier on the battery when left open.
- **The app holds on to less memory.** Profile photos were cached by count rather than by size, so
  up to about five megabytes of images could sit in memory for as long as the app was running, and
  nothing released them when the system came under pressure. The cache is now measured in actual
  memory used and is emptied when you leave the app — which also makes Android less likely to close
  it in the background and lose where you were.

### Fixed

- **Changing what a swipe does could keep performing the old action.** Setting a direction to
  Snooze or Delete in Settings and then swiping a row already on screen updated the icon behind it
  but could still carry out whatever action was configured when that row first appeared, until it
  scrolled off and back. Newly-appearing rows were unaffected.
- **The Today header ignored the Date format setting** once drawn — it read correctly on the first
  frame but didn't update if you changed Day-first/Month-first/ISO while the tab was already open.
- **Reduce Motion was only ever shortening animations, never removing them.** It now takes effect
  on anything already on screen the moment you toggle it, rather than only on animations that
  start afterwards, and large movements are replaced rather than sped up — screen transitions and
  the Upcoming calendar cross-fade instead of sliding, the calendar no longer staggers its cells in
  one after another, and the decorative wave on progress rings stops entirely.
- Project and List detail's drag-to-reorder rows now fade in and out consistently with every other
  task list in the app, instead of popping in and out abruptly.
- **Today and every other task list could show the wrong day.** They read the date once when
  first opened and never again, so leaving the app open (or merely backgrounded — the process
  keeps running) across midnight left Today showing yesterday's tasks, overdue badges wrong, and
  deferred tasks not yet un-deferred, until the app was force-closed and relaunched.
- **Searching by subtask title missed archived and trashed tasks.** It worked everywhere else, so
  a search that should have found a shelved task quietly returned nothing — matching on the task's
  own title, notes, people and tags but never its checklist.

## [0.86 beta] - 2026-07-31

`versionCode 8`. Upgrades in place over 0.85 beta.

### Added

- **`+project` and `=list` while typing a task**, alongside the `#tag` and `@person` that already
  worked. All four now pop the same picker as you type, so a project or a list no longer needs the
  longhand "project Foo" phrasing with nothing on screen to confirm it was understood. Picking a
  project clears the list and vice versa, since a task can only be in one. There's no "create new"
  option on these two — a project or list is more than a name, so it's better made properly than
  conjured mid-sentence.
- **Export a tag or a person as Markdown**, from the ⋮ menu on their screen — the same option
  projects already had. Copies a checklist of the open tasks to the clipboard and opens the share
  sheet.
- **Time format** (Settings → Display). Follow the device, or force 12-hour or 24-hour. Times were
  hardcoded to 12-hour everywhere, so a phone set to a 24-hour clock still read "5:00 PM" and got a
  12-hour time picker. Changing this never rewrites a task — the stored value is untouched, only
  how it's drawn changes.
- **Date format** (Settings → Display). Follow the device's language, or pick day-first
  ("4 Jul"), month-first ("Jul 4") or ISO ("2026-07-04"). Dates were hardcoded to month-first;
  setting the phone to English (UK) or English (India) translated the month's *name* but still
  wrote the fields in US order. This also decides how smart add reads an ambiguous typed date, so
  "3/4" means the 3rd of April on a day-first setting and the 4th of March on a month-first one.
- **Choose what a swipe does** (Settings → Sound & Feedback). Each direction can be set to
  complete, delete, snooze to tomorrow, edit the title, or nothing at all. Right-to-complete and
  left-to-delete stay the defaults.
- **Open on a fixed tab** (Settings → Navigation). The app can start on a tab of your choosing
  instead of wherever you were last. A tab that's switched off in Features falls back to Today.
- **Confetti can be turned off on its own** (Settings → Sound & Feedback). Previously the only way
  to stop it was Reduce Motion, which costs you every other animation too.
- A **Sound & vibration** row (Settings → Notifications) opening Android's own notification
  settings for YATA, where tone and vibration are set per notification type.
- **Confetti when you clear the day.** Finishing the last open task on Today gets a brief
  celebration. Only for actually finishing it — deleting the last one doesn't count — and only for
  clearing the whole day, not a filtered slice of it. Skipped entirely if Reduce Motion is on, in
  the app or system-wide.
- Task rows now dip slightly when pressed, the same feedback the buttons and chips already gave.
- **Task cards** (Settings → Display). Off by default. Turn it on and every task draws as its own
  rounded card instead of a flat row, everywhere tasks are listed — Today, Upcoming, Project, List,
  Tag, Person, Search and Next 10 Days. Works with all three row densities and with the priority
  colour stripe, which follows the card's rounded left edge.

- **Color intensity and Background tint sliders** (Settings → Appearance). Muted to Pop for how
  saturated the accent colors are, over four stops; Clean to Max for how much of the theme color
  carries into page and card backgrounds, over ten. Both scale whatever scheme you already have
  rather than replacing it, so they work on top of Material You, a custom seed colour, or the
  built-in palette, and your wallpaper's hue survives at every stop. The defaults change nothing
  about an existing theme until you move a slider.
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
- **Smart add understands a lot more.** Typing a new task now picks up:
  - **Repeats** — "every weekday", "every weekend", "every monday and wednesday", "every mon, wed,
    fri", "each monday" (`each` works anywhere `every` does), "every month on the 15th", "every 1st
    of the month", "every last day of the month".
  - **When a repeat stops** — "every 3 days until august 15", "daily standup for 10 times".
  - **More dates** — "next year", "end of year", "next quarter", "end of quarter", "in 2 years",
    "a week today", "wednesday next week", "beginning of next month", "later this week", "over the
    weekend", "mid january", "in a couple of days", "in a few weeks", "in 3 business days" (skips
    the weekend), "on the first"/"the twenty-first" spelled out, and dates written "20.07.2026" or
    "20-07-2026".
  - **More times** — "this morning", "this afternoon", "this evening", "later today", "5ish",
    "noon-ish", "first thing", "cob", and mealtimes behind a preposition ("call the bank at lunch").
  - **More priorities** — "not urgent", "backburner", "when i can", "nice to have", "eventually",
    "drop everything", and the "prio" short forms.
- **60 more icons to choose from** for projects and lists — food and drink, sport and outdoors,
  more travel and transport, health, media, and work icons like handshake, campaign and inventory.
  The picker also shows more of them at once before you have to scroll.

### Changed

- **The app lock screen is a real lock screen now.** The keypad is on screen from the start rather
  than behind a "use PIN instead" button, the keys are 76dp circles that dip as you press them, the
  dots show how long your PIN is and swell as they fill, and a wrong PIN shakes them — with a
  double buzz that's deliberately unlike the tick of a keypress — instead of printing a line of
  small red text. It unlocks as soon as the last digit lands, with no confirm button. The screen
  itself rises into place when it appears, the message under the dots crossfades rather than
  jumping, and the whole layout is spread out: header in the upper third, keypad low enough to
  reach with a thumb. The fingerprint prompt still comes up first, with a fingerprint key at the
  bottom-left of the pad to call it back. Every animation is skipped when Reduce Motion is on.
- **The profile picture in the top bar matches the buttons beside it.** It was drawn smaller than
  the search and select buttons it sits next to, so the row of circles stepped down at the end.
- **Settings section headings are easier to find.** Each of the fourteen headings now carries an
  icon for what it covers and is set in title-sized text rather than the same small caption size
  used for the description under a toggle, so a long scroll has landmarks to scan by.
- **Tags now show their own initial** instead of the same generic label icon on every one. The Tags
  tab and each tag's own screen draw the first letter of the tag's name — two letters if the name
  has more than one word — in the tag's colour. Nothing to set up; it follows the name.

- The task title in the New Task sheet is now the clear focal point of the screen: a tonal
  container with the largest rounding and type on the sheet, and a focus ring, instead of a thin
  underline that read as the least important control there. The mic button sits inside it.
- Notes, comment and subtask fields are filled tonal surfaces with generous rounding instead of
  outlined boxes, which sat oddly against the tonal cards around them. The project, list, tag and
  person editor sheets now match.
- "Create another" in the New Task sheet is a switch rather than a checkbox, and the whole row is
  one target: it's a mode that takes effect immediately, not something submitted with the task.
- The top bar buttons on the person, tag, project and list screens now sit in the same circular
  containers as the ones on the main tabs, and hide-completed fills in while it's on rather than
  only swapping its icon. Every task list in the app has a consistent top bar again.
- **Dark mode is a few points lighter**, off the near-black it used to sit at. AMOLED is untouched
  and remains the true-black option, so the two now divide cleanly into a comfortable dark and a
  panel-off black.
- **People tab is tighter.** The gap between cards was being applied twice and came out at 24dp;
  it is now 10dp, so roughly two more people fit on screen.
- Each person's open-task count moved from a badge clipped to the corner of their photo into the
  middle of the progress ring on the right, next to the "N assigned · M done" line it belongs with.

### Removed

- The dashed "New tag", "Add person" and "New project" rows on the Tags, People and Projects tabs.
  Each duplicated the button already floating over the same screen. Note that if you have the
  quick-add button set to Hidden, these three tabs no longer offer any way to create a tag, a
  person or a project.

### Security

- **The app-lock PIN is hashed properly.** It was a single round of salted SHA-256, which for a
  4-digit PIN means the entire range of possibilities could be checked in well under a second by
  anyone who got hold of the preferences file. It now uses PBKDF2 with 120,000 iterations, and the
  check is constant-time. Your existing PIN keeps working and is upgraded silently the next time
  you unlock — nothing to re-enter.
- **Repeated wrong PINs now back off**, pausing entry for 30 seconds after five wrong tries and
  longer after that. The count survives closing the app, so force-stopping doesn't reset it.
- The biometric prompt reports real failures. Previously anything other than success was swallowed,
  so a prompt that failed for any reason simply vanished with no explanation.
- App Lock no longer strands you if you remove every biometric and screen lock from the device
  after switching it on and never set a PIN. Previously that combination left no way in short of
  clearing app data.

### Fixed

- **No more white flash when the app opens.** Launching showed a blank white screen before the
  first frame — the platform default, which was especially jarring on the dark and AMOLED themes.
  There's a proper splash screen now, on a background that matches the app icon and follows your
  system's light or dark setting, and it holds just long enough for your saved theme to load so
  the app no longer opens in the wrong one for a moment.
- **A person's photo now shows everywhere their avatar appears.** Five places drew initials on a
  coloured circle instead of the avatar, so someone with a profile photo showed their face on a
  task row and their initials in the assignee chips beside it — on the task detail screen, in the
  new-task sheet and in the @-mention picker.
- **More of the app is translatable.** The tab titles, drawer section headers, empty-state text on
  Projects, People, Tags, Upcoming and Search, and the "YOU" badge were all still hardcoded
  English and would have stayed English in any other language.
- **White text on light accent colours was hard to read** — worst on the yellow, lime and green
  ones, where initials on a person's avatar all but disappeared. The ink is now chosen per colour
  by measuring which of light or dark actually reads better against it, so twelve of the sixteen
  light-theme accents switched to dark text. This covers people's initials everywhere they appear
  (avatars, assignee stacks, the mention picker, the new-task sheet), the add button on the
  project, list, tag and person screens, and custom accent colours picked by hex, which no fixed
  choice of ink could ever have suited. Dark theme is unchanged — its accents are pastels that
  were already taking dark text.
- **Initials on profile circles are bigger**, and now scale with the circle instead of stepping
  between four fixed sizes. The same initials used to look a different weight from one screen to
  the next — a 56dp avatar took the same text size as a 40dp one — and the small assignee circles
  on task rows were the worst affected. Applies everywhere a person appears: task rows, the task
  detail screen, the new-task sheet, the mention picker and the People tab.
- The Export button in the image and PDF export sheets was squashed flat. The options above it
  were laid out first and the buttons got whatever height was left, so on a shorter screen — or
  with the PDF page-size options showing — there was barely any. The options scroll now and the
  Cancel/Export row stays put at the bottom at full size.
- **Smart add read some phrases as the opposite of what they said.** "a week today" resolved to
  today, "every week until dec 20" put the *end* of the repeat in the due date and then swallowed
  the rest of the title, "wednesday next week" ignored the Wednesday, "beginning of next month"
  landed on today's date a month out, "not important" flagged the task as important, and
  "eod friday" meant today rather than Friday.
- The Projects tab showed the same generic layers icon on every project, ignoring the icon picked
  for it. Projects now show their own icon in the list, as the project screen already did.
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

[Unreleased]: https://github.com/rjwarrier/yata/compare/v0.88-beta...HEAD
[0.88 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.88-beta
[0.87 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.87-beta
[0.86 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.86-beta
[0.85 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.85-beta
[0.7 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.7-beta
[0.6 beta]: https://github.com/rjwarrier/yata/releases/tag/v0.6-beta
[0.5]: https://github.com/rjwarrier/yata/releases/tag/v0.5
