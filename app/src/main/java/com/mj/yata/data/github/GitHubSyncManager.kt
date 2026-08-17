package com.mj.yata.data.github

import android.content.Context
import android.util.Log
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sync.SnapshotSyncEngine
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncCommitMessage
import com.mj.yata.domain.sync.SyncRunOptions
import com.mj.yata.domain.sync.SyncRunReport
import com.mj.yata.domain.sync.SyncTransport
import com.mj.yata.util.BackupCrypto
import com.mj.yata.util.JsonExporter
import com.mj.yata.util.syncDeviceLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val credentialsStore: RemoteBackupCredentialsStore,
    private val snapshotSyncEngine: SnapshotSyncEngine,
    private val recoveryBackupManager: RecoveryBackupManager,
    private val jsonExporter: JsonExporter
) : SyncTransport {

    private val sessionMutex = Mutex()

    override suspend fun syncNow(
        progress: (Int, String) -> Unit,
        options: SyncRunOptions
    ): Result<SyncRunReport> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // Only the local-write step (inside publisher()'s commit lambda, below) runs under
                // NonCancellable now - it used to wrap the entire network round trip, which made a
                // sync retrying against a slow/unreachable host (CAS retries x HTTP retries x
                // per-request timeouts) uncancellable for potentially minutes, with no way for the
                // user or WorkManager to stop it. Network calls fail safely if cancelled; a
                // half-applied local snapshot replace does not, so that part still needs the guard.
                val config = config()
                val api = api(config)
                var syncResult = fastUnchangedSync(config, api, progress, options)
                    ?: publisher(api, options).sync(config, progress)
                userPreferences.setGitHubTokenExpiresAt(api.tokenExpiresAtEpochMillis)
                syncResult = syncResult.map { report ->
                    report.copy(details = report.details + githubSyncDetails(api.tokenExpiresAtEpochMillis))
                }
                if (syncResult.isSuccess) {
                    userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
                }
                syncResult
            } catch (e: Exception) {
                Log.w(TAG, "syncNow failed", e)
                Result.failure<SyncRunReport>(e)
            }
        }
    }

    override suspend fun listRestorePoints(limit: Int): Result<List<RestorePoint>> = withContext(Dispatchers.IO) {
        try {
            val config = config()
            val commits = api(config).listCommits(
                config.owner,
                config.repo,
                config.branch,
                GitHubSnapshotPublisher.SNAPSHOT_PATH,
                maxResults = limit
            )
            Result.success(commits.map { commit ->
                RestorePoint(
                    id = commit.sha,
                    label = commit.message.lineSequence().firstOrNull()?.ifBlank { null } ?: commit.sha.take(12),
                    createdAt = commit.authoredAt
                )
            })
        } catch (e: Exception) {
            Log.w(TAG, "listRestorePoints failed", e)
            Result.failure(e)
        }
    }

    override suspend fun restore(id: String): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val bytes = readSnapshot(id).getOrThrow()
                jsonExporter.dryRunRestoreBytes(bytes)
                recoveryBackupManager.saveCurrent("pre_github_restore").getOrElse { e ->
                    throw IllegalStateException(
                        "Could not create a recovery backup before restore; local data was not changed",
                        e
                    )
                }
                if (jsonExporter.importBytes(bytes)) {
                    userPreferences.setGitHubSyncedState(id, snapshotSyncEngine.localCanonicalHash())
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Restore failed - backup file unreadable"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "restore failed", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun inspect(id: String): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            Result.success(jsonExporter.summarise(readSnapshot(id).getOrThrow()))
        } catch (e: Exception) {
            Log.w(TAG, "inspect failed", e)
            Result.failure(e)
        }
    }

    override suspend fun readSnapshot(id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val config = config()
            val api = api(config)
            Result.success(publisher(api).readSnapshot(config, id))
        } catch (e: Exception) {
            Log.w(TAG, "readSnapshot failed", e)
            Result.failure(e)
        }
    }

    override suspend fun isConfigured(): Boolean =
        userPreferences.githubOwnerFlow.first().isNotBlank() &&
            userPreferences.githubRepoFlow.first().isNotBlank() &&
            !credentialsStore.githubToken.isNullOrBlank()

    private fun encodePayload(jsonBytes: ByteArray): ByteArray {
        val passphrase = credentialsStore.backupPassphrase
        return if (passphrase == null) jsonBytes else BackupCrypto.encrypt(jsonBytes, passphrase)
    }

    private fun decodePayload(bytes: ByteArray): ByteArray {
        if (!BackupCrypto.isEncrypted(bytes)) return bytes
        val passphrase = credentialsStore.backupPassphrase
            ?: throw IllegalStateException("This GitHub snapshot is encrypted - set the backup passphrase first")
        return try {
            BackupCrypto.decrypt(bytes, passphrase)
        } catch (e: java.security.GeneralSecurityException) {
            // Otherwise this surfaces as a raw AEADBadTagException with no actionable message -
            // and, worse, propagates past readRemoteSnapshot's damaged-head handling, so it never
            // gets the "try an older recovery commit" treatment a genuinely corrupt snapshot does.
            throw IllegalStateException("Wrong backup passphrase, or the GitHub snapshot is damaged", e)
        }
    }

    private fun commitMessage(bytes: ByteArray): String {
        val summary = runCatching { jsonExporter.summarise(bytes) }.getOrNull()
        val counts = if (summary != null) {
            "${summary.totalTasks} tasks, ${summary.totalProjects} projects"
        } else {
            "snapshot"
        }
        return SyncCommitMessage.format(deviceLabel(), counts)
    }

    private fun publisher(api: GitHubApi, options: SyncRunOptions = SyncRunOptions()): GitHubSnapshotPublisher =
        GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, scopeKey, remoteIsRecovery ->
                val prepared = snapshotSyncEngine.prepare(
                    remoteBytes = remoteBytes,
                    scopeKey = scopeKey,
                    remoteIsRecovery = remoteIsRecovery,
                    allowInitialJoinMerge = options.allowInitialJoinMerge
                )
                GitHubPreparedSnapshot(
                    canonicalBytes = prepared.canonicalBytes,
                    remoteNeedsPublish = prepared.remoteNeedsPublish,
                    canonicalHash = prepared.canonicalHash,
                    token = prepared
                )
            },
            commit = { prepared ->
                withContext(NonCancellable) {
                    snapshotSyncEngine.commit(
                        prepared.token as com.mj.yata.data.sync.PreparedSnapshotSync
                    )
                }
            },
            encode = ::encodePayload,
            decode = ::decodePayload,
            validateRemoteSnapshot = snapshotSyncEngine::isValidRemoteSnapshot,
            commitMessage = ::commitMessage,
            lastObservedHead = { userPreferences.githubLastHeadShaFlow.first() },
            onHeadSynced = { headSha, canonicalHash ->
                userPreferences.setGitHubSyncedState(headSha, canonicalHash)
            }
        )

    private suspend fun fastUnchangedSync(
        config: GitHubSyncConfig,
        api: HttpGitHubApi,
        progress: (Int, String) -> Unit,
        options: SyncRunOptions
    ): Result<SyncRunReport>? {
        if (options.allowInitialJoinMerge) return null

        val localHash = snapshotSyncEngine.localCanonicalHash()
        val lastHash = userPreferences.githubLastCanonicalHashFlow.first()?.takeIf { it.isNotBlank() }
            ?: return null
        if (localHash != lastHash) return null

        val lastHead = userPreferences.githubLastHeadShaFlow.first()?.takeIf { it.isNotBlank() }
            ?: return null

        progress(12, "Checking GitHub")
        val repo = api.getRepo(config.owner, config.repo)
        if (!repo.isPrivate) {
            throw GitHubPublicRepoException()
        }
        val currentHead = try {
            api.getRef(config.owner, config.repo, config.branch).sha
        } catch (_: GitHubNotFoundException) {
            return null
        }
        if (currentHead != lastHead) return null

        progress(74, "GitHub already up to date")
        progress(88, "No local changes")
        return Result.success(SyncRunReport(details = listOf("unchanged")))
    }

    private fun api(config: GitHubSyncConfig): HttpGitHubApi =
        HttpGitHubApi(
            tokenProvider = { credentialsStore.githubToken },
            apiBaseProvider = { config.apiBase }
        )

    private suspend fun githubSyncDetails(tokenExpiresAt: Long?): List<String> =
        buildList {
            userPreferences.githubLastHeadShaFlow.first()
                ?.takeIf { it.isNotBlank() }
                ?.let { add("head ${it.take(12)}") }
            tokenExpiresAt?.let { add(githubTokenExpiryDetail(it)) }
        }

    private fun githubTokenExpiryDetail(epochMillis: Long): String {
        val expiresAt = java.time.Instant.ofEpochMilli(epochMillis)
        val days = java.time.Duration.between(java.time.Instant.now(), expiresAt).toDays()
        return when {
            days < 0 -> "token expired ${expiresAt}"
            days <= 14 -> "token expires in ${days.coerceAtLeast(0).formatDays()}"
            else -> "token expires ${expiresAt}"
        }
    }

    private fun Long.formatDays(): String =
        if (this == 1L) "1 day" else "$this days"

    private suspend fun config(): GitHubSyncConfig {
        val owner = userPreferences.githubOwnerFlow.first().trim()
        val repo = userPreferences.githubRepoFlow.first().trim()
        val branch = userPreferences.githubBranchFlow.first().trim().ifBlank { "main" }
        val apiBase = userPreferences.githubApiBaseFlow.first().trim().ifBlank { "https://api.github.com" }
        if (owner.isBlank() || repo.isBlank()) {
            throw GitHubNotFoundException("GitHub repo is not configured")
        }
        if (credentialsStore.githubToken.isNullOrBlank()) {
            throw GitHubAuthException("GitHub token is not configured")
        }
        return GitHubSyncConfig(owner, repo, branch, apiBase)
    }

    private fun deviceLabel(): String = context.syncDeviceLabel()

    private companion object {
        const val TAG = "GitHubSyncManager"
    }
}
