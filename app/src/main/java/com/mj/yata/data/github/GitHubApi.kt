package com.mj.yata.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

interface GitHubApi {
    suspend fun getRepo(owner: String, repo: String): GitHubRepo
    suspend fun getUser(): GitHubUser
    suspend fun createRepo(name: String, private: Boolean = true): GitHubRepo
    suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef
    suspend fun createRef(owner: String, repo: String, ref: String, sha: String): GitHubRef
    suspend fun updateRef(owner: String, repo: String, branch: String, sha: String): GitHubRef
    suspend fun getCommit(owner: String, repo: String, sha: String): GitHubCommit
    suspend fun createCommit(owner: String, repo: String, message: String, treeSha: String, parents: List<String>): GitHubCommit
    suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree
    suspend fun createTree(owner: String, repo: String, baseTreeSha: String?, entries: List<GitHubTreeEntry>): GitHubTree
    suspend fun getContent(owner: String, repo: String, path: String, ref: String): GitHubContent?
    suspend fun getBlob(owner: String, repo: String, sha: String): ByteArray
    suspend fun createBlob(owner: String, repo: String, bytes: ByteArray): String
    suspend fun listCommits(owner: String, repo: String, branch: String, path: String): List<GitHubCommitSummary>
}

data class GitHubRepo(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val canPush: Boolean
)

data class GitHubUser(val login: String)

data class GitHubRef(val sha: String)

data class GitHubCommit(
    val sha: String,
    val treeSha: String,
    val parentShas: List<String> = emptyList()
)

data class GitHubTree(
    val sha: String,
    val entries: List<GitHubTreeEntry>,
    val truncated: Boolean = false
)

