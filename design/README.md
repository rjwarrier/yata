# Handoff: YATA — Android Task Manager UI

## Overview
YATA ("Yet Another Task App") is a Material 3 Expressive task manager for Android. This handoff covers the full set of screens: Today, Upcoming, Inbox, Browse/Lists, List Detail, Task Detail, Search, Labels, Settings, a New Task bottom sheet, and a Navigation Drawer.

## About the Design Files
The files in this bundle are **high-fidelity design references built in HTML + React** — interactive prototypes showing intended look, layout, and behavior. They are NOT production code to copy directly. Your task is to **recreate these designs in the target Android codebase** using Jetpack Compose, Material 3 components, and any existing design system conventions. The HTML files are there for you to run in a browser so you can interact with every screen and understand navigation flows before implementing.

To run the prototype:
1. Open `YATA Prototype.html` in a browser (Chrome recommended).
2. Tap around — bottom nav, task rows, FAB, menu icon, list cards.
3. Press `Esc` to go back from any pushed screen.

## Fidelity
**High-fidelity.** Colors, typography, spacing, corner radii, shadows, animations, and interaction states are all specified precisely. Recreate the UI pixel-accurately using Compose + Material 3, substituting HTML/CSS constructs with their Compose equivalents (e.g. `LazyColumn`, `BottomSheetScaffold`, `NavigationBar`, etc.).

---

## Design Tokens

### Color Palette (M3 Expressive — Warm Coral)

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#8E4A3B` | Primary actions, active states |
| `onPrimary` | `#FFFFFF` | Text/icon on primary |
| `primaryContainer` | `#FFDAD1` | Tonal containers, chips |
| `onPrimaryContainer` | `#3A0B01` | Text on primary container |
| `secondary` | `#5D6140` | Secondary actions |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#E2E6BC` | Active nav indicator, selected chips |
| `onSecondaryContainer` | `#1B1D04` | |
| `tertiary` | `#5F5791` | Lavender accents, avatar, tertiary chips |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#E5DEFF` | Tertiary tonal containers |
| `onTertiaryContainer` | `#1B1148` | |
| `error` | `#BA1A1A` | Errors, high-priority flags |
| `errorContainer` | `#FFDAD6` | |
| `surface` | `#FFF8F6` | Main background |
| `surfaceContainerLowest` | `#FFFFFF` | Sheet background |
| `surfaceContainerLow` | `#FFF0EC` | Card backgrounds |
| `surfaceContainer` | `#FCEAE4` | Bottom nav background |
| `surfaceContainerHigh` | `#F6E4DE` | Input fields, icon bg |
| `surfaceContainerHighest` | `#F0DED8` | Filled text fields |
| `onSurface` | `#231916` | Primary text |
| `onSurfaceVariant` | `#53433F` | Secondary text, icons |
| `outline` | `#85736E` | Borders, dividers |
| `outlineVariant` | `#D8C2BC` | Subtle dividers |

**List / accent colors** (for list color dots and card tints):
- Peach: `#F2B8A6`
- Lime: `#C7D08A`
- Lavender: `#B8A8E8`
- Mustard: `#F5D06F`
- Sage: `#8CC4A3`
- Rose: `#E69AB8`

### Shape Scale (M3 Expressive)
| Name | Radius |
|---|---|
| XS | 8dp |
| SM | 12dp |
| MD | 16dp |
| LG | 20dp |
| XL | 28dp |
| Full | 9999dp (pill) |

### Typography
- **Display / Headline / Title**: Inter Tight, weights 500–600, letter-spacing −0.01em to −0.02em
- **Body**: Inter, weight 400
- **Label**: Inter, weight 500, letter-spacing +0.01em
- **Mono**: JetBrains Mono (used sparingly for time values)

| Role | Size | Weight |
|---|---|---|
| Display large | 34sp | 500 |
| Title large | 20sp | 600 |
| Title medium | 17sp | 600 |
| Body large | 16sp | 400 |
| Body medium | 15sp | 400 |
| Label large | 14sp | 500–600 |
| Label medium | 13sp | 500 |
| Label small | 11–12sp | 500 |

### Elevation / Shadows
- L1: `0 1px 2px rgba(35,25,22,0.10), 0 1px 3px 1px rgba(35,25,22,0.06)`
- L2: `0 1px 2px rgba(35,25,22,0.10), 0 2px 6px 2px rgba(35,25,22,0.08)`
- L3: `0 4px 8px 3px rgba(35,25,22,0.08), 0 1px 3px rgba(35,25,22,0.12)`

