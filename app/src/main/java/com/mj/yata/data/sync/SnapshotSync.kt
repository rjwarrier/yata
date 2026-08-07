package com.mj.yata.data.sync

import android.content.Context
import android.util.Log
import com.mj.yata.data.local.backup.RecoveryBackupManager
import com.mj.yata.util.CURRENT_BACKUP_VERSION
import com.mj.yata.util.JsonExporter
import com.mj.yata.util.isRecognizedBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server stores one canonical snapshot; each device stores the last canonical snapshot it
 * successfully observed. That makes a three-way merge possible without adding updatedAt columns
 * to every Room table or running any new service on the user's server.
 *
 * A record changed on only one side wins. Independent additions/edits/deletions are combined. If
 * the same record was changed concurrently on both devices, the already-published server version
 * wins. This is intentionally record-level (a task and its subtasks are one record): it is small,
 * deterministic, and prevents an older device from repeatedly resurrecting its stale copy.
 */
internal object SnapshotMerger {
    private const val SYNC_FORMAT_VERSION = 1

    private val keyedCollections = linkedMapOf(
        "people" to "id",
        "personGroups" to "id",
        "projects" to "id",
        "lists" to "id",
        "tags" to "id",
        "tagGroups" to "id",
        "tasks" to "id",
        "comments" to "id",
        "settings" to "name"
    )

    private object Missing

    data class ConflictRecord(
        val path: String,
        val collection: String?,
        val id: String?,
        val base: Any,
        val local: Any,
        val remote: Any
    )

    data class MergeResult(
        val json: JSONObject,
        val conflicts: Int,
        val conflictRecords: List<ConflictRecord> = emptyList()
    )

    fun merge(base: JSONObject?, local: JSONObject, remote: JSONObject?): MergeResult {
        if (remote == null) {
            return MergeResult(deepCopy(local).apply {
                put("version", CURRENT_BACKUP_VERSION)
                put("syncVersion", SYNC_FORMAT_VERSION)
            }, conflicts = 0)
        }

        val merged = JSONObject()
        var conflicts = 0
        val conflictRecords = mutableListOf<ConflictRecord>()

        val scalarKeys = allKeys(base, local, remote)
            .filterNot { it in keyedCollections || it == "version" || it == "syncVersion" }
            .sorted()
        scalarKeys.forEach { key ->
            val decision = mergeValue(
                base = valueAt(base, key),
                local = valueAt(local, key),
                remote = valueAt(remote, key),
                initialJoin = base == null
            )
            if (decision.conflict) {
                conflicts++
                conflictRecords += ConflictRecord(
                    path = key,
                    collection = null,
                    id = null,
                    base = valueAt(base, key),
                    local = valueAt(local, key),
                    remote = valueAt(remote, key)
                )
            }
            if (decision.value !== Missing) merged.put(key, deepCopyValue(decision.value))
        }

        keyedCollections.forEach { (collection, idKey) ->
            val baseRows = rowsById(base?.optJSONArray(collection), idKey)
            val localRows = rowsById(local.optJSONArray(collection), idKey)
            val remoteRows = rowsById(remote.optJSONArray(collection), idKey)
            val ids = (baseRows.keys + localRows.keys + remoteRows.keys).toSortedSet()
            val out = JSONArray()
            ids.forEach { id ->
                val baseRow = baseRows[id] ?: Missing
                val localRow = localRows[id] ?: Missing
                val remoteRow = remoteRows[id] ?: Missing
                val rowMerge = mergeRecord(
                    path = "$collection/$id",
                    collection = collection,
                    id = id,
                    base = baseRow,
                    local = localRow,
                    remote = remoteRow,
                    initialJoin = base == null
                )
                conflicts += rowMerge.conflicts
                conflictRecords += rowMerge.conflictRecords
                if (rowMerge.value !== Missing) {
                    out.put(deepCopyValue(rowMerge.value))
                }
            }
            merged.put(collection, out)
        }

        merged.put("version", CURRENT_BACKUP_VERSION)
        merged.put("syncVersion", SYNC_FORMAT_VERSION)
        repairReferences(merged)
        return MergeResult(merged, conflicts, conflictRecords)
    }

