package com.mj.yata.util.export

import android.net.Uri
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
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
//   e=…  v3 compressed payload
//   d=…  v2 compressed payload (decode only; no longer generated)
private const val HOST = "i"
private const val PARAM_V3_PAYLOAD = "e"
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

private val PRIORITIES = listOf("none", "low", "med", "high")

data class TaskTransferLink(
    val uri: String,
    val includesStructure: Boolean
) {
    val mayNotAutoLinkEverywhere: Boolean get() = uri.length > RELIABLE_LINK_LENGTH

    fun asShareText(title: String, count: Int): String = buildString {
        appendLine("YATA shared ${if (count == 1) "task" else "$count tasks"}: $title")
        appendLine()
        if (includesStructure) {
            appendLine("Add to Inbox (creates any missing lists, projects, tags, and people):")
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
    val personIds = if (includeStructure) tasks.flatMap { it.assigneeIds }.toSet() else emptySet()

    val lists = listIds.mapNotNull { listsById[it] }
    val projects = projectIds.mapNotNull { projectsById[it] }
    val tags = tagIds.mapNotNull { tagsById[it] }
    val people = personIds.mapNotNull { peopleById[it] }

    val listIndex = lists.mapIndexed { index, list -> list.id to index }.toMap()
    val projectIndex = projects.mapIndexed { index, project -> project.id to index }.toMap()
    val tagIndex = tags.mapIndexed { index, tag -> tag.id to index }.toMap()
    val personIndex = people.mapIndexed { index, person -> person.id to index }.toMap()

    // v3 layout: [version, lists, projects, tags, people, tasks]. v2 carried the share title at
    // index 1, which no importer ever read — the share text around the link already shows it —
    // so every compressed link was paying 16-24 characters for a field nobody consumed.
    val payload = JSONArray()
        .put(3)
        .put(JSONArray().also { array -> lists.forEach { array.put(it.toTransferRow()) } })
        .put(JSONArray().also { array -> projects.forEach { array.put(it.toTransferRow(tagIndex)) } })
        .put(JSONArray().also { array -> tags.forEach { array.put(it.toTransferRow()) } })
        .put(JSONArray().also { array -> people.forEach { array.put(it.toTransferRow()) } })
        .put(JSONArray().also { array ->
            tasks.forEach { task ->
                array.put(
                    task.toTransferRow(
                        includeStructure = includeStructure,
                        includeNotes = includeNotes,
                        listIndex = listIndex,
                        projectIndex = projectIndex,
                        tagIndex = tagIndex,
                        personIndex = personIndex
                    )
                )
            }
        })

    val builder = Uri.Builder().scheme(TRANSFER_SCHEME).authority(HOST)
    // "s" is emitted only when set: the import side already reads absence as false, so spelling
    // out the default cost four characters to say nothing.
    if (includeStructure) builder.appendQueryParameter("s", "1")
    builder.appendQueryParameter(PARAM_V3_PAYLOAD, encodeCompressedPayload(payload))
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
        task.subtasks.forEach { builder.appendQueryParameter("b$index", it.title) }
    }
    return TaskTransferLink(uri = webTransferUri(builder.build()), includesStructure = false)
}

private fun isPlaintextTransferUri(uri: Uri): Boolean = uri.getQueryParameter("t") != null

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
    tagIndex: Map<String, Int>,
    personIndex: Map<String, Int>
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
        .put(JSONArray().also { array ->
            if (includeStructure) assigneeIds.mapNotNull { personIndex[it] }.forEach { array.put(it) }
        })
        .put(JSONArray().also { array -> subtasks.forEach { array.put(JSONArray().put(it.title)) } })
    return row.trimTrailingTaskDefaults()
}

/** Drops trailing elements while they equal the "unset" value for their position, so a simple
 * task never pays for fields it isn't using. Stops at the first non-default element found
 * scanning from the end — an earlier default in the middle is kept, since position is meaning. */
