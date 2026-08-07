package com.mj.yata.data.github

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GitHubSnapshotPublisherTest {

    private val config = GitHubSyncConfig(
        owner = "owner",
        repo = "repo",
        branch = "main",
        apiBase = "https://api.github.com"
    )

    @Test
    fun emptyRepo_createsInitialCommitAndRef() = runTest {
        val api = FakeGitHubApi()
        var committed = false
        val publisher = publisher(api, canonical = "first".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertEquals(1, api.createRefCalls)
        assertEquals(0, api.updateRefCalls)
        assertEquals(emptyList<String>(), api.createdCommitParents.single())
        assertTrue(api.headCommitSha != null)
        assertTrue(api.treeForHead().entries.any { it.path == GitHubSnapshotPublisher.SNAPSHOT_PATH })
        assertTrue(api.treeForHead().entries.any { it.path == GitHubSnapshotPublisher.README_PATH })
    }

    @Test
    fun existingRepoWithoutSnapshot_publishesWithExistingHeadParent() = runTest {
        val api = FakeGitHubApi().apply {
            seedHeadWithoutSnapshot()
            createdCommitParents.clear()
        }
        val previousHead = api.headCommitSha
        var remoteWasMissing = false
        val publisher = publisher(api, canonical = "first".bytes(), onPrepare = { remote ->
            remoteWasMissing = remote == null
        })

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(remoteWasMissing)
        assertEquals(0, api.createRefCalls)
        assertEquals(1, api.updateRefCalls)
        assertEquals(listOf(listOf(previousHead)), api.createdCommitParents)
    }

    @Test
    fun tokenWithoutPushPermission_failsBeforeMergeOrCommit() = runTest {
        val api = FakeGitHubApi().apply {
            canPush = false
            seedHead("server".bytes())
        }
        var prepared = false
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { _, _, _ ->
                prepared = true
                GitHubPreparedSnapshot(canonicalBytes = "merged".bytes(), remoteNeedsPublish = true)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "test commit" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is GitHubPermissionException)
        assertFalse(prepared)
        assertFalse(committed)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun staleRef_reReadsMergesAndRetries() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            failUpdateRefTimes = 1
        }
        val remoteSnapshots = mutableListOf<String?>()
        val publisher = publisher(api, canonical = "merged".bytes(), onPrepare = { remote ->
            remoteSnapshots += remote?.string()
        })

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertEquals(2, api.updateRefCalls)
        assertEquals(listOf("server", "server"), remoteSnapshots)
    }

    @Test
    fun repeatedStaleRefs_failWithoutCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            failUpdateRefTimes = 3
        }
        var committed = false
        val publisher = publisher(api, canonical = "merged".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is GitHubConflictException)
        assertFalse(committed)
        assertEquals(GitHubSnapshotPublisher.MAX_CAS_ATTEMPTS, api.updateRefCalls)
    }

    @Test
    fun readBlobShaMismatch_rejectsBeforeCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            corruptSnapshotBlobReads = true
        }
        var committed = false
        val publisher = publisher(api, canonical = "merged".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertEquals(0, api.updateRefCalls)
        assertEquals(0, api.createRefCalls)
    }

    @Test
    fun uploadBlobShaMismatch_abortsBeforeRefMoveOrCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            corruptCreateBlobSha = true
        }
        var committed = false
        val publisher = publisher(api, canonical = "merged".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun branchMovedToUnexpectedCommit_failsBeforeLocalCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            returnUnexpectedUpdateRefSha = true
        }
        var committed = false
        val publisher = publisher(api, canonical = "merged".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertEquals(1, api.updateRefCalls)
    }

    @Test
    fun branchMovesAfterRefUpdate_failsBeforeLocalCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            headCommitShaAfterRefWrite = seedCommit("external".bytes())
            createdCommitParents.clear()
        }
        var committed = false
        val publisher = publisher(api, canonical = "merged".bytes()) { committed = true }

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertEquals(1, api.updateRefCalls)
    }

    @Test
    fun previouslyObservedHeadStillInHistory_allowsSync() = runTest {
        lateinit var previousHead: String
        val api = FakeGitHubApi().apply {
            seedHead("previous".bytes())
            previousHead = headCommitSha!!
            seedHead("server".bytes(), parents = listOf(previousHead))
            createdCommitParents.clear()
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, _ ->
                assertEquals("server", remoteBytes?.string())
                GitHubPreparedSnapshot(canonicalBytes = "merged".bytes(), remoteNeedsPublish = true)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "test commit" },
            lastObservedHead = { previousHead }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertEquals(1, api.updateRefCalls)
    }

    @Test
    fun previouslyObservedHeadMissingFromHistory_failsBeforeMergeOrCommit() = runTest {
        lateinit var previousHead: String
        val api = FakeGitHubApi().apply {
            seedHead("previous".bytes())
            previousHead = headCommitSha!!
            seedHead("rewound".bytes())
            createdCommitParents.clear()
        }
        var prepared = false
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { _, _, _ ->
                prepared = true
                GitHubPreparedSnapshot(canonicalBytes = "merged".bytes(), remoteNeedsPublish = true)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "test commit" },
            lastObservedHead = { previousHead }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is GitHubConflictException)
        assertFalse(prepared)
        assertFalse(committed)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun truncatedFullTree_doesNotBlockPathBasedSyncLookup() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
            returnTruncatedTrees = true
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, _ ->
                assertEquals("server", remoteBytes?.string())
                GitHubPreparedSnapshot(canonicalBytes = "merged".bytes(), remoteNeedsPublish = true)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "test commit" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertEquals(1, api.updateRefCalls)
    }

    @Test
    fun alreadyUpToDate_skipsPublishAndStillCommitsBaseline() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("same".bytes())
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, _ ->
                assertEquals("same", remoteBytes?.string())
                GitHubPreparedSnapshot(canonicalBytes = "same".bytes(), remoteNeedsPublish = false)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "no-op" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertEquals(0, api.createRefCalls)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun successfulSyncReportsResolvedConflicts() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("server".bytes())
        }
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { _, _, _ ->
                GitHubPreparedSnapshot(canonicalBytes = "merged".bytes(), remoteNeedsPublish = true)
            },
            commit = { 3 },
            encode = { it },
            decode = { it },
            commitMessage = { "test commit" }
        )

        val report = publisher.sync(config) { _, _ -> }.getOrThrow()

        assertEquals(3, report.conflictsResolved)
    }

    @Test
    fun alreadyUpToDate_whenHeadMovesBeforeCommit_reReadsAndCommitsLatestBaseline() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("same".bytes())
            beforeGetRef = { call, fake ->
                if (call == 2) fake.seedHead("newer".bytes())
            }
        }
        val remoteSnapshots = mutableListOf<String?>()
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, _ ->
                remoteSnapshots += remoteBytes?.string()
                GitHubPreparedSnapshot(canonicalBytes = remoteBytes ?: ByteArray(0), remoteNeedsPublish = false)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "no-op" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertEquals(listOf("same", "newer"), remoteSnapshots)
        assertEquals(4, api.getRefCalls)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun alreadyUpToDate_whenHeadKeepsMoving_failsWithoutCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("same".bytes())
            beforeGetRef = { call, fake ->
                if (call % 2 == 0) fake.seedHead("newer-$call".bytes())
            }
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, _ ->
                GitHubPreparedSnapshot(canonicalBytes = remoteBytes ?: ByteArray(0), remoteNeedsPublish = false)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            commitMessage = { "no-op" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is GitHubConflictException)
        assertFalse(committed)
        assertEquals(GitHubSnapshotPublisher.MAX_CAS_ATTEMPTS * 2, api.getRefCalls)
        assertEquals(0, api.updateRefCalls)
    }

    @Test
    fun damagedHeadSnapshot_promotesValidHistoryCandidateOnFirstJoin() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("valid-history".bytes())
            seedHead("damaged-head".bytes())
            createdCommitParents.clear()
        }
        var preparedRemote: String? = null
        var preparedWasRecovery = false
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _, remoteIsRecovery ->
                preparedRemote = remoteBytes?.string()
                preparedWasRecovery = remoteIsRecovery
                GitHubPreparedSnapshot(canonicalBytes = remoteBytes ?: ByteArray(0), remoteNeedsPublish = false)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            validateRemoteSnapshot = { it.string() != "damaged-head" },
            commitMessage = { "promote recovery" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.isSuccess)
        assertTrue(committed)
        assertTrue(preparedWasRecovery)
        assertEquals("valid-history", preparedRemote)
        assertEquals(1, api.updateRefCalls)
    }

    @Test
    fun damagedHeadSnapshot_recoveryRejectedByPrepareDoesNotPublishOrCommit() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("valid-history".bytes())
            seedHead("damaged-head".bytes())
            createdCommitParents.clear()
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { _, _, remoteIsRecovery ->
                check(!remoteIsRecovery) { "Recovery requires manual restore once a baseline exists" }
                GitHubPreparedSnapshot(canonicalBytes = "promoted".bytes(), remoteNeedsPublish = true)
            },
            commit = { committed = true; 0 },
            encode = { it },
            decode = { it },
            validateRemoteSnapshot = { it.string() != "damaged-head" },
            commitMessage = { "blocked recovery" }
        )

        val result = publisher.sync(config) { _, _ -> }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertFalse(committed)
        assertEquals(0, api.updateRefCalls)
        assertEquals(0, api.createRefCalls)
    }

    @Test
    fun readSnapshot_readsBlobAtCommitAndVerifiesSha() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("history".bytes())
        }
        val publisher = publisher(api, canonical = "unused".bytes())

        val bytes = publisher.readSnapshot(config, api.headCommitSha!!)

        assertEquals("history", bytes.string())
    }

    private fun publisher(
        api: FakeGitHubApi,
        canonical: ByteArray,
        onPrepare: (ByteArray?) -> Unit = {},
        onCommit: () -> Unit = {}
    ) = GitHubSnapshotPublisher(
        api = api,
        prepare = { remoteBytes, _, _ ->
            onPrepare(remoteBytes)
            GitHubPreparedSnapshot(canonicalBytes = canonical, remoteNeedsPublish = true)
        },
        commit = { onCommit(); 0 },
        encode = { it },
        decode = { it },
        commitMessage = { "test commit" }
    )

    private class FakeGitHubApi : GitHubApi {
        private val blobs = linkedMapOf<String, ByteArray>()
        private val commits = linkedMapOf<String, GitHubCommit>()
        private val trees = linkedMapOf<String, GitHubTree>()
        var headCommitSha: String? = null
        var canPush = true
        var failUpdateRefTimes = 0
        var corruptSnapshotBlobReads = false
        var corruptCreateBlobSha = false
        var returnUnexpectedUpdateRefSha = false
        var returnTruncatedTrees = false
        var headCommitShaAfterRefWrite: String? = null
        var createRefCalls = 0
        var updateRefCalls = 0
        var getRefCalls = 0
        var beforeGetRef: ((Int, FakeGitHubApi) -> Unit)? = null
        val createdCommitParents = mutableListOf<List<String>>()

        fun seedHead(snapshot: ByteArray, parents: List<String> = emptyList()) {
            val blobSha = putBlob(snapshot)
            val tree = putTree(listOf(GitHubTreeEntry(path = GitHubSnapshotPublisher.SNAPSHOT_PATH, sha = blobSha)))
            val commit = putCommit(tree.sha, parents)
            headCommitSha = commit.sha
        }

        fun seedCommit(snapshot: ByteArray): String {
            val blobSha = putBlob(snapshot)
            val tree = putTree(listOf(GitHubTreeEntry(path = GitHubSnapshotPublisher.SNAPSHOT_PATH, sha = blobSha)))
            return putCommit(tree.sha, emptyList()).sha
        }

        fun seedHeadWithoutSnapshot() {
            val blobSha = putBlob("notes".toByteArray(Charsets.UTF_8))
            val tree = putTree(listOf(GitHubTreeEntry(path = "notes.txt", sha = blobSha)))
            val commit = putCommit(tree.sha, emptyList())
            headCommitSha = commit.sha
        }

        fun treeForHead(): GitHubTree = trees.getValue(commits.getValue(headCommitSha!!).treeSha)

        override suspend fun getRepo(owner: String, repo: String): GitHubRepo =
            GitHubRepo(owner, repo, defaultBranch = "main", canPush = canPush)

        override suspend fun getUser(): GitHubUser = GitHubUser("owner")

        override suspend fun createRepo(name: String, private: Boolean): GitHubRepo =
            GitHubRepo("owner", name, defaultBranch = "main", canPush = true)

        override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef {
            getRefCalls++
            beforeGetRef?.invoke(getRefCalls, this)
            return headCommitSha?.let(::GitHubRef) ?: throw GitHubNotFoundException()
        }

        override suspend fun createRef(owner: String, repo: String, ref: String, sha: String): GitHubRef {
            createRefCalls++
            headCommitSha = sha
            headCommitShaAfterRefWrite?.let { headCommitSha = it }
            return GitHubRef(sha)
        }

        override suspend fun updateRef(owner: String, repo: String, branch: String, sha: String): GitHubRef {
            updateRefCalls++
            if (failUpdateRefTimes > 0) {
                failUpdateRefTimes--
                throw GitHubConflictException()
            }
            headCommitSha = sha
            headCommitShaAfterRefWrite?.let { headCommitSha = it }
            return GitHubRef(if (returnUnexpectedUpdateRefSha) "unexpected-commit" else sha)
        }

        override suspend fun getCommit(owner: String, repo: String, sha: String): GitHubCommit =
            commits[sha] ?: throw GitHubNotFoundException()

        override suspend fun createCommit(owner: String, repo: String, message: String, treeSha: String, parents: List<String>): GitHubCommit {
            return putCommit(treeSha, parents)
        }

        override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree =
            trees[treeSha]?.copy(truncated = returnTruncatedTrees) ?: throw GitHubNotFoundException()

        override suspend fun createTree(owner: String, repo: String, baseTreeSha: String?, entries: List<GitHubTreeEntry>): GitHubTree {
            val baseEntries = baseTreeSha?.let { trees.getValue(it).entries }.orEmpty()
                .filterNot { base -> entries.any { it.path == base.path } }
            return putTree(baseEntries + entries)
        }

        override suspend fun getContent(owner: String, repo: String, path: String, ref: String): GitHubContent? {
            val commit = commits[ref] ?: return null
            val tree = trees[commit.treeSha] ?: return null
            val entry = tree.entries.firstOrNull { it.path == path && it.type == "blob" } ?: return null
            return GitHubContent(path = entry.path, type = "file", sha = entry.sha)
        }

        override suspend fun getBlob(owner: String, repo: String, sha: String): ByteArray {
            val bytes = blobs[sha] ?: throw GitHubNotFoundException()
            return if (corruptSnapshotBlobReads) bytes + "!".toByteArray(Charsets.UTF_8) else bytes
        }

        override suspend fun createBlob(owner: String, repo: String, bytes: ByteArray): String =
            if (corruptCreateBlobSha) {
                putBlob(bytes)
                "bad-sha"
            } else {
                putBlob(bytes)
            }

        override suspend fun listCommits(owner: String, repo: String, branch: String, path: String): List<GitHubCommitSummary> =
            commits.keys.reversed().map { GitHubCommitSummary(it, "commit", Instant.EPOCH) }

        private fun putBlob(bytes: ByteArray): String {
            val sha = GitBlobSha.of(bytes)
            blobs[sha] = bytes
            return sha
        }

        private fun putTree(entries: List<GitHubTreeEntry>): GitHubTree {
            val sha = "tree-${trees.size + 1}"
            val tree = GitHubTree(sha, entries)
            trees[sha] = tree
            return tree
        }

        private fun putCommit(treeSha: String, parents: List<String>): GitHubCommit {
            val sha = "commit-${commits.size + 1}"
            val commit = GitHubCommit(sha, treeSha, parents)
            commits[sha] = commit
            createdCommitParents += parents
            return commit
        }
    }

    private fun String.bytes(): ByteArray = toByteArray(Charsets.UTF_8)
    private fun ByteArray.string(): String = String(this, Charsets.UTF_8)
}