    fun conflictRecordsJson(records: List<ConflictRecord>): JSONArray = JSONArray().apply {
        records.forEach { record ->
            put(
                JSONObject()
                    .put("path", record.path)
                    .put("collection", record.collection ?: JSONObject.NULL)
                    .put("id", record.id ?: JSONObject.NULL)
                    .put("base", conflictValueJson(record.base))
                    .put("local", conflictValueJson(record.local))
                    .put("remote", conflictValueJson(record.remote))
            )
        }
    }

    /** Removes settings that must remain tied to one installation/connection. */
    fun normalizeForSync(source: JSONObject): JSONObject {
        require(isRecognizedBackup(source)) { "Not a recognized YATA snapshot" }
        if (source.has("syncVersion")) {
            require(source.optInt("syncVersion", -1) == SYNC_FORMAT_VERSION) {
                "Unsupported sync snapshot version"
            }
        }
        keyedCollections.keys.forEach { key ->
            require(!source.has(key) || source.get(key) is JSONArray) {
                "Sync collection $key is not an array"
            }
        }
        val normalized = deepCopy(source)
        normalized.remove("backupMetadata")
        val settings = normalized.optJSONArray("settings")
        if (settings != null) {
            val portable = JSONArray()
            for (i in 0 until settings.length()) {
                val row = settings.optJSONObject(i)
                    ?: throw IllegalArgumentException("Setting row $i is not an object")
                if (!isDeviceLocalSetting(row.optString("name"))) {
                    val copy = deepCopy(row)
                    if (copy.optString("type") == "stringSet") {
                        copy.optJSONArray("value")?.let { values ->
                            val sorted = (0 until values.length())
                                .map { values.getString(it) }
                                .sorted()
                            copy.put("value", JSONArray(sorted))
                        }
                    }
                    portable.put(copy)
                }
            }
            normalized.put("settings", portable)
        }
        normalized.optJSONArray("people")?.let { people ->
            for (i in 0 until people.length()) {
                // A restored avatar is written to a fresh random local path. The embedded bytes
                // are portable; comparing that installation-specific Uri causes false conflicts.
                people.optJSONObject(i)?.remove("photoUri")
            }
        }
        normalized.optJSONArray("tasks")?.let { tasks ->
            for (i in 0 until tasks.length()) {
                val task = tasks.optJSONObject(i) ?: continue
                task.optJSONArray("tagIds")?.let { ids ->
                    task.put("tagIds", JSONArray((0 until ids.length()).map { ids.getString(it) }.sorted()))
                }
                task.optJSONArray("assigneeIds")?.let { ids ->
                    val ordered = (0 until ids.length()).map { ids.getString(it) }
                    task.put(
                        "assigneeIds",
                        JSONArray(
                            ordered.firstOrNull()?.let { owner ->
                                listOf(owner) + ordered.drop(1).sorted()
                            } ?: emptyList<String>()
                        )
                    )
                }
                normalizeSubtasks(task)
            }
        }
        // Every collection is explicit in a canonical sync snapshot. This lets an empty array
        // mean "delete all records" rather than "an old backup did not carry this section".
        keyedCollections.forEach { (key, idKey) ->
            val array = normalized.optJSONArray(key)
            if (array == null) {
                normalized.put(key, JSONArray())
            } else {
                val rows = rowsById(array, idKey)
                normalized.put(key, JSONArray().apply {
                    rows.toSortedMap().values.forEach(::put)
                })
            }
        }
        normalized.put("version", CURRENT_BACKUP_VERSION)
        normalized.put("syncVersion", SYNC_FORMAT_VERSION)
        return normalized
    }

    fun equivalent(left: JSONObject, right: JSONObject): Boolean =
        canonical(left) == canonical(right)

