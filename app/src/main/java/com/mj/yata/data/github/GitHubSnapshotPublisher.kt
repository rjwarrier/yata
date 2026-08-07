package com.mj.yata.data.github

internal data class GitHubSyncConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val apiBase: String
)

internal data class GitHubPreparedSnapshot(
    val canonicalBytes: ByteArray,
    val remoteNeedsPublish: Boolean,
    val token: Any? = null
)

internal class GitHubSnapshotPublisher(
    private val api: GitHubApi,
    private val prepare: suspend (remoteBytes: ByteArray?, scopeKey: String, remoteIsRecovery: Boolean) -> GitHubPreparedSnapshot,
    private val commit: suspend (GitHubPreparedSnapshot) -> Unit,
    private val encode: (ByteArray) -> ByteArray,
    private val decode: (ByteArray) -> ByteArray,
    private val validateRemoteSnapshot: (ByteArray) -> Boolean = { true },
    private val commitMessage: (ByteArray) -> String,
    private val lastObservedHead: suspend () -> String? = { null },
    private val onHeadObserved: suspend (String?) -> Unit = {},
    private val onHeadPublished: suspend (String) -> Unit = {}
) {
    suspend fun sync(config: GitHubSyncConfig, progress: (Int, String) -> Unit): Result<Unit> {
        try {
            progress(12, "Connecting to GitHub")
            val repo = api.getRepo(config.owner, config.repo)
            if (!repo.canPush) {
                throw GitHubPermissionException()
            }

            var attempt = 0
            while (true) {
                attempt++
                progress(30, "Reading GitHub repo")
                val head = readHead(config)
                ensureHeadHasNotRewound(config, head.commitSha)
                onHeadObserved(head.commitSha)
                val remoteSnapshot = readRemoteSnapshot(config, head, progress)

                progress(56, "Merging GitHub changes")
                val prepared = prepare(
                    remoteSnapshot.bytes,
                    scopeKey(config),
                    remoteSnapshot.isRecovery
                )

                if (prepared.remoteNeedsPublish || head.snapshotBlobSha == null || remoteSnapshot.isRecovery) {
                    progress(74, "Publishing GitHub commit")
                    val encoded = encode(prepared.canonicalBytes)
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
                        check(publishedRef.sha == newCommit.sha) {
                            "GitHub moved the branch to a different commit than the uploaded snapshot"
                        }
                        verifyPublishedHead(config, expectedCommitSha = newCommit.sha, expectedBlobSha = blobSha)
                        onHeadPublished(newCommit.sha)
                    } catch (e: GitHubConflictException) {
                        if (attempt < MAX_CAS_ATTEMPTS) continue
                        throw exhaustedConflict()
                    }
                } else {
                    progress(74, "GitHub already up to date")
                    val latestHead = readHead(config)
                    if (!latestHead.sameIdentityAs(head)) {
                        if (attempt < MAX_CAS_ATTEMPTS) continue
                        throw exhaustedConflict()
                    }
                }

                progress(88, "Applying GitHub updates")
                commit(prepared)
                return Result.success(Unit)
            }
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
        val headBytes = decode(readBlobVerified(config, blobSha))
        if (validateRemoteSnapshot(headBytes)) {
            return RemoteSnapshot(bytes = headBytes, isRecovery = false)
        }
        progress(44, "Finding GitHub recovery point")
        return findRecoverySnapshot(config, excludedCommitSha = head.commitSha)?.let { bytes ->
            RemoteSnapshot(bytes = bytes, isRecovery = true)
        } ?: throw GitHubTransportException(
            "GitHub snapshot is damaged and no valid history snapshot was found"
        )
    }

    private suspend fun findRecoverySnapshot(
        config: GitHubSyncConfig,
        excludedCommitSha: String?
    ): ByteArray? =
        api.listCommits(config.owner, config.repo, config.branch, SNAPSHOT_PATH)
            .asSequence()
            .filterNot { it.sha == excludedCommitSha }
            .take(MAX_RECOVERY_COMMITS)
            .firstNotNullOfOrNull { commit ->
                runCatching { readSnapshot(config, commit.sha) }
                    .getOrNull()
                    ?.takeIf(validateRemoteSnapshot)
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
        check(GitBlobSha.of(bytes) == sha) {
            "GitHub returned a blob whose content did not match its SHA"
        }
        return bytes
    }

    private suspend fun createBlobVerified(config: GitHubSyncConfig, bytes: ByteArray, label: String): String {
        val sha = api.createBlob(config.owner, config.repo, bytes)
        check(sha == GitBlobSha.of(bytes)) {
            "GitHub returned a blob SHA that did not match the $label"
        }
        return sha
    }

    private suspend fun verifyPublishedHead(
        config: GitHubSyncConfig,
        expectedCommitSha: String,
        expectedBlobSha: String
    ) {
        val publishedHead = readHead(config)
        check(publishedHead.commitSha == expectedCommitSha && publishedHead.snapshotBlobSha == expectedBlobSha) {
            "GitHub did not retain the uploaded snapshot at the branch head"
        }
    }

    private suspend fun ensureHeadHasNotRewound(config: GitHubSyncConfig, currentHeadSha: String?) {
        val previousHeadSha = lastObservedHead()?.takeIf { it.isNotBlank() } ?: return
        if (currentHeadSha == previousHeadSha) return
        if (currentHeadSha == null) {
            throw GitHubConflictException(
                "GitHub branch history changed outside YATA; restore or reconnect before syncing"
            )
        }
        if (isAncestor(config, ancestorSha = previousHeadSha, descendantSha = currentHeadSha)) return
        throw GitHubConflictException(
            "GitHub branch history changed outside YATA; restore or reconnect before syncing"
        )
    }

    private suspend fun isAncestor(
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

    companion object {
        const val SNAPSHOT_PATH = "yata/snapshot.json"
        const val README_PATH = "README.md"
        const val MAX_CAS_ATTEMPTS = 3
        const val MAX_RECOVERY_COMMITS = 20
        const val MAX_ANCESTRY_COMMITS = 250

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
