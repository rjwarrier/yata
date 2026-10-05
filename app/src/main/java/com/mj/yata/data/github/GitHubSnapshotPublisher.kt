package com.mj.yata.data.github

import com.mj.yata.domain.sync.SyncRunReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class GitHubSyncConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val apiBase: String
)

internal data class GitHubPreparedSnapshot(
    val canonicalBytes: ByteArray,
    val remoteNeedsPublish: Boolean,
    val canonicalHash: String? = null,
    val token: Any? = null
)

internal class GitHubSnapshotPublisher(
    private val api: GitHubApi,
    private val prepare: suspend (remoteBytes: ByteArray?, scopeKey: String, remoteIsRecovery: Boolean) -> GitHubPreparedSnapshot,
    private val commit: suspend (GitHubPreparedSnapshot) -> Int,
    private val encode: (ByteArray) -> ByteArray,
    private val decode: (ByteArray) -> ByteArray,
    private val validateRemoteSnapshot: (ByteArray) -> Boolean = { true },
    private val commitMessage: (ByteArray) -> String,
    private val lastObservedHead: suspend () -> String? = { null },
    private val onHeadSynced: suspend (String?, String?) -> Unit = { _, _ -> },
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun sync(config: GitHubSyncConfig, progress: (Int, String) -> Unit): Result<SyncRunReport> {
        try {
            progress(12, "Connecting to GitHub")
            val repo = api.getRepo(config.owner, config.repo)
            // Re-checked every sync, not just at connect time - the repo could be made public on
            // GitHub's side, or the token could lose push permission, after setup.
            repo.requirePrivateWriteAccess()

            var attempt = 0
            while (true) {
                attempt++
                progress(30, "Reading GitHub repo")
                val head = readHead(config)
                ensureHeadHasNotRewound(config, head.commitSha)
                val remoteSnapshot = readRemoteSnapshot(config, head, progress)

                progress(56, "Merging GitHub changes")
                val prepared = prepare(
                    remoteSnapshot.bytes,
                    scopeKey(config),
                    remoteSnapshot.isRecovery
                )
                val remoteAlreadyCanonical =
                    !remoteSnapshot.isRecovery &&
                        remoteSnapshot.bytes?.contentEquals(prepared.canonicalBytes) == true
                var syncedHeadSha: String?

                if ((prepared.remoteNeedsPublish && !remoteAlreadyCanonical) || head.snapshotBlobSha == null || remoteSnapshot.isRecovery) {
                    progress(74, "Publishing GitHub commit")
                    val encoded = encode(prepared.canonicalBytes)
                    // GitHub's blob endpoint caps content around 100MB after base64 encoding; this
                    // margin catches an oversized snapshot (large embedded photos) with a clear
                    // message instead of an opaque upload failure partway through.
                    if (encoded.size > MAX_SNAPSHOT_BYTES) {
                        throw GitHubTransportException(
                            "This snapshot is too large to sync to GitHub (${encoded.size / (1024 * 1024)}MB). " +
                                "Remove some photos or large attachments and try again."
                        )
                    }
                    val blobSha = createBlobVerified(config, encoded, "uploaded snapshot")
                    val entries = mutableListOf(GitHubTreeEntry(path = SNAPSHOT_PATH, sha = blobSha))
                    if (!head.hasReadme) {
                        val readmeBytes = README_TEXT.toByteArray(Charsets.UTF_8)
                        entries += GitHubTreeEntry(
                            path = README_PATH,
                            sha = createBlobVerified(config, readmeBytes, "repository README")
                        )
                    }
                    val tree = api.createTree(
                        owner = config.owner,
                        repo = config.repo,
                        baseTreeSha = head.treeSha,
                        entries = entries
                    )
                    val newCommit = api.createCommit(
                        owner = config.owner,
                        repo = config.repo,
                        message = commitMessage(prepared.canonicalBytes),
                        treeSha = tree.sha,
                        parents = head.commitSha?.let(::listOf).orEmpty()
                    )
                    try {
                        val publishedRef = if (head.commitSha == null) {
                            api.createRef(config.owner, config.repo, "refs/heads/${config.branch}", newCommit.sha)
                        } else {
                            api.updateRef(config.owner, config.repo, config.branch, newCommit.sha)
                        }
                        if (publishedRef.sha != newCommit.sha) {
                            throw GitHubTransportException(
                                "GitHub moved the branch to a different commit than the uploaded snapshot"
                            )
                        }
                        val publishStillCurrent = verifyPublishedHead(
                            config,
                            expectedCommitSha = newCommit.sha,
                            expectedBlobSha = blobSha
                        )
                        if (!publishStillCurrent) {
                            if (attempt < MAX_CAS_ATTEMPTS) {
                                delayBeforeRetry(attempt)
                                continue
                            }
                            throw exhaustedConflict()
                        }
                        syncedHeadSha = newCommit.sha
                    } catch (e: GitHubConflictException) {
                        if (attempt < MAX_CAS_ATTEMPTS) {
                            delayBeforeRetry(attempt)
                            continue
                        }
                        throw exhaustedConflict()
                    }
                } else {
                    progress(74, "GitHub already up to date")
                    val latestHead = readHead(config)
                    if (!latestHead.sameIdentityAs(head)) {
                        if (attempt < MAX_CAS_ATTEMPTS) {
                            delayBeforeRetry(attempt)
                            continue
                        }
                        throw exhaustedConflict()
                    }
                    syncedHeadSha = latestHead.commitSha
                }

                progress(88, "Applying GitHub updates")
                val conflictsResolved = commit(prepared)
                onHeadSynced(syncedHeadSha, prepared.canonicalHash)
                return Result.success(SyncRunReport(conflictsResolved = conflictsResolved))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun readSnapshot(config: GitHubSyncConfig, commitSha: String): ByteArray {
        val commit = api.getCommit(config.owner, config.repo, commitSha)
        val blobSha = api.getContent(config.owner, config.repo, SNAPSHOT_PATH, commit.sha)
            ?.takeIf { it.type == "file" }
            ?.sha
            ?: throw GitHubNotFoundException("No YATA snapshot exists at this commit")
        return decode(readBlobVerified(config, blobSha))
    }

    private suspend fun readRemoteSnapshot(
        config: GitHubSyncConfig,
        head: HeadState,
        progress: (Int, String) -> Unit
    ): RemoteSnapshot {
        val blobSha = head.snapshotBlobSha ?: return RemoteSnapshot(bytes = null, isRecovery = false)
        val headBytes = try {
            decode(readBlobVerified(config, blobSha))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!e.canRecoverWithHistorySnapshot()) throw e
            progress(44, "Finding GitHub recovery point")
            val recovery = findRecoverySnapshot(
                config = config,
                excludedCommitSha = head.commitSha,
                initialFailures = listOf("${head.commitSha.orEmpty().take(12).ifBlank { "head" }}: ${e.safeReason()}")
            )
            return recovery.bytes?.let { bytes ->
                RemoteSnapshot(bytes = bytes, isRecovery = true)
            } ?: throw GitHubTransportException(recovery.failureMessage())
        }
        if (validateRemoteSnapshot(headBytes)) {
            return RemoteSnapshot(bytes = headBytes, isRecovery = false)
        }
        progress(44, "Finding GitHub recovery point")
        val recovery = findRecoverySnapshot(
            config = config,
            excludedCommitSha = head.commitSha,
            initialFailures = listOf("${head.commitSha.orEmpty().take(12).ifBlank { "head" }}: snapshot did not validate")
        )
        return recovery.bytes?.let { bytes ->
            RemoteSnapshot(bytes = bytes, isRecovery = true)
        } ?: throw GitHubTransportException(recovery.failureMessage())
    }

    private suspend fun findRecoverySnapshot(
        config: GitHubSyncConfig,
        excludedCommitSha: String?,
        initialFailures: List<String> = emptyList()
    ): RecoverySearchResult {
        val failures = initialFailures.toMutableList()
        var checked = 0
        // +1: the excluded (damaged) head commit is filtered out below, but listCommits doesn't
        // know that, so ask for one extra to still get MAX_RECOVERY_COMMITS real candidates.
        api.listCommits(config.owner, config.repo, config.branch, SNAPSHOT_PATH, maxResults = MAX_RECOVERY_COMMITS + 1)
            .asSequence()
            .filterNot { it.sha == excludedCommitSha }
            .take(MAX_RECOVERY_COMMITS)
            .forEach { commit ->
                checked++
                val bytes = try {
                    readSnapshot(config, commit.sha)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${commit.sha.take(12)}: ${e.safeReason()}"
                    return@forEach
                }
                if (validateRemoteSnapshot(bytes)) {
                    return RecoverySearchResult(bytes = bytes, checked = checked, failures = failures)
                }
                failures += "${commit.sha.take(12)}: snapshot did not validate"
            }
        return RecoverySearchResult(bytes = null, checked = checked, failures = failures)
    }

    private suspend fun readHead(config: GitHubSyncConfig): HeadState {
        val ref = try {
            api.getRef(config.owner, config.repo, config.branch)
        } catch (e: GitHubNotFoundException) {
            return HeadState(commitSha = null, treeSha = null, snapshotBlobSha = null, hasReadme = false)
        }
        val commit = api.getCommit(config.owner, config.repo, ref.sha)
        val snapshot = api.getContent(config.owner, config.repo, SNAPSHOT_PATH, commit.sha)
            ?.takeIf { it.type == "file" }
        val readme = api.getContent(config.owner, config.repo, README_PATH, commit.sha)
            ?.takeIf { it.type == "file" }
        return HeadState(
            commitSha = commit.sha,
            treeSha = commit.treeSha,
            snapshotBlobSha = snapshot?.sha,
            hasReadme = readme != null
        )
    }

    private suspend fun readBlobVerified(config: GitHubSyncConfig, sha: String): ByteArray {
        val bytes = api.getBlob(config.owner, config.repo, sha)
        if (GitBlobSha.of(bytes) != sha) {
            throw GitHubTransportException(
                message = "GitHub returned a snapshot whose content did not match its SHA",
                retryable = false
            )
        }
        return bytes
    }

    private suspend fun createBlobVerified(config: GitHubSyncConfig, bytes: ByteArray, label: String): String {
        val sha = api.createBlob(config.owner, config.repo, bytes)
        if (sha != GitBlobSha.of(bytes)) {
            throw GitHubTransportException(
                "GitHub returned a blob SHA that did not match the $label"
            )
        }
        return sha
    }

    private suspend fun verifyPublishedHead(
        config: GitHubSyncConfig,
        expectedCommitSha: String,
        expectedBlobSha: String
    ): Boolean {
        val publishedHead = readHead(config)
        if (publishedHead.commitSha == expectedCommitSha) {
            if (publishedHead.snapshotBlobSha != expectedBlobSha) {
                throw GitHubTransportException(
                    "GitHub did not retain the uploaded snapshot at the branch head"
                )
            }
            return true
        }
        val actualHeadSha = publishedHead.commitSha
            ?: throw GitHubHistoryRewrittenException()
        if (isAncestor(config, ancestorSha = expectedCommitSha, descendantSha = actualHeadSha)) {
            return false
        }
        throw GitHubHistoryRewrittenException()
    }

    private suspend fun ensureHeadHasNotRewound(config: GitHubSyncConfig, currentHeadSha: String?) {
        val previousHeadSha = lastObservedHead()?.takeIf { it.isNotBlank() } ?: return
        if (currentHeadSha == previousHeadSha) return
        if (currentHeadSha == null) {
            throw GitHubHistoryRewrittenException()
        }
        if (isAncestor(config, ancestorSha = previousHeadSha, descendantSha = currentHeadSha)) return
        throw GitHubHistoryRewrittenException()
    }

    /** One request via GitHub's compare API when available (also works across GHES versions that
     * support it); falls back to a manual BFS walk of parent commits otherwise. Both answer the
     * same question - is [ancestorSha] reachable by walking [descendantSha]'s parents - which is
     * what "history was not rewritten" and "the branch really did advance" need to know. */
    private suspend fun isAncestor(
        config: GitHubSyncConfig,
        ancestorSha: String,
        descendantSha: String
    ): Boolean {
        if (ancestorSha == descendantSha) return true
        val compareResult = try {
            api.compareCommits(config.owner, config.repo, base = ancestorSha, head = descendantSha)
        } catch (_: GitHubException) {
            null
        }
        if (compareResult != null) {
            return compareResult.status == "ahead" || compareResult.status == "identical"
        }
        return isAncestorByWalk(config, ancestorSha, descendantSha)
    }

    private suspend fun isAncestorByWalk(
        config: GitHubSyncConfig,
        ancestorSha: String,
        descendantSha: String
    ): Boolean {
        val pending = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        pending += descendantSha
        var inspected = 0
        while (pending.isNotEmpty() && inspected < MAX_ANCESTRY_COMMITS) {
            val sha = pending.removeFirst()
            if (!seen.add(sha)) continue
            if (sha == ancestorSha) return true
            inspected++
            val commit = api.getCommit(config.owner, config.repo, sha)
            pending += commit.parentShas
        }
        return false
    }

    private fun exhaustedConflict(): GitHubConflictException =
        GitHubConflictException("GitHub repository kept changing during sync; try again")

    private suspend fun delayBeforeRetry(attempt: Int) {
        retryDelay(CAS_RETRY_BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtLeast(0)).coerceAtMost(8L))
    }

    private fun scopeKey(config: GitHubSyncConfig): String =
        "github|${config.owner}/${config.repo}@${config.branch}:$SNAPSHOT_PATH"

    private data class HeadState(
        val commitSha: String?,
        val treeSha: String?,
        val snapshotBlobSha: String?,
        val hasReadme: Boolean
    ) {
        fun sameIdentityAs(other: HeadState): Boolean =
            commitSha == other.commitSha && treeSha == other.treeSha && snapshotBlobSha == other.snapshotBlobSha
    }

    private data class RemoteSnapshot(
        val bytes: ByteArray?,
        val isRecovery: Boolean
    )

    private data class RecoverySearchResult(
        val bytes: ByteArray?,
        val checked: Int,
        val failures: List<String>
    ) {
        fun failureMessage(): String = buildString {
            append("GitHub snapshot is damaged and no valid history snapshot was found")
            append("; checked ").append(checked).append(" recovery commit(s)")
            failures.lastOrNull()?.let { append("; latest recovery failure: ").append(it) }
        }
    }

    private fun Throwable.safeReason(): String {
        var current: Throwable? = this
        while (current != null) {
            current.message
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.take(160) }
            current = current.cause
        }
        return javaClass.simpleName.takeIf { it.isNotBlank() } ?: "failed"
    }

    private fun Throwable.canRecoverWithHistorySnapshot(): Boolean =
        when (this) {
            is GitHubAuthException,
            is GitHubRateLimitException,
            is GitHubPermissionException,
            is GitHubConflictException,
            is GitHubHistoryRewrittenException -> false
            is GitHubTransportException -> !retryable
            else -> true
        }

    companion object {
        const val SNAPSHOT_PATH = "yata/snapshot.json"
        const val README_PATH = "README.md"
        const val MAX_CAS_ATTEMPTS = 6
        private const val CAS_RETRY_BASE_DELAY_MS = 150L
        const val MAX_RECOVERY_COMMITS = 20
        const val MAX_ANCESTRY_COMMITS = 250
        const val MAX_SNAPSHOT_BYTES = 60 * 1024 * 1024

        val README_TEXT = """
            # YATA sync repository

            This repository holds a YATA task-app snapshot, written automatically. Do not edit
            `yata/snapshot.json` by hand - the app merges against it.

            Recovering without the app:

            ```bash
            git log --oneline -- yata/snapshot.json
            git show <commit>:yata/snapshot.json > yata-backup.json
            ```

            Import that file via Settings > Backup & Data > Import.
        """.trimIndent() + "\n"
    }
}
