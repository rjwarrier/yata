# GitHub repo sync — implementation plan

Sync YATA between devices through a **private GitHub repository** the user owns, authenticated with
a token they paste into Settings. No login screen, no OAuth app registration, no server to run.

Two design priorities drive every decision below, in this order:

1. **Data integrity at any cost.** The user's data must be recoverable from the worst plausible
   state — corrupt snapshot, half-finished publish, two devices racing, a bad merge shipped in a
   release, a phone lost mid-sync. Where integrity and convenience conflict, integrity wins.
2. **Setup is two fields.** Token + repo. Everything else is derived.

Target audience is assumed GitHub-literate: they know what a repo, a commit and a token are, and
"recover it with `git show`" is a usable instruction rather than a dead end.

---

## 1. Why this fits the existing architecture

The merge engine is already transport-independent. `SnapshotSyncEngine.prepare(remoteBytes,
scopeKey)` and `.commit(prepared)` know nothing about SFTP; they take bytes and give back bytes.
Everything risky — three-way merge, reference repair, subtask topological ordering, the
pre-apply recovery backup — is written, and covered by `SnapshotSyncTest`.

So a GitHub transport is genuinely additive. It replaces the *transport* half only, and that half
gets substantially **smaller**, because git provides for free what `SftpBackupManager` hand-rolls:

| Hand-rolled today (SFTP/FTP) | On GitHub |
|---|---|
| `acquireSyncLease` / `verifySyncLeaseOwnership` / `releaseSyncLease`, lock directory, lease token, 60-minute stale-lock breaking, `clearSyncLock()` + its Settings button and `SyncLockBusyException` UI | `PATCH /git/refs` with `force: false`. Fast-forward-only. No lock exists, so no lock can wedge. |
| `writeTimestampedBackup` + `pruneOldBackups` + `keepCount` (default 5, max 15) | Commit history. Unbounded, free, and every point is restorable. |
| `.yata_sync_v1.previous` fallback copy | `HEAD~1`. |
| `uploadedSize == bytes.size` truncation check | Git blob SHA verification — checks *content*, not just length. |

The lease machinery is roughly 150 lines across the two managers plus its own UI surface. None of it
is needed here.

---

## 2. Repo layout

```
<user's private repo>
├── README.md              # written once, on first sync — see §7.3
└── yata/
    └── snapshot.json      # the canonical snapshot; the only file the app writes
```

That is the whole design. History is git history. There is no rotation, no pruning, no lock file,
no metadata sidecar.

---

## 3. Auth

A bearer token in an `Authorization: Bearer <token>` header. The transport takes an **opaque
string** and never inspects it, so a fine-grained PAT, a classic PAT, or (if device flow is ever
added) an OAuth token are all interchangeable with zero transport changes. Token acquisition is a
UI concern, permanently decoupled from sync.

**Recommended to users:** a fine-grained PAT scoped to that one repository, `Contents: read/write`.
A leaked token then exposes one repo instead of every repo they own. The setup screen says this
explicitly and links to `https://github.com/settings/personal-access-tokens/new`.

Storage: `RemoteBackupCredentialsStore` (EncryptedSharedPreferences), alongside the existing SFTP
password. Write-only from the UI's perspective — blank field means "keep what's stored", matching
the established pattern.

### Token hygiene (integrity requirement)

- Header only. Never a URL parameter, never a query string.
- **Scrubbed from every diagnostic path.** `OperationHistoryStore`, `CrashLogStore` and `Log.w`
  currently record exception messages verbatim; an HTTP client that puts a token in an exception
  message would leak it into all three. `GitHubApi` must construct its own exception types with
  known-safe messages and never echo request headers.
- 401 is a **first-class state**, not a failure. Fine-grained PATs expire (1 year max). "Your
  GitHub token expired — paste a new one" must never render as a sync failure, which reads as data
  loss. GitHub returns `GitHub-Authentication-Token-Expiration` on responses for tokens with an
  expiry; if Phase 0 confirms it, surface "expires in N days" in Settings and warn at 14 days.

---

