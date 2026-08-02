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

internal const val CURRENT_BACKUP_VERSION = 4

/** Rejects arbitrary JSON before restore mutates any state. Every YATA backup, including an
 * archive-only payload and a legitimately empty database, contains a version and a tasks array. */
internal fun isRecognizedBackup(root: JSONObject): Boolean =
    root.optInt("version", -1) in 1..CURRENT_BACKUP_VERSION &&
        root.optJSONArray("tasks") != null

@Singleton
class JsonExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YataRepository,
    private val userPreferences: com.mj.yata.data.local.datastore.UserPreferences
) {
    /**
     * Photos are stored as file:// Uris into the app's own filesDir (see ProfilePhotoUtils), so
     * the Uri string alone is worthless in a backup — after a reinstall or on a different device
     * the path doesn't exist and the avatar silently falls back to initials. The image bytes
     * therefore travel with the backup, base64-encoded, and are rewritten to fresh files on
     * restore. The Uri is still exported alongside, purely so a same-device restore that predates
     * this field keeps working.
     */
    private fun encodePhoto(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            // Strip the cache-busting ?t= query param saveCircularProfilePhoto appends.
            val path = Uri.parse(uriString).path ?: return null
            val file = java.io.File(path)
            if (!file.exists()) return null
            android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w("JsonExporter", "Could not read photo for backup: $uriString", e)
            null
        }
    }

    private fun decodePhotoToAvatarFile(base64: String?): Uri? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            val dir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
            val file = java.io.File(dir, "avatar_restored_${java.util.UUID.randomUUID()}.png")
            file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w("JsonExporter", "Could not restore person photo", e)
            null
        }
    }

    /** The user's own photo lives at a single fixed filename, unlike per-person avatars. */
    private fun decodeProfilePhoto(base64: String?): Uri? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            val file = java.io.File(context.filesDir, "profile_photo.png")
            file.writeBytes(bytes)
            // Cache-busting param so avatar composables keyed on the Uri string reload it.
            Uri.fromFile(file).buildUpon()
                .appendQueryParameter("t", System.currentTimeMillis().toString())
                .build()
        } catch (e: Exception) {
            Log.w("JsonExporter", "Could not restore profile photo", e)
            null
        }
    }
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
        val comments: List<TaskComment>,
        /** The user's own avatar, base64-encoded. Null when none is set or the file is gone. */
        val profilePhoto: String?,
        /** The user's own name and email. Like the avatar these live in DataStore, not the
         * database, so they are not part of any entity list. Blank when never set. */
        val profileName: String,
        val profileEmail: String,
        /** App settings from DataStore — theme, feature flags, task defaults, notification prefs. */
        val settings: List<com.mj.yata.data.local.datastore.PortableSetting>
    )

    private suspend fun loadBackupData(): BackupData = BackupData(
        people = repository.getPeople().first(),
        personGroups = repository.getPersonGroups().first(),
        projects = repository.getProjects().first(),
        lists = repository.getLists().first(),
        tags = repository.getTags().first(),
        tagGroups = repository.getTagGroups().first(),
        // Archived tasks must be included explicitly: getTasks() excludes them by design, and a
        // backup that silently omitted them would lose them outright on restore — and worse, the
        // export-then-wipe path (backupThenDeleteAllData) would delete them after writing a
        // backup that never contained them. Trash (deletedAt) is still deliberately excluded.
        tasks = repository.getTasks().first() + repository.getArchivedTasks().first(),
        comments = repository.getAllComments().first(),
        profilePhoto = encodePhoto(userPreferences.userPhotoUriFlow.first()),
        profileName = userPreferences.userNameFlow.first(),
        profileEmail = userPreferences.userEmailFlow.first(),
        settings = userPreferences.exportPortableSettings()
    )

    suspend fun exportData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = buildBackupJson(loadBackupData())
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext false
            outputStream.use { os ->
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
            put("version", CURRENT_BACKUP_VERSION)
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
            root.put("version", CURRENT_BACKUP_VERSION)
            // The user's own profile — avatar, name, email. All three live in DataStore rather
            // than the database, so they are not part of any entity list and have to be carried
            // at the root. Blank name/email are omitted rather than written as "", so restoring a
            // backup taken before the profile was filled in can't blank out a name set since.
            data.profilePhoto?.let { root.put("profilePhoto", it) }
            data.profileName.takeIf { it.isNotBlank() }?.let { root.put("profileName", it) }
            data.profileEmail.takeIf { it.isNotBlank() }?.let { root.put("profileEmail", it) }

            // Settings live in DataStore, not the database, so like the profile they have to be
            // carried explicitly. Without this a restore rebuilt every task and left the user on
            // default theme, default task settings and every feature flag back on.
            if (data.settings.isNotEmpty()) {
                val settingsArr = JSONArray()
                data.settings.forEach { setting ->
                    settingsArr.put(JSONObject().apply {
                        put("name", setting.name)
                        put("type", setting.type)
                        put("value", when (val v = setting.value) {
                            is Set<*> -> JSONArray().also { arr -> v.forEach { arr.put(it) } }
                            else -> v
                        })
                    })
                }
                root.put("settings", settingsArr)
            }

            // People
            val peopleArr = JSONArray()
            people.forEach { p ->
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("initials", p.initials)
                o.put("color", p.color)
                o.put("photoUri", p.photoUri ?: JSONObject.NULL)
                // The bytes, not just the path — see encodePhoto. Absent for people with no
                // avatar, so backups don't carry empty keys for most rows.
                encodePhoto(p.photoUri)?.let { o.put("photoData", it) }
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
                val sectionNamesArr = JSONArray()
                pr.sectionNames.forEach { sectionNamesArr.put(it) }
                o.put("sectionNames", sectionNamesArr)
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
            o.put("startDate", t.startDate)
            o.put("time", t.time)
            o.put("reminder", t.reminder)
            o.put("priority", t.priority)
            o.put("flag", t.flag)
            o.put("done", t.done)
            o.put("completedAt", t.completedAt ?: JSONObject.NULL)
            o.put("createdAt", t.createdAt ?: JSONObject.NULL)
            o.put("notes", t.notes)
            o.put("sortOrder", t.sortOrder)
            o.put("archived", t.archived)
            o.put("followUpAt", t.followUpAt ?: JSONObject.NULL)
            o.put("estimateMinutes", t.estimateMinutes ?: JSONObject.NULL)

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

    /**
     * Counts what a backup holds without importing it. Lives here rather than in the backup
     * managers because this class owns the payload's shape — the key names below are the same
     * ones [importJson] reads, and they only have to stay in step in one place.
     */
    fun summarise(bytes: ByteArray): BackupSummary {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        require(isRecognizedBackup(root)) { "File is not a recognized YATA backup" }
        val tasks = root.optJSONArray("tasks")
        val totalTasks = tasks?.length() ?: 0
        var openTasks = 0
        for (i in 0 until totalTasks) {
            if (tasks?.optJSONObject(i)?.optBoolean("done", false) == false) openTasks++
        }
        return BackupSummary(
            totalTasks = totalTasks,
            openTasks = openTasks,
            totalProjects = root.optJSONArray("projects")?.length() ?: 0
        )
    }

    private suspend fun importJson(root: JSONObject): Boolean {
            if (!isRecognizedBackup(root)) {
                Log.w("JsonExporter", "importJson: unrecognized or unsupported backup payload")
                return false
            }
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

            // 0. The user's own profile. The avatar is written back to filesDir and re-pointed in
            // DataStore; name and email go straight to DataStore. Each only overwrites when the
            // backup actually carries it, so restoring an older backup — one written before these
            // fields existed, or before the user filled them in — can't wipe a value set since.
            root.optString("profilePhoto", null)?.let { encoded ->
                decodeProfilePhoto(encoded)?.let { uri ->
                    val uriString = uri.toString()
                    userPreferences.setUserPhotoUri(uriString)
                    // Keeps the "me" Person's avatar (assignee stacks, PersonDetailScreen, ...) in
                    // sync immediately — MainViewModel.setUserPhotoUri normally does this, but a
                    // restore writes straight to DataStore and would otherwise leave the Person
                    // row stale until the next app start.
                    repository.getPeople().first().find { it.isMe }?.let { me ->
                        repository.upsertPerson(me.copy(photoUri = uriString))
                    }
                }
            }
            root.optString("profileName", null)?.takeIf { it.isNotBlank() }?.let {
                userPreferences.setUserName(it)
            }
            root.optString("profileEmail", null)?.takeIf { it.isNotBlank() }?.let {
                userPreferences.setUserEmail(it)
            }

            // Settings, applied before the entity rows below so that anything reading a preference
            // during the restore (feature flags, defaults) already sees the restored value.
            // Absent in any backup written before this existed, which simply leaves the current
            // settings alone.
            root.optJSONArray("settings")?.let { arr ->
                val restored = mutableListOf<com.mj.yata.data.local.datastore.PortableSetting>()
                for (i in 0 until arr.length()) {
                    // Per-row guard, matching importRow: one malformed setting must not cost the
                    // other seventy-odd.
                    try {
                        val o = arr.getJSONObject(i)
                        val name = o.getString("name")
                        val type = o.getString("type")
                        val raw = o.get("value")
                        val value: Any = if (raw is JSONArray) {
                            (0 until raw.length()).mapNotNull { raw.optString(it, null) }.toSet()
                        } else raw
                        restored.add(com.mj.yata.data.local.datastore.PortableSetting(name, type, value))
                    } catch (e: Exception) {
                        Log.w("JsonExporter", "importData: skipping malformed setting row $i", e)
                    }
                }
                userPreferences.importPortableSettings(restored)
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
                                // Prefer the embedded bytes, rewritten to a fresh local file.
                                // The stored photoUri only survives a same-device restore where
                                // that exact path still exists; on a new device or after a
                                // reinstall it is a dangling reference that silently degrades to
                                // initials. Backups written before photoData existed still fall
                                // back to it, which is no worse than before.
                                photoUri = decodePhotoToAvatarFile(o.optString("photoData", null))?.toString()
                                    ?: if (o.isNull("photoUri")) null else o.optString("photoUri"),
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
                        // Absent in backups written before sections existed — an empty list is
                        // exactly right there, since that project simply had no sections.
                        val sectionNamesArr = o.optJSONArray("sectionNames")
                        val sectionNames = mutableListOf<String>()
                        if (sectionNamesArr != null) {
                            for (j in 0 until sectionNamesArr.length()) {
                                sectionNames.add(sectionNamesArr.getString(j))
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
                                archived = o.optBoolean("archived", false),
                                sectionNames = sectionNames
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

            // 5. Import Tasks — collected into one list and written with a single upsertTasks()
            // call after the loop, instead of one upsertTask() (and one DB transaction, one
            // reminder-default-time DataStore read) per row.
            val tasksArr = root.optJSONArray("tasks")
            val tasksToImport = mutableListOf<Task>()
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

                        tasksToImport.add(
                            Task(
                                id = o.getString("id"),
                                title = o.getString("title"),
                                listId = if (o.isNull("listId")) null else o.optString("listId", null),
                                projectId = if (o.isNull("projectId")) null else o.optString("projectId", null),
                                section = o.getString("section"),
                                due = if (o.isNull("due")) null else o.optString("due"),
                                // Absent in any backup written before start dates existed, which
                                // reads as null — "available now", the behaviour those tasks had.
                                startDate = if (o.isNull("startDate")) null else o.optString("startDate", null),
                                time = if (o.isNull("time")) null else o.optString("time"),
                                reminder = if (o.isNull("reminder")) null else o.optString("reminder"),
                                priority = o.getString("priority"),
                                flag = o.optBoolean("flag", false),
                                done = o.optBoolean("done", false),
                                completedAt = if (o.isNull("completedAt")) null else o.optLong("completedAt"),
                                // Absent in backups written before DB 27 — stays null there, and
                                // the upsert then treats the restored task as created now.
                                createdAt = if (o.isNull("createdAt")) null else o.optLong("createdAt"),
                                assigneeIds = assigneeIds,
                                tagIds = tagIds,
                                recurrence = recurrence,
                                subtasks = subtasks,
                                notes = if (o.isNull("notes")) null else o.optString("notes"),
                                sortOrder = o.optInt("sortOrder", i),
                                // Absent in backups written before task archiving existed —
                                // those restore as un-archived, which is the correct reading.
                                archived = o.optBoolean("archived", false),
                                // Absent before waiting-on dates existed — null means "no
                                // follow-up set", so the task stays visible in Today as it did.
                                followUpAt = if (o.isNull("followUpAt")) null else o.optLong("followUpAt"),
                                // Null rather than 0 when absent: unestimated has to stay
                                // distinguishable from "estimated at zero minutes", or every old
                                // task would count toward a day's planned total as a real zero.
                                estimateMinutes = if (o.isNull("estimateMinutes")) null else o.optInt("estimateMinutes")
                            )
                        )
                    }
                }
            }
            if (tasksToImport.isNotEmpty()) {
                repository.upsertTasks(tasksToImport, notify = true, resyncReminder = true)
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
            // Callers must not announce a successful restore when any requested data was lost.
            // Successfully imported rows remain available, but the failure result makes the
            // partial restore explicit instead of silently presenting it as complete.
            return skippedRows == 0
    }
}
