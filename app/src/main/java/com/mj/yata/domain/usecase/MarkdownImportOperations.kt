package com.mj.yata.domain.usecase

import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.mergeMarkdownTask
import com.mj.yata.util.undoMarkdownUpdate
import kotlinx.coroutines.flow.first
import java.util.UUID

data class MarkdownImportBatch(val created: List<Task>, val before: List<Task>, val updated: List<Task>) {
    val count: Int get() = created.size + updated.size
}

suspend fun applyMarkdownImport(
    repository: YataRepository,
    prepared: List<Task>,
    selected: Set<Int>,
    updateTargets: Map<Int, String>
): MarkdownImportBatch {
    require(selected.isNotEmpty() && selected.all { it in prepared.indices })
    require(updateTargets.keys.all { it in selected })
    val projects = repository.getProjects().first().filter { !it.archived }.mapTo(hashSetOf()) { it.id }
    val lists = repository.getLists().first().filter { !it.archived }.mapTo(hashSetOf()) { it.id }
    val originals = repository.getTasksByIds(updateTargets.values.toSet()).associateBy { it.id }
    require(updateTargets.values.all { it in originals })
    val tagIds = repository.getTags().first().mapTo(hashSetOf()) { it.id }
    val personIds = repository.getPeople().first().mapTo(hashSetOf()) { it.id }
    val nextOrder = mutableMapOf<Pair<String?, String?>, Int>()
    val created = mutableListOf<Task>()
    val updates = linkedMapOf<String, Task>()
    selected.sorted().forEach { index ->
        val task = prepared[index]
        require(task.title.isNotBlank())
        require(task.tagIds.all { it in tagIds } && task.assigneeIds.all { it in personIds })
        require((task.projectId != null || task.listId != null) &&
            (task.projectId == null || task.projectId in projects) && (task.listId == null || task.listId in lists))
        val target = updateTargets[index]
        if (target != null) {
            updates[target] = mergeMarkdownTask(updates[target] ?: originals.getValue(target), task)
        } else {
            val destination = task.projectId to task.listId
            if (destination !in nextOrder) {
                val existing = task.projectId?.let { repository.getTasksForProject(it).first() }
                    ?: repository.getTasksForList(requireNotNull(task.listId)).first()
                nextOrder[destination] = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            }
            val order = nextOrder.getValue(destination)
            nextOrder[destination] = order + 1
            created += task.copy(sortOrder = order)
        }
    }
    val changed = updates.values.filter { it != originals[it.id] }
    val writes = created + changed
    require(writes.map { it.id }.distinct().size == writes.size)
    if (writes.isNotEmpty()) repository.upsertTasks(writes, resyncReminder = true)
    // Read back repository-managed createdAt, completedAt and postponement counts for accurate undo.
    val saved = repository.getTasksByIds(writes.map { it.id }).associateBy { it.id }
    return MarkdownImportBatch(created.map { saved[it.id] ?: it }, changed.map { originals.getValue(it.id) },
        changed.map { saved[it.id] ?: it })
}

suspend fun undoMarkdownImport(repository: YataRepository, batch: MarkdownImportBatch) {
    val ids = (batch.created + batch.updated).map { it.id }
    val current = repository.getTasksByIds(ids).associateBy { it.id }
    val now = System.currentTimeMillis()
    val deleted = batch.created.mapNotNull { current[it.id]?.copy(deletedAt = now) }
    val before = batch.before.associateBy { it.id }
    val restored = batch.updated.mapNotNull { after ->
        current[after.id]?.let { undoMarkdownUpdate(before.getValue(after.id), after, it) }
    }
    repository.upsertTasks(deleted + restored, resyncReminder = true, trackPostponements = false)
}

/** Called only by an explicit Create action; await persistence before mapping task references. */
suspend fun createMarkdownReference(repository: YataRepository, token: String, requestedId: String? = null, color: String = "accentA"): String {
    val name = token.drop(1).trim()
    require(name.isNotBlank())
    val id = requestedId ?: "md_${UUID.randomUUID()}"
    return when (token.first()) {
        '#' -> repository.getTags().first().firstOrNull { it.name.trim().equals(name, true) }?.id
            ?: Tag(id, name, color).also { repository.upsertTag(it) }.id
        '@' -> repository.getPeople().first().firstOrNull { !it.archived && it.name.trim().equals(name, true) }?.id
            ?: Person(id, name, com.mj.yata.util.initialsFor(name), color).also { repository.upsertPerson(it) }.id
        '+' -> repository.getProjects().first().firstOrNull { !it.archived && it.name.trim().equals(name, true) }?.id
            ?: Project(id, name, color, "Folder").also { repository.upsertProject(it) }.id
        '=' -> repository.getLists().first().firstOrNull { !it.archived && it.name.trim().equals(name, true) }?.id
            ?: YataList(id, name, color, "List").also { repository.upsertList(it) }.id
        else -> error("Unknown reference type")
    }
}
