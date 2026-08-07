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
    private val prepare: suspend (remoteBytes: ByteArray?, scopeKey: String) -> GitHubPreparedSnapshot,
    private val commit: suspend (GitHubPreparedSnapshot) -> Unit,
    private val encode: (ByteArray) -> ByteArray,
    private val decode: (ByteArray) -> ByteArray,
    private val commitMessage: (ByteArray) -> String,
    private val onHeadObserved: suspend (String?) -> Unit = {},
    private val onHeadPublished: suspend (String) -> Unit = {}
) {
    suspend fun sync(config: GitHubSyncConfig, progress: (Int, String) -> Unit): Result<Unit> {
        try {
            progress(12, "Connecting to GitHub")
            api.getRepo(config.owner, config.repo)

            var attempt = 0
            while (true) {
                attempt++
                progress(30, "Reading GitHub repo")
                val head = readHead(config)
                onHeadObserved(head.commitSha)
                val remoteBytes = head.snapshotBlobSha?.let { sha ->
                    readBlobVerified(config, sha)
                }?.let(decode)

                progress(56, "Merging GitHub changes")
                val prepared = prepare(remoteBytes, scopeKey(config))

                if (prepared.remoteNeedsPublish || head.snapshotBlobSha == null) {
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
        val tree = api.getTree(config.owner, config.repo, commit.treeSha)
        if (tree.truncated) {
            throw GitHubTransportException("GitHub tree response was truncated; restore was not attempted")
        }
        val blobSha = tree.entries.firstOrNull { it.path == SNAPSHOT_PATH && it.type == "blob" }?.sha
            ?: throw GitHubNotFoundException("No YATA snapshot exists at this commit")
        return decode(readBlobVerified(config, blobSha))
    }

    private suspend fun readHead(config: GitHubSyncConfig): HeadState {
        val ref = try {
            api.getRef(config.owner, config.repo, config.branch)
        } catch (e: GitHubNotFoundException) {
            return HeadState(commitSha = null, treeSha = null, snapshotBlobSha = null, hasReadme = false)
        }
        val commit = api.getCommit(config.owner, config.repo, ref.sha)
        val tree = api.getTree(config.owner, config.repo, commit.treeSha)
        if (tree.truncated) {
            throw GitHubTransportException("GitHub tree response was truncated; sync was not attempted")
        }
        return HeadState(
            commitSha = commit.sha,
            treeSha = commit.treeSha,
            snapshotBlobSha = tree.entries.firstOrNull { it.path == SNAPSHOT_PATH && it.type == "blob" }?.sha,
            hasReadme = tree.entries.any { it.path == README_PATH && it.type == "blob" }
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

    companion object {
        const val SNAPSHOT_PATH = "yata/snapshot.json"
        const val README_PATH = "README.md"
        const val MAX_CAS_ATTEMPTS = 3

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
