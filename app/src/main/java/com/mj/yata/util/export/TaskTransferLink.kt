package com.mj.yata.util.export

import android.net.Uri
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TRANSFER_SCHEME = "yata"

// Legacy (v1) links, produced up to and including commit 7db50e9. No longer generated, but
// still decoded — links already shared before this format change must keep working.
// gzip-compressed verbose JSON, "v":1 embedded inside the payload itself.
private const val LEGACY_HOST = "import"
private const val LEGACY_PATH = "/tasks"

// Current links, all under one host. The encoding is identified by which query parameter carries
// the payload, which costs no characters at all — a "v=3" parameter would spend four, and a
// second host letter per version would leave isTaskTransferUri knowing every historical letter
// forever. See docs/app-links-v2-plan.md and docs/app-links-v3-plan.md.
//   t=…  plaintext form (repeated, one per task)
//   e=…  current compressed payload
//   d=…  v2 compressed payload (decode only; no longer generated)
private const val HOST = "i"
private const val CURRENT_COMPRESSED_VERSION = 4
private const val MIN_SUPPORTED_COMPRESSED_VERSION = 2
private const val MAX_SUPPORTED_COMPRESSED_VERSION = CURRENT_COMPRESSED_VERSION
private const val PARAM_COMPRESSED_PAYLOAD = "e"
private const val PARAM_V2_PAYLOAD = "d"

// Shared links are https, not yata://, because messaging apps only linkify known schemes — a
// custom-scheme link arrives as inert text the recipient cannot tap, which made the whole feature
// unusable over chat. The yata:// form is still accepted (already-shared links, Tasker,
// automation) but is no longer what gets handed to a human.
//
// The payload rides in the URL **fragment**, never the query string: fragments are not sent to
// the server, and messaging apps fetch link previews server-side, so a query-string payload would
// hand task titles and notes to every preview crawler that touches the message. With the payload
// in the fragment the crawler fetches a bare URL and learns nothing.
// See docs/app-links-reliability-plan.md §0.
private const val WEB_SCHEME = "https"
private const val WEB_HOST = "ranjithj.in"
private const val WEB_PATH = "/yata/i"

private const val MAX_DECODED_BYTES = 256_000

// Above this, some messaging/email apps stop turning the link into something tappable — see
// "Link length budget" in docs/app-links-v2-plan.md. Not a hard limit, just a heads-up.
private const val RELIABLE_LINK_LENGTH = 1000

// Hard limits, unlike the length warning above — importing past these is refused outright rather
// than merely flagged, since nothing legitimate shares this much through one link. See
// docs/app-links-reliability-plan.md, item 6.
private const val MAX_TASKS_PER_LINK = 200
private const val MAX_SUBTASKS_PER_TASK = 100

// A per-field cap alongside MAX_DECODED_BYTES/MAX_TASKS_PER_LINK: those bound the payload and the
// task count, but neither stops a single field within an otherwise-small link from being
// pathologically long (a plaintext "t="/"n=" query parameter has no length limit of its own, and a
// compressed link could spend its whole decoded budget on one field). No legitimate share needs a
// title/name this long, and without this check one field could bloat a row past what the rest of
// the app assumes for a piece of task/entity text.
private const val MAX_TEXT_FIELD_LENGTH = 2_000
private const val MAX_NOTES_FIELD_LENGTH = 20_000

private val PRIORITIES = listOf("none", "low", "med", "high")

// Guards against a malformed link producing a Recurrence with a frequency nothing downstream
// knows how to evaluate; matches the set RecurrenceEvaluator handles.
private val RECURRENCE_FREQUENCIES = setOf("daily", "weekly", "monthly", "yearly")

data class TaskTransferLink(
    val uri: String,
    val includesStructure: Boolean
) {
    val mayNotAutoLinkEverywhere: Boolean get() = uri.length > RELIABLE_LINK_LENGTH

    fun asShareText(title: String, count: Int): String = buildString {
        appendLine("YATA shared ${if (count == 1) "task" else "$count tasks"}: $title")
        appendLine()
        if (includesStructure) {
            appendLine("Add to Inbox (creates any missing lists, projects, and tags):")
        } else {
            appendLine("Add to Inbox:")
        }
        append(uri)
        if (mayNotAutoLinkEverywhere) {
            appendLine()
            appendLine()
            append("(This link is long — some apps may not turn it into a tappable link.)")
        }
    }
}

data class TaskTransferImportResult(
    val taskCount: Int,
    val copiedStructure: Boolean
)

enum class TaskTransferLinkError {
    NotATaskLink,
    Unsupported,
    Empty,
    TooManyTasks,
    Incomplete,
    TooLarge,
    Malformed
}

