package com.mj.yata.domain.usecase

import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.repository.YataRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Persist the tag before linking tasks to it; separate async writes can race the foreign key. */
internal suspend fun createAndApplyBulkTag(
    repository: YataRepository,
    ids: List<String>,
    rawName: String,
    color: String
) {
    if (ids.isEmpty()) return
    val name = rawName.trim().removePrefix("#").trim()
    require(name.isNotBlank())
    val selected = repository.getTasksByIds(ids.distinct())
    if (selected.isEmpty()) return
    val existing = repository.getTags().first().firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
    val tag = existing ?: Tag("tag_${UUID.randomUUID()}", name, color).also { repository.upsertTag(it) }
    val updated = selected.filter { tag.id !in it.tagIds }.map { it.copy(tagIds = it.tagIds + tag.id) }
    if (updated.isNotEmpty()) repository.upsertTasks(updated, notify = true, resyncReminder = false)
}