## 4. The sync algorithm

Per sync, using the Git Data API throughout:

```
1. GET  /repos/{o}/{r}/git/ref/heads/{branch}     → head commit sha   (404 ⇒ empty repo)
2. GET  /repos/{o}/{r}/git/commits/{headSha}      → tree sha
3. GET  /repos/{o}/{r}/git/trees/{treeSha}?recursive=1
                                                  → blob sha for yata/snapshot.json (may be absent)
4. GET  /repos/{o}/{r}/git/blobs/{blobSha}        → snapshot bytes   (Accept: …raw, ≤100 MB)
   └─ verify sha1("blob "+len+"\0"+bytes) == blobSha        ← read integrity check
5. snapshotSyncEngine.prepare(remoteBytes, scopeKey)         ← existing, unchanged
   └─ internally runs validateBytesForSync before anything is published
6. if (prepared.remoteNeedsPublish):
     POST  /git/blobs    {content: base64, encoding: "base64"}   → newBlobSha
       └─ verify newBlobSha == locally computed git blob sha     ← write integrity check
     POST  /git/trees    {base_tree: treeSha, tree: [{path, mode:"100644", type:"blob", sha}]}
     POST  /git/commits  {message, tree, parents:[headSha]}
     PATCH /git/refs/heads/{branch}  {sha: newCommitSha, force: false}
       └─ 422 ⇒ another device published first ⇒ GOTO 1 (bounded, 3 attempts)
7. snapshotSyncEngine.commit(prepared)                       ← existing, unchanged
   └─ internally: recoveryBackupManager.saveCurrent("pre_sync_apply") then replaceBytesForSync
```

### Why `force: false` is the keystone

A non-fast-forward ref update is **rejected by GitHub**, not merged and not forced. The app
therefore cannot orphan a commit, cannot overwrite another device's publish, and cannot rewrite
history — not through a bug, not through a race, not through a retry storm. Every snapshot ever
published stays reachable from `HEAD` forever.

This is the single strongest integrity property available, and it is the reason for choosing the
Git Data API over the simpler Contents API.

**The app never force-pushes and never deletes.** No history squashing, no repo cleanup, no
"compact" maintenance action — not in this plan and not later without an explicit, separate,
loudly-confirmed user action. Repo growth is the accepted cost of guaranteed recoverability.

### Empty repo

`GET /git/ref/heads/{branch}` returning 404 means a fresh repo with zero commits. Handle explicitly:
commit with `parents: []`, then `POST /git/refs` (create) rather than `PATCH` (update). Deterministic,
no guessing about how the Contents API behaves on an empty repo.

### Failure taxonomy

Each maps to a distinct, actionable message — never a generic "sync failed":

| Status | Meaning | Behaviour |
|---|---|---|
| 401 | Token invalid or expired | Prompt to re-paste. Not a failure state. |
| 403 + `x-ratelimit-remaining: 0` | Rate limited | Show reset time, retry later. |
| 403 otherwise | Token lacks write permission | Point at token permissions. |
| 404 on repo | Wrong repo name, or token can't see it | Setup error, distinguish from empty-repo 404 on the *ref*. |
| 409 / 422 on ref | Another device published first | Silent retry (≤3), then surface. |
| 5xx, timeout, no network | Transient | `Result.retry()` from the worker; no local change. |

### Ordering guarantee

`commit()` runs **only** after the remote publish is confirmed. If publish fails, nothing local
changes. If publish succeeds but `commit()` fails, the local baseline is not written — so the next
sync re-merges from the new canonical and converges. Both failure modes are safe; neither loses data.

---

## 5. Phases

### Phase 0 — Verify against a throwaway repo *(before writing app code)*

Integrity-critical API contracts get confirmed empirically, not assumed. `curl` against a scratch
private repo, results recorded in this document:

- [ ] `PATCH /git/refs/heads/main` with `force: false` and a stale sha → confirm rejection + exact status/body
- [ ] Empty repo: `GET /git/ref/heads/main` → 404; commit with `parents: []` + `POST /git/refs` → succeeds
- [ ] `POST /git/blobs` with a >1 MB base64 payload → succeeds; `GET /git/blobs/{sha}` with
      `Accept: application/vnd.github.raw` returns identical bytes
