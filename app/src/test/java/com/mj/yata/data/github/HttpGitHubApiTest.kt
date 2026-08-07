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
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getHeaderField(name: String?): String? = headers[name]
    }
}