class TaskTransferLinkException(
    val reason: TaskTransferLinkError,
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

private fun taskLinkError(reason: TaskTransferLinkError, message: String, cause: Throwable? = null): Nothing =
    throw TaskTransferLinkException(reason, message, cause)

fun buildTaskTransferLink(
    title: String,
    tasks: List<Task>,
    listsById: Map<String, YataList>,
    projectsById: Map<String, Project>,
    tagsById: Map<String, Tag>,
    peopleById: Map<String, Person>,
    includeStructure: Boolean,
    includeNotes: Boolean
): TaskTransferLink {
    val compressed = buildCompressedTransferLink(
        title = title,
        tasks = tasks,
        listsById = listsById,
        projectsById = projectsById,
        tagsById = tagsById,
        peopleById = peopleById,
        includeStructure = includeStructure,
        includeNotes = includeNotes
    )
    // The plaintext form is only *sometimes* shorter, and which way it falls is not something a
    // rule about task shape can predict: percent-encoding costs 3 chars per UTF-8 byte, so a
    // Latin title rides nearly 1:1 while an Indic one (3 bytes/char) costs 9 chars per character
    // and loses badly to Base64. Picking by shape rather than size made single-task links up to
    // 2x *longer* for the nine Indic locales YATA ships. So: build both, measure, take the
    // shorter. See docs/app-links-v3-plan.md.
    val plaintext = buildPlaintextTransferLink(tasks, includeStructure, includeNotes)
    return if (plaintext != null && plaintext.uri.length < compressed.uri.length) plaintext else compressed
}

private fun buildCompressedTransferLink(
    title: String,
    tasks: List<Task>,
    listsById: Map<String, YataList>,
    projectsById: Map<String, Project>,
    tagsById: Map<String, Tag>,
    @Suppress("UNUSED_PARAMETER")
    peopleById: Map<String, Person>,
    includeStructure: Boolean,
    includeNotes: Boolean
): TaskTransferLink {
    val listIds = if (includeStructure) tasks.mapNotNull { it.listId }.toSet() else emptySet()
    val projectIds = if (includeStructure) tasks.mapNotNull { it.projectId }.toSet() else emptySet()
    val tagIds = if (includeStructure) {
        (tasks.flatMap { it.tagIds } + projectIds.mapNotNull { projectsById[it] }.flatMap { it.commonTagIds }).toSet()
    } else {
        emptySet()
    }
    val lists = listIds.mapNotNull { listsById[it] }
    val projects = projectIds.mapNotNull { projectsById[it] }
    val tags = tagIds.mapNotNull { tagsById[it] }

    val listIndex = lists.mapIndexed { index, list -> list.id to index }.toMap()
    val projectIndex = projects.mapIndexed { index, project -> project.id to index }.toMap()
    val tagIndex = tags.mapIndexed { index, tag -> tag.id to index }.toMap()

    // v3/v4 layout: [version, lists, projects, tags, people, tasks]. v2 carried the share title
    // at index 1, which no importer ever read — the share text around the link already shows it —
    // so every compressed link was paying 16-24 characters for a field nobody consumed.
    // v4 differs from v3 in the task row, which gained the schedule fields. The people section
    // remains as an empty compatibility slot only: new links deliberately do not carry sender
    // identities, while older links that already did still decode through the same parser.
    val payload = JSONArray()
        .put(CURRENT_COMPRESSED_VERSION)
        .put(JSONArray().also { array -> lists.forEach { array.put(it.toTransferRow()) } })
        .put(JSONArray().also { array -> projects.forEach { array.put(it.toTransferRow(tagIndex)) } })
        .put(JSONArray().also { array -> tags.forEach { array.put(it.toTransferRow()) } })
        .put(JSONArray())
        .put(JSONArray().also { array ->
            tasks.forEach { task ->
                array.put(
                    task.toTransferRow(
                        includeStructure = includeStructure,
                        includeNotes = includeNotes,
                        listIndex = listIndex,
                        projectIndex = projectIndex,
                        tagIndex = tagIndex
                    )
                )
            }
        })

    val builder = Uri.Builder().scheme(TRANSFER_SCHEME).authority(HOST)
    // "s" is emitted only when set: the import side already reads absence as false, so spelling
    // out the default cost four characters to say nothing.
    if (includeStructure) builder.appendQueryParameter("s", "1")
    builder.appendQueryParameter(PARAM_COMPRESSED_PAYLOAD, encodeCompressedPayload(payload))
    return TaskTransferLink(uri = webTransferUri(builder.build()), includesStructure = includeStructure)
}

/** Readable, uncompressed alternative encoding: the task titles ride in the query string as-is,
 * so the receiver can see what they're about to import before tapping. Returns null only when the
 * share copies structure, which this form deliberately doesn't express — an index-referenced
 * dictionary costs more in query parameters than it saves, and it is exactly the high-volume case
 * DEFLATE handles best. Being *representable* is not the same as being *shorter*; the caller
 * decides that by measuring both. */
private fun buildPlaintextTransferLink(
    tasks: List<Task>,
    includeStructure: Boolean,
    includeNotes: Boolean
): TaskTransferLink? {
    if (tasks.isEmpty() || includeStructure) return null
    // Recurrence is the one carried field with no compact readable spelling — expressing it here
    // would cost more than the compressed form and read as noise to a human. A recurring task
    // therefore has only the compressed candidate, which is correct: silently dropping it to keep
    // the pretty encoding would lose data the sender expects to travel.
    if (tasks.any { it.recurrence != null }) return null

    val builder = Uri.Builder().scheme(TRANSFER_SCHEME).authority(HOST)
    tasks.forEach { builder.appendQueryParameter("t", it.title) }
    // Everything per-task is keyed by the task's index in the "t" list rather than written
    // positionally, so a task holding a default can be left out entirely. Positional encoding
    // would mean spending characters on every task's defaults purely to keep later tasks
    // aligned. Subtasks repeat their indexed key ("b0" twice = two subtasks on task 0), which
    // keeps per-task grouping without needing a delimiter inside the value.
    tasks.forEachIndexed { index, task ->
        val priorityIndex = PRIORITIES.indexOf(task.priority).coerceAtLeast(0)
        if (priorityIndex != 0) builder.appendQueryParameter("p$index", priorityIndex.toString())
        if (task.flag) builder.appendQueryParameter("f$index", "1")
        task.notes?.takeIf { includeNotes && it.isNotBlank() }?.let {
            builder.appendQueryParameter("n$index", it)
        }
        task.due?.takeIf { it.isNotBlank() }?.let { builder.appendQueryParameter("d$index", it) }
        task.time?.takeIf { it.isNotBlank() }?.let { builder.appendQueryParameter("h$index", it) }
        task.startDate?.takeIf { it.isNotBlank() }?.let { builder.appendQueryParameter("g$index", it) }
        task.estimateMinutes?.takeIf { it > 0 }?.let { builder.appendQueryParameter("m$index", it.toString()) }
        task.subtasks.forEach { builder.appendQueryParameter("b$index", it.title) }
    }
    // Unlike the compressed form, plaintext has no structural self-check: DEFLATE or JSON parsing
    // fails loudly on a truncated compressed payload, but a plaintext link clipped mid-parameter
    // by a messaging app's linkifier is still a perfectly valid URI — it just silently imports
    // less than the sender sent. "c" carries a checksum over every other parameter so that
    // specific failure mode is caught instead of imported. See docs/app-links-reliability-plan.md,
    // item 7.
    builder.appendQueryParameter(CHECKSUM_PARAM, plaintextChecksum(builder.build()))
    return TaskTransferLink(uri = webTransferUri(builder.build()), includesStructure = false)
}

private fun isPlaintextTransferUri(uri: Uri): Boolean = uri.getQueryParameter("t") != null

private const val CHECKSUM_PARAM = "c"

/** CRC32 over a canonical (sorted-by-key) form rather than the literal query string, so the
 * checksum is invariant to a link-processing step in transit reordering parameters but still
 * changes if any value is lost or altered — which is the actual failure this defends against.
 * CRC32 rather than a cryptographic hash because the threat is accidental truncation, not a
 * sender tampering with their own data (they already control the payload before encoding it).
 * 8 hex chars is far more than enough to catch a clipped URL while costing almost nothing per
 * link. */
private fun plaintextChecksum(uri: Uri): String {
    val canonical = uri.queryParameterNames.filter { it != CHECKSUM_PARAM }.sorted().joinToString("&") { key ->
        key + "=" + uri.getQueryParameters(key).joinToString(",") { Uri.encode(it) }
    }
    val crc = java.util.zip.CRC32()
    crc.update(canonical.toByteArray(Charsets.UTF_8))
    return crc.value.toString(16).padStart(8, '0')
}

fun isTaskTransferUri(uri: Uri?): Boolean {
    if (uri == null) return false
    if (isWebTransferUri(uri)) return true
    if (uri.scheme != TRANSFER_SCHEME) return false
    return uri.host == HOST || (uri.host == LEGACY_HOST && uri.path == LEGACY_PATH)
}

private fun isWebTransferUri(uri: Uri): Boolean =
    uri.scheme == WEB_SCHEME && uri.host == WEB_HOST && uri.path == WEB_PATH

/** Rewrites the https form into the parameter-bearing shape the rest of this file reads.
 *
 * The payload lives in the fragment, so there are no query parameters to read directly. Parsing
 * `"?" + fragment` turns it back into a Uri whose getQueryParameter(s) work exactly as they do for
 * the yata:// form, which keeps every decoder below unaware of which transport delivered it. */
private fun transferParams(uri: Uri): Uri =
    if (isWebTransferUri(uri)) Uri.parse("?" + uri.encodedFragment.orEmpty()) else uri

/** Wraps already-built transfer parameters into the shareable https link. */
private fun webTransferUri(params: Uri): String =
    Uri.Builder()
        .scheme(WEB_SCHEME)
        .authority(WEB_HOST)
        .path(WEB_PATH)
        .encodedFragment(params.encodedQuery)
        .build()
        .toString()

// --- v2 row shapes -------------------------------------------------------------------------
//
// Every row is a JSONArray with fixed field positions, trailing-truncated once fields hit their
// default value — e.g. a task with no notes, list, project, tags, people, or subtasks encodes as
// just [title, priority]. Dictionary rows are referenced from task rows by array index, not id;
// the index only needs to be stable within one payload.

private fun YataList.toTransferRow(): JSONArray =
    JSONArray().put(name).put(color).put(icon)

private fun Project.toTransferRow(tagIndex: Map<String, Int>): JSONArray {
    val row = JSONArray().put(name).put(color).put(icon)
        .put(JSONArray().also { array -> commonTagIds.mapNotNull { tagIndex[it] }.forEach { array.put(it) } })
    return row.trimTrailingDefaults(defaultTag3 = JSONArray())
}

private fun Tag.toTransferRow(): JSONArray {
    val row = JSONArray().put(name).put(color).put(description.orEmpty())
    return row.trimTrailingDefaults(defaultTag2 = "")
}

private fun Person.toTransferRow(): JSONArray =
    JSONArray().put(name).put(initials).put(color)

private fun Task.toTransferRow(
    includeStructure: Boolean,
    includeNotes: Boolean,
    listIndex: Map<String, Int>,
    projectIndex: Map<String, Int>,
    tagIndex: Map<String, Int>
): JSONArray {
    val row = JSONArray()
        .put(title)
        .put(PRIORITIES.indexOf(priority).coerceAtLeast(0))
        .put(flag)
        .put((notes.takeIf { includeNotes }).orEmpty())
        .put(listId?.takeIf { includeStructure }?.let { listIndex[it] } ?: -1)
        .put(projectId?.takeIf { includeStructure }?.let { projectIndex[it] } ?: -1)
        .put(JSONArray().also { array ->
            if (includeStructure) tagIds.mapNotNull { tagIndex[it] }.forEach { array.put(it) }
        })
        .put(JSONArray())
        .put(JSONArray().also { array -> subtasks.forEach { array.put(JSONArray().put(it.title)) } })
        // v4 additions: fields that describe *the work* rather than the sender's personal
        // scheduling, so they belong to the recipient too. Absolute ISO dates, never offsets —
        // a link opened two days after it was sent must still mean the same Friday.
        // Deliberately still absent: reminder (my nudge preference, not theirs), section (the
        // sender's private project layout), followUpAt, and done/completedAt.
        .put(due.orEmpty())
        .put(time.orEmpty())
        .put(startDate.orEmpty())
        .put(estimateMinutes ?: 0)
        .put(recurrence?.toTransferRow() ?: JSONArray())
    return row.trimTrailingTaskDefaults()
}

/** Mirrors the field set `JsonExporter` already persists for a recurrence, positionally:
 * [freq, interval, byday, bymonthday, endsType, endsValue, basedOnCompletion, byweekday, bysetpos].
 * Kept in step with that encoding on purpose — two different notions of "a serialized recurrence"
 * in one codebase is how they drift apart. The last two fields were appended after the rest, so a
 * link created before they existed simply omits them — [toRecurrenceOrNull] defaults them to null
 * via `optString`/`optInt`, same as every other optional field here. */
private fun Recurrence.toTransferRow(): JSONArray = JSONArray()
    .put(freq)
    .put(interval)
    .put(JSONArray().also { array -> byday?.forEach { day -> array.put(day) } })
    .put(bymonthday ?: 0)
    .put(
        when (ends) {
            is RecurrenceEnds.Never -> ""
            is RecurrenceEnds.After -> "a"
            is RecurrenceEnds.On -> "o"
        }
    )
    .put(
        when (val e = ends) {
            is RecurrenceEnds.Never -> ""
            is RecurrenceEnds.After -> e.count.toString()
            is RecurrenceEnds.On -> e.date
        }
    )
    .put(basedOnCompletion)
    .also { array ->
        // Only appended when actually used, so the vast majority of shared recurrences (which
        // never touch this monthly-by-weekday-position mode) keep the exact link length they had
        // before these two fields existed.
        if (byweekday != null && bysetpos != null) {
            array.put(byweekday)
            array.put(bysetpos)
        }
    }

private fun JSONArray?.toRecurrenceOrNull(): Recurrence? {
    val row = this ?: return null
    val freq = row.optString(0).takeIf { it.isNotBlank() } ?: return null
    if (freq !in RECURRENCE_FREQUENCIES) return null
    val endsValue = row.optString(5)
    return Recurrence(
        freq = freq,
        // A non-positive interval would make every downstream date calculation nonsense, and
        // JsonExporter rejects it outright on import for the same reason.
        interval = row.optInt(1, 1).coerceAtLeast(1),
        byday = row.optJSONArray(2).stringValues().takeIf { it.isNotEmpty() },
        bymonthday = row.optInt(3, 0).takeIf { it != 0 },
        ends = when (row.optString(4)) {
            "a" -> endsValue.toIntOrNull()?.takeIf { it > 0 }?.let { RecurrenceEnds.After(it) } ?: RecurrenceEnds.Never
            "o" -> endsValue.takeIf { it.isNotBlank() }?.let { RecurrenceEnds.On(it) } ?: RecurrenceEnds.Never
            else -> RecurrenceEnds.Never
        },
        basedOnCompletion = row.optBoolean(6, false),
        byweekday = row.optString(7).takeIf { it.isNotBlank() },
        bysetpos = row.optInt(8, 0).takeIf { it != 0 }
    )
}

/** Drops trailing elements while they equal the "unset" value for their position, so a simple
 * task never pays for fields it isn't using. Stops at the first non-default element found
 * scanning from the end — an earlier default in the middle is kept, since position is meaning. */
private fun JSONArray.trimTrailingTaskDefaults(): JSONArray {
    // Positions: 0 title(required), 1 priority(0), 2 flag(false), 3 notes(""), 4 listIdx(-1),
    // 5 projIdx(-1), 6 tagIdx([]), 7 personIdx([]), 8 subs([]),
    // 9 due(""), 10 time(""), 11 startDate(""), 12 estimateMinutes(0), 13 recurrence([]).
    //
    // The v4 fields are appended rather than slotted in by likelihood-of-being-set, which would
    // truncate more often. A task with only a due date therefore still pays for placeholder
    // slots 4-8 — but those are a highly repetitive run across every task in a payload, which is
    // exactly what DEFLATE removes, so the compressed cost is far below the raw one.
    while (length() > 1) {
        val last = length() - 1
        val isDefault = when (last) {
            1 -> optInt(1) == 0
            2 -> !optBoolean(2)
            3, 9, 10, 11 -> optString(last).isEmpty()
            4, 5 -> optInt(last) == -1
            6, 7, 8, 13 -> optJSONArray(last)?.length() == 0
            12 -> optInt(12) == 0
            else -> false
        }
        if (!isDefault) break
        remove(last)
    }
    return this
}

private fun JSONArray.trimTrailingDefaults(defaultTag3: JSONArray? = null, defaultTag2: String? = null): JSONArray {
    while (length() > 1) {
        val last = length() - 1
        val isDefault = when {
            defaultTag3 != null && last == 3 -> optJSONArray(3)?.length() == 0
            defaultTag2 != null && last == 2 -> optString(2).isEmpty()
            else -> false
        }
        if (!isDefault) break
        remove(last)
    }
    return this
}

private fun encodeCompressedPayload(payload: JSONArray): String {
    val bytes = payload.toString().toByteArray(Charsets.UTF_8)
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    val zipped = ByteArrayOutputStream().use { bytesOut ->
        DeflaterOutputStream(bytesOut, deflater).use { it.write(bytes) }
        bytesOut.toByteArray()
    }
    deflater.end()
    return Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
}

private fun decodeCompressedPayload(encoded: String, inflate: Boolean): String {
    val zipped = Base64.getUrlDecoder().decode(encoded)
    val stream = if (inflate) {
        InflaterInputStream(ByteArrayInputStream(zipped), Inflater(true))
    } else {
        GZIPInputStream(ByteArrayInputStream(zipped))
    }
    return ByteArrayOutputStream().use { bytesOut ->
        stream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_DECODED_BYTES) {
                    taskLinkError(TaskTransferLinkError.TooLarge, "Shared task link is too large.")
                }
                bytesOut.write(buffer, 0, read)
            }
        }
        bytesOut.toString(Charsets.UTF_8.name())
    }
}

