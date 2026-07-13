package com.mj.yata.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mj.yata.domain.repository.YataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** One-way export of due tasks as a standard .ics calendar file — for viewing YATA's schedule
 * in any calendar app. This is not sync: re-importing the file later won't reconcile with
 * changes made on either side, it just re-creates the events. */
@Singleton
class IcsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YataRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    suspend fun exportIcs(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val tasks = repository.getTasks().first().filter { it.due != null }
            val ics = buildIcs(tasks)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream).use { it.write(ics) }
            }
            true
        } catch (e: Exception) {
            Log.e("IcsExporter", "exportIcs failed", e)
            false
        }
    }

    private fun buildIcs(tasks: List<com.mj.yata.domain.model.Task>): String {
        val now = java.time.Instant.now().atZone(ZoneId.of("UTC")).format(stampFormatter)
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//YATA//YATA Export//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")

        tasks.forEach { task ->
            val due = runCatching { LocalDate.parse(task.due) }.getOrNull() ?: return@forEach
            val time = TaskScheduleUtils.parseTime(task.time)

            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:${task.id}@yata\r\n")
            sb.append("DTSTAMP:$now\r\n")
            if (time != null) {
                val start = due.atTime(time)
                sb.append("DTSTART:${start.format(dateTimeFormatter)}\r\n")
                sb.append("DTEND:${start.plusMinutes(30).format(dateTimeFormatter)}\r\n")
            } else {
                sb.append("DTSTART;VALUE=DATE:${due.format(dateFormatter)}\r\n")
                sb.append("DTEND;VALUE=DATE:${due.plusDays(1).format(dateFormatter)}\r\n")
            }
            sb.append("SUMMARY:${escape(if (task.done) "✓ ${task.title}" else task.title)}\r\n")
            if (!task.notes.isNullOrBlank()) {
                sb.append("DESCRIPTION:${escape(task.notes)}\r\n")
            }
            sb.append("STATUS:CONFIRMED\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
