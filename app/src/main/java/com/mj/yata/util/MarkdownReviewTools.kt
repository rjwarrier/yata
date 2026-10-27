package com.mj.yata.util

import com.mj.yata.domain.model.Task
import java.util.Base64

enum class MarkdownPreviewFilter { ALL, SELECTED, DUPLICATES, UNRESOLVED }
data class MarkdownReferenceMemory(val enabled: Boolean = true, val mappings: Map<String, String> = emptyMap())

object MarkdownReferenceCodec {
    fun decode(values: Set<String>): Map<String, String> = buildMap {
        values.take(500).forEach { encoded ->
            runCatching {
                val parts = encoded.split(':', limit = 2)
                val token = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8).trim().lowercase()
                val id = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                if (token.firstOrNull() in listOf('#', '@', '+', '=') && token.drop(1).isNotBlank() && id.isNotBlank()) put(token, id)
            }
        }
    }
    fun encode(values: Map<String, String>): Set<String> = values.entries.toList().takeLast(500).mapTo(linkedSetOf()) {
        fun encode(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
        encode(it.key.trim().lowercase()) + ":" + encode(it.value)
    }
}

fun MarkdownImportReview.withBulkDuplicates(matches: Map<Int, Task>, action: MarkdownDuplicateAction): MarkdownImportReview =
    copy(duplicateActions = duplicateActions + matches.mapValues { MarkdownDuplicateDecision(action, it.value.id) })

fun markdownReferenceTokens(previews: List<MarkdownTaskPreview>): Set<String> = buildSet {
    previews.forEach { preview ->
        addAll(preview.unmatched.map { it.lowercase() })
        preview.source.quickAdd?.let { parsed ->
            parsed.projectName?.let { add("+${it.lowercase()}") }
            parsed.listName?.let { add("=${it.lowercase()}") }
            parsed.tagNames.forEach { add("#${it.lowercase()}") }
            parsed.assigneeNames.forEach { add("@${it.lowercase()}") }
        }
    }
}

fun validMarkdownReference(token: String, id: String, context: MarkdownImportContext): Boolean = when (token.firstOrNull()) {
    '+' -> context.projects.any { it.id == id && !it.archived }
    '=' -> context.lists.any { it.id == id && !it.archived }
    '#' -> context.tags.any { it.id == id }
    '@' -> context.people.any { it.id == id && !it.archived }
    else -> false
}

fun markdownVisibleIndices(previews: List<MarkdownTaskPreview>, selected: Set<Int>, matches: Map<Int, Task>, filter: MarkdownPreviewFilter): List<Int> =
    previews.indices.filter { index -> when (filter) {
        MarkdownPreviewFilter.ALL -> true
        MarkdownPreviewFilter.SELECTED -> index in selected
        MarkdownPreviewFilter.DUPLICATES -> index in matches
        MarkdownPreviewFilter.UNRESOLVED -> previews[index].unmatched.isNotEmpty()
    } }

data class MarkdownImportSummary(val newTasks: Int, val updatedTasks: Int, val skippedRows: Int, val unresolved: Int)

fun markdownImportSummary(previews: List<MarkdownTaskPreview>, selected: Set<Int>, matches: Map<Int, Task>, review: MarkdownImportReview): MarkdownImportSummary {
    var created = 0
    var skipped = previews.size - selected.count { it in previews.indices }
    val updates = linkedMapOf<String, Task>()
    val before = matches.values.associateBy { it.id }
    val unresolved = linkedSetOf<String>()
    selected.sorted().filter { it in previews.indices }.forEach { index ->
        val target = matches[index]
        val action = if (target == null) MarkdownDuplicateAction.COPY else review.duplicateAction(index, target)
        if (action == MarkdownDuplicateAction.SKIP) skipped++ else {
            unresolved.addAll(previews[index].unmatched.map { it.lowercase() })
            if (action == MarkdownDuplicateAction.UPDATE && target != null)
                updates[target.id] = mergeMarkdownTask(updates[target.id] ?: target, previews[index].task)
            else created++
        }
    }
    val unchanged = updates.filter { (id, task) -> task == before[id] }.keys
    skipped += selected.count { index -> matches[index]?.id in unchanged && review.duplicateAction(index, matches[index]) == MarkdownDuplicateAction.UPDATE }
    return MarkdownImportSummary(created, updates.size - unchanged.size, skipped, unresolved.size)
}

enum class MarkdownUpdateField { TITLE, PROJECT, LIST, DUE, START, TIME, REMINDER, RECURRENCE, PRIORITY, FLAG, DONE, PEOPLE, TAGS, NOTES, SUBTASKS, ESTIMATE }

fun markdownChangedFields(before: Task, incoming: Task): Pair<Task, Set<MarkdownUpdateField>> {
    val after = mergeMarkdownTask(before, incoming)
    val changed = buildSet {
        if (before.title != after.title) add(MarkdownUpdateField.TITLE)
        if (before.projectId != after.projectId) add(MarkdownUpdateField.PROJECT)
        if (before.listId != after.listId) add(MarkdownUpdateField.LIST)
        if (before.due != after.due) add(MarkdownUpdateField.DUE)
        if (before.startDate != after.startDate) add(MarkdownUpdateField.START)
        if (before.time != after.time) add(MarkdownUpdateField.TIME)
        if (before.reminder != after.reminder) add(MarkdownUpdateField.REMINDER)
        if (before.recurrence != after.recurrence) add(MarkdownUpdateField.RECURRENCE)
        if (before.priority != after.priority) add(MarkdownUpdateField.PRIORITY)
        if (before.flag != after.flag) add(MarkdownUpdateField.FLAG)
        if (before.done != after.done) add(MarkdownUpdateField.DONE)
        if (before.assigneeIds != after.assigneeIds) add(MarkdownUpdateField.PEOPLE)
        if (before.tagIds != after.tagIds) add(MarkdownUpdateField.TAGS)
        if (before.notes != after.notes) add(MarkdownUpdateField.NOTES)
        if (before.subtasks != after.subtasks) add(MarkdownUpdateField.SUBTASKS)
        if (before.estimateMinutes != after.estimateMinutes) add(MarkdownUpdateField.ESTIMATE)
    }
    return after to changed
}