// ============================================================================================
// Parsing
//
// Decoding a link is deliberately separate from importing it. The share flow opens a prefilled
// editor and writes nothing until the user saves, so it needs the link's contents without any
// repository access at all. Keeping one parser as the source of truth (rather than a preview
// path beside the import path) is also what stops the two from drifting — the sort of drift that
// already bit this file once, when a blank-name guard survived in the v1 readers and was lost
// from their v2 replacements.
//
// Entities are surfaced by *name*, not id: the sender's ids mean nothing on this device, and
// resolving names to local rows is the importer's job, not the format's.
// ============================================================================================

/** A list, project, tag or person as named by the sender. [icon] and [description] are only
 * populated for the entity kinds that have them. */
data class SharedEntityRef(
    val name: String,
    val color: String,
    val icon: String? = null,
    val description: String? = null,
    val commonTagNames: List<String> = emptyList()
)

/** One task as described by a link, before it has been reconciled against local data. */
data class SharedTaskDraft(
    val title: String,
    val notes: String? = null,
    val priority: String = "none",
    val flag: Boolean = false,
    val due: String? = null,
    val startDate: String? = null,
    val time: String? = null,
    val estimateMinutes: Int? = null,
    val recurrence: Recurrence? = null,
    val subtaskTitles: List<String> = emptyList(),
    val list: SharedEntityRef? = null,
    val project: SharedEntityRef? = null,
    val tags: List<SharedEntityRef> = emptyList(),
    val assigneeNames: List<String> = emptyList()
)