private fun JSONArray.trimTrailingTaskDefaults(): JSONArray {
    // Positions: 0 title(required), 1 priority(0), 2 flag(false), 3 notes(""), 4 listIdx(-1),
    // 5 projIdx(-1), 6 tagIdx([]), 7 personIdx([]), 8 subs([]).
    while (length() > 1) {
        val last = length() - 1
        val isDefault = when (last) {
            1 -> optInt(1) == 0
            2 -> !optBoolean(2)
            3 -> optString(3).isEmpty()
            4, 5 -> optInt(last) == -1
            6, 7, 8 -> optJSONArray(last)?.length() == 0
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
                require(total <= MAX_DECODED_BYTES) { "Shared task link is too large." }
                bytesOut.write(buffer, 0, read)
            }
        }
        bytesOut.toString(Charsets.UTF_8.name())
    }
}

@Singleton
class TaskTransferImporter @Inject constructor(
    private val repository: YataRepository
) {
    suspend fun importFrom(uri: Uri): TaskTransferImportResult {
        require(isTaskTransferUri(uri)) { "Not a YATA task import link." }
        // Everything below reads parameters, not the transport: the https form carries them in the
        // fragment, the yata:// form in the query string, and transferParams normalises the two.
        val params = transferParams(uri)
        // Absence of "s" reads as false, which is what lets the builder omit it at its default.
        val copyStructure = params.getQueryParameter("s") == "1"
        if (uri.scheme == TRANSFER_SCHEME && uri.host == LEGACY_HOST) {
            return importLegacyV1(params.getQueryParameter(PARAM_V2_PAYLOAD).orEmpty(), copyStructure)
        }
        if (isPlaintextTransferUri(params)) return importPlaintext(params)
        params.getQueryParameter(PARAM_V3_PAYLOAD)?.let { return importV3(it, copyStructure) }
        params.getQueryParameter(PARAM_V2_PAYLOAD)?.let { return importV2(it, copyStructure) }
        throw IllegalArgumentException("Unsupported YATA task link.")
    }

    private suspend fun importPlaintext(uri: Uri): TaskTransferImportResult {
        val tasks = uri.getQueryParameters("t").mapIndexedNotNull { index, rawTitle ->
            val title = rawTitle.trim()
            if (title.isBlank()) return@mapIndexedNotNull null
            // Read against the title's own index, so a blank title dropped above can't shift the
            // remaining tasks' priorities onto the wrong rows.
            val priorityIndex = uri.getQueryParameter("p$index")?.toIntOrNull()
                ?.coerceIn(0, PRIORITIES.lastIndex) ?: 0
            Task(
                id = newId("import_task"),
                title = title,
                listId = null,
                projectId = null,
                section = "",
                due = null,
                startDate = null,
                time = null,
                reminder = null,
                priority = PRIORITIES[priorityIndex],
                flag = uri.getQueryParameter("f$index") == "1",
                done = false,
                completedAt = null,
                createdAt = System.currentTimeMillis(),
                deletedAt = null,
                assigneeIds = emptyList(),
                tagIds = emptyList(),
                recurrence = null,
                subtasks = uri.getQueryParameters("b$index").mapIndexedNotNull { subIndex, rawSub ->
                    val subTitle = rawSub.trim()
                    if (subTitle.isBlank()) return@mapIndexedNotNull null
                    Subtask(id = newId("import_subtask"), title = subTitle, done = false, parentSubtaskId = null, sortOrder = subIndex)
                },
                notes = uri.getQueryParameter("n$index")?.trim()?.takeIf { it.isNotBlank() },
                sortOrder = System.currentTimeMillis().toInt(),
                seriesId = null,
                archived = false,
                followUpAt = null,
                estimateMinutes = null
            )
        }
        require(tasks.isNotEmpty()) { "No tasks found in shared link." }
        repository.upsertTasks(tasks, notify = false)
        repository.notifyTasksChanged()
        return TaskTransferImportResult(tasks.size, false)
    }

    // --- v2 / v3 ---------------------------------------------------------------------------
    //
    // The two differ only in whether a (never-read) share title sits at index 1, so the section
    // indices shift by one. Task row layout is identical, which is the bulk of the parsing —
    // hence one decoder taking the offset rather than two near-copies that could drift apart.

    private suspend fun importV3(encoded: String, copyStructure: Boolean) =
        importCompressed(encoded, copyStructure, expectedVersion = 3, dictionaryBase = 1)

    private suspend fun importV2(encoded: String, copyStructure: Boolean) =
        importCompressed(encoded, copyStructure, expectedVersion = 2, dictionaryBase = 2)

    private suspend fun importCompressed(
        encoded: String,
        copyStructure: Boolean,
        expectedVersion: Int,
        dictionaryBase: Int
    ): TaskTransferImportResult {
        val payload = JSONArray(decodeCompressedPayload(encoded, inflate = true))
        require(payload.optInt(0) == expectedVersion) { "Unsupported YATA task link." }

        val tagMap = if (copyStructure) importTagsV2(payload.optJSONArray(dictionaryBase + 2)) else emptyList()
        val listMap = if (copyStructure) importListsV2(payload.optJSONArray(dictionaryBase)) else emptyList()
        val projectMap = if (copyStructure) importProjectsV2(payload.optJSONArray(dictionaryBase + 1), tagMap) else emptyList()
        val personMap = if (copyStructure) importPeopleV2(payload.optJSONArray(dictionaryBase + 3)) else emptyList()

        val tasks = payload.optJSONArray(dictionaryBase + 4).orEmptySequence().mapNotNull { value ->
            val row = value as? JSONArray ?: return@mapNotNull null
            val title = row.optString(0).trim()
            if (title.isBlank()) return@mapNotNull null
            val priorityIndex = row.optInt(1, 0).coerceIn(0, PRIORITIES.lastIndex)
            val listIdx = row.optInt(4, -1)
            val projIdx = row.optInt(5, -1)
            Task(
                id = newId("import_task"),
                title = title,
                listId = listIdx.takeIf { copyStructure && it >= 0 }?.let { listMap.getOrNull(it) },
                projectId = projIdx.takeIf { copyStructure && it >= 0 }?.let { projectMap.getOrNull(it) },
                section = "",
                due = null,
                startDate = null,
                time = null,
                reminder = null,
                priority = PRIORITIES[priorityIndex],
                flag = row.optBoolean(2, false),
                done = false,
                completedAt = null,
                createdAt = System.currentTimeMillis(),
                deletedAt = null,
                assigneeIds = if (copyStructure) row.optJSONArray(7).intValues().mapNotNull { personMap.getOrNull(it) } else emptyList(),
                tagIds = if (copyStructure) row.optJSONArray(6).intValues().mapNotNull { tagMap.getOrNull(it) } else emptyList(),
                recurrence = null,
                subtasks = row.optJSONArray(8).orEmptySequence().mapIndexedNotNull { index, subValue ->
                    val sub = subValue as? JSONArray ?: return@mapIndexedNotNull null
                    val subTitle = sub.optString(0).trim()
                    if (subTitle.isBlank()) return@mapIndexedNotNull null
                    Subtask(id = newId("import_subtask"), title = subTitle, done = false, parentSubtaskId = null, sortOrder = index)
                }.toList(),
                notes = row.optString(3).trim().takeIf { it.isNotBlank() },
                sortOrder = System.currentTimeMillis().toInt(),
                seriesId = null,
                archived = false,
                followUpAt = null,
                estimateMinutes = null
            )
        }.toList()

        require(tasks.isNotEmpty()) { "No tasks found in shared link." }
        repository.upsertTasks(tasks, notify = false)
        repository.notifyTasksChanged()
        return TaskTransferImportResult(tasks.size, copyStructure)
    }

    // Sequence.map's transform is lazily replayed by its Iterator, which the compiler can't run
    // suspend calls through — these build the index-ordered result with forEach + a mutable
    // list instead, matching the pattern the legacy v1 importers already use below.

    private suspend fun importListsV2(array: JSONArray?): List<String> {
        val existing = repository.getLists().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableListOf<String>()
        array.orEmptySequence().mapNotNull { it as? JSONArray }.forEach { row ->
            val name = row.optString(0).trim()
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_list")
                val created = YataList(id = id, name = name, color = row.optString(1, "accentA"), icon = row.optString(2, "folder"))
                repository.upsertList(created)
                existing[name.normalizedName()] = created
                id
            }
            result.add(target)
        }
        return result
    }

    private suspend fun importProjectsV2(array: JSONArray?, tagMap: List<String>): List<String> {
        val existing = repository.getProjects().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableListOf<String>()
        array.orEmptySequence().mapNotNull { it as? JSONArray }.forEach { row ->
            val name = row.optString(0).trim()
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_project")
                val commonTagIds = row.optJSONArray(3).intValues().mapNotNull { tagMap.getOrNull(it) }
                val created = Project(
                    id = id, name = name, color = row.optString(1, "accentA"), icon = row.optString(2, "layers"),
                    commonTagIds = commonTagIds
                )
                repository.upsertProject(created)
                existing[name.normalizedName()] = created
                id
            }
            result.add(target)
        }
        return result
    }

    private suspend fun importTagsV2(array: JSONArray?): List<String> {
        val existing = repository.getTags().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableListOf<String>()
        array.orEmptySequence().mapNotNull { it as? JSONArray }.forEach { row ->
            val name = row.optString(0).trim()
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_tag")
                val created = Tag(id = id, name = name, color = row.optString(1, "accentA"), description = row.optString(2).takeIf { it.isNotBlank() })
                repository.upsertTag(created)
                existing[name.normalizedName()] = created
                id
            }
            result.add(target)
        }
        return result
    }

    private suspend fun importPeopleV2(array: JSONArray?): List<String> {
        val existing = repository.getPeople().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableListOf<String>()
        array.orEmptySequence().mapNotNull { it as? JSONArray }.forEach { row ->
            val name = row.optString(0).trim()
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_person")
                val initials = row.optString(1).takeIf { it.isNotBlank() } ?: initialsFor(name)
                val created = Person(id = id, name = name, initials = initials, color = row.optString(2, "accentA"))
                repository.upsertPerson(created)
                existing[name.normalizedName()] = created
                id
            }
            result.add(target)
        }
        return result
    }

    // --- v1 (legacy decode only) -----------------------------------------------------------

    private suspend fun importLegacyV1(encoded: String, copyStructure: Boolean): TaskTransferImportResult {
        val payload = JSONObject(decodeCompressedPayload(encoded, inflate = false))
        require(payload.optInt("v") == 1) { "Unsupported YATA task link." }

        val listMap = if (copyStructure) importLegacyLists(payload.optJSONArray("lists")) else emptyMap()
        val tagMap = if (copyStructure) importLegacyTags(payload.optJSONArray("tags")) else emptyMap()
        val projectMap = if (copyStructure) importLegacyProjects(payload.optJSONArray("projects")) else emptyMap()
        val personMap = if (copyStructure) importLegacyPeople(payload.optJSONArray("people")) else emptyMap()

        val tasks = payload.optJSONArray("tasks").orEmptySequence().mapNotNull { value ->
            val item = value as? JSONObject ?: return@mapNotNull null
            val title = item.optString("t").trim()
            if (title.isBlank()) return@mapNotNull null
            Task(
                id = newId("import_task"),
                title = title,
                listId = item.optString("l").takeIf { copyStructure && it.isNotBlank() }?.let { listMap[it] },
                projectId = item.optString("pr").takeIf { copyStructure && it.isNotBlank() }?.let { projectMap[it] },
                section = "",
                due = null,
                startDate = null,
                time = null,
                reminder = null,
                priority = item.optString("p", "none").takeIf { it in PRIORITIES } ?: "none",
                flag = item.optBoolean("f", false),
                done = false,
                completedAt = null,
                createdAt = System.currentTimeMillis(),
                deletedAt = null,
                assigneeIds = if (copyStructure) item.optJSONArray("people").stringValues().mapNotNull { personMap[it] } else emptyList(),
                tagIds = if (copyStructure) item.optJSONArray("tags").stringValues().mapNotNull { tagMap[it] } else emptyList(),
                recurrence = null,
                subtasks = item.optJSONArray("subs").orEmptySequence().mapIndexedNotNull { index, subValue ->
                    val sub = subValue as? JSONObject ?: return@mapIndexedNotNull null
                    val subTitle = sub.optString("t").trim()
                    if (subTitle.isBlank()) return@mapIndexedNotNull null
                    Subtask(id = newId("import_subtask"), title = subTitle, done = false, parentSubtaskId = null, sortOrder = sub.optInt("o", index))
                }.toList(),
                notes = item.optString("n").takeIf { it.isNotBlank() },
                sortOrder = System.currentTimeMillis().toInt(),
                seriesId = null,
                archived = false,
                followUpAt = null,
                estimateMinutes = null
            )
        }.toList()

        require(tasks.isNotEmpty()) { "No tasks found in shared link." }
        repository.upsertTasks(tasks, notify = false)
        repository.notifyTasksChanged()
        return TaskTransferImportResult(tasks.size, copyStructure)
    }

    private suspend fun importLegacyLists(array: JSONArray?): Map<String, String> {
        val existing = repository.getLists().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableMapOf<String, String>()
        array.orEmptySequence().mapNotNull { it as? JSONObject }.forEach { item ->
            val oldId = item.optString("id")
            val name = item.optString("n").trim()
            if (oldId.isBlank() || name.isBlank()) return@forEach
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_list")
                val created = YataList(id = id, name = name, color = item.optString("c", "accentA"), icon = item.optString("i", "folder"))
                repository.upsertList(created)
                existing[name.normalizedName()] = created
                id
            }
            result[oldId] = target
        }
        return result
    }

    private suspend fun importLegacyProjects(array: JSONArray?): Map<String, String> {
        val existing = repository.getProjects().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableMapOf<String, String>()
        array.orEmptySequence().mapNotNull { it as? JSONObject }.forEach { item ->
            val oldId = item.optString("id")
            val name = item.optString("n").trim()
            if (oldId.isBlank() || name.isBlank()) return@forEach
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_project")
                val created = Project(id = id, name = name, color = item.optString("c", "accentA"), icon = item.optString("i", "layers"))
                repository.upsertProject(created)
                existing[name.normalizedName()] = created
                id
            }
            result[oldId] = target
        }
        return result
    }

    private suspend fun importLegacyTags(array: JSONArray?): Map<String, String> {
        val existing = repository.getTags().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableMapOf<String, String>()
        array.orEmptySequence().mapNotNull { it as? JSONObject }.forEach { item ->
            val oldId = item.optString("id")
            val name = item.optString("n").trim()
            if (oldId.isBlank() || name.isBlank()) return@forEach
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_tag")
                val created = Tag(id = id, name = name, color = item.optString("c", "accentA"), description = item.optString("d").takeIf { it.isNotBlank() })
                repository.upsertTag(created)
                existing[name.normalizedName()] = created
                id
            }
            result[oldId] = target
        }
        return result
    }

    private suspend fun importLegacyPeople(array: JSONArray?): Map<String, String> {
        val existing = repository.getPeople().first().associateBy { it.name.normalizedName() }.toMutableMap()
        val result = mutableMapOf<String, String>()
        array.orEmptySequence().mapNotNull { it as? JSONObject }.forEach { item ->
            val oldId = item.optString("id")
            val name = item.optString("n").trim()
            if (oldId.isBlank() || name.isBlank()) return@forEach
            val match = existing[name.normalizedName()]
            val target = if (match != null) {
                match.id
            } else {
                val id = newId("import_person")
                val created = Person(id = id, name = name, initials = item.optString("in").takeIf { it.isNotBlank() } ?: initialsFor(name), color = item.optString("c", "accentA"))
                repository.upsertPerson(created)
                existing[name.normalizedName()] = created
                id
            }
            result[oldId] = target
        }
        return result
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
