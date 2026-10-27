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
    peopleEnabled: Boolean,
    entityLookup: QuickAddEntityLookup? = null
): Task {
    val resolved = resolveParsedQuickAddEntities(
        quickAdd = quickAdd,
        baseListId = listId,
        baseProjectId = projectId,
        baseTagIds = tagIds,
        baseAssigneeIds = assigneeIds,
        lists = lists,
        projects = projects,
        people = people,
        tags = tags,
        projectsEnabled = projectsEnabled,
        tagsEnabled = tagsEnabled,
        peopleEnabled = peopleEnabled,
        ignoredFields = ignoredFields,
        entityLookup = entityLookup
    )
    return copy(
        due = if ("due" in ignoredFields) due else quickAdd.due ?: resolved.projectDue ?: due,
        startDate = if ("start" in ignoredFields) startDate else quickAdd.startDate ?: startDate,
        time = if ("time" in ignoredFields) time else quickAdd.time ?: time,
        recurrence = if ("recurrence" in ignoredFields) recurrence else quickAdd.recurrence ?: recurrence,
        reminder = if ("reminder" in ignoredFields) reminder else quickAdd.reminder ?: reminder,
        priority = if ("priority" in ignoredFields) priority else quickAdd.priority ?: priority,
        flag = flag || ("flag" !in ignoredFields && quickAdd.flag),
        listId = resolved.listId,
        projectId = resolved.projectId,
        tagIds = resolved.tagIds,
        assigneeIds = resolved.assigneeIds
    )
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
    ignoredFields: Set<String> = emptySet(),
    entityLookup: QuickAddEntityLookup? = null
): ParsedQuickAddEntityResolution {
    var listId = baseListId
    var projectId = baseProjectId
    var projectDue: String? = null
    var tagIds = baseTagIds
    var assigneeIds = baseAssigneeIds

    if (projectsEnabled && "project" !in ignoredFields && quickAdd.projectName != null) {
        val match = if (entityLookup != null) entityLookup.project(quickAdd.projectName, projectId)
            else findBestEntityMatch(quickAdd.projectName, projects.activeProjects(includeId = projectId), { it.name })
        match?.let { project ->
            projectId = project.id
            listId = null
            projectDue = project.due
        }
    }
    if ("list" !in ignoredFields && quickAdd.listName != null) {
        val match = if (entityLookup != null) entityLookup.list(quickAdd.listName, listId)
            else findBestEntityMatch(quickAdd.listName, lists.activeLists(includeId = listId), { it.name })
        match?.let { list ->
            listId = list.id
            projectId = null
            projectDue = null
        }
    }
    if (tagsEnabled && "tags" !in ignoredFields && quickAdd.tagNames.isNotEmpty()) {
        val matchedTagIds = quickAdd.tagNames.mapNotNull { target ->
            (if (entityLookup != null) entityLookup.tag(target) else findBestEntityMatch(target, tags, { it.name }))?.id
        }.distinct()
        if (matchedTagIds.isNotEmpty()) tagIds = (tagIds + matchedTagIds).distinct()
    }
    if (peopleEnabled && "people" !in ignoredFields && quickAdd.assigneeNames.isNotEmpty()) {
        val matchedAssigneeIds = quickAdd.assigneeNames.mapNotNull { target ->
            (if (entityLookup != null) entityLookup.person(target, assigneeIds.toSet())
                else findBestEntityMatch(target, people.activePeople(includeIds = assigneeIds.toSet()), { it.name }))?.id
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