data class ParsedTransfer(
    val tasks: List<SharedTaskDraft>,
    val copyStructure: Boolean
)

/** Decodes a transfer link. Pure: touches no storage and writes nothing, so callers can inspect
 * what a link contains before deciding whether to act on it. Throws [TaskTransferLinkException]
 * for anything malformed, unsupported, oversized, or carrying no usable task. */
fun parseTransferLink(uri: Uri): ParsedTransfer {
    if (!isTaskTransferUri(uri)) {
        taskLinkError(TaskTransferLinkError.NotATaskLink, "Not a YATA task import link.")
    }
    val params = transferParams(uri)
    val copyStructure = params.getQueryParameter("s") == "1"

    val tasks = try {
        when {
            uri.scheme == TRANSFER_SCHEME && uri.host == LEGACY_HOST ->
                parseLegacyV1(params.getQueryParameter(PARAM_V2_PAYLOAD).orEmpty(), copyStructure)
            isPlaintextTransferUri(params) -> parsePlaintext(params)
            else -> {
                val encoded = params.getQueryParameter(PARAM_COMPRESSED_PAYLOAD)
                    ?: params.getQueryParameter(PARAM_V2_PAYLOAD)
                    ?: taskLinkError(TaskTransferLinkError.Unsupported, "Unsupported YATA task link.")
                parseCompressed(encoded, copyStructure)
            }
        }
    } catch (error: TaskTransferLinkException) {
        throw error
    } catch (error: RuntimeException) {
        taskLinkError(TaskTransferLinkError.Malformed, "Malformed YATA task link.", error)
    }

    if (tasks.isEmpty()) {
        taskLinkError(TaskTransferLinkError.Empty, "No tasks found in shared link.")
    }
    // Applied once here rather than per-format: the compressed path is already implicitly bounded
    // by MAX_DECODED_BYTES, but the plaintext path has no size limit at all — repeated "t="
    // parameters cost only what Android's Intent transport allows, which is far more than a share
    // link should ever legitimately carry. Without this, one tap on a malformed or hostile link
    // could bulk-insert thousands of rows with no review step in between (SharedTaskImportScreen's
    // prefilled-editor confirmation only exists for the single-task case).
    if (tasks.size > MAX_TASKS_PER_LINK) {
        taskLinkError(
            TaskTransferLinkError.TooManyTasks,
            "This shared link contains too many tasks ($MAX_TASKS_PER_LINK max)."
        )
    }
    if (tasks.any { it.hasOversizedField() }) {
        taskLinkError(
            TaskTransferLinkError.TooLarge,
            "This shared task link is too large to import."
        )
    }
    return ParsedTransfer(tasks, copyStructure)
}

