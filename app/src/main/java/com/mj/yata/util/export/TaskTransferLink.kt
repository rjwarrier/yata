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

// Current (v2) links. Raw-deflate-compressed compact positional JSON; the format version lives
// in the host, not the payload, so the importer can dispatch before spending work decompressing.
// See docs/app-links-v2-plan.md.
private const val HOST = "i"

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
    val soleTask = tasks.singleOrNull()
    if (soleTask != null && !includeStructure && soleTask.subtasks.isEmpty() &&
        (soleTask.notes.isNullOrBlank() || !includeNotes)
    ) {
        return buildPlaintextTransferLink(soleTask)
    }

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

    val payload = JSONArray()
        .put(2)
        .put(title)
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

    val encoded = encodeV2Payload(payload)
    val uri = Uri.Builder()
        .scheme(TRANSFER_SCHEME)
        .authority(HOST)
        .appendQueryParameter("s", if (includeStructure) "1" else "0")
        .appendQueryParameter("d", encoded)
        .build()
        .toString()
    return TaskTransferLink(uri = uri, includesStructure = includeStructure)
}

/** Fast path for the most common share: one task, no structure, no notes, no subtasks. Skips
 * Base64/compression entirely — the receiver can read what they're about to import straight out
 * of the URL, and it's shorter than the compressed blob for anything this small. */
private fun buildPlaintextTransferLink(task: Task): TaskTransferLink {
    val builder = Uri.Builder()
        .scheme(TRANSFER_SCHEME)
        .authority(HOST)
        .appendQueryParameter("t", task.title)
    val priorityIndex = PRIORITIES.indexOf(task.priority).coerceAtLeast(0)
    if (priorityIndex != 0) builder.appendQueryParameter("p", priorityIndex.toString())
    if (task.flag) builder.appendQueryParameter("f", "1")
    return TaskTransferLink(uri = builder.build().toString(), includesStructure = false)
}

private fun isPlaintextTransferUri(uri: Uri): Boolean = uri.getQueryParameter("t") != null

fun isTaskTransferUri(uri: Uri?): Boolean {
    if (uri?.scheme != TRANSFER_SCHEME) return false
    return uri.host == HOST || (uri.host == LEGACY_HOST && uri.path == LEGACY_PATH)
}

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

private fun encodeV2Payload(payload: JSONArray): String {
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
        if (uri.host != LEGACY_HOST && isPlaintextTransferUri(uri)) {
            return importPlaintext(uri)
        }
        val encoded = uri.getQueryParameter("d").orEmpty()
        val copyStructure = uri.getQueryParameter("s") == "1"
        return if (uri.host == LEGACY_HOST) {
            importLegacyV1(encoded, copyStructure)
        } else {
            importV2(encoded, copyStructure)
        }
    }

    private suspend fun importPlaintext(uri: Uri): TaskTransferImportResult {
        val title = uri.getQueryParameter("t").orEmpty().trim()
        require(title.isNotBlank()) { "No tasks found in shared link." }
        val priorityIndex = uri.getQueryParameter("p")?.toIntOrNull()?.coerceIn(0, PRIORITIES.lastIndex) ?: 0
        val task = Task(
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
            flag = uri.getQueryParameter("f") == "1",
            done = false,
            completedAt = null,
            createdAt = System.currentTimeMillis(),
            deletedAt = null,
            assigneeIds = emptyList(),
            tagIds = emptyList(),
            recurrence = null,
            subtasks = emptyList(),
            notes = null,
            sortOrder = System.currentTimeMillis().toInt(),
            seriesId = null,
            archived = false,
            followUpAt = null,
            estimateMinutes = null
        )
        repository.upsertTasks(listOf(task), notify = false)
        repository.notifyTasksChanged()
        return TaskTransferImportResult(1, false)
    }

    // --- v2 -------------------------------------------------------------------------------

    private suspend fun importV2(encoded: String, copyStructure: Boolean): TaskTransferImportResult {
        val payload = JSONArray(decodeCompressedPayload(encoded, inflate = true))
        require(payload.optInt(0) == 2) { "Unsupported YATA task link." }

        val tagMap = if (copyStructure) importTagsV2(payload.optJSONArray(4)) else emptyList()
        val listMap = if (copyStructure) importListsV2(payload.optJSONArray(2)) else emptyList()
        val projectMap = if (copyStructure) importProjectsV2(payload.optJSONArray(3), tagMap) else emptyList()
        val personMap = if (copyStructure) importPeopleV2(payload.optJSONArray(5)) else emptyList()

        val tasks = payload.optJSONArray(6).orEmptySequence().mapNotNull { value ->
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