    internal fun isDeviceLocalSetting(name: String): Boolean =
        name == "backup_interval_minutes" ||
            name == "remote_backup_protocol" ||
            name == "ftp_use_tls" ||
            name.startsWith("app_lock_") ||
            name.startsWith("github_") ||
            name.startsWith("sftp_") ||
            name.startsWith("cloud_backup_") ||
            name.startsWith("local_backup_")

    private data class Decision(val value: Any, val conflict: Boolean)

    private data class RecordMerge(
        val value: Any,
        val conflicts: Int,
        val conflictRecords: List<ConflictRecord>
    )

    private fun mergeRecord(
        path: String,
        collection: String,
        id: String,
        base: Any,
        local: Any,
        remote: Any,
        initialJoin: Boolean
    ): RecordMerge {
        if (!initialJoin && base is JSONObject && local is JSONObject && remote is JSONObject) {
            val merged = JSONObject()
            val conflicts = mutableListOf<ConflictRecord>()
            allKeys(base, local, remote)
                .filterNot { it == "id" || it == "name" }
                .sorted()
                .forEach { key ->
                    val decision = mergeFieldValue(
                        key = key,
                        base = valueAt(base, key),
                        local = valueAt(local, key),
                        remote = valueAt(remote, key),
                        initialJoin = false
                    )
                    if (decision.conflict) {
                        conflicts += ConflictRecord(
                            path = "$path/$key",
                            collection = collection,
                            id = id,
                            base = valueAt(base, key),
                            local = valueAt(local, key),
                            remote = valueAt(remote, key)
                        )
                    }
                    if (decision.value !== Missing) merged.put(key, deepCopyValue(decision.value))
                }
            merged.put(if (collection == "settings") "name" else "id", id)
            return RecordMerge(merged, conflicts.size, conflicts)
        }

        val decision = mergeValue(base, local, remote, initialJoin)
        return RecordMerge(
            value = decision.value,
            conflicts = if (decision.conflict) 1 else 0,
            conflictRecords = if (decision.conflict) {
                listOf(
                    ConflictRecord(
                        path = path,
                        collection = collection,
                        id = id,
                        base = base,
                        local = local,
                        remote = remote
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun mergeFieldValue(
        key: String,
        base: Any,
        local: Any,
        remote: Any,
        initialJoin: Boolean
    ): Decision {
        if (!initialJoin && key in setLikeIdArrayFields) {
            mergeSetLikeIdArray(base, local, remote)?.let { return it }
        }
        if (!initialJoin && key == "assigneeIds") {
            mergeAssigneeIds(base, local, remote)?.let { return it }
        }
        return mergeValue(base, local, remote, initialJoin)
    }

    private fun mergeSetLikeIdArray(base: Any, local: Any, remote: Any): Decision? {
        if (same(local, remote)) return Decision(local, conflict = false)
        if (same(local, base) || same(remote, base)) return null
        val baseIds = (base as? JSONArray)?.stringListOrNull() ?: return null
        val localIds = (local as? JSONArray)?.stringListOrNull() ?: return null
        val remoteIds = (remote as? JSONArray)?.stringListOrNull() ?: return null
        val baseSet = baseIds.toSet()
        val localSet = localIds.toSet()
        val remoteSet = remoteIds.toSet()
        val removed = (baseSet - localSet) + (baseSet - remoteSet)
        val added = (localSet - baseSet) + (remoteSet - baseSet)
        return Decision(JSONArray(((baseSet - removed) + added).sorted()), conflict = false)
    }

    private fun mergeAssigneeIds(base: Any, local: Any, remote: Any): Decision? {
        if (same(local, remote)) return Decision(local, conflict = false)
        if (same(local, base) || same(remote, base)) return null
        val baseIds = (base as? JSONArray)?.stringListOrNull() ?: return null
        val localIds = (local as? JSONArray)?.stringListOrNull() ?: return null
        val remoteIds = (remote as? JSONArray)?.stringListOrNull() ?: return null
        val owner = baseIds.firstOrNull()
        if (owner == null || localIds.firstOrNull() != owner || remoteIds.firstOrNull() != owner) {
            return null
        }
        val baseCollaborators = baseIds.drop(1).toSet()
        val localCollaborators = localIds.drop(1).toSet()
        val remoteCollaborators = remoteIds.drop(1).toSet()
        val removed = (baseCollaborators - localCollaborators) + (baseCollaborators - remoteCollaborators)
        val added = (localCollaborators - baseCollaborators) + (remoteCollaborators - baseCollaborators)
        return Decision(JSONArray(listOf(owner) + ((baseCollaborators - removed) + added).sorted()), conflict = false)
    }

    private fun mergeValue(base: Any, local: Any, remote: Any, initialJoin: Boolean): Decision {
        if (same(local, remote)) return Decision(local, conflict = false)
        if (initialJoin) {
            // A new device may already contain useful local records. Union both sides, but let the
            // established server resolve an ID/property collision (including the seeded "me").
            return when {
                remote === Missing -> Decision(local, conflict = false)
                local === Missing -> Decision(remote, conflict = false)
                else -> Decision(remote, conflict = true)
            }
        }
        if (same(local, base)) return Decision(remote, conflict = false)
        if (same(remote, base)) return Decision(local, conflict = false)
        // Both sides changed (modify/modify or modify/delete). The canonical server side wins so
        // the result is stable when an offline device comes back repeatedly with the same change.
        return Decision(remote, conflict = true)
    }

    private fun valueAt(root: JSONObject?, key: String): Any =
        if (root != null && root.has(key)) root.get(key) else Missing

    private val setLikeIdArrayFields = setOf("tagIds", "commonTagIds")

    private fun JSONArray.stringListOrNull(): List<String>? {
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            out += (get(i) as? String) ?: return null
        }
        return out
    }

    private fun rowsById(array: JSONArray?, idKey: String): Map<String, JSONObject> {
        if (array == null) return emptyMap()
        val rows = linkedMapOf<String, JSONObject>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i)
                ?: throw IllegalArgumentException("Sync collection row $i is not an object")
            val id = row.optString(idKey).takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Sync collection row $i has no $idKey")
            require(rows.put(id, row) == null) { "Duplicate sync record $id" }
        }
        return rows
    }

    /** Makes Room's immediate self-FK safe by placing every parent before its descendants. */
    private fun normalizeSubtasks(task: JSONObject) {
        if (!task.has("subtasks")) {
            task.put("subtasks", JSONArray())
            return
        }
        val source = task.get("subtasks") as? JSONArray
            ?: throw IllegalArgumentException("Task subtasks is not an array")
        val rows = linkedMapOf<String, JSONObject>()
        for (i in 0 until source.length()) {
            val row = source.optJSONObject(i)
                ?: throw IllegalArgumentException("Subtask row $i is not an object")
            val id = row.optString("id").takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Subtask row $i has no id")
            require(rows.put(id, row) == null) { "Duplicate subtask $id" }
        }

        val state = mutableMapOf<String, Int>()
        val ordered = JSONArray()
        fun visit(id: String) {
            when (state[id]) {
                2 -> return
                1 -> throw IllegalArgumentException("Cyclic subtask parent graph at $id")
            }
            state[id] = 1
            val row = rows.getValue(id)
            val rawParent = if (!row.has("parentSubtaskId") || row.isNull("parentSubtaskId")) {
                null
            } else {
                row.get("parentSubtaskId")
            }
            require(rawParent == null || rawParent is String) {
                "Subtask $id has a non-string parent"
            }
            (rawParent as? String)?.let { parentId ->
                require(parentId in rows) { "Subtask $id references missing parent $parentId" }
                visit(parentId)
            }
            state[id] = 2
            ordered.put(row)
        }
        rows.keys
            .sortedWith(compareBy({ rows.getValue(it).optInt("sortOrder", 0) }, { it }))
            .forEach(::visit)
        task.put("subtasks", ordered)
    }

    private fun allKeys(vararg roots: JSONObject?): Set<String> = buildSet {
        roots.filterNotNull().forEach { root ->
            val keys = root.keys()
            while (keys.hasNext()) add(keys.next())
        }
    }

    /** Keeps independently merged parent/child records valid after a delete-vs-edit conflict. */
    private fun repairReferences(root: JSONObject) {
        fun ids(collection: String): Set<String> {
            val array = root.getJSONArray(collection)
            return (0 until array.length()).mapTo(linkedSetOf()) {
                array.getJSONObject(it).getString("id")
            }
        }

        val peopleIds = ids("people")
        val personGroupIds = ids("personGroups")
        val projectIds = ids("projects")
        val listIds = ids("lists")
        val tagIds = ids("tags")
        val tagGroupIds = ids("tagGroups")
        val taskIds = ids("tasks")

        fun JSONObject.clearMissing(key: String, allowed: Set<String>) {
            if (!isNull(key) && optString(key) !in allowed) put(key, JSONObject.NULL)
        }
        fun filterIds(source: JSONArray, allowed: Set<String>): JSONArray = JSONArray().apply {
            for (i in 0 until source.length()) {
                source.optString(i, null)?.takeIf { it in allowed }?.let(::put)
            }
        }

        root.getJSONArray("people").let { rows ->
            for (i in 0 until rows.length()) rows.getJSONObject(i)
                .clearMissing("groupId", personGroupIds)
        }
        root.getJSONArray("tags").let { rows ->
            for (i in 0 until rows.length()) rows.getJSONObject(i)
                .clearMissing("groupId", tagGroupIds)
        }
        root.getJSONArray("projects").let { rows ->
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                row.optJSONArray("commonTagIds")?.let {
                    row.put("commonTagIds", filterIds(it, tagIds))
                }
            }
        }
        root.getJSONArray("tasks").let { rows ->
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                row.clearMissing("listId", listIds)
                row.clearMissing("projectId", projectIds)
                row.put("assigneeIds", filterIds(row.getJSONArray("assigneeIds"), peopleIds))
                row.put("tagIds", filterIds(row.getJSONArray("tagIds"), tagIds))
            }
        }
        val comments = root.getJSONArray("comments")
        val validComments = JSONArray()
        for (i in 0 until comments.length()) {
            val row = comments.getJSONObject(i)
            if (row.optString("taskId") in taskIds) validComments.put(row)
        }
        root.put("comments", validComments)
    }