/** True when any free-text field on this draft exceeds what a legitimate share would ever carry —
 * see [MAX_TEXT_FIELD_LENGTH]/[MAX_NOTES_FIELD_LENGTH]. Checked once per task here rather than at
 * each of the three parse formats, same reasoning as the [MAX_TASKS_PER_LINK] check above. */
private fun SharedTaskDraft.hasOversizedField(): Boolean =
    title.length > MAX_TEXT_FIELD_LENGTH ||
        (notes?.length ?: 0) > MAX_NOTES_FIELD_LENGTH ||
        subtaskTitles.any { it.length > MAX_TEXT_FIELD_LENGTH } ||
        assigneeNames.any { it.length > MAX_TEXT_FIELD_LENGTH } ||
        (list?.name?.length ?: 0) > MAX_TEXT_FIELD_LENGTH ||
        (project?.name?.length ?: 0) > MAX_TEXT_FIELD_LENGTH ||
        tags.any { it.name.length > MAX_TEXT_FIELD_LENGTH }

private fun parsePlaintext(params: Uri): List<SharedTaskDraft> {
    // Links built before this check existed have no "c" param at all — decline to verify rather
    // than reject every already-shared plaintext link outright.
    params.getQueryParameter(CHECKSUM_PARAM)?.let { expected ->
        if (plaintextChecksum(params) != expected) {
            taskLinkError(
                TaskTransferLinkError.Incomplete,
                "This shared task link looks incomplete — try opening it again."
            )
        }
    }
    return params.getQueryParameters("t").mapIndexedNotNull { index, rawTitle ->
        val title = rawTitle.trim()
        if (title.isBlank()) return@mapIndexedNotNull null
        // Read against the title's own index, so a blank title dropped here can't shift the
        // remaining tasks' values onto the wrong rows.
        SharedTaskDraft(
            title = title,
            notes = params.getQueryParameter("n$index")?.trim()?.takeIf { it.isNotBlank() },
            priority = PRIORITIES[
                params.getQueryParameter("p$index")?.toIntOrNull()?.coerceIn(0, PRIORITIES.lastIndex) ?: 0
            ],
            flag = params.getQueryParameter("f$index") == "1",
            due = params.getQueryParameter("d$index")?.takeIf { it.isNotBlank() },
            startDate = params.getQueryParameter("g$index")?.takeIf { it.isNotBlank() },
            time = params.getQueryParameter("h$index")?.takeIf { it.isNotBlank() },
            estimateMinutes = params.getQueryParameter("m$index")?.toIntOrNull()?.takeIf { it > 0 },
            subtaskTitles = params.getQueryParameters("b$index").take(MAX_SUBTASKS_PER_TASK)
                .map { it.trim() }.filter { it.isNotBlank() }
        )
    }
}

