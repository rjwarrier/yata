# YATA — Implementation Handoff for Claude Code

## ▶ Paste-this prompt

> You are building **YATA**, a lively Material 3 (Expressive) to-do manager for **Android** (Jetpack Compose, Material 3). This folder contains a working HTML/React design prototype — treat it as the **source of truth for layout, spacing, color, motion, and behavior**, and recreate it natively. Do **not** port the React code; reimplement idiomatically in Compose.
>
> Run `YATA Prototype.html` in Chrome first and click through every screen (bottom nav has 5 tabs; tap a task to open detail; open a task → "Repeats" for the recurrence builder; complete a task to see confetti; toggle Light/Dark in Settings). Then implement the app to match.
>
> **Scope for v1:** Projects (group lists) · Lists · Tags · People with local-only assignment · full RRULE recurrence · priorities & flags · due dates & reminders · subtasks · light+dark theme · the "lively" motion detailed below. Persist everything locally (Room + DataStore). No backend, no accounts.
>
> Build the data layer first (schema below), then the design system (tokens + reusable composables), then screens, then motion. Match the prototype's numbers (paddings, radii, font sizes, durations) — they're all specified in this doc and in the `.jsx` files.

---

## 1. Product overview

YATA is a personal task manager. The organizing hierarchy is **Project → List → Task**. Tasks also carry cross-cutting **Tags**, **People** (assignees, stored only on-device), **priority**, **flag**, **due date/time**, **reminder**, **subtasks**, **notes**, and an optional **recurrence rule** (RRULE).

Five primary destinations (bottom navigation):
1. **Today** — everything due today, grouped by time-of-day, with a live completion ring.
2. **Projects** — project cards, each with a progress ring, list swatches, and member avatars.
3. **People** — manage local persons; tap one to see their assigned tasks.
4. **Tags** — tag cloud with counts; tap to filter every task carrying that tag.
5. **Upcoming** — 7-day agenda strip + per-day task list.

Plus pushed screens: **Task detail, List detail, Project detail, Person detail, Tag detail, Search, Settings**. And bottom sheets: **New task, Recurrence builder, Assignee picker, Tag picker, List picker, Person editor, Project editor, Tag editor**.

---

## 2. Design tokens  (see `m3-tokens.jsx`)

The prototype ships **two full M3 palettes**, switchable at runtime. Map these to your Compose `lightColorScheme` / `darkColorScheme` plus a small set of custom "accent" colors held in a `CompositionLocal`.

### Light scheme (seed = warm coral)
| Role | Hex |
|---|---|
| primary / onPrimary | `#8E4A3B` / `#FFFFFF` |
| primaryContainer / on | `#FFDAD1` / `#3A0B01` |
| secondary / onSecondary | `#5D6140` / `#FFFFFF` |
| secondaryContainer / on | `#E2E6BC` / `#1B1D04` |
| tertiary / onTertiary | `#5F5791` / `#FFFFFF` |
| tertiaryContainer / on | `#E5DEFF` / `#1B1148` |
| error / errorContainer | `#BA1A1A` / `#FFDAD6` |
| surface | `#FFF8F6` |
| surfaceContainerLowest → Highest | `#FFFFFF`, `#FFF0EC`, `#FCEAE4`, `#F6E4DE`, `#F0DED8` |
| onSurface / onSurfaceVariant | `#231916` / `#53433F` |
| outline / outlineVariant | `#85736E` / `#D8C2BC` |

### Dark scheme
| Role | Hex |
|---|---|
| primary / onPrimary | `#FFB4A2` / `#561F11` |
| primaryContainer / on | `#723524` / `#FFDAD1` |
| secondary / secondaryContainer | `#C6CA9C` / `#454929` |
| tertiary / tertiaryContainer | `#C8BFFF` / `#484078` |
| error | `#FFB4AB` |
| surface | `#191110` |
| surfaceContainerLowest → Highest | `#130B0A`, `#221816`, `#261C1A`, `#312724`, `#3D322F` |
| onSurface / onSurfaceVariant | `#F0DED8` / `#D8C2BC` |
| outline / outlineVariant | `#A08C87` / `#53433F` |

