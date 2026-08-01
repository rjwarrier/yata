package com.mj.yata.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PlainTextImportResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedMalformed: Int
)

private data class ParsedRow(
    val title: String,
    val due: String?,
    val priority: String,
    val listName: String?,
    val notes: String?
)

/** Imports tasks from a CSV (`title,due,priority,list,notes` header, only `title` required) or
 * a plain-text file (one task title per line) — a lighter, hand-editable complement to
 * [JsonExporter]'s full-fidelity JSON backup/restore. */
@Singleton
class PlainTextImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YataRepository,
    private val userPreferences: UserPreferences
) {
    /** Exports every non-deleted task as CSV, in the exact `title,due,priority,list,notes`
     * column shape [importData] reads — resolves each task's `listId` to a list name (reverse of
     * import's name-to-id lookup) so the file stays human-editable, matching this file's role as
     * the lighter complement to [JsonExporter]'s full-fidelity JSON backup. */
    suspend fun exportCsv(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val tasks = repository.getTasks().first().filter { it.deletedAt == null }
            val listNamesById = repository.getLists().first().associate { it.id to it.name }

            val sb = StringBuilder("title,due,priority,list,notes\n")
            tasks.forEach { task ->
                sb.append(csvField(task.title)).append(',')
                sb.append(csvField(task.due ?: "")).append(',')
                sb.append(csvField(task.priority)).append(',')
                sb.append(csvField(task.listId?.let { listNamesById[it] } ?: "")).append(',')
                sb.append(csvField(task.notes ?: "")).append('\n')
            }

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer -> writer.write(sb.toString()) }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            Log.e("PlainTextImporter", "exportCsv failed", e)
            false
        }
    }

    /** Wraps in quotes (doubling any embedded quotes) whenever the field contains a comma, quote,
     * or newline — the counterpart to [splitCsvLine]'s quoted-field handling on import. */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    suspend fun importData(uri: Uri): Result<PlainTextImportResult> = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).readLines()
            } ?: return@withContext Result.failure(IllegalStateException("Could not open file"))

            val isCsv = lines.firstOrNull()
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?.contains("title") == true

            val rows = if (isCsv) parseCsv(lines) else parsePlainText(lines)
            val totalDataLines = (if (isCsv) lines.drop(1) else lines).count { it.isNotBlank() }
            val skippedMalformed = totalDataLines - rows.size

            val lists = repository.getLists().first()
            val existingTasks = repository.getTasks().first()
            val defaultListId = userPreferences.defaultListIdFlow.first()
            val baseSortOrder = existingTasks.size

            var skippedDuplicates = 0
            val newTasks = mutableListOf<Task>()
            rows.forEachIndexed { index, row ->
                val allTasksSoFar = existingTasks + newTasks
                if (findSimilarTask(row.title, allTasksSoFar) != null) {
                    skippedDuplicates++
                    return@forEachIndexed
                }
                val listId = row.listName
                    ?.let { name -> lists.find { it.name.equals(name, ignoreCase = true) }?.id }
                    ?: defaultListId
                newTasks += Task(
                    id = "t_" + UUID.randomUUID().toString(),
                    title = row.title,
                    listId = listId,
                    projectId = null,
                    section = "",
                    due = row.due,
                    time = null,
                    reminder = null,
                    priority = row.priority,
                    flag = false,
                    done = false,
                    assigneeIds = emptyList(),
                    tagIds = emptyList(),
                    recurrence = null,
                    subtasks = emptyList(),
                    notes = row.notes,
                    sortOrder = baseSortOrder + index
                )
            }

            repository.upsertTasks(newTasks, notify = true)

            Result.success(
                PlainTextImportResult(
                    imported = newTasks.size,
                    skippedDuplicates = skippedDuplicates,
                    skippedMalformed = skippedMalformed
                )
            )
        } catch (e: Exception) {
            Log.e("PlainTextImporter", "importData failed", e)
            Result.failure(e)
        }
    }

    private fun parsePlainText(lines: List<String>): List<ParsedRow> =
        lines.map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ParsedRow(title = it, due = null, priority = "none", listName = null, notes = null) }

    private fun parseCsv(lines: List<String>): List<ParsedRow> {
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val titleIdx = header.indexOf("title")
        if (titleIdx == -1) return emptyList()
        val dueIdx = header.indexOf("due")
        val priorityIdx = header.indexOf("priority")
        val listIdx = header.indexOf("list")
        val notesIdx = header.indexOf("notes")

        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = splitCsvLine(line)
            val title = cols.getOrNull(titleIdx)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null

            val rawDue = dueIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.trim() }
            val due = rawDue?.takeIf { it.isNotEmpty() && runCatching { LocalDate.parse(it) }.isSuccess }

            val rawPriority = priorityIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.trim()?.lowercase() }
            val priority = rawPriority?.takeIf { it in setOf("low", "med", "high") } ?: "none"

            val listName = listIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            val notes = notesIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

            ParsedRow(title = title, due = due, priority = priority, listName = listName, notes = notes)
        }
    }

    /** Minimal CSV field split honoring double-quoted fields with embedded commas/escaped quotes. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
