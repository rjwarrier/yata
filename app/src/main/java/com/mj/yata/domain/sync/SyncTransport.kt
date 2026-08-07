package com.mj.yata.domain.sync

import com.mj.yata.domain.model.BackupSummary
import java.time.Instant

interface SyncTransport {
    suspend fun syncNow(progress: (Int, String) -> Unit = { _, _ -> }): Result<SyncRunReport>
    suspend fun listRestorePoints(): Result<List<RestorePoint>>
    suspend fun restore(id: String): Result<Unit>
    suspend fun inspect(id: String): Result<BackupSummary>
    suspend fun readSnapshot(id: String): Result<ByteArray>
    suspend fun isConfigured(): Boolean
}

data class SyncRunReport(
    val conflictsResolved: Int = 0
)

/** Lease-based transports only; GitHub uses fast-forward refs rather than a remote lock. */
interface LockableSyncTransport : SyncTransport {
    suspend fun clearSyncLock(): Result<Unit>
}

data class RestorePoint(
    val id: String,
    val label: String,
    val createdAt: Instant?
)
