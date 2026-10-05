# Task Sharing for a Team — Design

Supersedes the framing (not the defect list) of `docs/app-links-reliability-plan.md`. That document
assumed the recipient might be anyone. The actual requirement is narrower and more useful:

> Share tasks with teammates who **all have YATA installed**.

That constraint changes what the right design is, and most of the current behaviour was built for
a different one.

## What the constraint changes

| Assumption | Anyone-can-receive | Team, all have the app |
| --- | --- | --- |
| No-app fallback | must be good | rare; needs to be sane, not polished |
| App Links verification | may fail for many | works for everyone — worth doing properly |
| Import fidelity | strip to Inbox; recipient has no shared context | **shared context exists — stripping destroys the point** |
| Duplicate handling | nuisance | frequent and confusing — same task arrives twice, from two people |
| Sender identity | unknown stranger | known colleague; "who sent this" is load-bearing |

The third row is the important one, and it is where the current implementation is actively wrong
for this use case.

## 1. Clickability is still the blocker (unchanged)

Everything below is worthless until links are tappable. `yata://` is a custom scheme and messaging
apps do not linkify custom schemes. This is Phase 0 of `docs/app-links-reliability-plan.md` and
still ships first, on `ranjithj.in` with a verified `assetlinks.json`.

The team constraint makes it *easier*, not harder: every recipient has the app, so once
verification is in place, links open YATA directly with no chooser and no browser bounce.

**Keep the payload in the URL fragment** even though the team has the app. Two reasons survive the
constraint:

- Messaging apps fetch **link previews server-side** — WhatsApp, Slack, Signal and Telegram all
  do. A query-string payload would be sent to the web server by the *preview crawler* even when
  every human recipient has the app and never opens a browser. Fragments are not transmitted, so
  the crawler fetches a bare `https://ranjithj.in/yata/i` and sees nothing.
- The resulting preview is therefore generic ("A YATA task was shared with you") rather than
  showing the task title. That is the correct trade: a preview card that renders task content
  would leak it into every group chat's preview cache.

## 2. Import fidelity: the real design change

Today every import path strips `due`, `startDate`, `time`, `reminder`, `recurrence`,
`estimateMinutes`, `section` and `followUpAt`, forcing the task into the recipient's Inbox with no
schedule. For sharing with a stranger that is defensible. For a team it defeats the purpose: a
teammate sent "Ship release notes, due Friday, ~2h" and the recipient receives "Ship release
notes".

The right rule is not "strip everything" or "keep everything", but a split by **what the field
describes**:

| Field | Travels | Why |
| --- | --- | --- |
| `title`, `notes`, `subtasks` | **Yes** | The work itself |
| `priority`, `flag` | **Yes** | Already does |
| `due`, `time` | **Yes** (change) | A deadline is a property of the work, not of who holds it |
| `startDate` | **Yes** (change) | "Not actionable before" is likewise about the work |
| `recurrence` | **Yes** (change) | Shared recurring duties are a real team pattern |
| `estimateMinutes` | **Yes** (change) | Effort is a property of the task, not the person |
| `reminder` | No | *My* nudge preference; imposing it on a colleague is wrong |
| `section` | No | The sender's private layout within their project |
| `followUpAt` | No | The sender's delegation tracking; meaningless on the receiving side |
| `done`, `completedAt` | No | The recipient has not done the work |
| `archived`, `deletedAt`, `seriesId`, `createdAt` | No | Sender-local lifecycle state |
| `sortOrder` | No | Recomputed locally — see defect 1 in the reliability plan |

Dates travel as **absolute ISO strings**, never as offsets from today: a link tapped two days
after it was sent must still mean Friday.

This is a payload change, so it lands with the same version discipline as v3.

## 3. Identity: stop creating duplicates

In a team the same task legitimately arrives more than once — resent after an edit, or forwarded
by two people. Today every import mints fresh ids, so each arrival is a new copy.

**Carry the sender's task id as a stable origin id.** Task ids are already UUIDs, so no new
identifier scheme is needed. Add `tasks.sharedOriginId` (DB 32: bump `@Database`, hand-written
`MIGRATION_31_32`, register in `DatabaseModule`, add an `AppDatabaseMigrationTest` case — per
`CLAUDE.md`).

On import, match **only** on `sharedOriginId == originId` — that is, only against copies this
device previously imported. Never match an incoming `originId` against a local task's own `id`.

That restriction is the whole point, and it is worth being explicit about because the looser rule
is tempting: matching on `id` too would let a teammate who edits a task you sent them share it
back and silently rewrite *your* original. Task copies are independent once shared, so an incoming
link must never modify a task the recipient authored themselves. It can only ever:

- create new tasks, or
- refresh a copy this device imported from the same origin earlier.

What this buys, concretely:

| Situation | Result |
| --- | --- |
| Same link tapped twice | One task, not two |
| Two teammates forward the same task | One task |
| Sender edits and resends | The imported copy refreshes; nothing else moves |
| Teammate shares back a task you authored | A **new** task — your original is untouched |

The last row looks like a duplicate, and it is — deliberately. Silently editing someone's own task
from a link is a far worse failure than an extra row they can delete.

This subsumes reliability item 8 and is a better answer than title-similarity matching, which
would wrongly merge two genuinely different tasks that happen to share a name.

## 4. Attribution

For a team, "who sent this" is part of the content. Carry the sender's profile name
(`UserPreferences.userNameFlow`) in the payload and show it on the confirmation: *"From Ranjith"*.

Keep it opt-out via the existing privacy-mode toggle, since the name is visible to anyone the link
is forwarded to.

Assignment: when the sender is delegating, the natural semantic is "this is yours now". On import,
resolve the assignee to the recipient's own `Person` where `isMe` is true — the repository already
guarantees one exists (`YataRepositoryImpl.kt:610`).

**Do not copy the sender's people records.** This needs calling out because structure-copy
currently does exactly that, and it is the most invasive part of it. Each teammate's people list is
their own: importing the sender's colleagues creates near-duplicate `Person` rows under whatever
spelling the sender happened to use ("Ravi K" vs "Ravi Kumar"), and name matching then silently
assigns work to the wrong person. Lists, projects and tags are shared vocabulary and are
reasonable to match by name; **people are identity and are not**.

Recommendation: drop people from structure copy for team sharing, keeping only "assign to me". If
the sender's assignees are worth conveying at all, put them in the confirmation sheet as text
("assigned by the sender to: Ravi K") rather than creating rows.

## 5. Confirmation before import — now mandatory, not optional

With richer fidelity, origin-id updates and structure creation all in play, a single tap can
change a lot. The confirmation sheet shows, before anything is written:

- who it is from,
- how many tasks, and their titles,
- what will be **created** (lists/projects/tags/people, by name),
- what will be **updated** (existing tasks matched by origin id),
- what will be **dropped** (reminders, sender's section), so nobody assumes those travelled,
- a one-tap "add without dates" for recipients who want the old Inbox behaviour.

This is the single highest-value UX change in this document and it also closes reliability items
5, 6 and 9.

## 6. The model: independent copies — decided, not open

Each teammate's data is their own, and a teammate may or may not run GitHub sync at all. So:

- After import there are **two independent tasks**. Edits do not propagate. Completion does not
  propagate — marking it done on either side is invisible to the other.
- **The link is the entire transport.** Nothing may depend on the sender or the recipient having
  sync, an account, a server, or shared infrastructure of any kind. The payload has to be
  self-contained and importable by a device that has only ever run YATA offline.
- The sender should not expect status back. If they need to know whether it got done, that is a
  conversation, not a feature of this link.

This settles what the earlier draft left open. It also rules out two things that would otherwise
look attractive:

- **GitHub sync is not a sharing mechanism.** It merges a whole personal database snapshot, so
  pointing two teammates at one repo would merge their entire task lists — not share selected
  tasks. It is off the table regardless of who has it enabled.
- **No server-side anything**, including short links, because a recipient with no sync configured
  must still be able to import.

If the team ever does want shared live state — one task both people see, status flowing both
ways — that is a shared-workspace feature and a separate product decision. It should not be bolted
onto links.

## 7. What not to do

- **Do not add a server-side short-link service.** It would put task content on a server, take on
  storage, expiry and abuse handling, and buy only length — which is no longer the binding
  constraint.
- **Do not match teammates by person name across devices**, and do not create `Person` rows from a
  link. Names collide and the failure is silent misassignment.
- **Do not let an incoming link edit a task the recipient authored.** Only previously imported
  copies may be refreshed.
- **Do not let link previews render task content**, even though it would look good in chat.
- **Do not reintroduce a second link.** One link, one meaning.

## Recommended sequence

1. **Phase 0 — HTTPS App Links with fragment payload.** Nothing works until this does. Acceptance:
   a link tapped in the team's actual chat app opens YATA.
2. **Phase 1 — defects 1–3** from the reliability plan (`sortOrder` truncation, blank structure
   names, structure written before validation). Small, isolated, and they get worse with traffic.
3. **Phase 2 — atomic import** (reliability defect 4). Required before import does more work.
4. **Phase 3 — fidelity split** (§2). Payload change; the visible win for the team.
5. **Phase 4 — origin id + DB 32** (§3). Kills duplicates, enables resend-to-update.
6. **Phase 5 — confirmation sheet** (§5). Needs 3 and 4 to have something meaningful to say.
7. **Phase 6 — attribution** (§4) and subtask hierarchy (reliability item 10).

Phases 1–2 are safe to do immediately and independently. Phases 3–5 are the team-sharing feature
proper and are best reviewed together even if committed separately.