### Accent colors (list / tag / person swatches — custom, not part of the M3 scheme)
Light & dark share most; a few brighten in dark.
`accentA #E8886B` · `accentB #9DAE55` · `accentC #8C7BE0`/dark `#A99BEE` · `accentD #E0A93A` · `accentE #4FA97D`/dark `#5CBB8C` · `accentF #DB6FA0`/dark `#E080AC` · `accentG #4A93C7`/dark `#5CA3D4` · `accentH #C77B4A`/dark `#D48C5C`.
`onAccent` = `#FFFFFF` (light) / `#1A1110` (dark). Tinted backgrounds use the accent at **16–20% alpha**; the glyph/text uses the accent at full strength.

### Shape scale (dp corner radius)
`xs 8 · sm 12 · md 16 · lg 20 · xl 28 · full (pill)`. Checkboxes, avatars, rings, FAB use these. This is the M3 **Expressive** scale — lean generous.

### Type
- Display / Headline / Title → **Inter Tight** (Compose: bundle it or swap to a similar grotesk), weights 500–600, tight tracking (−0.01 to −0.02em).
- Body → **Inter** 400. Label → Inter 500, +0.01em. Mono (RRULE preview) → **JetBrains Mono**.
- Key sizes (sp): display 25–28, title 15–22, body 14–16, label 11–14. **Never below 11sp.** Hit targets ≥ 48dp.

### Elevation
L1 `0 1 3` · L2 `0 2 6` · L3 `0 4 8`, tint `rgba(35,25,22,·)` light / `rgba(0,0,0,·)` dark.

---

## 3. Data model  (see `m3-data.jsx`)

```
Person   { id, name, initials, color: AccentKey, photoUri?: String, isMe: Boolean }
Project  { id, name, color: AccentKey, icon, listIds: [ListId] }   // order defines display
List     { id, name, color: AccentKey, icon, projectId: ProjectId }
Tag      { id, name, color: AccentKey|"error" }
Subtask  { id, title, done: Boolean }
Task {
  id, title,
  listId,                       // → project via List.projectId
  section: "Morning"|"Afternoon",   // v1 grouping bucket for Today
  due: LocalDate?, time: String?,   // "2:00 PM"
  reminder: String?,                // "15 min before"
  priority: "none"|"low"|"med"|"high",
  flag: Boolean,
  done: Boolean,
  assigneeIds: [PersonId],
  tagIds: [TagId],
  recurrence: Recurrence?,          // null = one-off
  subtasks: [Subtask],
  notes: String?
}
Recurrence {                        // maps 1:1 to RFC-5545 RRULE
  freq: "daily"|"weekly"|"monthly"|"yearly",
  interval: Int,                    // "every N"
  byday: ["MO","TU",...]?,          // weekly
  bymonthday: Int?,                 // monthly
  ends: { type: "never" } | { type: "after", count: Int } | { type: "on", date: LocalDate }
}
```

**Persistence:** Room for Person/Project/List/Tag/Task (+ join tables for assignees & tags, or JSON columns). Theme choice in DataStore. Everything is local — "People are stored on this device only" is shown to the user; honor it (no network, no contacts permission required — creating a person is name + color + auto-initials).

### Derived helpers to implement
- `progressOf(tasks) → {total, done, pct}`
- `tasksInProject`, `tasksForPerson`, `tasksForTag`
- `recurrenceSummary(r)` → human string. Rules:
  - interval 1 → "Every day/week/month/year"; else "Every N days/…".
  - weekly + byday: 5 weekdays → "on weekdays"; Sat+Sun → "on weekends"; else "on Mon, Wed".
  - monthly + bymonthday → "on the 16th" (ordinal).
  - ends after → " · N×"; ends on → " · until <date>".
- `toRRULE(r)` → `RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TH` etc. (shown live in the recurrence sheet). Use a real RRULE lib for scheduling the next occurrence when a recurring task is completed.

**Recurrence completion behavior (implement):** completing a recurring task should mark this instance done and spawn/advance to the next due date per the rule until `ends` is reached.

---

## 4. Reusable components  (see `m3-widgets.jsx`, `m3-components.jsx`)

Build these as composables first; screens compose them.

