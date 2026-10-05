package com.mj.yata.util

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeLists
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects

data class ParsedQuickAddEntityResolution(
    val listId: String?,
    val projectId: String?,
    val tagIds: List<String>,
    val assigneeIds: List<String>,
    val projectDue: String?
)

fun Task.withParsedQuickAdd(
    quickAdd: ParsedQuickAdd,
    ignoredFields: Set<String> = emptySet(),
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    projectsEnabled: Boolean,
    tagsEnabled: Boolean,
    peopleEnabled: Boolean
): Task {
    var updated = this
    if ("due" !in ignoredFields && quickAdd.due != null) updated = updated.copy(due = quickAdd.due)
    if ("start" !in ignoredFields && quickAdd.startDate != null) updated = updated.copy(startDate = quickAdd.startDate)
    if ("time" !in ignoredFields && quickAdd.time != null) updated = updated.copy(time = quickAdd.time)
    if ("recurrence" !in ignoredFields && quickAdd.recurrence != null) updated = updated.copy(recurrence = quickAdd.recurrence)
    if ("reminder" !in ignoredFields && quickAdd.reminder != null) updated = updated.copy(reminder = quickAdd.reminder)
    if ("priority" !in ignoredFields && quickAdd.priority != null) updated = updated.copy(priority = quickAdd.priority)
    if ("flag" !in ignoredFields && quickAdd.flag) updated = updated.copy(flag = true)

    val resolved = resolveParsedQuickAddEntities(
        quickAdd = quickAdd,
        baseListId = updated.listId,
        baseProjectId = updated.projectId,
        baseTagIds = updated.tagIds,
        baseAssigneeIds = updated.assigneeIds,
        lists = lists,
        projects = projects,
        people = people,
        tags = tags,
        projectsEnabled = projectsEnabled,
        tagsEnabled = tagsEnabled,
        peopleEnabled = peopleEnabled,
        ignoredFields = ignoredFields
    )
    updated = updated.copy(
        listId = resolved.listId,
        projectId = resolved.projectId,
        tagIds = resolved.tagIds,
        assigneeIds = resolved.assigneeIds
    )
    if ("due" !in ignoredFields && quickAdd.due == null && resolved.projectDue != null) {
        updated = updated.copy(due = resolved.projectDue)
    }
    return updated
}

fun resolveParsedQuickAddEntities(
    quickAdd: ParsedQuickAdd,
    baseListId: String?,
    baseProjectId: String?,
    baseTagIds: List<String>,
    baseAssigneeIds: List<String>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    projectsEnabled: Boolean,
    tagsEnabled: Boolean,
    peopleEnabled: Boolean,
    ignoredFields: Set<String> = emptySet()
): ParsedQuickAddEntityResolution {
    var listId = baseListId
    var projectId = baseProjectId
    var projectDue: String? = null
    var tagIds = baseTagIds
    var assigneeIds = baseAssigneeIds

    if (projectsEnabled && "project" !in ignoredFields && quickAdd.projectName != null) {
        findBestEntityMatch(quickAdd.projectName, projects.activeProjects(includeId = projectId), { it.name })?.let { project ->
            projectId = project.id
            listId = null
            projectDue = project.due
        }
    }
    if ("list" !in ignoredFields && quickAdd.listName != null) {
        findBestEntityMatch(quickAdd.listName, lists.activeLists(includeId = listId), { it.name })?.let { list ->
            listId = list.id
            projectId = null
            projectDue = null
        }
    }
    if (tagsEnabled && "tags" !in ignoredFields && quickAdd.tagNames.isNotEmpty()) {
        val matchedTagIds = quickAdd.tagNames.mapNotNull { target ->
            findBestEntityMatch(target, tags, { it.name })?.id
        }.distinct()
        if (matchedTagIds.isNotEmpty()) tagIds = (tagIds + matchedTagIds).distinct()
    }
    if (peopleEnabled && "people" !in ignoredFields && quickAdd.assigneeNames.isNotEmpty()) {
        val matchedAssigneeIds = quickAdd.assigneeNames.mapNotNull { target ->
            findBestEntityMatch(target, people.activePeople(includeIds = assigneeIds.toSet()), { it.name })?.id
        }.distinct()
        if (matchedAssigneeIds.isNotEmpty()) assigneeIds = (assigneeIds + matchedAssigneeIds).distinct()
    }

    return ParsedQuickAddEntityResolution(
        listId = listId,
        projectId = projectId,
        tagIds = tagIds,
        assigneeIds = assigneeIds,
        projectDue = projectDue
    )
}
