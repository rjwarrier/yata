package com.mj.yata.data.github

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpGitHubApiTest {

    @Test
    fun retriesTransientServerFailure() = runTest {
        val connections = ArrayDeque(
            listOf(
                FakeConnection(status = 503, body = """{"message":"try later"}"""),
                FakeConnection(status = 200, body = """{"login":"octo"}""")
            )
        )
        val delays = mutableListOf<Long>()
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { connections.removeFirst() },
            retryDelay = { delays += it }
        )

        val user = api.getUser()

        assertEquals("octo", user.login)
        assertEquals(listOf(500L), delays)
        assertEquals(0, connections.size)
    }

    @Test
    fun doesNotRetryAuthFailure() = runTest {
        var calls = 0
        val api = HttpGitHubApi(
            tokenProvider = { "bad-token" },
            connectionFactory = {
                calls++
                FakeConnection(status = 401, body = """{"message":"bad credentials"}""")
            },
            retryDelay = {}
        )

        val result = runCatching { api.getUser() }

        assertTrue(result.exceptionOrNull() is GitHubAuthException)
        assertEquals(1, calls)
    }

    @Test
    fun retriesShortRateLimitDelay() = runTest {
        val resetNow = System.currentTimeMillis() / 1_000L
        val connections = ArrayDeque(
            listOf(
                FakeConnection(
                    status = 403,
                    body = """{"message":"rate limit"}""",
                    headers = mapOf(
                        "x-ratelimit-remaining" to "0",
                        "x-ratelimit-reset" to resetNow.toString()
                    )
                ),
                FakeConnection(status = 200, body = """{"login":"octo"}""")
            )
        )
        val delays = mutableListOf<Long>()
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { connections.removeFirst() },
            retryDelay = { delays += it }
        )

        val user = api.getUser()

        assertEquals("octo", user.login)
        assertEquals(1, delays.size)
        assertEquals(0, connections.size)
    }

    @Test
    fun secondaryRateLimitDoesNotSurfaceAsPermissionFailure() = runTest {
        // GitHub's abuse-detection (secondary) rate limit returns 403 with Retry-After but no
        // x-ratelimit-remaining header. Misreading it as GitHubPermissionException would make the
        // scheduled worker give up permanently and tell the user their token lost write access,
        // for a throttle that clears itself.
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = {
                FakeConnection(
                    status = 403,
                    body = """{"message":"You have exceeded a secondary rate limit"}""",
                    headers = mapOf("retry-after" to "30")
                )
            },
            retryDelay = {}
        )

        val result = runCatching { api.getUser() }

        assertTrue(result.exceptionOrNull() is GitHubRateLimitException)
    }

    @Test
    fun plainForbiddenStaysPermissionFailure() = runTest {
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = {
                FakeConnection(status = 403, body = """{"message":"no access"}""")
            },
            retryDelay = {}
        )

        val result = runCatching { api.getUser() }

        assertTrue(result.exceptionOrNull() is GitHubPermissionException)
    }

    @Test
    fun networkFailureSurfacesSpecificReason() = runTest {
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { ThrowingConnection(java.net.UnknownHostException("api.github.com")) },
            retryDelay = {}
        )

        val result = runCatching { api.getUser() }

        val error = result.exceptionOrNull()
        assertTrue(error is GitHubTransportException)
        assertEquals("No internet connection or GitHub is unreachable", error?.message)
    }

    @Test
    fun capturesTokenExpirationHeader() = runTest {
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = {
                FakeConnection(
                    status = 200,
                    body = """{"login":"octo"}""",
                    headers = mapOf(
                        "GitHub-Authentication-Token-Expiration" to "2026-01-01T00:00:00Z"
                    )
                )
            },
            retryDelay = {}
        )

        api.getUser()

        assertEquals(1767225600000L, api.tokenExpiresAtEpochMillis)
    }

    @Test
    fun missingTokenExpirationHeaderClearsPreviousValue() = runTest {
        val connections = ArrayDeque(
            listOf(
                FakeConnection(
                    status = 200,
                    body = """{"login":"octo"}""",
                    headers = mapOf(
                        "GitHub-Authentication-Token-Expiration" to "2026-01-01T00:00:00Z"
                    )
                ),
                FakeConnection(status = 200, body = """{"login":"octo"}""")
            )
        )
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { connections.removeFirst() },
            retryDelay = {}
        )

        api.getUser()
        api.getUser()

        assertEquals(null, api.tokenExpiresAtEpochMillis)
    }

    @Test
    fun getContentReturnsNullForMissingPath() = runTest {
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = {
                FakeConnection(status = 404, body = """{"message":"not found"}""")
            },
            retryDelay = {}
        )

        val content = api.getContent("owner", "repo", "yata/snapshot.json", "main")

        assertEquals(null, content)
    }

    @Test
    fun getContentEncodesPathAndRefSafely() = runTest {
        var requestedUrl = ""
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { url ->
                requestedUrl = url.toString()
                FakeConnection(
                    status = 200,
                    body = """{"path":"yata/snapshot one.json","type":"file","sha":"blob-sha"}"""
                )
            },
            retryDelay = {}
        )

        val content = api.getContent("my owner", "sync repo", "yata/snapshot one.json", "feature/sync now")

        assertEquals("blob-sha", content?.sha)
        assertEquals(
            "https://api.github.com/repos/my%20owner/sync%20repo/contents/yata/snapshot%20one.json?ref=feature%2Fsync%20now",
            requestedUrl
        )
    }

    @Test
    fun getRefEncodesBranchSegmentsSafely() = runTest {
        var requestedUrl = ""
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { url ->
                requestedUrl = url.toString()
                FakeConnection(status = 200, body = """{"object":{"sha":"commit-sha"}}""")
            },
            retryDelay = {}
        )

        val ref = api.getRef("owner", "repo", "feature/sync now")

        assertEquals("commit-sha", ref.sha)
        assertEquals(
            "https://api.github.com/repos/owner/repo/git/ref/heads/feature/sync%20now",
            requestedUrl
        )
    }

    @Test
    fun listCommitsPaginatesUntilShortPage() = runTest {
        val firstPage = (1..100).joinToString(prefix = "[", postfix = "]") { commitJson("sha-$it") }
        val secondPage = (101..102).joinToString(prefix = "[", postfix = "]") { commitJson("sha-$it") }
        val connections = ArrayDeque(
            listOf(
                FakeConnection(status = 200, body = firstPage),
                FakeConnection(status = 200, body = secondPage)
            )
        )
        val requestedUrls = mutableListOf<String>()
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { url ->
                requestedUrls += url.toString()
                connections.removeFirst()
            },
            retryDelay = {}
        )

        val commits = api.listCommits("owner", "repo", "main", "yata/snapshot.json")

        assertEquals(102, commits.size)
        assertEquals("sha-1", commits.first().sha)
        assertEquals("sha-102", commits.last().sha)
        assertTrue(requestedUrls[0].contains("page=1"))
        assertTrue(requestedUrls[1].contains("page=2"))
    }

    @Test
    fun listCommitsEncodesQueryValuesSafely() = runTest {
        var requestedUrl = ""
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { url ->
                requestedUrl = url.toString()
                FakeConnection(status = 200, body = "[]")
            },
            retryDelay = {}
        )

        api.listCommits("owner", "repo", "feature/sync now", "yata/snapshot one.json")

        assertEquals(
            "https://api.github.com/repos/owner/repo/commits?sha=feature%2Fsync%20now&path=yata%2Fsnapshot%20one.json&per_page=100&page=1",
            requestedUrl
        )
    }

    @Test
    fun updateRefEncodesBranchSegmentsSafely() = runTest {
        var requestedUrl = ""
        val api = HttpGitHubApi(
            tokenProvider = { "token" },
            connectionFactory = { url ->
                requestedUrl = url.toString()
                FakeConnection(status = 200, body = """{"object":{"sha":"commit-sha"}}""")
            },
            retryDelay = {}
        )

        val ref = api.updateRef("owner", "repo", "feature/sync now", "commit-sha")

        assertEquals("commit-sha", ref.sha)
        assertEquals(
            "https://api.github.com/repos/owner/repo/git/refs/heads/feature/sync%20now",
            requestedUrl
        )
    }

    private fun commitJson(sha: String): String =
        """{"sha":"$sha","commit":{"message":"sync","author":{"date":"2026-01-01T00:00:00Z"}}}"""

    private class FakeConnection(
        url: URL = URL("https://api.github.test/user"),
        private val status: Int,
        private val body: String,
        private val headers: Map<String, String> = emptyMap()
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun setRequestMethod(method: String?) {
            this.method = method
        }
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getHeaderField(name: String?): String? = headers[name]
    }

    private class ThrowingConnection(
        private val failure: java.io.IOException,
        url: URL = URL("https://api.github.test/user")
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun setRequestMethod(method: String?) {
            this.method = method
        }
        override fun getResponseCode(): Int = throw failure
    }
}
