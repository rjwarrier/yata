package com.mj.yata.data.github

import android.os.Build
import android.util.Log
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sync.SnapshotSyncEngine
import com.mj.yata.domain.model.BackupSummary
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncRunReport
import com.mj.yata.domain.sync.SyncTransport
import com.mj.yata.util.BackupCrypto
import com.mj.yata.util.JsonExporter
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
    private val userPreferences: UserPreferences,
    private val credentialsStore: RemoteBackupCredentialsStore,
    private val snapshotSyncEngine: SnapshotSyncEngine,
    private val recoveryBackupManager: RecoveryBackupManager,
    private val jsonExporter: JsonExporter
) : SyncTransport {

    private val sessionMutex = Mutex()

    override suspend fun syncNow(progress: (Int, String) -> Unit): Result<SyncRunReport> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                var syncResult: Result<SyncRunReport>? = null
                withContext(NonCancellable) {
                    val config = config()
                    val api = api(config)
                    syncResult = publisher(api).sync(config, progress)
                    if (syncResult?.isSuccess == true) {
                        userPreferences.setSftpLastBackupAt(System.currentTimeMillis())
                    }
                }
                syncResult ?: Result.failure(IllegalStateException("GitHub sync did not complete"))
            } catch (e: Exception) {
                Log.w(TAG, "syncNow failed", e)
                Result.failure<SyncRunReport>(e)
            }
        }
    }

    override suspend fun listRestorePoints(): Result<List<RestorePoint>> = withContext(Dispatchers.IO) {
        try {
            val config = config()
            val commits = api(config).listCommits(
                config.owner,
                config.repo,
                config.branch,
                GitHubSnapshotPublisher.SNAPSHOT_PATH
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
        return BackupCrypto.decrypt(bytes, passphrase)
    }

    private fun commitMessage(bytes: ByteArray): String {
        val summary = runCatching { jsonExporter.summarise(bytes) }.getOrNull()
        val counts = if (summary != null) {
            "${summary.totalTasks} tasks, ${summary.totalProjects} projects"
        } else {
            "snapshot"
        }
        return "YATA sync from ${deviceLabel()} - $counts"
    }

    private fun publisher(api: GitHubApi): GitHubSnapshotPublisher =
        GitHubSnapshotPublisher(
            api = api,
            prepare = { remoteBytes, scopeKey, remoteIsRecovery ->
                val prepared = snapshotSyncEngine.prepare(
                    remoteBytes = remoteBytes,
                    scopeKey = scopeKey,
                    remoteIsRecovery = remoteIsRecovery
                )
                GitHubPreparedSnapshot(
                    canonicalBytes = prepared.canonicalBytes,
                    remoteNeedsPublish = prepared.remoteNeedsPublish,
                    token = prepared
                )
            },
            commit = { prepared ->
                snapshotSyncEngine.commit(
                    prepared.token as com.mj.yata.data.sync.PreparedSnapshotSync
                )
            },
            encode = ::encodePayload,
            decode = ::decodePayload,
            validateRemoteSnapshot = snapshotSyncEngine::isValidRemoteSnapshot,
            commitMessage = ::commitMessage,
            lastObservedHead = { userPreferences.githubLastHeadShaFlow.first() },
            onHeadObserved = { userPreferences.setGitHubLastHeadSha(it) },
            onHeadPublished = { userPreferences.setGitHubLastHeadSha(it) }
        )

    private fun api(config: GitHubSyncConfig): GitHubApi =
        HttpGitHubApi(
            tokenProvider = { credentialsStore.githubToken },
            apiBaseProvider = { config.apiBase }
        )

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

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val cleanedModel = if (
            manufacturer.isNotBlank() &&
            model.startsWith(manufacturer, ignoreCase = true)
        ) {
            model
        } else {
            listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
        }
        return cleanedModel.ifBlank { "Unknown Android device" }
    }

    private companion object {
        const val TAG = "GitHubSyncManager"
    }
}