private fun parseCompressed(encoded: String, copyStructure: Boolean): List<SharedTaskDraft> {
    val payload = JSONArray(decodeCompressedPayload(encoded, inflate = true))
    val version = payload.optInt(0)
    if (version !in MIN_SUPPORTED_COMPRESSED_VERSION..MAX_SUPPORTED_COMPRESSED_VERSION) {
        taskLinkError(TaskTransferLinkError.Unsupported, "Unsupported YATA task link.")
    }
    // v2 carried a share title at index 1 that nothing ever read, so its sections sit one slot
    // later. v4 only extended the task row, so it shares v3's layout here.
    val base = if (version == 2) 2 else 1

    val tags = payload.optJSONArray(base + 2).entityRows { row ->
        SharedEntityRef(
            name = row.optString(0).trim(),
            color = row.optString(1, "accentA"),
            description = row.optString(2).takeIf { it.isNotBlank() }
        )
    }
    val lists = payload.optJSONArray(base).entityRows { row ->
        SharedEntityRef(row.optString(0).trim(), row.optString(1, "accentA"), row.optString(2, "folder"))
    }
    val projects = payload.optJSONArray(base + 1).entityRows { row ->
        SharedEntityRef(
            name = row.optString(0).trim(),
            color = row.optString(1, "accentA"),
            icon = row.optString(2, "layers"),
            commonTagNames = row.optJSONArray(3).intValues().mapNotNull { tags.getOrNull(it)?.name }
        )
    }
    val people = payload.optJSONArray(base + 3).entityRows { row ->
        SharedEntityRef(row.optString(0).trim(), row.optString(2, "accentA"))
    }

    return payload.optJSONArray(base + 4).orEmptySequence().mapNotNull { value ->
        val row = value as? JSONArray ?: return@mapNotNull null
        val title = row.optString(0).trim()
        if (title.isBlank()) return@mapNotNull null
        SharedTaskDraft(
            title = title,
            notes = row.optString(3).trim().takeIf { it.isNotBlank() },
            priority = PRIORITIES[row.optInt(1, 0).coerceIn(0, PRIORITIES.lastIndex)],
            flag = row.optBoolean(2, false),
            // v4 fields; a v2/v3 row stops short of these and they read as absent.
            due = row.optString(9).takeIf { it.isNotBlank() },
            startDate = row.optString(11).takeIf { it.isNotBlank() },
            time = row.optString(10).takeIf { it.isNotBlank() },
            estimateMinutes = row.optInt(12, 0).takeIf { it > 0 },
            recurrence = row.optJSONArray(13).toRecurrenceOrNull(),
            subtaskTitles = row.optJSONArray(8).orEmptySequence().take(MAX_SUBTASKS_PER_TASK).mapNotNull { sub ->
                (sub as? JSONArray)?.optString(0)?.trim()?.takeIf { it.isNotBlank() }
            }.toList(),
            list = row.optInt(4, -1).takeIf { copyStructure && it >= 0 }?.let { lists.getOrNull(it) },
            project = row.optInt(5, -1).takeIf { copyStructure && it >= 0 }?.let { projects.getOrNull(it) },
            tags = if (copyStructure) row.optJSONArray(6).intValues().mapNotNull { tags.getOrNull(it) } else emptyList(),
            assigneeNames = if (copyStructure) {
                row.optJSONArray(7).intValues().mapNotNull { people.getOrNull(it)?.name }
            } else {
                emptyList()
            }
        )
    }.toList()
}

