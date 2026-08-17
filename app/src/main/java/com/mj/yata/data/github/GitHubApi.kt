package com.mj.yata.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * The GitHub API base is a free-text field (so self-hosted GitHub Enterprise Server's
 * `https://<host>/api/v3` works), but the PAT is sent as `Authorization: Bearer` to whatever it
 * says. Requiring https here doesn't fully close that - an attacker-controlled host can still
 * terminate TLS and receive the token - but it does rule out a plaintext leak and typos that
 * silently resolve to an unintended scheme (`javascript:`, `file:`, a bare host with no scheme).
 */
object GitHubApiBase {
    const val DEFAULT = "https://api.github.com"

    fun validate(raw: String): String {
        val trimmed = raw.trim().ifBlank { DEFAULT }.trimEnd('/')
        val url = try {
            URL(trimmed)
        } catch (e: java.net.MalformedURLException) {
            throw IllegalArgumentException("GitHub API base URL is not valid", e)
        }
        require(url.protocol == "https") { "GitHub API base URL must start with https://" }
        require(!url.host.isNullOrBlank()) { "GitHub API base URL must include a host" }
        return trimmed
    }
}

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
    suspend fun listCommits(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        maxResults: Int = Int.MAX_VALUE
    ): List<GitHubCommitSummary>
    suspend fun compareCommits(owner: String, repo: String, base: String, head: String): GitHubCompareResult
}

data class GitHubRepo(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val canPush: Boolean,
    // Absence is treated as "not confirmed private" (false) rather than assumed-safe, so a
    // malformed/unexpected API response blocks sync instead of silently publishing task data to
    // a public repo.
    val isPrivate: Boolean
)

/** `status` is one of GitHub's compare values: "identical", "ahead", "behind", "diverged". */
data class GitHubCompareResult(val status: String, val aheadBy: Int, val behindBy: Int)

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

sealed class GitHubException(message: String, cause: Throwable? = null) : Exception(message, cause)
class GitHubAuthException(message: String = "GitHub token is invalid or expired") : GitHubException(message)
class GitHubRateLimitException(val resetAtEpochSeconds: Long?) : GitHubException("GitHub rate limit reached")
class GitHubPermissionException(message: String = "GitHub token does not have write access to this repo") : GitHubException(message)
class GitHubConflictException(message: String = "GitHub repository changed during sync") : GitHubException(message)
class GitHubHistoryRewrittenException(
    message: String =
        "GitHub branch history changed outside YATA. Restore a snapshot from GitHub history, or reconnect this repo after confirming the current snapshot is correct."
) : GitHubException(message)
class GitHubNotFoundException(message: String = "GitHub resource was not found") : GitHubException(message)
class GitHubTransportException(
    message: String = "GitHub request failed",
    cause: Throwable? = null,
    val retryable: Boolean = true
) : GitHubException(message, cause)
class GitHubPublicRepoException(
    message: String = "This GitHub repository is public. YATA only syncs to a private repository " +
        "- make the repo private, or connect a different one, then try again."
) : GitHubException(message)