- [ ] Locally computed `sha1("blob <len>\0" + bytes)` matches the sha GitHub returns
- [ ] `GitHub-Authentication-Token-Expiration` header present for an expiring fine-grained PAT
- [ ] `GET /repos/{o}/{r}` → `permissions.push` reflects a read-only token correctly

**Deliverable:** a "Verified behaviour" section appended here. ~half a session.

### Phase 1 — Extract the transport interface *(pure refactor, zero behaviour change)*

`BackupOperations` has **eight** `if (useFtp) … else …` branches. A third protocol turns those into
eight three-arm `when`s. `SftpBackupManager` and `FtpBackupManager` already expose identical
surfaces, so:

```kotlin
interface SyncTransport {
    suspend fun syncNow(progress: (Int, String) -> Unit = { _, _ -> }): Result<Unit>
    suspend fun listRestorePoints(): Result<List<RestorePoint>>
    suspend fun restore(id: String): Result<Unit>
    suspend fun inspect(id: String): Result<BackupSummary>
    suspend fun readSnapshot(id: String): Result<ByteArray>
    suspend fun isConfigured(): Boolean
}

/** Lease-based transports only; GitHub has no lock, so the Settings button hides for it. */
interface LockableSyncTransport : SyncTransport { suspend fun clearSyncLock(): Result<Unit> }

data class RestorePoint(val id: String, val label: String, val createdAt: Instant?)
```

Transport-specific setup (`testConnection`, `pinHostKey`) stays off the shared interface.

**`RestorePoint` replaces the bare filename strings.** Today
`BackupOperations.backupCreatedTimeFromFilename` parses a timestamp back out of
`yata_backup_<date>_<time>.json` with a regex; commit SHAs will never match it. The transport
supplies the timestamp instead.

`BackupOperations` collapses to one `currentTransport()` lookup, ~60 lines lighter.

`syncSelfHostedIfConfigured()` currently gates on `sftpHostFlow.isBlank()`, meaningless for GitHub —
becomes `transport.isConfigured()`. Three call sites (`BackupOperations` ×2, `MainViewModel:1030`).

**Files:** `domain/sync/SyncTransport.kt` *(new)*, `SftpBackupManager`, `FtpBackupManager`,
`BackupOperations`, `MainViewModel`, `SettingsScreen` (restore/inspect dialogs).
**Acceptance:** `:app:testDebugUnitTest` green; SFTP and FTP behave identically on-device.

### Phase 2 — GitHub API client

`data/github/GitHubApi.kt` — an **interface** plus `HttpGitHubApi` built on `HttpURLConnection`.
No new dependency: `NetworkModule`/OkHttp went away with Drive and needn't return for six endpoints.
The interface is the test seam — every Phase 3 test runs against a fake.

Surface: `getRef`, `getCommit`, `getTree`, `getBlob`, `createBlob`, `createTree`, `createCommit`,
`updateRef`, `createRef`, `listCommits`, `getRepo`, `getUser`, `createRepo`.

Plus `GitBlobSha.of(bytes)` — `sha1("blob " + len + "\0" + bytes)`, used for both integrity checks.

Typed errors (`GitHubAuthException`, `GitHubRateLimitException`, `GitHubConflictException`,
`GitHubNotFoundException`, `GitHubTransportException`) carrying **no** request detail, per §3.

**Acceptance:** unit tests for `GitBlobSha` against known git object hashes; response-parsing tests
from recorded fixtures.

### Phase 3 — `GitHubSyncManager`

`data/github/GitHubSyncManager.kt`, implementing `SyncTransport`. The §4 algorithm, the bounded CAS
retry, both integrity checks, and progress reporting matched to the existing labels
("Connecting", "Reading server changes", "Merging changes", "Uploading changes", "Applying updates").

