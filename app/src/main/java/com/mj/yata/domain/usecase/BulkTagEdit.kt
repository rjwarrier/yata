package com.mj.yata.domain.usecase

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.first

data class BulkTagEdit(val changes: Map<String, Boolean>, val newTags: List<Tag> = emptyList())

fun previewBulkTagEdit(tasks: List<Task>, projects: List<Project>, changes: Map<String, Boolean>): List<Task> {
    val inherited = projects.associate { it.id to it.commonTagIds.toSet() }
    return tasks.map { task ->
        val protected = inherited[task.projectId].orEmpty()
        val kept = task.tagIds.filter { changes[it] != false || it in protected }
        val added = changes.filterValues { it }.keys.filter { it !in protected }
        task.copy(tagIds = (kept + added).distinct())
    }
}

/** Resolve live names/IDs, then persist new tags and every task edit in one transaction. */
suspend fun applyBulkTagEdit(repository: YataRepository, ids: List<String>, edit: BulkTagEdit): Int {
    if (ids.isEmpty() || edit.changes.isEmpty()) return 0
    val tasks = repository.getTasksByIds(ids.toSet())
    if (tasks.isEmpty()) return 0
    val existing = repository.getTags().first()
    val byName = existing.associateBy { it.name.trim().lowercase() }.toMutableMap()
    val aliases = mutableMapOf<String, String>()
    val pending = mutableListOf<Tag>()
    edit.newTags.filter { edit.changes[it.id] == true }.forEach { proposed ->
        val name = proposed.name.trim().removePrefix("#").trim()
        require(name.isNotBlank())
        val reused = byName[name.lowercase()]
        if (reused != null) aliases[proposed.id] = reused.id else {
            require(existing.none { it.id == proposed.id } && pending.none { it.id == proposed.id })
            val tag = proposed.copy(name = name, groupId = null)
            pending += tag
            byName[name.lowercase()] = tag
        }
    }
    val valid = (existing + pending).mapTo(hashSetOf()) { it.id }
    val proposedIds = edit.newTags.mapTo(hashSetOf()) { it.id }
    val changes = linkedMapOf<String, Boolean>()
    edit.changes.forEach { (id, added) ->
        val target = aliases[id] ?: id
        // A staged new tag removed again needs no write or permanent entity.
        if (id in proposedIds && !added) return@forEach
        require(target in valid)
        changes[target] = added
    }
    val projects = repository.getProjects().first()
    val originals = tasks.associateBy { it.id }
    val updated = previewBulkTagEdit(tasks, projects, changes).filter { it != originals[it.id] }
    if (updated.isNotEmpty()) repository.upsertTasks(updated, resyncReminder = false,
        pendingTags = pending.filter { tag -> updated.any { tag.id in it.tagIds } })
    return updated.size
}