data class GitHubTreeEntry(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

data class GitHubContent(
    val path: String,
    val type: String,
    val sha: String
)

data class GitHubCommitSummary(
    val sha: String,
    val message: String,
    val authoredAt: Instant?
)

sealed class GitHubException(message: String) : Exception(message)
class GitHubAuthException(message: String = "GitHub token is invalid or expired") : GitHubException(message)
class GitHubRateLimitException(val resetAtEpochSeconds: Long?) : GitHubException("GitHub rate limit reached")
class GitHubPermissionException(message: String = "GitHub token does not have write access to this repo") : GitHubException(message)
class GitHubConflictException(message: String = "GitHub repository changed during sync") : GitHubException(message)
class GitHubHistoryRewrittenException(
    message: String =
        "GitHub branch history changed outside YATA. Restore a snapshot from GitHub history, or reconnect this repo after confirming the current snapshot is correct."
) : GitHubException(message)
class GitHubNotFoundException(message: String = "GitHub resource was not found") : GitHubException(message)
class GitHubTransportException(message: String = "GitHub request failed") : GitHubException(message)

class HttpGitHubApi(
    private val tokenProvider: () -> String?,
    private val apiBaseProvider: () -> String = { "https://api.github.com" },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
) : GitHubApi {

    override suspend fun getRepo(owner: String, repo: String): GitHubRepo {
        val json = requestJson("GET", "/repos/${path(owner)}/${path(repo)}")
        val permissions = json.optJSONObject("permissions")
        return GitHubRepo(
            owner = json.optJSONObject("owner")?.optString("login").orEmpty().ifBlank { owner },
            name = json.optString("name", repo),
            defaultBranch = json.optString("default_branch", "main"),
            canPush = permissions?.optBoolean("push", false) ?: false
        )
    }

    override suspend fun getUser(): GitHubUser =
        GitHubUser(requestJson("GET", "/user").getString("login"))

    override suspend fun createRepo(name: String, private: Boolean): GitHubRepo {
        val body = JSONObject().put("name", name).put("private", private)
        val json = requestJson("POST", "/user/repos", body = body)
        return GitHubRepo(
            owner = json.optJSONObject("owner")?.optString("login").orEmpty(),
            name = json.optString("name", name),
            defaultBranch = json.optString("default_branch", "main"),
            canPush = json.optJSONObject("permissions")?.optBoolean("push", false) ?: true
        )
    }

    override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef =
        try {
            GitHubRef(requestJson("GET", "/repos/${path(owner)}/${path(repo)}/git/ref/heads/${path(branch)}")
                .getJSONObject("object").getString("sha"))
        } catch (e: GitHubConflictException) {
            // GitHub returns 409, not 404, when an otherwise valid repo has no commits yet.
            throw GitHubNotFoundException("GitHub repository is empty")
        }

    override suspend fun createRef(owner: String, repo: String, ref: String, sha: String): GitHubRef {
        val body = JSONObject().put("ref", ref).put("sha", sha)
        return GitHubRef(requestJson("POST", "/repos/${path(owner)}/${path(repo)}/git/refs", body = body)
            .getJSONObject("object").getString("sha"))
    }

    override suspend fun updateRef(owner: String, repo: String, branch: String, sha: String): GitHubRef {
        val body = JSONObject().put("sha", sha).put("force", false)
        return GitHubRef(requestJson("PATCH", "/repos/${path(owner)}/${path(repo)}/git/refs/heads/${path(branch)}", body = body)
            .getJSONObject("object").getString("sha"))
    }

    override suspend fun getCommit(owner: String, repo: String, sha: String): GitHubCommit {
        val json = requestJson("GET", "/repos/${path(owner)}/${path(repo)}/git/commits/${path(sha)}")
        return GitHubCommit(
            sha = json.getString("sha"),
            treeSha = json.getJSONObject("tree").getString("sha"),
            parentShas = json.optJSONArray("parents")?.objects()?.map { it.getString("sha") }.orEmpty()
        )
    }

    override suspend fun createCommit(owner: String, repo: String, message: String, treeSha: String, parents: List<String>): GitHubCommit {
        val body = JSONObject()
            .put("message", message)
            .put("tree", treeSha)
            .put("parents", JSONArray(parents))
        val json = requestJson("POST", "/repos/${path(owner)}/${path(repo)}/git/commits", body = body)
        return GitHubCommit(
            sha = json.getString("sha"),
            treeSha = json.getJSONObject("tree").getString("sha"),
            parentShas = parents
        )
    }

    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        val json = requestJson("GET", "/repos/${path(owner)}/${path(repo)}/git/trees/${path(treeSha)}?recursive=1")
        return GitHubTree(
            sha = json.getString("sha"),
            truncated = json.optBoolean("truncated", false),
            entries = json.getJSONArray("tree").objects().map {
                GitHubTreeEntry(
                    path = it.getString("path"),
                    mode = it.optString("mode", "100644"),
                    type = it.optString("type", "blob"),
                    sha = it.optString("sha")
                )
            }
        )
    }

    override suspend fun createTree(owner: String, repo: String, baseTreeSha: String?, entries: List<GitHubTreeEntry>): GitHubTree {
        val body = JSONObject()
            .put("tree", JSONArray(entries.map { entry ->
                JSONObject()
                    .put("path", entry.path)
                    .put("mode", entry.mode)
                    .put("type", entry.type)
                    .put("sha", entry.sha)
            }))
        if (baseTreeSha != null) body.put("base_tree", baseTreeSha)
        val json = requestJson("POST", "/repos/${path(owner)}/${path(repo)}/git/trees", body = body)
        return GitHubTree(sha = json.getString("sha"), entries = emptyList())
    }

    override suspend fun getContent(owner: String, repo: String, path: String, ref: String): GitHubContent? =
        try {
            val json = requestJson("GET", "/repos/${path(owner)}/${path(repo)}/contents/${path(path)}?ref=${path(ref)}")
            GitHubContent(
                path = json.optString("path", path),
                type = json.optString("type"),
                sha = json.getString("sha")
            )
        } catch (_: GitHubNotFoundException) {
            null
        }

    override suspend fun getBlob(owner: String, repo: String, sha: String): ByteArray {
        val json = requestJson("GET", "/repos/${path(owner)}/${path(repo)}/git/blobs/${path(sha)}")
        return Base64.getMimeDecoder().decode(json.getString("content"))
    }

    override suspend fun createBlob(owner: String, repo: String, bytes: ByteArray): String {
        val body = JSONObject()
            .put("content", Base64.getEncoder().encodeToString(bytes))
            .put("encoding", "base64")
        return requestJson("POST", "/repos/${path(owner)}/${path(repo)}/git/blobs", body = body)
            .getString("sha")
    }

    override suspend fun listCommits(owner: String, repo: String, branch: String, path: String): List<GitHubCommitSummary> {
        val encodedPath = path.replace("/", "%2F")
        val out = mutableListOf<GitHubCommitSummary>()
        var page = 1
        while (true) {
            val array = requestJsonArray(
                "GET",
                "/repos/${path(owner)}/${path(repo)}/commits?sha=${path(branch)}&path=$encodedPath&per_page=$COMMITS_PAGE_SIZE&page=$page"
            )
            if (array.length() == 0) break
            out += array.objects().map { json ->
                val commit = json.getJSONObject("commit")
                val author = commit.optJSONObject("author")
                GitHubCommitSummary(
                    sha = json.getString("sha"),
                    message = commit.optString("message"),
                    authoredAt = author?.optString("date")?.parseInstantOrNull()
                )
            }
            if (array.length() < COMMITS_PAGE_SIZE) break
            page++
        }
        return out
    }

    private suspend fun requestJson(method: String, endpoint: String, body: JSONObject? = null): JSONObject =
        JSONObject(String(requestBytes(method, endpoint, body), Charsets.UTF_8))

    private suspend fun requestJsonArray(method: String, endpoint: String): JSONArray =
        JSONArray(String(requestBytes(method, endpoint), Charsets.UTF_8))

    private suspend fun requestBytes(method: String, endpoint: String, body: JSONObject? = null): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return requestBytesOnce(method, endpoint, body)
            } catch (e: GitHubException) {
                val nextAttempt = attempt + 1
                if (nextAttempt >= MAX_HTTP_ATTEMPTS || !e.isRetryable()) throw e
                retryDelay(e.retryDelayMillis(attempt))
                attempt = nextAttempt
            }
        }
    }

    private suspend fun requestBytesOnce(method: String, endpoint: String, body: JSONObject? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val connection = openConnection(endpoint)
            try {
                connection.requestMethod = method
                if (body != null) {
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                val bytes = if (status in 200..299) {
                    connection.inputStream.use { it.readBytesCompat() }
                } else {
                    connection.errorStream?.use { it.readBytesCompat() } ?: ByteArray(0)
                }
                if (status !in 200..299) throw mapError(status, connection)
                bytes
            } catch (e: GitHubException) {
                throw e
            } catch (e: Exception) {
                throw GitHubTransportException()
            } finally {
                connection.disconnect()
            }
        }

    private fun openConnection(endpoint: String): HttpURLConnection {
        val base = apiBaseProvider().trimEnd('/')
        val connection = connectionFactory(URL(base + endpoint))
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        tokenProvider()?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        return connection
    }

    private fun mapError(status: Int, connection: HttpURLConnection): GitHubException =
        when {
            status == 401 -> GitHubAuthException()
            status == 403 && connection.getHeaderField("x-ratelimit-remaining") == "0" ->
                GitHubRateLimitException(connection.getHeaderField("x-ratelimit-reset")?.toLongOrNull())
            status == 403 -> GitHubPermissionException()
            status == 404 -> GitHubNotFoundException()
            status == 409 || status == 422 -> GitHubConflictException()
            status >= 500 -> GitHubTransportException("GitHub is temporarily unavailable")
            else -> GitHubTransportException("GitHub request was rejected")
        }

    private fun GitHubException.isRetryable(): Boolean =
        this is GitHubTransportException ||
            (this is GitHubRateLimitException && retryAfterMillis() != null)

    private fun GitHubException.retryDelayMillis(attempt: Int): Long =
        when (this) {
            is GitHubRateLimitException -> retryAfterMillis()
                ?: BASE_RETRY_DELAY_MS
            else -> BASE_RETRY_DELAY_MS * (1L shl attempt).coerceAtMost(4L)
        }

    private fun GitHubRateLimitException.retryAfterMillis(): Long? {
        val resetAt = resetAtEpochSeconds ?: return null
        val delta = resetAt * 1_000L - System.currentTimeMillis()
        return delta.coerceAtLeast(0L).takeIf { it <= MAX_RATE_LIMIT_RETRY_DELAY_MS }
    }

    private fun path(value: String): String =
        value.trim().replace(" ", "%20")

    private fun java.io.InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun String.parseInstantOrNull(): Instant? =
        try {
            Instant.parse(this)
        } catch (_: DateTimeParseException) {
            null
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_HTTP_ATTEMPTS = 3
        const val BASE_RETRY_DELAY_MS = 500L
        const val MAX_RATE_LIMIT_RETRY_DELAY_MS = 5_000L
        const val COMMITS_PAGE_SIZE = 100
    }
}