class HttpGitHubApi(
    private val tokenProvider: () -> String?,
    private val apiBaseProvider: () -> String = { "https://api.github.com" },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
) : GitHubApi {
    var tokenExpiresAtEpochMillis: Long? = null
        private set

    override suspend fun getRepo(owner: String, repo: String): GitHubRepo {
        val json = requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}")
        val permissions = json.optJSONObject("permissions")
        return GitHubRepo(
            owner = json.optJSONObject("owner")?.optString("login").orEmpty().ifBlank { owner },
            name = json.optString("name", repo),
            defaultBranch = json.optString("default_branch", "main"),
            canPush = permissions?.optBoolean("push", false) ?: false,
            isPrivate = json.optBoolean("private", false)
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
            canPush = json.optJSONObject("permissions")?.optBoolean("push", false) ?: true,
            isPrivate = json.optBoolean("private", private)
        )
    }

    override suspend fun getRef(owner: String, repo: String, branch: String): GitHubRef =
        try {
            GitHubRef(requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/ref/heads/${pathWithSlashes(branch)}")
                .getJSONObject("object").getString("sha"))
        } catch (e: GitHubConflictException) {
            // GitHub returns 409, not 404, when an otherwise valid repo has no commits yet.
            throw GitHubNotFoundException("GitHub repository is empty")
        }

    override suspend fun createRef(owner: String, repo: String, ref: String, sha: String): GitHubRef {
        val body = JSONObject().put("ref", ref).put("sha", sha)
        return GitHubRef(requestJson("POST", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/refs", body = body)
            .getJSONObject("object").getString("sha"))
    }

    override suspend fun updateRef(owner: String, repo: String, branch: String, sha: String): GitHubRef {
        val body = JSONObject().put("sha", sha).put("force", false)
        return GitHubRef(requestJson("PATCH", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/refs/heads/${pathWithSlashes(branch)}", body = body)
            .getJSONObject("object").getString("sha"))
    }

    override suspend fun getCommit(owner: String, repo: String, sha: String): GitHubCommit {
        val json = requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/commits/${pathSegment(sha)}")
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
        val json = requestJson("POST", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/commits", body = body)
        return GitHubCommit(
            sha = json.getString("sha"),
            treeSha = json.getJSONObject("tree").getString("sha"),
            parentShas = parents
        )
    }

    override suspend fun getTree(owner: String, repo: String, treeSha: String): GitHubTree {
        val json = requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/trees/${pathSegment(treeSha)}?recursive=1")
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
        val json = requestJson("POST", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/trees", body = body)
        return GitHubTree(sha = json.getString("sha"), entries = emptyList())
    }

    override suspend fun getContent(owner: String, repo: String, path: String, ref: String): GitHubContent? =
        try {
            val json = requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/contents/${pathWithSlashes(path)}?ref=${queryValue(ref)}")
            GitHubContent(
                path = json.optString("path", path),
                type = json.optString("type"),
                sha = json.getString("sha")
            )
        } catch (_: GitHubNotFoundException) {
            null
        }

    override suspend fun getBlob(owner: String, repo: String, sha: String): ByteArray {
        val json = requestJson("GET", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/blobs/${pathSegment(sha)}")
        val encoding = json.optString("encoding", "base64")
        if (encoding != "base64") {
            // GitHub returns encoding "none" with empty content for blobs above its inline size
            // limit. Decoding that as base64 silently yields empty bytes, which downstream looks
            // like a SHA mismatch rather than "this snapshot is too large to read this way".
            throw GitHubTransportException("GitHub blob is too large to read directly (encoding: $encoding)")
        }
        return Base64.getMimeDecoder().decode(json.getString("content"))
    }

    override suspend fun createBlob(owner: String, repo: String, bytes: ByteArray): String {
        val body = JSONObject()
            .put("content", Base64.getEncoder().encodeToString(bytes))
            .put("encoding", "base64")
        return requestJson("POST", "/repos/${pathSegment(owner)}/${pathSegment(repo)}/git/blobs", body = body)
            .getString("sha")
    }

    override suspend fun listCommits(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        maxResults: Int
    ): List<GitHubCommitSummary> {
        // Bounded to maxResults, not always the full page size: a caller asking for the latest 1
        // commit only shrinks the *number of pages* fetched if per_page stays uncapped - the page
        // itself still comes over the wire at full size. Capping per_page too means maxResults=1
        // actually downloads one commit, not a 100-commit page truncated client-side afterward.
        val perPage = minOf(maxResults, COMMITS_PAGE_SIZE)
        val out = mutableListOf<GitHubCommitSummary>()
        var page = 1
        while (out.size < maxResults) {
            val array = requestJsonArray(
                "GET",
                "/repos/${pathSegment(owner)}/${pathSegment(repo)}/commits?sha=${queryValue(branch)}&path=${queryValue(path)}&per_page=$perPage&page=$page"
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
            if (array.length() < perPage) break
            page++
        }
        return if (out.size > maxResults) out.take(maxResults) else out
    }

    override suspend fun compareCommits(owner: String, repo: String, base: String, head: String): GitHubCompareResult {
        val json = requestJson(
            "GET",
            "/repos/${pathSegment(owner)}/${pathSegment(repo)}/compare/${pathSegment(base)}...${pathSegment(head)}"
        )
        return GitHubCompareResult(
            status = json.optString("status", "diverged"),
            aheadBy = json.optInt("ahead_by", 0),
            behindBy = json.optInt("behind_by", 0)
        )
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
                tokenExpiresAtEpochMillis = connection.tokenExpirationEpochMillis()
                val bytes = if (status in 200..299) {
                    connection.inputStream.use { it.readBytesCompat() }
                } else {
                    connection.errorStream?.use { it.readBytesCompat() } ?: ByteArray(0)
                }
                if (status !in 200..299) throw mapError(status, connection, method, endpoint, bytes)
                bytes
            } catch (e: GitHubException) {
                throw e
            } catch (e: java.net.UnknownHostException) {
                throw GitHubTransportException("No internet connection or GitHub is unreachable", e)
            } catch (e: java.net.SocketTimeoutException) {
                throw GitHubTransportException("GitHub request timed out", e)
            } catch (e: javax.net.ssl.SSLException) {
                throw GitHubTransportException("Secure connection to GitHub failed", e)
            } catch (e: Exception) {
                throw GitHubTransportException(
                    e.message?.let { "GitHub request failed: $it" } ?: "GitHub request failed",
                    e
                )
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

    private fun mapError(
        status: Int,
        connection: HttpURLConnection,
        method: String,
        endpoint: String,
        body: ByteArray
    ): GitHubException =
        when {
            status == 401 -> GitHubAuthException()
            status == 403 && connection.getHeaderField("x-ratelimit-remaining") == "0" ->
                GitHubRateLimitException(connection.getHeaderField("x-ratelimit-reset")?.toLongOrNull())
            // GitHub's secondary (abuse-detection) rate limit also returns 403, but signals via
            // Retry-After instead of the primary limit's x-ratelimit-remaining header. Without this
            // check it fell through to GitHubPermissionException, which UnifiedBackupWorker treats
            // as permanent and stops retrying - wrongly telling the user their token lost write
            // access for what is actually a transient throttle that clears itself.
            status == 403 && connection.getHeaderField("retry-after") != null ->
                GitHubRateLimitException(connection.secondaryRateLimitResetEpochSeconds())
            status == 403 -> GitHubPermissionException(forbiddenMessage(method, endpoint, body))
            status == 404 -> GitHubNotFoundException()
            status == 429 -> GitHubRateLimitException(connection.secondaryRateLimitResetEpochSeconds())
            // A ref update genuinely races another writer and returns 409/422 - that's the
            // publish-time compare-and-swap conflict the publisher retries on. A 422 from any
            // other endpoint (e.g. an oversized/malformed blob or tree) is a validation failure
            // that retrying won't fix; treating it as a conflict burned all CAS retries and then
            // reported "repository kept changing during sync" for what was really a bad request.
            status == 409 -> GitHubConflictException()
            status == 422 && endpoint.contains("/git/refs/") -> GitHubConflictException()
            status == 422 -> GitHubTransportException("GitHub rejected the request as invalid", retryable = false)
            status >= 500 -> GitHubTransportException("GitHub is temporarily unavailable")
            else -> GitHubTransportException("GitHub request was rejected")
        }

    private fun forbiddenMessage(method: String, endpoint: String, body: ByteArray): String {
        val githubReason = githubErrorMessage(body)
        val action = when {
            method == "GET" -> "access this repo"
            endpoint.contains("/git/refs/") && method == "PATCH" -> "update this branch"
            method == "POST" || method == "PATCH" -> "write to this repo"
            else -> "use this repo"
        }
        return buildString {
            append("GitHub token cannot ").append(action)
            githubReason?.let { append(": ").append(it) }
        }
    }

    private fun githubErrorMessage(body: ByteArray): String? {
        val raw = body.toString(Charsets.UTF_8).trim()
        if (raw.isBlank()) return null
        return try {
            JSONObject(raw).optString("message")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.take(220)
        } catch (_: Exception) {
            raw.replace(Regex("\\s+"), " ").take(220)
        }
    }

    private fun HttpURLConnection.secondaryRateLimitResetEpochSeconds(): Long? =
        getHeaderField("retry-after")?.toLongOrNull()?.let { retryAfterSeconds ->
            System.currentTimeMillis() / 1_000 + retryAfterSeconds
        }

    private fun GitHubException.isRetryable(): Boolean =
        (this is GitHubTransportException && retryable) ||
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

    private fun pathSegment(value: String): String =
        urlEncode(value.trim())

    private fun pathWithSlashes(value: String): String =
        value.trim().split("/").joinToString("/") { pathSegment(it) }

    private fun queryValue(value: String): String =
        urlEncode(value.trim())

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun HttpURLConnection.tokenExpirationEpochMillis(): Long? =
        getHeaderField("GitHub-Authentication-Token-Expiration")
            ?.parseInstantOrNull()
            ?.toEpochMilli()

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
