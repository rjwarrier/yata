package com.mj.yata.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import com.mj.yata.domain.model.*
import com.mj.yata.data.local.db.entity.*

fun serializeRecurrence(r: Recurrence?): String? {
    if (r == null) return null
    val obj = JSONObject()
    obj.put("freq", r.freq)
    obj.put("interval", r.interval)
    if (r.byday != null) {
        val arr = JSONArray()
        r.byday.forEach { arr.put(it) }
        obj.put("byday", arr)
    }
    if (r.bymonthday != null) {
        obj.put("bymonthday", r.bymonthday)
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
    obj.put("ends", endsObj)
    return obj.toString()
}

fun deserializeRecurrence(json: String?): Recurrence? {
    if (json.isNullOrEmpty()) return null
    return try {
        val obj = JSONObject(json)
        val freq = obj.getString("freq")
        val interval = obj.getInt("interval")
        
        val byday = if (obj.has("byday")) {
            val arr = obj.getJSONArray("byday")
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } else null

        val bymonthday = if (obj.has("bymonthday")) {
            obj.getInt("bymonthday")
        } else null

        val endsObj = obj.getJSONObject("ends")
        val endsType = endsObj.getString("type")
        val ends = when (endsType) {
            "after" -> RecurrenceEnds.After(endsObj.getInt("count"))
            "on" -> RecurrenceEnds.On(endsObj.getString("date"))
            else -> RecurrenceEnds.Never
        }

        Recurrence(freq, interval, byday, bymonthday, ends)
    } catch (e: Exception) {
        null
    }
}

fun serializeSubtasks(list: List<Subtask>): String {
    val arr = JSONArray()
    list.forEach { st ->
        val obj = JSONObject()
        obj.put("id", st.id)
        obj.put("title", st.title)
        obj.put("done", st.done)
        arr.put(obj)
    }
    return arr.toString()
}

fun deserializeSubtasks(json: String?): List<Subtask> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<Subtask>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Subtask(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    done = obj.getBoolean("done")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun PersonEntity.toDomain() = Person(id, name, initials, color, photoUri, isMe, groupId, starred)
fun Person.toEntity() = PersonEntity(id, name, initials, color, photoUri, isMe, groupId, starred)

fun PersonGroupEntity.toDomain() = PersonGroup(id, name, color)
fun PersonGroup.toEntity() = PersonGroupEntity(id, name, color)

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    color = color,
    icon = icon,
    due = dueDate,
    starred = starred,
    commonTagIds = if (commonTagIds.isEmpty()) emptyList() else commonTagIds.split(","),
    defaultReminder = defaultReminder
)
fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    dueDate = due,
    starred = starred,
    commonTagIds = commonTagIds.joinToString(","),
    defaultReminder = defaultReminder
)

fun ListEntity.toDomain() = YataList(id, name, color, icon, starred)
fun YataList.toEntity() = ListEntity(id, name, color, icon, starred)

fun TagEntity.toDomain() = Tag(id, name, color, groupId, starred, hideCompletedByDefault)
fun Tag.toEntity() = TagEntity(id, name, color, groupId, starred, hideCompletedByDefault)

fun TagGroupEntity.toDomain() = TagGroup(id, name, color)
fun TagGroup.toEntity() = TagGroupEntity(id, name, color)

fun TaskEntity.toDomain(assigneeIds: List<String>, tagIds: List<String>) = Task(
    id = id,
    title = title,
    listId = listId,
    projectId = projectId,
    section = section,
    due = dueDate,
    time = dueTime,
    reminder = reminder,
    priority = priority,
    flag = flag,
    done = done,
    assigneeIds = assigneeIds,
    tagIds = tagIds,
    recurrence = deserializeRecurrence(recurrenceJson),
    subtasks = deserializeSubtasks(subtasksJson),
    notes = notes
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    listId = listId,
    projectId = projectId,
    section = section,
    dueDate = due,
    dueTime = time,
    reminder = reminder,
    priority = priority,
    flag = flag,
    done = done,
    notes = notes,
    recurrenceJson = serializeRecurrence(recurrence),
    subtasksJson = serializeSubtasks(subtasks)
)

fun TaskWithRelations.toDomain() = task.toDomain(
    assigneeIds = assignees.map { it.id },
    tagIds = tags.map { it.id }
)