- **Check** — round checkbox, 24/28/20dp. Unchecked = 2dp `outline` ring; checked = filled with list/accent color + white check, **springy pop** (`scale 0.6→1.08→1`, ~140ms, overshoot easing).
- **ProgressRing** — SVG-style arc (Compose `Canvas`/`drawArc`), round cap, animated sweep (380ms emphasized-decelerate). Center label shows `%`. Used at 38/46/48/56/72dp.
- **PersonAvatar** — circle, initials on accent color (or photo). `ring` param draws a 2dp surface-colored border for stacking.
- **AssigneeStack** — overlapping avatars (−32% overlap), max 3–4 then a `+N` chip.
- **TagChip** — pill, accent @16% bg + accent text + a 6dp dot; sizes sm/md; optional remove ✕.
- **RecurrenceBadge** — repeat glyph + summary (compact = glyph only), tertiary color.
- **PriorityBars** — 3 ascending bars; filled count = 1/2/3 for low/med/high (color: low=green accentE, med=amber accentD, high=error). none = nothing.
- **Segmented** — M3 segmented control (used for theme, frequency, priority).
- **Chip / FAB / IconBtn / TopBar / SectionHeader / TextField / Btn / Toggle / Stepper / ColorPicker** — see `m3-components.jsx` + `sheets.jsx`.
- **Confetti** — celebration burst on task completion (~26 pieces, 6 palette colors, ~1s fall+fade). In Compose use an `AnimatedVisibility`/`Animatable` particle overlay anchored center.

---

## 5. Screens (layout specs)

Grid: content padding **20dp** horizontal. Task rows **10–11dp** vertical, min-height 52–56dp.

### Today
- Top row: menu (opens drawer) · search · profile avatar (opens Settings).
- Header: uppercase primary date label; big "N to go" (display 26sp); **ProgressRing 56dp** on the right showing today's % done.
- Filter chips (scrollable): All · Assigned to me · Priority.
- Sections **Morning / Afternoon**, each a `SectionHeader` + task rows.
- Extended FAB "New task" bottom-right (88dp above nav). Hides when a sheet opens (scale→0.3 + fade).

### Task row (the workhorse)
Left: Check (color = list color). Middle: title (ellipsis) with trailing flag icon + PriorityBars; meta line below = time · list dot+name · RecurrenceBadge(compact) · up to 2 TagChips(sm). Right: AssigneeStack (24dp). Tap check = toggle done (+ confetti when it becomes done). Tap body = open Task detail.

### Projects / Project detail
Card: 44dp rounded icon tile (accent @20%), name, "N lists · done/total", **ProgressRing 46dp**; footer row = list color swatches + AssigneeStack. Dashed "New project" CTA. Detail: colored header (accent @16%) with big **72dp ring**, member stack; then each list as a tappable header (swatch, name, done/total, chevron) followed by its first few task rows. FAB "Add task".

### People / Person detail
List of person rows (44dp avatar, name, "N assigned · M done", small progress ring, chevron); "YOU" badge on self. Dashed "Add person" CTA (opens Person editor sheet). Detail: accent header with 72dp avatar; Open / Completed task sections.

### Tags / Tag detail
Tag cloud: pills (accent @16%) with name + count badge; "New tag" dashed pill. Detail: accent header (tag glyph tile, name, counts) + tagged task list.

### Upcoming
Month label; 7-day strip (selected day = filled primary pill, dot under days that have tasks); below, the selected day's date label + task rows.

### Task detail
Back · flag toggle · archive · more. Big Check (28dp) + title (25sp, strike when done). **Meta rows** (surfaceContainerLow, 16dp radius, 32dp icon tile): Due · Reminder · **Repeats** (→ recurrence sheet, shows summary, tertiary when active) · **List** (→ list picker, shows "Project · List" + swatch) · **Priority** (tap cycles none→low→med→high, shows PriorityBars). **Assigned to** section (avatar+name pills + dashed add → assignee sheet). **Tags** section (TagChips + dashed add → tag picker). **Subtasks** (progress bar + checkable rows). **Notes** card.

### Settings
Profile card (tertiaryContainer). **Appearance → Theme** as a Light/Dark **Segmented** (persist; drives the whole app). **Manage** rows → People / Tags / Projects tabs. **Defaults** (default list, notifications, start of week).

