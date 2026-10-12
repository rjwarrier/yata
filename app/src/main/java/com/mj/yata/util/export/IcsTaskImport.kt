package com.mj.yata.util.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.mj.yata.domain.model.Task
import com.mj.yata.util.TaskScheduleUtils
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Calendar event → task. A shared calendar event or an opened .ics/.vcs file becomes a task whose
 * title is the event's SUMMARY, due date/time its start (converted to this device's zone), and
 * notes its DESCRIPTION.
 *
 * Deliberately no screen of its own: the parsed tasks are re-encoded as an ordinary task transfer
 * link and handed to the SharedTaskImport route, which already does exactly the review step this
 * needs — one event opens prefilled in NewTaskSheet, several go through the multi-task preview —
 * and already enforces the row-count and field-size caps on untrusted input.
 */

private val CALENDAR_MIME_TYPES = setOf("text/calendar", "text/x-vcalendar", "application/ics")

/** Big enough for any real event or a few-hundred-event export; stops a hostile file from
 * being read whole into memory. */
private const val MAX_ICS_BYTES = 1_000_000

private val ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm[ss]")

fun isCalendarImportIntent(context: Context, intent: Intent): Boolean =
    (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) &&
        intent.resolveType(context)?.lowercase()?.substringBefore(';')?.trim() in CALENDAR_MIME_TYPES

/** Blocking (content resolver I/O) — call off the main thread. Null when nothing readable or the
 * file is over [MAX_ICS_BYTES]. */
fun readCalendarText(context: Context, intent: Intent): String? {
    val uri = if (intent.action == Intent.ACTION_SEND) {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        intent.data
    }
    if (uri != null) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(MAX_ICS_BYTES + 1)
            var total = 0
            while (total < bytes.size) {
                val read = input.read(bytes, total, bytes.size - total)
                if (read < 0) break
                total += read
            }
            return if (total > MAX_ICS_BYTES) null else String(bytes, 0, total, Charsets.UTF_8)
        }
    }
    // Some apps put the calendar body inline instead of attaching a file.
    return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length <= MAX_ICS_BYTES }
}

/** The transfer link that opens these events for review, or null when the text holds none. */
fun buildCalendarImportLink(ics: String): String? {
    val tasks = parseIcsTasks(ics).ifEmpty { return null }
    return buildTaskTransferLink(
        title = "",
        tasks = tasks,
        listsById = emptyMap(),
        projectsById = emptyMap(),
        tagsById = emptyMap(),
        peopleById = emptyMap(),
        includeStructure = false,
        includeNotes = true
    ).uri
}

/**
 * Pure: VEVENTs and VTODOs from an iCalendar (RFC 5545) or vCalendar 1.0 body. A to-do's DUE wins
 * over its DTSTART; an event uses DTSTART. All-day starts carry a date and no time. Untitled
 * components, and recurrence overrides (RECURRENCE-ID — the series' master is in the same file),
 * are skipped.
 */
fun parseIcsTasks(ics: String, zone: ZoneId = ZoneId.systemDefault()): List<Task> {
    val tasks = mutableListOf<Task>()
    var props: MutableMap<String, IcsProperty>? = null
    // Nested components (VALARM) have their own DESCRIPTION etc. that must not land on the task.
    var nestedDepth = 0
    for (line in unfoldIcsLines(ics)) {
        val prop = parseIcsProperty(line) ?: continue
        val component = prop.value.trim().uppercase()
        when {
            prop.name == "BEGIN" && props == null && (component == "VEVENT" || component == "VTODO") ->
                props = mutableMapOf()
            prop.name == "BEGIN" && props != null -> nestedDepth++
            prop.name == "END" && props != null && nestedDepth > 0 -> nestedDepth--
            prop.name == "END" && props != null -> {
                props.toTask(zone)?.let(tasks::add)
                props = null
            }
            props != null && nestedDepth == 0 -> props.putIfAbsent(prop.name, prop)
        }
    }
    return tasks
}

private class IcsProperty(val name: String, val params: Map<String, String>, val value: String)