---

## Screens

### 1. Today (`PTToday`)
**Purpose**: The home screen. Shows tasks grouped by time-of-day section (Morning, Afternoon) for today's date.

**Layout**:
- **Top bar** (56dp tall): Leading = hamburger menu icon → opens Navigation Drawer. Trailing = search icon + user avatar (32dp circle, tertiary color, initial "M").
- **Date + stats area** (below top bar, ~80dp): Date label in primary color, uppercase, 13sp, `Thursday · May 16`. Below: two pill-shaped chips side by side — "N tasks due today" (primaryContainer tint, 38dp tall, full radius) and "N done" (surfaceContainerHigh, sparkle icon).
- **Filter chip row** (32dp tall, horizontally scrollable, no visible scrollbar): Chips = All (selected, tertiaryContainer), Priority, Work, Personal.
- **Task list** (`LazyColumn`, fills remaining space, bottom padding 100dp to clear FAB + nav): Grouped under `SectionHeader` dividers (Morning, Afternoon).
- **Bottom nav** (72dp): 4 items — Today, Upcoming, Inbox, Browse.
- **FAB** (extended, "New task", add icon, primaryContainer color): positioned `bottom=84dp, end=20dp`. Disappears (scale to 0.3 + opacity 0) when the New Task sheet is open.

**Task row layout** (min-height 56dp, padding 11dp top/bottom, 20dp sides):
- Leading: 24dp circular checkbox. Filled = list color when checked.
- Title: Body large (16sp). Strike-through + `onSurfaceVariant` + 0.7 opacity when done.
- Meta row (6dp below title): clock icon + time, dot + list name — both 12sp `onSurfaceVariant`.
- Trailing: flag icon in `error` color (if flagged), or 8dp red dot (if high priority).

**Tap behaviors**:
- Tap checkbox → toggle done state (animated).
- Tap task body → push Task Detail screen.

---

### 2. Upcoming (`PTUpcoming`)
**Purpose**: Browse tasks across a 7-day rolling week.

**Layout**:
- **Top bar**: Title "Upcoming", menu + calendar + more icons.
- **Week strip** (below bar, ~110dp): Month/year header with chevron. 7 day columns, each showing 2-letter day abbrev (11sp uppercase) + date number (18sp, weight 600). Selected day: primary background, full radius, white text. Days with tasks: 4dp primary dot below number.
- **Date label**: 13sp primary color uppercase e.g. "THURSDAY · MAY 16".
- **Timeline events** (`LazyColumn`): Each event = 48dp wide time column (12sp tabular-nums, `onSurfaceVariant`) + card (`surfaceContainer` bg, `SHAPE.LG=20dp` radius, 4dp left border in list color, padding 12dp×14dp). Card shows title (15sp weight 500) + list name + duration (12sp `onSurfaceVariant`).

---

### 3. Inbox (`PTInbox`)
**Purpose**: Quick-capture area — tasks added without sorting go here.

**Layout**:
- **Top bar** (large): Title "Inbox", subtitle "Capture now, organize later".
- **Stats row**: tertiaryContainer chip showing item count + sparkle icon; "Auto-sort" chip.
- **Item cards** (`LazyColumn`, gap 10dp): Each card = `surfaceContainerLow` bg, `SHAPE.LG` radius, padding 14dp. Leading 8dp primary dot. Title (15sp) + "Added N ago" label (12sp). Below title: row of suggestion chips ("To Work", "To Reading", "Today"). Trailing: more icon.

---

### 4. Browse (`PTBrowse`)
**Purpose**: All lists and smart views in one place.

**Layout**:
- **Top bar** (large): Title "Browse".
- **Smart views grid** (2-column, gap 10dp, 96dp tall cards): Today, Upcoming, Priority, All. Each card = tinted background (container colors), icon, title (18sp weight 600), task count (13sp 70% opacity).
- **"My lists" section**: Section label + add icon. Below: single `surfaceContainerLow` card with dividers between rows. Each row = 36dp square icon with list color bg + 18dp icon, list name (15sp), count (13sp), chevron.
- **Labels row**: Same card style, single row, peach accent.

---

### 5. List Detail (`PTListDetail`)
**Purpose**: All tasks within a single list.

