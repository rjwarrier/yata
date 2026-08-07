package com.mj.yata.data.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSyncTest {

    @Test
    fun threeWayMerge_combinesIndependentChangesAndDeletion() {
        val base = snapshot(
            tasks = listOf(
                task("local-edit", "base"),
                task("remote-edit", "base"),
                task("deleted-locally", "base")
            )
        )
        val local = snapshot(
            tasks = listOf(
                task("local-edit", "from local"),
                task("remote-edit", "base"),
                task("local-new", "new here")
            )
        )
        val remote = snapshot(
            tasks = listOf(
                task("local-edit", "base"),
                task("remote-edit", "from remote"),
                task("deleted-locally", "base"),
                task("remote-new", "new there")
            )
        )

        val result = SnapshotMerger.merge(base, local, remote)
        val rows = rows(result.json, "tasks")

        assertEquals("from local", rows.getValue("local-edit").getString("title"))
        assertEquals("from remote", rows.getValue("remote-edit").getString("title"))
        assertTrue("local-new" in rows)
        assertTrue("remote-new" in rows)
        assertFalse("deleted-locally" in rows)
        assertEquals(0, result.conflicts)
    }

    @Test
    fun concurrentEdit_serverRecordWinsDeterministically() {
        val base = snapshot(tasks = listOf(task("t1", "base")))
        val local = snapshot(tasks = listOf(task("t1", "local")))
        val remote = snapshot(tasks = listOf(task("t1", "server")))

        val result = SnapshotMerger.merge(base, local, remote)

        assertEquals("server", rows(result.json, "tasks").getValue("t1").getString("title"))
        assertEquals(1, result.conflicts)
    }

    @Test
    fun concurrentEdit_mergesIndependentFieldsWithinSameRecord() {
        val base = snapshot(tasks = listOf(task("t1", "base").put("priority", "none").put("flag", false)))
        val local = snapshot(tasks = listOf(task("t1", "local").put("priority", "none").put("flag", false)))
        val remote = snapshot(tasks = listOf(task("t1", "base").put("priority", "high").put("flag", true)))

        val result = SnapshotMerger.merge(base, local, remote)
        val task = rows(result.json, "tasks").getValue("t1")

        assertEquals("local", task.getString("title"))
        assertEquals("high", task.getString("priority"))
        assertTrue(task.getBoolean("flag"))
        assertEquals(0, result.conflicts)
    }

    @Test
    fun concurrentEdit_recordsLosingLocalConflictData() {
        val base = snapshot(tasks = listOf(task("t1", "base")))
        val local = snapshot(tasks = listOf(task("t1", "local")))
        val remote = snapshot(tasks = listOf(task("t1", "server")))

        val result = SnapshotMerger.merge(base, local, remote)
        val conflict = result.conflictRecords.single()
        val conflictJson = SnapshotMerger.conflictRecordsJson(result.conflictRecords).getJSONObject(0)

        assertEquals("tasks/t1/title", conflict.path)
        assertEquals("tasks", conflict.collection)
        assertEquals("t1", conflict.id)
        assertEquals("base", conflictJson.getString("base"))
        assertEquals("local", conflictJson.getString("local"))
        assertEquals("server", conflictJson.getString("remote"))
    }

    @Test
    fun firstJoin_unionsDevicesAndUsesEstablishedServerOnCollision() {
        val local = snapshot(tasks = listOf(task("same", "local"), task("local", "only")))
        val remote = snapshot(tasks = listOf(task("same", "server"), task("remote", "only")))

        val result = SnapshotMerger.merge(base = null, local = local, remote = remote)
        val rows = rows(result.json, "tasks")

        assertEquals(setOf("same", "local", "remote"), rows.keys)
        assertEquals("server", rows.getValue("same").getString("title"))
    }

    @Test
    fun parentDeleteVersusChildEdit_repairsForeignKeysAndDropsOrphans() {
        val base = snapshot(
            projects = listOf(project("p1")),
            tasks = listOf(task("kept", "base", projectId = "p1"), task("gone", "base"))
        )
        // Project deletion cascades both the project task and the separately deleted task locally.
        val local = snapshot()
        val remote = snapshot(
            projects = listOf(project("p1")),
            tasks = listOf(task("kept", "edited remotely", projectId = "p1"), task("gone", "base")),
            comments = listOf(comment("orphan", "gone"))
        )

        val result = SnapshotMerger.merge(base, local, remote)
        val tasks = rows(result.json, "tasks")

        assertTrue("kept" in tasks)
        assertTrue(tasks.getValue("kept").isNull("projectId"))
        assertFalse("gone" in tasks)
        assertEquals(0, result.json.getJSONArray("comments").length())
    }

    @Test
    fun normalization_keepsInstallLocalStateOutAndCanonicalizesSetsAndPhotoPaths() {
        val raw = snapshot(
            settings = listOf(
                setting("theme_mode", "string", "DARK"),
                setting("sftp_host", "string", "server"),
                setting("github_owner", "string", "owner"),
                setting("app_lock_enabled", "bool", true),
                setting("saved", "stringSet", JSONArray().put("z").put("a"))
            )
        )
        raw.getJSONArray("people").getJSONObject(0)
            .put("photoUri", "file:///random/device/path.png")

        val normalized = SnapshotMerger.normalizeForSync(raw)
        val settings = rows(normalized, "settings", idKey = "name")

        assertEquals(setOf("theme_mode", "saved"), settings.keys)
        assertEquals("a", settings.getValue("saved").getJSONArray("value").getString(0))
        assertFalse(normalized.getJSONArray("people").getJSONObject(0).has("photoUri"))
    }

    @Test
    fun equivalent_ignoresObjectPropertyOrder() {
        val left = JSONObject().put("a", 1).put("b", JSONObject().put("x", true).put("y", 2))
        val right = JSONObject().put("b", JSONObject().put("y", 2).put("x", true)).put("a", 1)

        assertTrue(SnapshotMerger.equivalent(left, right))
    }

    @Test
    fun normalization_ignoresRoomCollectionRowOrder() {
        val left = snapshot(tasks = listOf(task("b", "second"), task("a", "first")))
        val right = snapshot(tasks = listOf(task("a", "first"), task("b", "second")))

        assertTrue(
            SnapshotMerger.equivalent(
                SnapshotMerger.normalizeForSync(left),
                SnapshotMerger.normalizeForSync(right)
            )
        )
    }

    @Test
    fun normalization_preservesOwnerAndCanonicalizesCollaboratorOrder() {
        val raw = snapshot(tasks = listOf(task("t1", "assigned")))
        raw.getJSONArray("tasks").getJSONObject(0).put(
            "assigneeIds",
            JSONArray().put("owner").put("z").put("a")
        )

        val assignees = SnapshotMerger.normalizeForSync(raw)
            .getJSONArray("tasks").getJSONObject(0).getJSONArray("assigneeIds")

        assertEquals(listOf("owner", "a", "z"), (0 until assignees.length()).map(assignees::getString))
    }

    @Test
    fun normalization_rejectsMalformedCollectionsAndFutureFormats() {
        val malformed = snapshot().put("projects", JSONObject())
        val future = snapshot().put("syncVersion", 2)

        assertTrue(runCatching { SnapshotMerger.normalizeForSync(malformed) }.isFailure)
        assertTrue(runCatching { SnapshotMerger.normalizeForSync(future) }.isFailure)
    }

    @Test
    fun normalization_ordersSubtaskParentsFirstAndRejectsCycles() {
        val childFirst = snapshot(
            tasks = listOf(
                task(
                    "t1",
                    "nested",
                    subtasks = JSONArray()
                        .put(subtask("child", "parent"))
                        .put(subtask("parent"))
                )
            )
        )
        val ordered = SnapshotMerger.normalizeForSync(childFirst)
            .getJSONArray("tasks").getJSONObject(0).getJSONArray("subtasks")

        assertEquals("parent", ordered.getJSONObject(0).getString("id"))
        assertEquals("child", ordered.getJSONObject(1).getString("id"))

        val cyclic = snapshot(
            tasks = listOf(
                task(
                    "t1",
                    "cycle",
                    subtasks = JSONArray()
                        .put(subtask("a", "b"))
                        .put(subtask("b", "a"))
                )
            )
        )
        assertTrue(runCatching { SnapshotMerger.normalizeForSync(cyclic) }.isFailure)
    }

    private fun snapshot(
        projects: List<JSONObject> = emptyList(),
        tasks: List<JSONObject> = emptyList(),
        comments: List<JSONObject> = emptyList(),
        settings: List<JSONObject> = emptyList()
    ): JSONObject = JSONObject().apply {
        put("version", 4)
        put("syncVersion", 1)
        put("people", JSONArray().put(personMe()))
        put("personGroups", JSONArray())
        put("projects", JSONArray(projects))
        put("lists", JSONArray())
        put("tags", JSONArray())
        put("tagGroups", JSONArray())
        put("tasks", JSONArray(tasks))
        put("comments", JSONArray(comments))
        put("settings", JSONArray(settings))
    }

    private fun personMe() = JSONObject().apply {
        put("id", "me"); put("name", "You"); put("initials", "Y")
        put("color", "accentC"); put("isMe", true); put("groupId", JSONObject.NULL)
    }

    private fun project(id: String) = JSONObject().apply {
        put("id", id); put("name", id); put("color", "accentA"); put("icon", "work")
        put("commonTagIds", JSONArray())
    }

    private fun task(
        id: String,
        title: String,
        projectId: String? = null,
        subtasks: JSONArray = JSONArray()
    ) = JSONObject().apply {
        put("id", id); put("title", title); put("listId", JSONObject.NULL)
        put("projectId", projectId ?: JSONObject.NULL); put("section", "")
        put("priority", "none"); put("assigneeIds", JSONArray().put("me"))
        put("tagIds", JSONArray()); put("subtasks", subtasks)
    }

    private fun subtask(id: String, parentId: String? = null) = JSONObject().apply {
        put("id", id); put("title", id); put("done", false); put("sortOrder", 0)
        put("parentSubtaskId", parentId ?: JSONObject.NULL)
    }

    private fun comment(id: String, taskId: String) = JSONObject().apply {
        put("id", id); put("taskId", taskId); put("body", "note"); put("createdAt", 1L)
    }

    private fun setting(name: String, type: String, value: Any) = JSONObject().apply {
        put("name", name); put("type", type); put("value", value)
    }

    private fun rows(root: JSONObject, key: String, idKey: String = "id"): Map<String, JSONObject> {
        val array = root.getJSONArray(key)
        return (0 until array.length()).associate { index ->
            val row = array.getJSONObject(index)
            row.getString(idKey) to row
        }
    }
}