private fun Map<String, IcsProperty>.toTask(zone: ZoneId): Task? {
    if ("RECURRENCE-ID" in this) return null
    val title = this["SUMMARY"]?.text()?.lines()?.joinToString(" ")?.trim()
        ?.take(MAX_TEXT_FIELD_LENGTH)?.takeIf { it.isNotBlank() } ?: return null
    val start = (this["DUE"] ?: this["DTSTART"])?.let { parseIcsStart(it, zone) }
    return Task(
        id = "",
        title = title,
        listId = null,
        projectId = null,
        section = "",
        due = start?.first?.toString(),
        time = start?.second?.let { TaskScheduleUtils.formatTime(it.hour, it.minute) },
        reminder = null,
        priority = "none",
        flag = false,
        done = false,
        assigneeIds = emptyList(),
        tagIds = emptyList(),
        recurrence = null,
        subtasks = emptyList(),
        notes = this["DESCRIPTION"]?.text()?.trim()?.take(MAX_NOTES_FIELD_LENGTH)?.takeIf { it.isNotBlank() }
    )
}

/** Date plus time-of-day in [zone]; null time for an all-day value. UTC ("Z") and IANA TZIDs are
 * converted; a floating time, or a TZID Java doesn't know, is taken as already local. */
private fun parseIcsStart(prop: IcsProperty, zone: ZoneId): Pair<LocalDate, LocalTime?>? = runCatching {
    val raw = prop.value.trim()
    if (prop.params["VALUE"].equals("DATE", ignoreCase = true) || raw.length == 8) {
        return@runCatching LocalDate.parse(raw.take(8), DateTimeFormatter.BASIC_ISO_DATE) to null
    }
    val local = LocalDateTime.parse(raw.removeSuffix("Z").removeSuffix("z"), ICS_DATE_TIME)
    // ponytail: non-IANA TZIDs (Outlook's "India Standard Time") fall back to local time; reading
    // the file's VTIMEZONE block would fix them if that turns out to matter.
    val source: ZoneId = when {
        raw.endsWith("Z", ignoreCase = true) -> ZoneOffset.UTC
        else -> prop.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
    }
    val inZone = local.atZone(source).withZoneSameInstant(zone).toLocalDateTime()
    inZone.toLocalDate() to inZone.toLocalTime()
}.getOrNull()

/** RFC 5545 line unfolding (continuation lines start with a space or tab), plus vCalendar 1.0's
 * quoted-printable soft breaks (a QP line ending in "="). */
private fun unfoldIcsLines(ics: String): List<String> {
    // StringBuilders, not String +: a long description folded every 75 chars would otherwise be
    // re-copied once per fold — quadratic on a file this importer accepts from any app.
    val out = mutableListOf<StringBuilder>()
    for (raw in ics.lines()) {
        val last = out.lastOrNull()
        when {
            last != null && (raw.startsWith(" ") || raw.startsWith("\t")) -> last.append(raw, 1, raw.length)
            last != null && last.endsWith("=") &&
                last.substring(0, last.indexOf(":").coerceAtLeast(0)).contains("QUOTED-PRINTABLE", ignoreCase = true) -> {
                last.setLength(last.length - 1)
                last.append(raw)
            }
            else -> out += StringBuilder(raw)
        }
    }
    return out.map { it.toString() }
}

/** NAME;PARAM=x;PARAM="y:z":value — the first colon outside quotes ends the parameters. */
private fun parseIcsProperty(line: String): IcsProperty? {
    var inQuotes = false
    val colon = line.indices.firstOrNull { i ->
        if (line[i] == '"') inQuotes = !inQuotes
        line[i] == ':' && !inQuotes
    } ?: return null
    val head = line.substring(0, colon).split(';')
    val params = head.drop(1).mapNotNull { param ->
        val eq = param.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
        param.substring(0, eq).trim().uppercase() to param.substring(eq + 1).trim().removeSurrounding("\"")
    }.toMap()
    return IcsProperty(head[0].trim().uppercase(), params, line.substring(colon + 1))
}

/** A TEXT value, decoded: quoted-printable for vCalendar 1.0, RFC 5545 backslash escapes otherwise. */
private fun IcsProperty.text(): String {
    if (params["ENCODING"].equals("QUOTED-PRINTABLE", ignoreCase = true)) {
        val charset = params["CHARSET"]?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val hex = if (value[i] == '=' && i + 2 < value.length) {
                value.substring(i + 1, i + 3).toIntOrNull(16)?.takeIf { it >= 0 }
            } else {
                null
            }
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(value[i].toString().toByteArray(charset))
                i++
            }
        }
        return String(bytes.toByteArray(), charset).replace("\r\n", "\n")
    }
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            val next = value[i + 1]
            out.append(if (next == 'n' || next == 'N') '\n' else next)
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