    private fun same(left: Any, right: Any): Boolean = when {
        left === Missing || right === Missing -> left === right
        else -> canonical(left) == canonical(right)
    }

    private fun canonical(value: Any?): String = when (value) {
        Missing -> "<missing>"
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = mutableListOf<String>()
            val iterator = value.keys()
            while (iterator.hasNext()) keys += iterator.next()
            keys.sorted().joinToString(prefix = "{", postfix = "}") { key ->
                JSONObject.quote(key) + ":" + canonical(value.get(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") {
            canonical(value.get(it))
        }
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun deepCopy(source: JSONObject): JSONObject = JSONObject(source.toString())

    private fun deepCopyValue(value: Any): Any = when (value) {
        is JSONObject -> deepCopy(value)
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }

    private fun conflictValueJson(value: Any): Any = when (value) {
        Missing -> JSONObject.NULL
        is JSONObject -> deepCopy(value)
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }
}

internal data class PreparedSnapshotSync(
    val scopeKey: String,
    val localAtStart: JSONObject,
    val canonical: JSONObject,
    val conflicts: Int,
    val conflictRecords: List<SnapshotMerger.ConflictRecord>,
    val remoteNeedsPublish: Boolean
) {
    val canonicalBytes: ByteArray
        get() = canonical.toString(2).toByteArray(Charsets.UTF_8)
}

class InitialSyncConfirmationRequiredException(
    message: String =
        "This device and the remote sync snapshot both contain data. Review the remote snapshot before first sync so nothing is merged unexpectedly."
) : Exception(message)

@Singleton
class SnapshotSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val recoveryBackupManager: RecoveryBackupManager
) {
    internal suspend fun prepare(
        remoteBytes: ByteArray?,
        scopeKey: String,
        remoteIsRecovery: Boolean = false
    ): PreparedSnapshotSync =
        withContext(Dispatchers.IO) {
            val local = normalized(jsonExporter.exportToBytes())
            val observedRemote = remoteBytes?.let(::normalized)
            val base = readBaseline(scopeKey)
            checkInitialJoinIsExplicit(base, local, observedRemote)
            // A previous/history file is necessarily older than a missing or corrupt canonical.
            // Once this device has a baseline, auto-promoting such a copy could make a newer
            // device accept a rollback. Before the first baseline exists, though, those history
            // files are the migration bridge from backup-only servers into full sync.
            check(!remoteIsRecovery || base == null) {
                "The canonical sync file is missing or damaged. An automatic recovery copy is " +
                    "available, but it cannot be promoted safely; restore a chosen backup first"
            }
            val remote = observedRemote
            val result = SnapshotMerger.merge(base = base, local = local, remote = remote)
            PreparedSnapshotSync(
                scopeKey = scopeKey,
                localAtStart = local,
                canonical = result.json,
                conflicts = result.conflicts,
                conflictRecords = result.conflictRecords,
                remoteNeedsPublish =
                    remote == null || !SnapshotMerger.equivalent(remote, result.json)
            ).also {
                // Do this before a transport publishes anything; exact apply repeats validation as
                // defense in depth, but discovering an invalid graph after upload is too late.
                jsonExporter.validateBytesForSync(it.canonicalBytes)
            }
        }

    /**
     * Called after the canonical remote file is known to match [PreparedSnapshotSync.canonical]
     * (either it was just published or it was already identical). If the user edited locally
     * while the network transfer was in flight, merge that edit onto the canonical snapshot and
     * leave it pending for the next sync instead of wiping it.
     */
    internal suspend fun commit(prepared: PreparedSnapshotSync): Int = withContext(Dispatchers.IO) {
        val current = normalized(jsonExporter.exportToBytes())
        val inFlightMerge = if (SnapshotMerger.equivalent(current, prepared.localAtStart)) {
            null
        } else {
            SnapshotMerger.merge(
                base = prepared.localAtStart,
                local = current,
                remote = prepared.canonical
            )
        }
        val localTarget = inFlightMerge?.json ?: prepared.canonical
        val conflictRecords = prepared.conflictRecords + inFlightMerge?.conflictRecords.orEmpty()
        if (!SnapshotMerger.equivalent(current, localTarget)) {
            recoveryBackupManager.saveCurrent("pre_sync_apply").getOrElse { e ->
                throw IllegalStateException(
                    "Could not create a recovery backup before applying sync; local data was not changed",
                    e
                )
            }
            if (conflictRecords.isNotEmpty()) {
                writeConflictArtifact(prepared.scopeKey, conflictRecords)
            }
            check(jsonExporter.replaceBytesForSync(localTarget.toString(2).toByteArray(Charsets.UTF_8))) {
                "The server snapshot was published, but applying it locally failed; sync again to retry"
            }
            val applied = normalized(jsonExporter.exportToBytes())
            check(SnapshotMerger.equivalent(applied, localTarget)) {
                "The server snapshot was published, but local verification failed; sync again to retry"
            }
        }
        writeBaseline(prepared.scopeKey, prepared.canonical)
        val totalConflicts = prepared.conflicts + (inFlightMerge?.conflicts ?: 0)
        if (totalConflicts > 0) {
            Log.i(TAG, "Resolved $totalConflicts concurrent sync conflict(s) using the server copy")
        }
        totalConflicts
    }

    /** Lets transports reject a damaged head and continue to an older recovery candidate. */
    internal fun isValidRemoteSnapshot(bytes: ByteArray): Boolean = runCatching {
        val candidate = normalized(bytes)
        jsonExporter.validateBytesForSync(candidate.toString().toByteArray(Charsets.UTF_8))
    }.isSuccess

    private fun normalized(bytes: ByteArray): JSONObject =
        SnapshotMerger.normalizeForSync(JSONObject(String(bytes, Charsets.UTF_8)))

    private fun checkInitialJoinIsExplicit(base: JSONObject?, local: JSONObject, remote: JSONObject?) {
        if (base != null || remote == null) return
        if (hasUserData(local) && hasUserData(remote)) {
            throw InitialSyncConfirmationRequiredException()
        }
    }

    private fun hasUserData(snapshot: JSONObject): Boolean =
        snapshot.optJSONArray("tasks").orZero() > 0 ||
            snapshot.optJSONArray("projects").orZero() > 0 ||
            snapshot.optJSONArray("lists").orZero() > 0 ||
            snapshot.optJSONArray("tags").orZero() > 0 ||
            snapshot.optJSONArray("tagGroups").orZero() > 0 ||
            snapshot.optJSONArray("personGroups").orZero() > 0 ||
            snapshot.optJSONArray("comments").orZero() > 0 ||
            peopleBeyondCurrentUser(snapshot) > 0

    private fun peopleBeyondCurrentUser(snapshot: JSONObject): Int {
        val people = snapshot.optJSONArray("people") ?: return 0
        var count = 0
        for (i in 0 until people.length()) {
            val person = people.optJSONObject(i) ?: continue
            if (!person.optBoolean("isMe", false)) count++
        }
        return count
    }

    private fun JSONArray?.orZero(): Int = this?.length() ?: 0

    private fun readBaseline(scopeKey: String): JSONObject? = try {
        val file = baselineFile(scopeKey)
        if (!file.exists()) null else SnapshotMerger.normalizeForSync(JSONObject(file.readText()))
    } catch (e: Exception) {
        Log.w(TAG, "Primary sync baseline is unreadable; trying previous copy", e)
        readPreviousBaseline(scopeKey)
    }

    private fun readPreviousBaseline(scopeKey: String): JSONObject? = try {
        val file = previousBaselineFile(scopeKey)
        if (!file.exists()) null else SnapshotMerger.normalizeForSync(JSONObject(file.readText()))
    } catch (e: Exception) {
        // A baseline is only merge metadata; the canonical server copy remains the source of
        // truth. Treat corruption as a first join only after both baseline copies fail.
        Log.w(TAG, "Ignoring unreadable previous sync baseline", e)
        null
    }

    private fun writeBaseline(scopeKey: String, value: JSONObject) {
        val target = baselineFile(scopeKey)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.part")
        temporary.writeText(value.toString())
        try {
            preservePreviousBaseline(target, previousBaselineFile(scopeKey))
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not publish local sync baseline", e)
        }
    }

    private fun preservePreviousBaseline(target: File, previous: File) {
        if (!target.exists()) return
        previous.parentFile?.mkdirs()
        Files.copy(
            target.toPath(),
            previous.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun writeConflictArtifact(
        scopeKey: String,
        records: List<SnapshotMerger.ConflictRecord>
    ) {
        val directory = File(context.filesDir, "yata-sync-conflicts")
        directory.mkdirs()
        val target = File(directory, "${scopeDigest(scopeKey)}-${System.currentTimeMillis()}.json")
        val temporary = File(directory, ".${target.name}.part")
        val payload = JSONObject()
            .put("createdAt", System.currentTimeMillis())
            .put("scopeKey", scopeKey)
            .put("conflictCount", records.size)
            .put("conflicts", SnapshotMerger.conflictRecordsJson(records))
        temporary.writeText(payload.toString(2))
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not save sync conflict recovery data", e)
        }
    }

    private fun baselineFile(scopeKey: String): File {
        return File(File(context.filesDir, "yata-sync"), "${scopeDigest(scopeKey)}.json")
    }

    private fun previousBaselineFile(scopeKey: String): File {
        return File(File(context.filesDir, "yata-sync"), "${scopeDigest(scopeKey)}.previous.json")
    }

    private fun scopeDigest(scopeKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(scopeKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "SnapshotSyncEngine"
    }
}
