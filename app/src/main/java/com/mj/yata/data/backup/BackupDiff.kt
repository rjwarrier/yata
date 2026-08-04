package com.mj.yata.data.backup

import com.mj.yata.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject

/** A read-only comparison between current tasks and a versioned self-hosted backup. */
data class BackupDiff(
    val currentPending: Int,
    val currentDone: Int,
    val backupPending: Int,
    val backupDone: Int,
    val backupCreatedTime: String,
    val addedTitles: List<String>,
    val addedCount: Int,
    val removedTitles: List<String>,
    val removedCount: Int,
    val changedTitles: List<String>,
    val changedCount: Int
) {
    val pendingDiff: Int get() = currentPending - backupPending
    val doneDiff: Int get() = currentDone - backupDone
}

/** Compares a self-hosted JSON snapshot without mutating local data. */
fun compareBackupJsonWithTasks(
    backupJsonBytes: ByteArray,
    currentTasks: List<Task>,
    backupCreatedTime: String
): BackupDiff {
    val tasks = JSONObject(String(backupJsonBytes, Charsets.UTF_8))
        .optJSONArray("tasks") ?: JSONArray()
    var backupDone = 0
    var backupPending = 0
    val backupById = HashMap<String, Pair<String, Boolean>>(tasks.length())
    for (index in 0 until tasks.length()) {
        val row = tasks.getJSONObject(index)
        val done = row.optBoolean("done", false)
        if (done) backupDone++ else backupPending++
        backupById[row.getString("id")] = row.getString("title") to done
    }

    val currentById = currentTasks.associateBy { it.id }
    val currentDone = currentTasks.count { it.done }
    val addedTitles = currentById.keys.minus(backupById.keys)
        .map { currentById.getValue(it).title }
    val removedTitles = backupById.keys.minus(currentById.keys)
        .map { backupById.getValue(it).first }
    val changedTitles = currentById.keys.intersect(backupById.keys).mapNotNull { id ->
        val current = currentById.getValue(id)
        val backup = backupById.getValue(id)
        if (current.title != backup.first || current.done != backup.second) current.title else null
    }

    return BackupDiff(
        currentPending = currentTasks.size - currentDone,
        currentDone = currentDone,
        backupPending = backupPending,
        backupDone = backupDone,
        backupCreatedTime = backupCreatedTime,
        addedTitles = addedTitles.take(MAX_DIFF_TITLES),
        addedCount = addedTitles.size,
        removedTitles = removedTitles.take(MAX_DIFF_TITLES),
        removedCount = removedTitles.size,
        changedTitles = changedTitles.take(MAX_DIFF_TITLES),
        changedCount = changedTitles.size
    )
}

private const val MAX_DIFF_TITLES = 8
