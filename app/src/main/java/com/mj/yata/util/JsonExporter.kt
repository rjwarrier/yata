package com.mj.yata.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YataRepository
) {
    /** Everything a backup payload is built from — loaded once so both the full export and the
     * primary/archive split can slice [tasks]/[comments] differently without hitting the
     * repository twice. */
    private data class BackupData(
        val people: List<Person>,
        val personGroups: List<PersonGroup>,
        val projects: List<Project>,
        val lists: List<YataList>,
        val tags: List<Tag>,
        val tagGroups: List<TagGroup>,
        val tasks: List<Task>,
        val comments: List<TaskComment>
    )

    private suspend fun loadBackupData(): BackupData = BackupData(
        people = repository.getPeople().first(),
        personGroups = repository.getPersonGroups().first(),
        projects = repository.getProjects().first(),
        lists = repository.getLists().first(),
        tags = repository.getTags().first(),
        tagGroups = repository.getTagGroups().first(),
        tasks = repository.getTasks().first(),
        comments = repository.getAllComments().first()
    )

    suspend fun exportData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = buildBackupJson(loadBackupData())
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(root.toString(2))
                }
            }
            true
        } catch (e: Exception) {
            Log.e("JsonExporter", "exportData failed", e)
            false
        }
    }

    /** Raw JSON bytes of a full backup — used for cloud upload, where there's no [Uri] to write
     * through a [android.content.ContentResolver]. */
    suspend fun exportToBytes(): ByteArray = withContext(Dispatchers.IO) {
        buildBackupJson(loadBackupData()).toString(2).toByteArray(Charsets.UTF_8)
    }

    /**
     * Splits completed tasks older than [archiveMonths] (and their comments) out of the payload
     * cloud backup uploads — that payload gets rebuilt and re-uploaded on every debounce/interval
     * trigger, so letting years of completed tasks pile up in it makes every single backup bigger
     * forever. The split-off tasks go in the returned archive payload instead, uploaded to its own
     * file that's only replaced when its contents actually change. [archiveMonths] <= 0 disables
     * the split (archive is always null, primary is the full unsplit payload) — same shape
     * [buildBackupJson] alone produces, so callers don't need a separate code path for "off".
     *
     * Manual export/[exportToDownloads] deliberately don't use this — those are one-off,
     * user-triggered actions where a single complete file is more useful than a split one.
     */
    suspend fun buildSplitBackupJson(archiveMonths: Int): Pair<JSONObject, JSONObject?> {
        val data = loadBackupData()
        if (archiveMonths <= 0) return buildBackupJson(data) to null

        val cutoffMillis = java.time.ZonedDateTime.now()
            .minusMonths(archiveMonths.toLong())
            .toInstant()
            .toEpochMilli()
        val (oldTasks, recentTasks) = data.tasks.partition { it.done && (it.completedAt ?: Long.MAX_VALUE) < cutoffMillis }
        if (oldTasks.isEmpty()) return buildBackupJson(data) to null

        val oldTaskIds = oldTasks.map { it.id }.toSet()
        val (oldComments, recentComments) = data.comments.partition { it.taskId in oldTaskIds }

        val primary = buildBackupJson(data.copy(tasks = recentTasks, comments = recentComments))
        val archive = JSONObject().apply {
            put("version", 4)
            put("archive", true)
            put("tasks", taskListToJson(oldTasks))
            put("comments", commentListToJson(oldComments))
        }
        return primary to archive
    }

    private fun buildBackupJson(data: BackupData): JSONObject {
            val people = data.people
            val projects = data.projects
            val lists = data.lists
            val tags = data.tags
            val tasks = data.tasks
            val tagGroups = data.tagGroups
            val personGroups = data.personGroups
            val comments = data.comments

            val root = JSONObject()
            root.put("version", 4)

            // People
            val peopleArr = JSONArray()
            people.forEach { p ->
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("initials", p.initials)
                o.put("color", p.color)
                o.put("photoUri", p.photoUri ?: JSONObject.NULL)
                o.put("isMe", p.isMe)
                o.put("groupId", p.groupId ?: JSONObject.NULL)
                o.put("starred", p.starred)
                o.put("archived", p.archived)
                peopleArr.put(o)
            }
            root.put("people", peopleArr)

            // Person groups
            val personGroupsArr = JSONArray()
            personGroups.forEach { g ->
                val o = JSONObject()
                o.put("id", g.id)
                o.put("name", g.name)
                o.put("color", g.color)
                personGroupsArr.put(o)
            }
            root.put("personGroups", personGroupsArr)

            // Projects
            val projectsArr = JSONArray()
            projects.forEach { pr ->
                val o = JSONObject()
                o.put("id", pr.id)
                o.put("name", pr.name)
                o.put("color", pr.color)
                o.put("icon", pr.icon)
                o.put("due", pr.due)
                o.put("starred", pr.starred)
                o.put("defaultReminder", pr.defaultReminder ?: JSONObject.NULL)
                o.put("description", pr.description ?: JSONObject.NULL)
                o.put("excludeFromToday", pr.excludeFromToday)
                o.put("archived", pr.archived)
                val commonTagIdsArr = JSONArray()
                pr.commonTagIds.forEach { commonTagIdsArr.put(it) }
                o.put("commonTagIds", commonTagIdsArr)
                projectsArr.put(o)
            }
            root.put("projects", projectsArr)

            // Lists
            val listsArr = JSONArray()
            lists.forEach { l ->
                val o = JSONObject()
                o.put("id", l.id)
                o.put("name", l.name)
                o.put("color", l.color)
                o.put("icon", l.icon)
                o.put("starred", l.starred)
                o.put("excludeFromToday", l.excludeFromToday)
                o.put("archived", l.archived)
                listsArr.put(o)
            }
            root.put("lists", listsArr)

            // Tags
            val tagsArr = JSONArray()
            tags.forEach { t ->
                val o = JSONObject()
                o.put("id", t.id)
                o.put("name", t.name)
                o.put("color", t.color)
                o.put("groupId", t.groupId ?: JSONObject.NULL)
                o.put("starred", t.starred)
                o.put("hideCompletedByDefault", t.hideCompletedByDefault)
                tagsArr.put(o)
            }
            root.put("tags", tagsArr)

            // Tag groups
            val tagGroupsArr = JSONArray()
            tagGroups.forEach { g ->
                val o = JSONObject()
                o.put("id", g.id)
                o.put("name", g.name)
                o.put("color", g.color)
                tagGroupsArr.put(o)
            }
            root.put("tagGroups", tagGroupsArr)

            // Tasks
            root.put("tasks", taskListToJson(tasks))

            // Comments
            root.put("comments", commentListToJson(comments))

            return root
    }

    private fun taskListToJson(tasks: List<Task>): JSONArray {
        val tasksArr = JSONArray()
        tasks.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("title", t.title)
            o.put("listId", t.listId ?: JSONObject.NULL)
            o.put("projectId", t.projectId ?: JSONObject.NULL)
            o.put("section", t.section)
            o.put("due", t.due)
            o.put("time", t.time)
            o.put("reminder", t.reminder)
            o.put("priority", t.priority)
            o.put("flag", t.flag)
            o.put("done", t.done)
            o.put("completedAt", t.completedAt ?: JSONObject.NULL)
            o.put("notes", t.notes)
            o.put("sortOrder", t.sortOrder)

            // Assignees
            val assArr = JSONArray()
            t.assigneeIds.forEach { assArr.put(it) }
            o.put("assigneeIds", assArr)

            // Tags
            val tagIdsArr = JSONArray()
            t.tagIds.forEach { tagIdsArr.put(it) }
            o.put("tagIds", tagIdsArr)

            // Recurrence
            val r = t.recurrence
            if (r != null) {
                val ro = JSONObject()
                ro.put("freq", r.freq)
                ro.put("interval", r.interval)
                if (r.byday != null) {
                    val bydayArr = JSONArray()
                    r.byday.forEach { bydayArr.put(it) }
                    ro.put("byday", bydayArr)
                }
                if (r.bymonthday != null) {
                    ro.put("bymonthday", r.bymonthday)
                }
                val endsObj = JSONObject()
                when (val ends = r.ends) {
                    is RecurrenceEnds.Never -> endsObj.put("type", "never")
                    is RecurrenceEnds.After -> {
                        endsObj.put("type", "after")
                        endsObj.put("count", ends.count)
                    }
                    is RecurrenceEnds.On -> {
                        endsObj.put("type", "on")
                        endsObj.put("date", ends.date)
                    }
                }
                ro.put("ends", endsObj)
                o.put("recurrence", ro)
            }

            // Subtasks
            val stArr = JSONArray()
            t.subtasks.forEach { st ->
                val sto = JSONObject()
                sto.put("id", st.id)
                sto.put("title", st.title)
                sto.put("done", st.done)
                sto.put("parentSubtaskId", st.parentSubtaskId ?: JSONObject.NULL)
                sto.put("sortOrder", st.sortOrder)
                stArr.put(sto)
            }
            o.put("subtasks", stArr)

            tasksArr.put(o)
        }
        return tasksArr
    }

    private fun commentListToJson(comments: List<TaskComment>): JSONArray {
        val commentsArr = JSONArray()
        comments.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("taskId", c.taskId)
            o.put("body", c.body)
            o.put("createdAt", c.createdAt)
            o.put("authorId", c.authorId ?: JSONObject.NULL)
            commentsArr.put(o)
        }
        return commentsArr
    }

    /**
     * Auto-backs up to the public Downloads folder without a file picker (used before a
     * destructive "delete all data" action). Returns the saved filename, or null on failure.
     */
    suspend fun exportToDownloads(): String? {
        val filename = "yata_backup_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) +
            ".json"

        val uri: Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
        } else {
            // Pre-scoped-storage (API 26-28) needs the legacy write permission at runtime.
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            Uri.fromFile(java.io.File(downloadsDir, filename))
        }

        val ok = exportData(uri)

        if (ok && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }

        return if (ok) filename else null
    }

    suspend fun importData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                }
            }
            importJson(JSONObject(sb.toString()))
        } catch (e: Exception) {
            Log.e("JsonExporter", "importData failed", e)
            false
        }
    }

    /** Restores from raw JSON bytes — used for cloud restore, where there's no [Uri] to read
     * through a [android.content.ContentResolver]. */
    suspend fun importBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            importJson(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (e: Exception) {
            Log.e("JsonExporter", "importBytes failed", e)
            false
        }
    }

    private suspend fun importJson(root: JSONObject): Boolean {
            var skippedRows = 0

            /** Runs [block] for row [i] of [label], logging and skipping just that row (instead
             * of aborting the whole restore) if it throws — a single malformed/corrupted row
             * used to take down the entire import, leaving the DB half-restored with no way to
             * tell which rows landed. */
            suspend fun importRow(label: String, i: Int, block: suspend () -> Unit) {
                try {
                    block()
                } catch (e: Exception) {
                    skippedRows++
                    Log.w("JsonExporter", "importData: skipping malformed $label row $i", e)
                }
            }

            // 1. Import Person groups (must exist before people reference them)
            val personGroupsArr = root.optJSONArray("personGroups")
            if (personGroupsArr != null) {
                for (i in 0 until personGroupsArr.length()) {
                    importRow("personGroup", i) {
                        val o = personGroupsArr.getJSONObject(i)
                        repository.upsertPersonGroup(
                            PersonGroup(id = o.getString("id"), name = o.getString("name"), color = o.getString("color"))
                        )
                    }
                }
            }

            // 1b. Import People
            val peopleArr = root.optJSONArray("people")
            if (peopleArr != null) {
                for (i in 0 until peopleArr.length()) {
                    importRow("person", i) {
                        val o = peopleArr.getJSONObject(i)
                        repository.upsertPerson(
                            Person(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                initials = o.getString("initials"),
                                color = o.getString("color"),
                                photoUri = if (o.isNull("photoUri")) null else o.optString("photoUri"),
                                isMe = o.optBoolean("isMe", false),
                                groupId = if (o.isNull("groupId")) null else o.optString("groupId", null),
                                starred = o.optBoolean("starred", false),
                                archived = o.optBoolean("archived", false)
                            )
                        )
                    }
                }
            }

            // 2. Import Projects
            val projectsArr = root.optJSONArray("projects")
            if (projectsArr != null) {
                for (i in 0 until projectsArr.length()) {
                    importRow("project", i) {
                        val o = projectsArr.getJSONObject(i)
                        val commonTagIdsArr = o.optJSONArray("commonTagIds")
                        val commonTagIds = mutableListOf<String>()
                        if (commonTagIdsArr != null) {
                            for (j in 0 until commonTagIdsArr.length()) {
                                commonTagIds.add(commonTagIdsArr.getString(j))
                            }
                        }
                        repository.upsertProject(
                            Project(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                color = o.getString("color"),
                                icon = o.getString("icon"),
                                due = if (o.isNull("due")) null else o.optString("due"),
                                starred = o.optBoolean("starred", false),
                                commonTagIds = commonTagIds,
                                defaultReminder = if (o.isNull("defaultReminder")) null else o.optString("defaultReminder", null),
                                description = if (o.isNull("description")) null else o.optString("description", null),
                                excludeFromToday = o.optBoolean("excludeFromToday", false),
                                archived = o.optBoolean("archived", false)
                            )
                        )
                    }
                }
            }

            // 3. Import Lists
            val listsArr = root.optJSONArray("lists")
            if (listsArr != null) {
                for (i in 0 until listsArr.length()) {
                    importRow("list", i) {
                        val o = listsArr.getJSONObject(i)
                        repository.upsertList(
                            YataList(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                color = o.getString("color"),
                                icon = o.getString("icon"),
                                starred = o.optBoolean("starred", false),
                                excludeFromToday = o.optBoolean("excludeFromToday", false),
                                archived = o.optBoolean("archived", false)
                            )
                        )
                    }
                }
            }

            // 4. Import Tag groups (must exist before tags reference them)
            val tagGroupsArr = root.optJSONArray("tagGroups")
            if (tagGroupsArr != null) {
                for (i in 0 until tagGroupsArr.length()) {
                    importRow("tagGroup", i) {
                        val o = tagGroupsArr.getJSONObject(i)
                        repository.upsertTagGroup(
                            TagGroup(id = o.getString("id"), name = o.getString("name"), color = o.getString("color"))
                        )
                    }
                }
            }

            // 4b. Import Tags
            val tagsArr = root.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    importRow("tag", i) {
                        val o = tagsArr.getJSONObject(i)
                        repository.upsertTag(
                            Tag(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                color = o.getString("color"),
                                groupId = if (o.isNull("groupId")) null else o.optString("groupId", null),
                                starred = o.optBoolean("starred", false),
                                hideCompletedByDefault = o.optBoolean("hideCompletedByDefault", false)
                            )
                        )
                    }
                }
            }

            // 5. Import Tasks
            val tasksArr = root.optJSONArray("tasks")
            if (tasksArr != null) {
                for (i in 0 until tasksArr.length()) {
                    importRow("task", i) {
                        val o = tasksArr.getJSONObject(i)

                        val assArr = o.getJSONArray("assigneeIds")
                        val assigneeIds = mutableListOf<String>()
                        for (j in 0 until assArr.length()) {
                            assigneeIds.add(assArr.getString(j))
                        }

                        val tagIdsArr = o.getJSONArray("tagIds")
                        val tagIds = mutableListOf<String>()
                        for (j in 0 until tagIdsArr.length()) {
                            tagIds.add(tagIdsArr.getString(j))
                        }

                        // A malformed recurrence object degrades this one task to non-recurring
                        // rather than aborting the whole row (matching Mappers.deserializeRecurrence's
                        // behavior for live data) — losing "repeats weekly" is recoverable by hand;
                        // losing the entire rest of the restore over it is not.
                        val recObj = o.optJSONObject("recurrence")
                        val recurrence = if (recObj != null) {
                            try {
                                val bydayArr = recObj.optJSONArray("byday")
                                val byday = if (bydayArr != null) {
                                    val l = mutableListOf<String>()
                                    for (k in 0 until bydayArr.length()) {
                                        l.add(bydayArr.getString(k))
                                    }
                                    l
                                } else null

                                val endsObj = recObj.getJSONObject("ends")
                                val endsType = endsObj.getString("type")
                                val ends = when (endsType) {
                                    "after" -> RecurrenceEnds.After(endsObj.getInt("count"))
                                    "on" -> RecurrenceEnds.On(endsObj.getString("date"))
                                    else -> RecurrenceEnds.Never
                                }

                                Recurrence(
                                    freq = recObj.getString("freq"),
                                    interval = recObj.getInt("interval"),
                                    byday = byday,
                                    bymonthday = if (recObj.has("bymonthday")) recObj.getInt("bymonthday") else null,
                                    ends = ends
                                )
                            } catch (e: Exception) {
                                Log.w("JsonExporter", "importData: dropping malformed recurrence on task row $i", e)
                                null
                            }
                        } else null

                        val stArr = o.getJSONArray("subtasks")
                        val subtasks = mutableListOf<Subtask>()
                        for (j in 0 until stArr.length()) {
                            val sto = stArr.getJSONObject(j)
                            subtasks.add(
                                Subtask(
                                    id = sto.getString("id"),
                                    title = sto.getString("title"),
                                    done = sto.getBoolean("done"),
                                    parentSubtaskId = if (sto.isNull("parentSubtaskId")) null else sto.optString("parentSubtaskId", null),
                                    sortOrder = sto.optInt("sortOrder", j)
                                )
                            )
                        }

                        repository.upsertTask(
                            Task(
                                id = o.getString("id"),
                                title = o.getString("title"),
                                listId = if (o.isNull("listId")) null else o.optString("listId", null),
                                projectId = if (o.isNull("projectId")) null else o.optString("projectId", null),
                                section = o.getString("section"),
                                due = if (o.isNull("due")) null else o.optString("due"),
                                time = if (o.isNull("time")) null else o.optString("time"),
                                reminder = if (o.isNull("reminder")) null else o.optString("reminder"),
                                priority = o.getString("priority"),
                                flag = o.optBoolean("flag", false),
                                done = o.optBoolean("done", false),
                                completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
                                assigneeIds = assigneeIds,
                                tagIds = tagIds,
                                recurrence = recurrence,
                                subtasks = subtasks,
                                notes = if (o.isNull("notes")) null else o.optString("notes"),
                                sortOrder = o.optInt("sortOrder", i)
                            )
                        )
                    }
                }
            }

            // 6. Import Comments (after tasks so the taskId foreign key exists)
            val commentsArr = root.optJSONArray("comments")
            if (commentsArr != null) {
                for (i in 0 until commentsArr.length()) {
                    importRow("comment", i) {
                        val o = commentsArr.getJSONObject(i)
                        repository.upsertComment(
                            TaskComment(
                                id = o.getString("id"),
                                taskId = o.getString("taskId"),
                                body = o.getString("body"),
                                createdAt = o.getLong("createdAt"),
                                authorId = if (o.isNull("authorId")) null else o.optString("authorId", null)
                            )
                        )
                    }
                }
            }
            if (skippedRows > 0) {
                Log.w("JsonExporter", "importJson: completed with $skippedRows malformed row(s) skipped")
            }
            return true
    }
}