### Sheets
- **New task** (`sheets.jsx` `PTNewTaskSheet`): title field; a row of attribute chips (Today · List · Priority · People · Tags · Repeat) that expand **inline panels** below (no nested modals) to set each; send FAB. Default assignee = "You". Quick recurrence presets here; full editor lives on the task.
- **Recurrence builder** (`PTRecurrenceSheet`): summary banner + live `RRULE:` string + enable Toggle; Frequency Segmented; "Every N" Stepper; weekly → 7 day toggle buttons; monthly → day-of-month Stepper; **Ends** = Never / After N times / On date (radio rows). This is the full RRULE editor — match it exactly.
- **Assignee / Tag / List pickers**: checkable lists; pickers offer "create new" that opens the matching editor.
- **Person editor**: live initials avatar (name → initials) + camera affordance (optional photo), name field, **ColorPicker** (8 swatches), Delete (non-self). **Project / Tag editors**: name + ColorPicker.

---

## 6. Motion & "lively" feel  (durations in `m3-widgets.jsx`)

Easings: emphasized `cubic(0.2,0,0,1)`, emphasized-decel `cubic(0.05,0.7,0.1,1)`, spring `cubic(0.34,1.56,0.64,1)`, bounce `cubic(0.68,-0.6,0.32,1.6)`.
Durations: nav 380 · sheet 340 · fade 200 · micro 140 (ms).

- **Press feedback** everywhere: scale to 0.96 (0.85 for checkboxes) on press, spring back.
- **Screen push**: incoming slides `100%→0`; outgoing shifts to `−28%` + fades to 0.5. Reverse on pop.
- **Sheets**: slide up from 100% (emphasized-decel), scrim fades to `scrim` token.
- **Checkbox**: springy pop on check; strike-through + fade on the row.
- **Progress rings & bars**: animate sweep/width on change.
- **Confetti** on completion.
- **Bottom nav**: active pill background + filled icon; animate the indicator.
- Respect `prefers-reduced-motion` / Android animator-duration-scale = 0 → show end states, skip confetti.

---

## 7. Files in this package

| File | What to mine it for |
|---|---|
| `YATA Prototype.html` | Run it. Entry point that loads everything below. |
| `m3-tokens.jsx` | Both color palettes, shape scale, type, elevation, icon set. |
| `m3-data.jsx` | Data model, seed data, all derived helpers + `recurrenceSummary`/`toRRULE`. |
| `m3-widgets.jsx` | ProgressRing, PersonAvatar, AssigneeStack, TagChip, PriorityBars, Segmented, Confetti, motion constants. |
| `m3-components.jsx` | Check, Chip, FAB, TopBar, IconBtn, SectionHeader, TextField, Btn. |
| `screens-core.jsx` | TaskRow, Today, Projects, Project detail. |
| `screens-people-tags.jsx` | People, Person detail, Tags, Tag detail. |
| `screens-detail.jsx` | Task detail, List detail, Upcoming, Search, Settings, Drawer. |
| `sheets.jsx` | New task, Recurrence (RRULE), Assignee, Tag, List pickers, Person/Project/Tag editors. |
| `prototype-app.jsx` | Nav stack, 5-tab bottom nav, theme switching, action reducers, confetti wiring, phone frame. |

### Suggested Compose module layout
```
data/        entities, dao, repo, RRULE util
designsystem/ Theme.kt (light/dark + accents CompositionLocal), Type, Shapes, tokens
ui/widgets/  Check, ProgressRing, PersonAvatar, AssigneeStack, TagChip, PriorityBars, Segmented, Confetti
ui/screens/  Today, Projects, People, Tags, Upcoming, TaskDetail, ListDetail, ProjectDetail, PersonDetail, TagDetail, Search, Settings
ui/sheets/   NewTask, Recurrence, pickers, editors
ui/nav/      NavHost + bottom bar + drawer
```

### Acceptance checklist
- [ ] 5-tab bottom nav; drawer mirrors it + lists + settings.
- [ ] Create a person (name+color) → appears in assignee picker → assign to a task → shows on the row and Person detail.
- [ ] Create/assign tags; tag detail filters correctly.
- [ ] Projects roll up list & task progress into rings.
- [ ] Full RRULE builder produces a correct `RRULE:` string and schedules next occurrence on completion.
- [ ] Priority cycles + PriorityBars; flag toggles.
- [ ] Light/dark toggle persists and recolors the whole app.
- [ ] Completing a task pops confetti; reduced-motion disables it.
- [ ] All data persists across relaunch (Room + DataStore).