`scopeKey = "github|{owner}/{repo}@{branch}:{path}"` — distinct from any SFTP scope, so switching
transports starts a fresh baseline rather than mis-merging against a foreign one.

**Commit messages carry provenance:**
```
YATA sync from Pixel 8 — 412 tasks, 38 projects
```
Makes `git log` readable and makes "which device did this" answerable. Reuse the existing
`deviceLabel()` helper from `SftpBackupManager` (lift it to a shared util).

**Conflicts become visible.** `prepared.conflicts > 0` is currently only `Log.i`. Surface it in the
sync result and in `OperationHistoryStore`: "3 records resolved in favour of the repo." The losing
local version is already captured — `commit()` writes a recovery backup before applying — so this
is about *telling* the user something recoverable happened, not about making it recoverable.

**Files:** new manager; `RemoteBackupCredentialsStore` (+`githubToken`);
`UserPreferences` (`GITHUB_OWNER/REPO/BRANCH/API_BASE`, `RemoteBackupProtocol.GITHUB`).

⚠️ **Two hand-maintained lists must both gain `github_`:**
`SnapshotMerger.isDeviceLocalSetting` (SnapshotSync.kt:185) and
`JsonExporter.SYNC_PRESERVED_SETTING_PREFIXES` (JsonExporter.kt:31). They already duplicate each
other and there is no test binding them. Repo config **must** be device-local — a device needs it
before it can sync at all, so syncing it is both useless and a chicken-and-egg trap.

⚠️ **`OperationHistoryStore` needs `SYNC_GITHUB` added to `OPERATIONS`.** `edit()` silently
returns for unrecognised ids, so forgetting this produces no error — just permanently blank
diagnostics.

### Phase 4 — Setup, in two fields

Third segment in the existing protocol picker. The GitHub branch of the config dialog shows only:

```
Token   ••••••••••••••••     ← password field, write-only
Repo    rjwarrier/yata-sync
```

Everything else derived on **Connect**:

- **Owner** — split on `/`; a bare name resolves via `GET /user` → `login`. Accepting both matters:
  org-owned repos need the explicit form.
- **Branch** — `GET /repos/{o}/{r}` → `default_branch`. Never asked.
- **Path** — hardcoded `yata/snapshot.json`. An implementation detail, not a user choice.
- **Write access** — `permissions.push` from the same response. One call separates bad token /
  wrong repo / read-only grant, which covers nearly every real setup mistake.
- **Repo missing?** Offer `POST /user/repos` (`private: true`). A repo-scoped fine-grained PAT will
  get 403 — fall back to "create it on github.com, then tap Connect". Costs one request, and some
  tokens will allow it.

Hidden for GitHub: host, port, username, host-key fingerprint, TLS toggle, keep-count, clear-lock.

