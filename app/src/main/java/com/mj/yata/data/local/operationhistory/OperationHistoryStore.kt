package com.mj.yata.data.local.operationhistory

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class OperationHistoryEntry(
    val id: String,
    val title: String,
    val category: String,
    val lastRunAt: Long?,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val retryCount: Int,
    val lastReason: String?,
    val lastStatus: OperationStatus,
    val copyableLog: String?
)

enum class OperationStatus {
    NEVER_RUN,
    RUNNING,
    SUCCESS,
    FAILURE,
    SKIPPED
}

/**
 * Small on-device operation history for background work that can fail silently from the user's
 * point of view: backups, self-hosted sync, reminders, and widget refreshes.
 *
 * This deliberately lives outside Room/DataStore-backed app data and outside backups. It is
 * diagnostic state only, similar to CrashLogStore, and every write swallows its own errors so
 * observability can never become the reason an operation fails.
 */
@Singleton
class OperationHistoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun recordRun(id: String, reason: String) = edit(id) {
        putLong(key(id, LAST_RUN_AT), System.currentTimeMillis())
        putString(key(id, LAST_STATUS), OperationStatus.RUNNING.name)
        putString(key(id, LAST_REASON), reason)
    }

    @Synchronized
    fun recordSuccess(id: String, reason: String) = edit(id) {
        val now = System.currentTimeMillis()
        appendCopyableLogIfNeeded(id, now, OperationStatus.SUCCESS, reason)
        putLong(key(id, LAST_RUN_AT), now)
        putLong(key(id, LAST_SUCCESS_AT), now)
        putInt(key(id, RETRY_COUNT), 0)
        putString(key(id, LAST_STATUS), OperationStatus.SUCCESS.name)
        putString(key(id, LAST_REASON), reason)
    }

    @Synchronized
    fun recordFailure(id: String, throwable: Throwable?, reason: String? = null) = edit(id) {
        val now = System.currentTimeMillis()
        val message = reason ?: throwable?.message ?: throwable?.javaClass?.simpleName ?: "Operation failed"
        appendCopyableLogIfNeeded(id, now, OperationStatus.FAILURE, message)
        putLong(key(id, LAST_RUN_AT), now)
        putLong(key(id, LAST_FAILURE_AT), now)
        putInt(key(id, RETRY_COUNT), prefs.getInt(key(id, RETRY_COUNT), 0) + 1)
        putString(key(id, LAST_STATUS), OperationStatus.FAILURE.name)
        putString(key(id, LAST_REASON), message)
    }

    @Synchronized
    fun recordSkipped(id: String, reason: String) = edit(id) {
        val now = System.currentTimeMillis()
        putLong(key(id, LAST_RUN_AT), now)
        putString(key(id, LAST_STATUS), OperationStatus.SKIPPED.name)
        putString(key(id, LAST_REASON), reason)
    }

    @Synchronized
    fun list(): List<OperationHistoryEntry> = try {
        OPERATIONS.map { op ->
            OperationHistoryEntry(
                id = op.id,
                title = op.title,
                category = op.category,
                lastRunAt = prefs.longOrNull(key(op.id, LAST_RUN_AT)),
                lastSuccessAt = prefs.longOrNull(key(op.id, LAST_SUCCESS_AT)),
                lastFailureAt = prefs.longOrNull(key(op.id, LAST_FAILURE_AT)),
                retryCount = prefs.getInt(key(op.id, RETRY_COUNT), 0),
                lastReason = prefs.getString(key(op.id, LAST_REASON), null),
                lastStatus = runCatching {
                    OperationStatus.valueOf(prefs.getString(key(op.id, LAST_STATUS), null) ?: OperationStatus.NEVER_RUN.name)
                }.getOrDefault(OperationStatus.NEVER_RUN),
                copyableLog = prefs.getString(key(op.id, COPYABLE_LOG), null)
            )
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Could not list operation history", t)
        emptyList()
    }

    @Synchronized
    fun clear() {
        try {
            prefs.edit().clear().commit()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not clear operation history", t)
        }
    }

    private fun edit(id: String, block: android.content.SharedPreferences.Editor.() -> Unit) {
        try {
            if (OPERATIONS.none { it.id == id }) return
            prefs.edit().apply(block).commit()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not record operation history for $id", t)
        }
    }

    private fun android.content.SharedPreferences.Editor.appendCopyableLogIfNeeded(
        id: String,
        timestampMillis: Long,
        status: OperationStatus,
        reason: String
    ) {
        if (id != SYNC_GITHUB) return
        val runReason = prefs.getString(key(id, LAST_REASON), null).orEmpty()
        val trigger = if (runReason.contains("manual", ignoreCase = true) ||
            runReason.contains("user", ignoreCase = true)
        ) {
            "User-induced sync"
        } else {
            "Automatic sync"
        }
        val outcome = when (status) {
            OperationStatus.SUCCESS -> "Succeeded"
            OperationStatus.FAILURE -> "Failed"
            else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        val entry = buildString {
            append("Date/time: ").append(formatLogTimestamp(timestampMillis)).append('\n')
            append("Trigger: ").append(trigger)
            if (runReason.isNotBlank()) append(" (").append(runReason).append(')')
            append('\n')
            append("Result: ").append(outcome).append('\n')
            append("Reason: ").append(reason.ifBlank { "No additional details were reported." })
        }
        val previousEntries = prefs.getString(key(id, COPYABLE_LOG), null)
            ?.split(LOG_ENTRY_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val retainedEntries = retainFailuresLonger(listOf(entry) + previousEntries)
        val updatedLog = retainedEntries
            .take(MAX_COPYABLE_LOG_ENTRIES)
            .joinToString(LOG_ENTRY_SEPARATOR)
        putString(key(id, COPYABLE_LOG), updatedLog)
    }

    private fun retainFailuresLonger(entries: List<String>): List<String> {
        if (entries.size <= MAX_COPYABLE_LOG_ENTRIES) return entries
        val newestEntries = entries.take(MAX_COPYABLE_LOG_ENTRIES).toMutableList()
        val olderFailures = entries
            .drop(MAX_COPYABLE_LOG_ENTRIES)
            .filter { it.contains("\nResult: Failed\n") }
            .take(MAX_RETAINED_OLDER_FAILURE_ENTRIES)
        for (failure in olderFailures) {
            val oldestSuccessIndex = newestEntries.indexOfLast { it.contains("\nResult: Succeeded\n") }
            if (oldestSuccessIndex == -1) break
            newestEntries.removeAt(oldestSuccessIndex)
            newestEntries.add(failure)
        }
        return newestEntries
    }

    private fun android.content.SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    private data class OperationDefinition(
        val id: String,
        val title: String,
        val category: String
    )

    companion object {
        const val BACKUP_UNIFIED = "backup.unified"
        const val BACKUP_LOCAL_LEGACY = "backup.local.legacy"
        const val SYNC_SFTP_LEGACY = "sync.sftp.legacy"
        const val SYNC_FTP_LEGACY = "sync.ftp.legacy"
        const val SYNC_GITHUB = "sync.github"
        const val REMINDERS_TASK = "reminders.task"
        const val REMINDERS_DAILY_AGENDA = "reminders.daily_agenda"
        const val REMINDERS_OVERDUE_ESCALATION = "reminders.overdue_escalation"
        const val WIDGETS_REFRESH = "widgets.refresh"

        private const val TAG = "OperationHistoryStore"
        private const val PREFS_NAME = "operation_history"
        private const val LAST_RUN_AT = "last_run_at"
        private const val LAST_SUCCESS_AT = "last_success_at"
        private const val LAST_FAILURE_AT = "last_failure_at"
        private const val RETRY_COUNT = "retry_count"
        private const val LAST_REASON = "last_reason"
        private const val LAST_STATUS = "last_status"
        private const val COPYABLE_LOG = "copyable_log"
        private const val LOG_ENTRY_SEPARATOR = "\n\n---\n\n"
        private const val MAX_COPYABLE_LOG_ENTRIES = 100
        private const val MAX_RETAINED_OLDER_FAILURE_ENTRIES = 25

        private val OPERATIONS = listOf(
            OperationDefinition(BACKUP_UNIFIED, "Scheduled backup", "Backup"),
            OperationDefinition(BACKUP_LOCAL_LEGACY, "Legacy local backup", "Backup"),
            OperationDefinition(SYNC_SFTP_LEGACY, "Legacy SFTP sync", "Sync"),
            OperationDefinition(SYNC_FTP_LEGACY, "Legacy FTP sync", "Sync"),
            OperationDefinition(SYNC_GITHUB, "GitHub sync", "Sync"),
            OperationDefinition(REMINDERS_TASK, "Task reminders", "Reminders"),
            OperationDefinition(REMINDERS_DAILY_AGENDA, "Daily agenda", "Reminders"),
            OperationDefinition(REMINDERS_OVERDUE_ESCALATION, "Overdue escalation", "Reminders"),
            OperationDefinition(WIDGETS_REFRESH, "Home-screen widgets", "Widgets")
        )

        private fun key(id: String, field: String) = "$id.$field"

        private fun formatLogTimestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date(millis))
    }
}