private fun parseLegacyV1(encoded: String, copyStructure: Boolean): List<SharedTaskDraft> {
    val payload = JSONObject(decodeCompressedPayload(encoded, inflate = false))
    if (payload.optInt("v") != 1) {
        taskLinkError(TaskTransferLinkError.Unsupported, "Unsupported YATA task link.")
    }

    // v1 dictionaries are keyed by the sender's ids rather than by position.
    fun refsById(key: String, iconKey: String?, defaultIcon: String?): Map<String, SharedEntityRef> =
        payload.optJSONArray(key).orEmptySequence().mapNotNull { it as? JSONObject }
            .mapNotNull { item ->
                val id = item.optString("id")
                val name = item.optString("n").trim()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                id to SharedEntityRef(
                    name = name,
                    color = item.optString("c", "accentA"),
                    icon = iconKey?.let { item.optString(it, defaultIcon.orEmpty()) },
                    description = item.optString("d").takeIf { it.isNotBlank() }
                )
            }.toMap()

    val lists = refsById("lists", "i", "folder")
    val projects = refsById("projects", "i", "layers")
    val tags = refsById("tags", null, null)
    val people = refsById("people", null, null)

    return payload.optJSONArray("tasks").orEmptySequence().mapNotNull { value ->
        val item = value as? JSONObject ?: return@mapNotNull null
        val title = item.optString("t").trim()
        if (title.isBlank()) return@mapNotNull null
        SharedTaskDraft(
            title = title,
            notes = item.optString("n").takeIf { it.isNotBlank() },
            priority = item.optString("p", "none").takeIf { it in PRIORITIES } ?: "none",
            flag = item.optBoolean("f", false),
            subtaskTitles = item.optJSONArray("subs").orEmptySequence().take(MAX_SUBTASKS_PER_TASK).mapNotNull { sub ->
                (sub as? JSONObject)?.optString("t")?.trim()?.takeIf { it.isNotBlank() }
            }.toList(),
            list = item.optString("l").takeIf { copyStructure && it.isNotBlank() }?.let { lists[it] },
            project = item.optString("pr").takeIf { copyStructure && it.isNotBlank() }?.let { projects[it] },
            tags = if (copyStructure) item.optJSONArray("tags").stringValues().mapNotNull { tags[it] } else emptyList(),
            assigneeNames = if (copyStructure) {
                item.optJSONArray("people").stringValues().mapNotNull { people[it]?.name }
            } else {
                emptyList()
            }
        )
    }.toList()
}

/** Maps dictionary rows, dropping blank-named ones **without collapsing the list** — task rows
 * address entries by position, so removing one would silently repoint every later reference. A
 * blank entry stays as a null hole that resolves to "no list/project/tag" instead. */
private fun JSONArray?.entityRows(build: (JSONArray) -> SharedEntityRef): List<SharedEntityRef?> =
    orEmptySequence().map { value ->
        val row = value as? JSONArray ?: return@map null
        build(row).takeIf { it.name.isNotBlank() }
    }.toList()

