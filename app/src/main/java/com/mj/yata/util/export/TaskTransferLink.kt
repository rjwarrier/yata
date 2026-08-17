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
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TRANSFER_SCHEME = "yata"
private const val TRANSFER_HOST = "import"
private const val TRANSFER_PATH = "/tasks"
private const val TRANSFER_VERSION = 1
private const val MAX_DECODED_BYTES = 256_000

data class TaskTransferLinks(
    val inboxOnly: String,
    val withStructure: String
) {
    fun asShareText(title: String, count: Int): String = buildString {
        appendLine("YATA shared ${if (count == 1) "task" else "$count tasks"}: $title")
        appendLine()
        appendLine("Add to Inbox:")
        appendLine(inboxOnly)
        appendLine()
        appendLine("Add to Inbox with missing lists, projects, tags, and people:")
        append(withStructure)
    }
}

data class TaskTransferImportResult(
    val taskCount: Int,
    val copiedStructure: Boolean
)

fun buildTaskTransferLinks(
    title: String,
    tasks: List<Task>,
    listsById: Map<String, YataList>,
    projectsById: Map<String, Project>,
    tagsById: Map<String, Tag>,
    peopleById: Map<String, Person>,
    includeStructure: Boolean,
    includeNotes: Boolean
): TaskTransferLinks {
    val payload = JSONObject()
        .put("v", TRANSFER_VERSION)
        .put("title", title)
        .put("tasks", JSONArray().also { array ->
            tasks.forEach { task ->
                array.put(task.toTransferJson(includeStructure = includeStructure, includeNotes = includeNotes))
            }
        })

    if (includeStructure) {
        val listIds = tasks.mapNotNull { it.listId }.toSet()
        val projectIds = tasks.mapNotNull { it.projectId }.toSet()
        val tagIds = tasks.flatMap { task -> task.effectiveTransferTagIds(projectsById) }.toSet()
        val personIds = tasks.flatMap { it.assigneeIds }.toSet()

        payload
            .put("lists", JSONArray().also { array ->
                listIds.mapNotNull { listsById[it] }.forEach { list ->
                    array.put(
                        JSONObject()
                            .put("id", list.id)
                            .put("n", list.name)
                            .put("c", list.color)
                            .put("i", list.icon)
                    )
                }
            })
            .put("projects", JSONArray().also { array ->
                projectIds.mapNotNull { projectsById[it] }.forEach { project ->
                    array.put(
                        JSONObject()
                            .put("id", project.id)
                            .put("n", project.name)
                            .put("c", project.color)
                            .put("i", project.icon)
                    )
                }
            })
            .put("tags", JSONArray().also { array ->
                tagIds.mapNotNull { tagsById[it] }.forEach { tag ->
                    array.put(
                        JSONObject()
                            .put("id", tag.id)
                            .put("n", tag.name)
                            .put("c", tag.color)
                            .putOpt("d", tag.description)
                    )
                }
            })
            .put("people", JSONArray().also { array ->
                personIds.mapNotNull { peopleById[it] }.forEach { person ->
                    array.put(
                        JSONObject()
                            .put("id", person.id)
                            .put("n", person.name)
                            .put("in", person.initials)
                            .put("c", person.color)
                    )
                }
            })
    }

    val encoded = encodeTransferPayload(payload)
    return TaskTransferLinks(
        inboxOnly = transferUri(encoded, copyStructure = false),
        withStructure = transferUri(encoded, copyStructure = true)
    )
}

fun isTaskTransferUri(uri: Uri?): Boolean =
    uri?.scheme == TRANSFER_SCHEME && uri.host == TRANSFER_HOST && uri.path == TRANSFER_PATH

private fun Task.toTransferJson(includeStructure: Boolean, includeNotes: Boolean): JSONObject =
    JSONObject()
        .put("t", title)
        .put("p", priority)
        .put("f", flag)
        .putOpt("n", notes.takeIf { includeNotes && !it.isNullOrBlank() })
        .putOpt("l", listId.takeIf { includeStructure })
        .putOpt("pr", projectId.takeIf { includeStructure })
        .put("tags", JSONArray().also { array ->
            if (includeStructure) tagIds.forEach { array.put(it) }
        })
        .put("people", JSONArray().also { array ->
            if (includeStructure) assigneeIds.forEach { array.put(it) }
        })
        .put("subs", JSONArray().also { array ->
            subtasks.forEach { subtask ->
                array.put(
                    JSONObject()
                        .put("t", subtask.title)
                        .put("d", subtask.done)
                        .putOpt("p", subtask.parentSubtaskId)
                        .put("o", subtask.sortOrder)
                )
            }
        })

