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

fun SubtaskEntity.toDomain() = Subtask(
    id = id,
    title = title,
    done = done,
    parentSubtaskId = parentSubtaskId,
    sortOrder = sortOrder
)

fun Subtask.toEntity(taskId: String) = SubtaskEntity(
    id = id,
    taskId = taskId,
    parentSubtaskId = parentSubtaskId,
    title = title,
    done = done,
    sortOrder = sortOrder
)

fun TaskCommentEntity.toDomain() = TaskComment(id, taskId, body, createdAt, authorId)
fun TaskComment.toEntity() = TaskCommentEntity(id, taskId, body, createdAt, authorId)

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
    defaultReminder = defaultReminder,
    description = description,
    excludeFromToday = excludeFromToday
)
fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    dueDate = due,
    starred = starred,
    commonTagIds = commonTagIds.joinToString(","),
    defaultReminder = defaultReminder,
    description = description,
    excludeFromToday = excludeFromToday
)

fun ListEntity.toDomain() = YataList(id, name, color, icon, starred, excludeFromToday)
fun YataList.toEntity() = ListEntity(id, name, color, icon, starred, excludeFromToday)

fun TagEntity.toDomain() = Tag(id, name, color, groupId, starred, hideCompletedByDefault)
fun Tag.toEntity() = TagEntity(id, name, color, groupId, starred, hideCompletedByDefault)

fun TagGroupEntity.toDomain() = TagGroup(id, name, color)
fun TagGroup.toEntity() = TagGroupEntity(id, name, color)

fun TaskEntity.toDomain(assigneeIds: List<String>, tagIds: List<String>, subtasks: List<Subtask> = emptyList()) = Task(
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
    completedAt = completedAt,
    deletedAt = deletedAt,
    assigneeIds = assigneeIds,
    tagIds = tagIds,
    recurrence = deserializeRecurrence(recurrenceJson),
    subtasks = subtasks,
    notes = notes,
    sortOrder = sortOrder
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
    completedAt = completedAt,
    deletedAt = deletedAt,
    notes = notes,
    recurrenceJson = serializeRecurrence(recurrence),
    sortOrder = sortOrder
)

fun TaskWithRelations.toDomain() = task.toDomain(
    assigneeIds = assignees.map { it.id },
    tagIds = tags.map { it.id },
    subtasks = subtaskEntities.sortedBy { it.sortOrder }.map { it.toDomain() }
)