**Layout**:
- **Colored header** (list's accent color bg): Back button + sort + more. Below: 44dp icon tile (dark tinted bg) + list name (32sp display) + task/done count. Progress bar (6dp tall, dark tinted track, `onSurface` fill, animated width).
- **Filter chips** (scrollable row below header).
- **Task list** (same row design as Today screen).
- **FAB** (extended, "Add to [List Name]"): `position=absolute, bottom=28dp, end=20dp`.

---

### 6. Task Detail (`PTTaskDetail`)
**Purpose**: Full view of a single task with all metadata.

**Layout**:
- **Top bar**: Back + star (primary color) + archive + more.
- **Title area**: 28dp checkbox + 26sp display title. Strike-through + 0.6 opacity when done.
- **Metadata rows** (8dp gap between): Each row = `surfaceContainerLow` bg, `SHAPE.MD` radius, 32dp icon tile (`surfaceContainerHigh` bg), label (11sp uppercase `onSurfaceVariant`) + value (15sp weight 500).
  - Calendar → date + time
  - Bell → reminder
  - Repeat → recurrence
  - Folder → list (with list color swatch dot)
  - Flag → priority (error color if high)
- **Labels section**: 12sp uppercase header + wrap row of selected chips.
- **Subtasks section**: Header + count badge + 4dp progress bar. Each subtask = 20dp checkbox + 14sp title. Strike-through when done.
- **Notes section**: `surfaceContainerLow` card, 12sp "Notes" label, 14sp body text, 1.5 line-height.

---

### 7. Search (`PTSearch`)
**Purpose**: Full-screen search across all tasks, lists, and labels.

**Layout**:
- **Search bar** (52dp, `surfaceContainerHigh` bg, full radius): Back arrow + text input + clear button + mic icon + avatar.
- **Filter chips** (scrollable, shown when query non-empty): Tasks, Lists, Labels, Notes counts.
- **Results list**: Section header "Top matches". Each result = checkbox + title (with `primaryContainer` highlight on matching substring) + list name + time.
- **Empty query state**: "Recent" section with clock icon + past search strings.

---

### 8. Labels (`PTLabels`)
**Purpose**: Manage and browse all labels.

**Layout**:
- **Top bar**: Back + title + search + add.
- **Description text** (14sp, 1.5 line-height).
- **Label chip cloud** (wrap flow, gap 8dp): Each chip = label's accent color bg, tag icon, name, count (60% opacity, 12sp).
- **"Recently used" list**: Each row = 36dp square icon tile + label name + "N tasks · used Xh ago" + more icon.

---

### 9. Settings (`PTSettings`)
**Purpose**: App preferences.

**Layout**:
- **Profile card** (`tertiaryContainer` bg, `SHAPE.XL` radius): 52dp avatar + name (17sp weight 600) + subtitle + "Manage" tonal button.
- **Settings groups** (Account, Appearance, Defaults): Each group = uppercase label + `surfaceContainerLow` card with divider rows. Each row = 36dp icon tile (`surfaceContainerHigh` bg) + title (15sp) + subtitle (12sp) + trailing chevron or toggle.
- **Toggle**: 52dp×32dp pill. On = primary bg + white 24dp thumb; Off = `surfaceContainerHigh` + `outline` 16dp thumb. Animated position and size.

---

### 10. New Task Sheet (`PTNewTaskSheet`)
**Purpose**: Quick task entry, presented as a bottom sheet.

**Layout**:
- **Drag handle**: 32dp×4dp pill, centered, `outline` color, 50% opacity.
- **Title input**: 22sp display font, `transparent` bg, `primary` 2dp bottom border, full width. Placeholder "Draft retro agenda".
- **Notes hint**: 13sp `onSurfaceVariant` "Add notes or details…"
- **Pre-set chips row** (wrap): Today · 4PM (primaryContainer), High (errorContainer), Work (accentC), + label, + remind.
- **Toolbar** (border-top, 10dp padding): Calendar, flag, tag, subtask, attach, mic icon buttons (48dp targets). Trailing = 40dp send FAB (primary tonal).

**Behavior**: Keyboard shows on mount (350ms delay). Enter submits. Tapping scrim closes.

---

### 11. Navigation Drawer (`PTDrawer`)
**Purpose**: Global navigation + list switching.

**Layout**: 85% screen width, `surface` bg, `border-top-right-radius` and `border-bottom-right-radius` 28dp.
- **Header** (24dp top padding): 44dp avatar + name (17sp) + email (12sp).
- **Nav rows** (full-width pill shape): Today, Upcoming, Inbox, Priority — each with icon + label + count badge.
- **"My lists" section label** (uppercase 12sp).
- **List rows**: 20dp colored square + list name + count.
- **Footer**: Settings row (above bottom edge, separated by `outlineVariant` border-top).

---

## Interactions & Animations

| Interaction | Duration | Easing |
|---|---|---|
| Screen push/pop (slide in/out) | 380ms | `cubic-bezier(0.05, 0.7, 0.1, 1)` (emphDecel) |
| Bottom sheet slide up/down | 320ms | `cubic-bezier(0.05, 0.7, 0.1, 1)` |
| Drawer slide in/out | 320ms | `cubic-bezier(0.05, 0.7, 0.1, 1)` |
| Tab cross-fade | 200ms | `cubic-bezier(0.2, 0, 0, 1)` |
| Micro interactions (press scale, checkbox) | 140ms | `cubic-bezier(0.2, 0, 0, 1)` |
| FAB spring appear | 200ms | `cubic-bezier(0.34, 1.56, 0.64, 1)` |
| Progress bar width | 380ms | emphDecel |
| Subtask strikethrough | 200ms | fade |

**Press feedback**: All tappable items scale to `0.97` on pointer-down, back to `1.0` on release.

**Screen push transition**:
- Incoming screen: slides in from `translateX(100%)` → `translateX(0)`, opacity 0 → 1.
- Previous screen (if any): shifts to `translateX(-30%)`, opacity → 0.5.
- On pop: reverse.

**Drawer**: Scrim fades in (rgba 0,0,0,0.4). Drawer slides from `translateX(-100%)` → `0`.

**Sheet**: Scrim fades in (rgba 0,0,0,0.35). Sheet slides from `translateY(100%)` → `0`.

---

## State Management

```
AppState {
  tasks: Task[]
  lists: List[]
}

Task {
  id: String
  title: String
  section: "Morning" | "Afternoon"
  list: String          // display name
  listId: String        // FK to List
  listColor: Color
  done: Boolean
  priority: Boolean     // high priority dot
  flag: Boolean         // flagged (red flag icon)
  time: String?         // e.g. "9:00 AM"
  notes: String?
  labels: String[]?
  subtasks: Subtask[]?
}

Subtask {
  id: String
  title: String
  done: Boolean
}

List {
  id: String
  name: String
  count: Int
  color: Color
  icon: String
}
```

**Actions**:
- `toggleDone(taskId)` — flips `done`, triggers strikethrough animation
- `toggleSubtask(taskId, subId)` — flips subtask `done`, updates subtask progress bar
- `addTask({ title, listId })` — appends task to state, appears at bottom of Afternoon section

---

## Assets
- All icons are custom line-icon SVGs defined inline (see `m3-tokens.jsx` → `Icon` component). Equivalent Material Symbols names: `menu`, `search`, `add`, `check`, `more_horiz`, `arrow_back`, `close`, `filter_list`, `sort`, `star`, `calendar_month`, `schedule`, `list`, `grid_view`, `home`, `label`, `inbox`, `today`, `upcoming`, `flag`, `notifications`, `folder`, `settings`, `edit`, `delete`, `arrow_forward`, `repeat`, `attach_file`, `account_tree`, `priority_high`, `archive`, `mic`, `person`, `chevron_right`, `expand_more`, `auto_awesome`.
- No external images.

---

## Files in this Package

| File | Purpose |
|---|---|
| `YATA Prototype.html` | Entry point — open in browser to run the prototype |
| `m3-tokens.jsx` | Design tokens: colors, shape, typography, elevation, Icon component |
| `m3-components.jsx` | Shared primitives: FAB, Chip, Check, TaskItem, BottomNav, TopBar, TextField, Btn, Avatar |
| `prototype-screens-1.jsx` | Today, Upcoming, Inbox, Browse screens + InteractiveTaskItem |
| `prototype-screens-2.jsx` | ListDetail, TaskDetail, Search, Labels, Settings, NewTaskSheet, Drawer |
| `prototype-app.jsx` | App shell: navigation stack, drawer/sheet overlays, phone frame, hints |