**Base URL** is a setting defaulting to `https://api.github.com` — one field, covers GitHub
Enterprise. (GitLab's API differs enough not to be free.)

**Encryption default: off for GitHub.** `encodePayload` applies `backupPassphrase` when set, and on
a private repo over TLS that costs git's delta compression *and* the ability to read your own tasks
on github.com — against a threat (GitHub itself) the user accepted by choosing GitHub. Honour the
setting if already set; don't default it on; state the trade-off in the UI.

### Phase 5 — Recovery surfaces

This phase *is* the integrity requirement, made reachable.

**5.1 First-sync merge preview.** When a device with existing data joins a repo that already has a
snapshot, `SnapshotMerger` takes the initial-join path: union both sides, server wins collisions.
Safe — nothing is lost — but the user should see it before it happens. Show counts
("412 tasks in the repo, 300 on this device") via the existing `BackupSummary`, and require a tap.
One-time, only when both sides are non-empty.

**5.2 Snapshot history.** `GET /repos/{o}/{r}/commits?path=yata/snapshot.json` → the full list, as
`RestorePoint`s. Settings shows date + device + task counts; tap to inspect (`inspect()` →
`BackupSummary`), tap to restore (`restore()` → existing `dryRunRestoreBytes` → recovery backup →
`importBytes`). Strictly better than SFTP's keep-5 rotation: every snapshot ever published is
restorable.

**5.3 `README.md`, written once on first sync.** The escape hatch that doesn't need YATA:

> This repository holds a YATA task-app snapshot, written automatically. Don't edit
> `yata/snapshot.json` by hand — the app merges against it.
>
> **Recovering without the app:**
> ```bash
> git log --oneline -- yata/snapshot.json
> git show <commit>:yata/snapshot.json > yata-backup.json
> ```
> Import that file via Settings → Backup & Data → Import.

For a GitHub-literate audience this is the real worst-case answer: the data is plain JSON, in git,
readable with standard tools, with no dependency on the app being installed or even existing.

**5.4 Diagnostics.** A GitHub row in the existing operation-history panel on `CrashLogScreen`:
last sync, head commit SHA, conflicts resolved, token expiry.

### Phase 6 — Tests, docs, changelog

Unit (JVM, against the `GitHubApi` fake — note `org.json` must be on the test classpath, the
`android.jar` stubs throw):
- CAS retry: `updateRef` 422 once → re-reads, re-merges, second attempt succeeds
- CAS exhaustion: 422 three times → fails cleanly, **no local mutation**
- Empty repo: ref 404 → `parents: []` + `createRef`
- Missing snapshot in an existing repo → initial-join path
- Blob sha mismatch on read → rejected, treated as corrupt, no local mutation
- Blob sha mismatch on write → publish aborted before the ref moves
- 401/403/404/5xx each map to their own error type
- Round-trip: two simulated devices, independent edits, converge to one snapshot

Instrumented: none needed — no new DB migration, no new screen. (And per `CLAUDE.md`, never against
the user's phone.)

Docs: this file's Phase 0 results; a `CLAUDE.md` note on the new transport; `CHANGELOG.md` under
`[Unreleased]` **in the same commit**, per project convention.

Strings: new keys in `values/strings.xml`; es/fr/pt fall back to English until translated, so not
blocking.

---

## 6. Effort

| Phase | Size |
|---|---|
| 0 — API verification | ½ session |
| 1 — Transport interface refactor | 1 session |
| 2 — API client | 1 session |
| 3 — Sync manager | 1–1½ sessions |
| 4 — Setup UI | 1 session |
| 5 — Recovery surfaces | 1 session |
| 6 — Tests + docs | ½–1 session |

≈ 6–7 sessions. Phases 1 and 2 are independent and can land in either order.

---

## 7. Accepted costs

- **Repo growth.** Every changed snapshot is a full-file commit; `scheduleDebouncedBackup()` fires
  15s after each edit burst. `remoteNeedsPublish` already suppresses no-op commits, and git deltas
  near-identical JSON well. Encryption would defeat that delta compression entirely — another
  reason it stays off by default. Monitored, not pre-optimised: the fix would be history rewriting,
  which is exactly what §4 forbids.
- **Photo bytes inflate the snapshot.** `JsonExporter` embeds person avatars as base64 (deliberate —
  path-only backups restored dangling references). The Git Data API's 100 MB blob ceiling makes
  this a non-issue where the Contents API's 1 MB would not have been.
- **5–7 requests per sync** instead of 2. Against 5000/hour, irrelevant.
- **No offline queue.** Sync needs network; without it the local DB is authoritative and the next
  sync merges. Unchanged from SFTP.

## 8. Open questions

1. **Should `SFTP_BACKUP_ENABLED` keep doubling as the master self-hosted toggle?** It already
   does for FTP, and its key comment acknowledges the legacy name. Reusing it is consistent and
   avoids a migration; renaming means a DataStore migration for no user-visible gain. *Leaning:
   reuse.*
2. **Auto-create the repo, or always instruct?** Auto-create works only for tokens broader than the
   one we recommend. *Leaning: try it, fall back to instructions on 403.*
3. **Does anything want more than one snapshot file** (e.g. splitting archived tasks, as
   `buildSplitBackupJson(archiveMonths)` already supports)? One file is simpler and the merger
   assumes it. *Leaning: one file; revisit only if snapshots get genuinely large.*
