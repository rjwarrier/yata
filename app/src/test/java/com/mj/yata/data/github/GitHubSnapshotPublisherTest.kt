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
    fun alreadyUpToDate_skipsPublishAndStillCommitsBaseline() = runTest {
        val api = FakeGitHubApi().apply {
            seedHead("same".bytes())
        }
        var committed = false
        val publisher = GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, _ ->
                assertEquals("same", remoteBytes?.string())
                GitHubPreparedSnapshot(canonicalBytes = "same".bytes(), remoteNeedsPublish = false)
            },
            commit = { committed = true },
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
        prepare = { remoteBytes, _ ->
            onPrepare(remoteBytes)
            GitHubPreparedSnapshot(canonicalBytes = canonical, remoteNeedsPublish = true)
        },
        commit = { onCommit() },
        encode = { it },
        decode = { it },
        commitMessage = { "test commit" }
    )

    private class FakeGitHubApi : GitHubApi {
        private val blobs = linkedMapOf<String, ByteArray>()
        private val commits = linkedMapOf<String, GitHubCommit>()
        private val trees = linkedMapOf<String, GitHubTree>()
        var headCommitSha: String? = null
        var failUpdateRefTimes = 0
        var corruptSnapshotBlobReads = false
        var corruptCreateBlobSha = false
        var createRefCalls = 0
        var updateRefCalls = 0
        val createdCommitParents = mutableListOf<List<String>>()

        fun seedHead(snapshot: ByteArray) {
            val blobSha = putBlob(snapshot)
            val tree = putTree(listOf(GitHubTreeEntry(path = GitHubSnapshotPublisher.SNAPSHOT_PATH, sha = blobSha)))
            val commit = putCommit(tree.sha, emptyList())
            headCommitSha = commit.sha
        }

        fun seedHeadWithoutSnapshot() {
            val blobSha = putBlob("notes".toByteArray(Charsets.UTF_8))
            val tree = putTree(listOf(GitHubTreeEntry(path = "notes.txt", sha = blobSha)))
            val commit = putCommit(tree.sha, emptyList())
            headCommitSha = commit.sha
        }

        fun treeForHead(): GitHubTree = trees.getValue(commits.getValue(headCommitSha!!).treeSha)

        override suspend fun getRepo(owner: String, repo: String): GitHubRepo =
            GitHubRepo(owner, repo, defaultBranch = "main", canPush = true)

        override suspend fun getUser(): GitHubUser = GitHubUser("owner")

        override suspend fun createRepo(name: String, private: Boolean): GitHubRepo =
            GitHubRepo("owner", name, defaultBranch = "main", canPush = true)

        override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef =
            headCommitSha?.let(::GitHubRef) ?: throw GitHubNotFoundException()

        override suspend fun createRef(owner: String, repo: String, ref: String, sha: String): GitHubRef {
            createRefCalls++
            headCommitSha = sha
            return GitHubRef(sha)
        }

        override suspend fun updateRef(owner: String, repo: String, branch: String, sha: String): GitHubRef {
            updateRefCalls++
            if (failUpdateRefTimes > 0) {
                failUpdateRefTimes--
                throw GitHubConflictException()
            }
            headCommitSha = sha
            return GitHubRef(sha)
        }

        override suspend fun getCommit(owner: String, repo: String, sha: String): GitHubCommit =
            commits[sha] ?: throw GitHubNotFoundException()

        override suspend fun createCommit(owner: String, repo: String, message: String, treeSha: String, parents: List<String>): GitHubCommit {
            return putCommit(treeSha, parents)
        }

        override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree =
            trees[treeSha] ?: throw GitHubNotFoundException()

        override suspend fun createTree(owner: String, repo: String, baseTreeSha: String?, entries: List<GitHubTreeEntry>): GitHubTree {
            val baseEntries = baseTreeSha?.let { trees.getValue(it).entries }.orEmpty()
                .filterNot { base -> entries.any { it.path == base.path } }
            return putTree(baseEntries + entries)
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
            commits.keys.map { GitHubCommitSummary(it, "commit", Instant.EPOCH) }

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
            val commit = GitHubCommit(sha, treeSha)
            commits[sha] = commit
            createdCommitParents += parents
            return commit
        }
    }

    private fun String.bytes(): ByteArray = toByteArray(Charsets.UTF_8)
    private fun ByteArray.string(): String = String(this, Charsets.UTF_8)
}