@Singleton
class TaskTransferImporter @Inject constructor(
    private val repository: YataRepository
) {
    /** Imports every task a link carries. Used for multi-task links; a single-task link goes
     * through the prefilled editor instead, so the user reviews it before anything is written. */
    suspend fun importFrom(uri: Uri): TaskTransferImportResult {
        // Parsing happens in full before the first write. Previously the structure dictionaries
        // were imported and only then was the task list checked, so a link carrying structure but
        // no usable task created lists, projects, tags and people and *then* reported failure —
        // leaving the receiver with entities they never asked for and no way to trace them.
        val parsed = parseTransferLink(uri)
        val tasks = materialise(parsed)
        repository.upsertTasks(tasks, notify = false)
        repository.notifyTasksChanged()
        return TaskTransferImportResult(tasks.size, parsed.copyStructure)
    }

    /** Resolves a parsed transfer against local data, creating any missing structure, and returns
     * the tasks ready to persist. */
    private suspend fun materialise(parsed: ParsedTransfer): List<Task> {
        val resolver = if (parsed.copyStructure) StructureResolver() else null
        // sortOrder continues the existing run rather than restarting: taking the low 32 bits of
        // currentTimeMillis (as this used to) overflows Int to a large negative number, and hands
        // every task in one import the same value, discarding the order the sender chose.
        val baseSortOrder = repository.getTasks().first().size

        return parsed.tasks.mapIndexed { index, draft ->
            Task(
                id = newId("import_task"),
                title = draft.title,
                listId = draft.list?.let { resolver?.listId(it) },
                projectId = draft.project?.let { resolver?.projectId(it) },
                section = "",
                due = draft.due,
                startDate = draft.startDate,
                time = draft.time,
                reminder = null,
                priority = draft.priority,
                flag = draft.flag,
                done = false,
                completedAt = null,
                createdAt = System.currentTimeMillis(),
                deletedAt = null,
                assigneeIds = emptyList(),
                tagIds = draft.tags.mapNotNull { tag -> resolver?.tagId(tag) },
                recurrence = draft.recurrence,
                subtasks = draft.subtaskTitles.mapIndexed { subIndex, subtitle ->
                    Subtask(
                        id = newId("import_subtask"),
                        title = subtitle,
                        done = false,
                        parentSubtaskId = null,
                        sortOrder = subIndex
                    )
                },
                notes = draft.notes,
                sortOrder = baseSortOrder + index,
                seriesId = null,
                archived = false,
                followUpAt = null,
                estimateMinutes = draft.estimateMinutes
            )
        }
    }

    /** Finds or creates the local rows a shared task refers to, matching case-insensitively by
     * name. Entities created during one import are remembered, so two tasks naming the same
     * project share it rather than racing to create two. */
    private inner class StructureResolver {
        private val lists = mutableMapOf<String, String>()
        private val projects = mutableMapOf<String, String>()
        private val tags = mutableMapOf<String, String>()
        private var loaded = false

        private suspend fun load() {
            if (loaded) return
            repository.getLists().first().forEach { lists[it.name.normalizedName()] = it.id }
            repository.getProjects().first().forEach { projects[it.name.normalizedName()] = it.id }
            repository.getTags().first().forEach { tags[it.name.normalizedName()] = it.id }
            loaded = true
        }

        suspend fun listId(ref: SharedEntityRef): String? = resolve(ref, lists) {
            val id = newId("import_list")
            repository.upsertList(
                YataList(id = id, name = ref.name, color = ref.color, icon = ref.icon ?: "folder")
            )
            id
        }

        suspend fun projectId(ref: SharedEntityRef): String? = resolve(ref, projects) {
            val id = newId("import_project")
            repository.upsertProject(
                Project(
                    id = id,
                    name = ref.name,
                    color = ref.color,
                    icon = ref.icon ?: "layers",
                    // Carried so a project's common tags aren't created as rows attached to
                    // nothing, which is what happened before they were imported here.
                    commonTagIds = ref.commonTagNames.mapNotNull { name ->
                        tagId(SharedEntityRef(name = name, color = "accentA"))
                    }
                )
            )
            id
        }

        suspend fun tagId(ref: SharedEntityRef): String? = resolve(ref, tags) {
            val id = newId("import_tag")
            repository.upsertTag(
                Tag(id = id, name = ref.name, color = ref.color, description = ref.description)
            )
            id
        }

        private suspend fun resolve(
            ref: SharedEntityRef,
            cache: MutableMap<String, String>,
            create: suspend () -> String
        ): String? {
            // A blank name would create an entity called "" — the v1 readers guarded against this
            // and their v2 replacements silently dropped the check.
            if (ref.name.isBlank()) return null
            load()
            val key = ref.name.normalizedName()
            return cache[key] ?: create().also { cache[key] = it }
        }
    }
}

private fun JSONArray?.stringValues(): List<String> =
    orEmptySequence().mapNotNull { it as? String }.toList()

private fun JSONArray?.intValues(): List<Int> =
    orEmptySequence().mapNotNull { (it as? Number)?.toInt() }.toList()

private fun JSONArray?.orEmptySequence(): Sequence<Any?> = sequence {
    val array = this@orEmptySequence ?: return@sequence
    for (index in 0 until array.length()) yield(array.opt(index))
}

private fun String.normalizedName(): String = trim().lowercase()

private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

private fun initialsFor(name: String): String =
    name.split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