private fun Task.effectiveTransferTagIds(projectsById: Map<String, Project>): List<String> =
    (tagIds + (projectId?.let { projectsById[it] }?.commonTagIds ?: emptyList())).distinct()

private fun encodeTransferPayload(payload: JSONObject): String {
    val bytes = payload.toString().toByteArray(Charsets.UTF_8)
    val zipped = ByteArrayOutputStream().use { bytesOut ->
        GZIPOutputStream(bytesOut).use { gzip -> gzip.write(bytes) }
        bytesOut.toByteArray()
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
}

private fun decodeTransferPayload(encoded: String): JSONObject {
    val zipped = Base64.getUrlDecoder().decode(encoded)
    val decoded = ByteArrayOutputStream().use { bytesOut ->
        GZIPInputStream(ByteArrayInputStream(zipped)).use { gzip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_DECODED_BYTES) { "Shared task link is too large." }
                bytesOut.write(buffer, 0, read)
            }
        }
        bytesOut.toString(Charsets.UTF_8.name())
    }
    return JSONObject(decoded)
}

private fun transferUri(encoded: String, copyStructure: Boolean): String =
    Uri.Builder()
        .scheme(TRANSFER_SCHEME)
        .authority(TRANSFER_HOST)
        .path(TRANSFER_PATH)
        .appendQueryParameter("s", if (copyStructure) "1" else "0")
        .appendQueryParameter("d", encoded)
        .build()
        .toString()

@Singleton
class TaskTransferImporter @Inject constructor(
    private val repository: YataRepository
) {
    suspend fun importFrom(uri: Uri): TaskTransferImportResult {
        require(isTaskTransferUri(uri)) { "Not a YATA task import link." }
        val payload = decodeTransferPayload(uri.getQueryParameter("d").orEmpty())
        require(payload.optInt("v") == TRANSFER_VERSION) { "Unsupported YATA task link." }
        val copyStructure = uri.getQueryParameter("s") == "1"
        val imported = importPayload(payload, copyStructure)
        return TaskTransferImportResult(imported, copyStructure)
    }

    private suspend fun importPayload(payload: JSONObject, copyStructure: Boolean): Int {
        val listMap = if (copyStructure) importLists(payload.optJSONArray("lists")) else emptyMap()
        val tagMap = if (copyStructure) importTags(payload.optJSONArray("tags")) else emptyMap()
        val projectMap = if (copyStructure) importProjects(payload.optJSONArray("projects")) else emptyMap()
        val personMap = if (copyStructure) importPeople(payload.optJSONArray("people")) else emptyMap()

        val tasks = payload.optJSONArray("tasks").orEmptySequence().mapNotNull { value ->
            val item = value as? JSONObject ?: return@mapNotNull null
            val title = item.optString("t").trim()
            if (title.isBlank()) return@mapNotNull null
            val newId = newId("import_task")
            Task(
                id = newId,
                title = title,
                listId = item.optString("l").takeIf { copyStructure && it.isNotBlank() }?.let { listMap[it] },
                projectId = item.optString("pr").takeIf { copyStructure && it.isNotBlank() }?.let { projectMap[it] },
                section = "",
                due = null,
                startDate = null,
                time = null,
                reminder = null,
                priority = item.optString("p", "none").takeIf { it in setOf("none", "low", "med", "high") } ?: "none",
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
                    Subtask(
                        id = newId("import_subtask"),
                        title = subTitle,
                        done = false,
                        parentSubtaskId = null,
                        sortOrder = sub.optInt("o", index)
                    )
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
        return tasks.size
    }

    private suspend fun importLists(array: JSONArray?): Map<String, String> {
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

    private suspend fun importProjects(array: JSONArray?): Map<String, String> {
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

    private suspend fun importTags(array: JSONArray?): Map<String, String> {
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
                val created = Tag(
                    id = id,
                    name = name,
                    color = item.optString("c", "accentA"),
                    description = item.optString("d").takeIf { it.isNotBlank() }
                )
                repository.upsertTag(created)
                existing[name.normalizedName()] = created
                id
            }
            result[oldId] = target
        }
        return result
    }

    private suspend fun importPeople(array: JSONArray?): Map<String, String> {
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
                val created = Person(
                    id = id,
                    name = name,
                    initials = item.optString("in").takeIf { it.isNotBlank() } ?: initialsFor(name),
                    color = item.optString("c", "accentA")
                )
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
