package com.mj.yata.domain.sync

import com.mj.yata.domain.model.BackupSummary
import java.time.Instant

interface SyncTransport {
    suspend fun syncNow(
        progress: (Int, String) -> Unit = { _, _ -> },
        options: SyncRunOptions = SyncRunOptions()
    ): Result<SyncRunReport>
    /** [limit] bounds how many points a caller that only needs the most recent few (e.g. a
     * one-line "last synced" summary) has to pay to fetch — for GitHub this is a real network
     * cost, since each restore point is a commit paginated 100 at a time from the API; SFTP/FTP's
     * rotated history files are already small enough that it's a no-op there. */
    suspend fun listRestorePoints(limit: Int = Int.MAX_VALUE): Result<List<RestorePoint>>
    suspend fun restore(id: String): Result<Unit>
    suspend fun inspect(id: String): Result<BackupSummary>
    suspend fun readSnapshot(id: String): Result<ByteArray>
    suspend fun isConfigured(): Boolean
}

data class SyncRunReport(
    val conflictsResolved: Int = 0,
    val details: List<String> = emptyList()
)

data class SyncRunOptions(
    val allowInitialJoinMerge: Boolean = false,
    /** See `SnapshotSyncEngine.checkLocalNotUnexpectedlyEmpty` — bypasses the confirmation that
     * fires when this device looks freshly restored (empty local data, but a populated baseline
     * and remote), letting the user proceed after explicitly confirming it's an intentional wipe. */
    val allowEmptyLocalOverwrite: Boolean = false
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
