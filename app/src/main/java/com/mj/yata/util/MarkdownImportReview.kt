package com.mj.yata.util

import com.mj.yata.domain.model.Task

enum class MarkdownDuplicateAction { SKIP, COPY, UPDATE }
data class MarkdownDuplicateDecision(val action: MarkdownDuplicateAction, val targetId: String)

/** In-memory review draft, owned by the ViewModel so rotation does not discard approved edits. */
data class MarkdownImportReview(
    val edits: Map<Int, MarkdownTaskEdit> = emptyMap(),
    val excludedSubtasks: Map<Int, Set<Int>> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
    val ignoredSavedReferences: Set<String> = emptySet(),
    val rememberMappings: Boolean? = null,
    val duplicateActions: Map<Int, MarkdownDuplicateDecision> = emptyMap()
)

fun reviewMarkdownTasks(
    previews: List<MarkdownTaskPreview>,
    review: MarkdownImportReview,
    context: MarkdownImportContext,
    manualDestination: Boolean = false,
    useDates: Boolean = true
): List<MarkdownTaskPreview> {
    val lookup = QuickAddEntityLookup(context.lists, context.projects, context.people, context.tags)
    val projects = context.projects.filter { !it.archived }.mapTo(hashSetOf()) { it.id }
    val lists = context.lists.filter { !it.archived }.mapTo(hashSetOf()) { it.id }
    val people = context.people.filter { !it.archived }.mapTo(hashSetOf()) { it.id }
    val tags = context.tags.mapTo(hashSetOf()) { it.id }
    val ignoredReferences = review.ignoredSavedReferences.mapTo(hashSetOf()) { it.lowercase() }
    val mappings = review.references.entries.associate { it.key.lowercase() to it.value }
    fun mapped(token: String): String? = mappings[token.lowercase()]?.takeIf { id -> when (token.first()) {
        '+' -> !manualDestination && context.projectsEnabled && id in projects
        '=' -> !manualDestination && id in lists
        '#' -> context.tagsEnabled && id in tags
        '@' -> context.peopleEnabled && id in people
        else -> false
    } }
    return previews.mapIndexed { index, preview ->
        var task = preview.task
        val parsed = preview.source.quickAdd
        val tokens = (preview.unmatched + listOfNotNull(parsed?.projectName?.let { "+$it" }, parsed?.listName?.let { "=$it" })).distinct()
        tokens.forEach { token ->
            if (!manualDestination && token.lowercase() in ignoredReferences) {
                when (token.first()) {
                    '+' -> task = task.copy(projectId = null, due = if (parsed?.due == null) null else task.due)
                    '=' -> task = task.copy(listId = null)
                }
            }
            mapped(token)?.let { id ->
            when (token.first()) {
                '+' -> task = task.copy(projectId = id, listId = null, due = if (useDates && parsed?.due == null) context.projects.first { it.id == id }.due else task.due)
                '=' -> task = task.copy(listId = id, projectId = null, due = if (useDates && parsed?.due == null) null else task.due)
            }
        } }
        fun remapNames(names: List<String>, trigger: String, base: List<String>, resolve: (String) -> String?): List<String> {
            if (names.none { mapped(trigger + it) != null || (trigger + it).lowercase() in ignoredReferences }) return base
            val prior = names.mapNotNull(resolve).toSet()
            val replacements = names.mapNotNull {
                if ((trigger + it).lowercase() in ignoredReferences) null else mapped(trigger + it) ?: resolve(it)
            }
            return ((base - prior) + replacements).distinct()
        }
        task = task.copy(
            tagIds = remapNames(parsed?.tagNames.orEmpty(), "#", task.tagIds) { lookup.tag(it)?.id },
            assigneeIds = remapNames(parsed?.assigneeNames.orEmpty(), "@", task.assigneeIds) { lookup.person(it)?.id }
        )
        val edit = review.edits[index]
        if (edit != null) task = applyMarkdownEdit(task, edit)
        task = task.withExcludedMarkdownSubtasks(review.excludedSubtasks[index].orEmpty())
        val ignored = edit?.fields.orEmpty()
        val sourceTokens = markdownReferenceTokens(listOf(preview))
        val invalidMappings = review.references.keys.filter { token ->
            token.lowercase() in sourceTokens && !validMarkdownReference(token, review.references.getValue(token), context) &&
                when (token.first()) {
                    '+', '=' -> !manualDestination
                    '#' -> context.tagsEnabled
                    '@' -> context.peopleEnabled
                    else -> false
                }
        }
        preview.copy(task = task, unmatched = (preview.unmatched + invalidMappings).distinctBy { it.lowercase() }.filter { token ->
            token.lowercase() !in ignoredReferences && mapped(token) == null && when (token.first()) {
                '+', '=' -> "destination" !in ignored
                '#' -> "tags" !in ignored
                '@' -> "people" !in ignored
                else -> true
            }
        })
    }
}

data class MarkdownTaskEdit(val task: Task, val fields: Set<String>)

fun MarkdownImportReview.withTaskEdit(index: Int, before: Task, edited: Task): MarkdownImportReview {
    val changed = buildSet {
        if (before.title != edited.title) add("title")
        if (before.projectId != edited.projectId || before.listId != edited.listId) add("destination")
        if (before.section != edited.section) add("section")
        if (before.due != edited.due) add("due")
        if (before.startDate != edited.startDate) add("start")
        if (before.time != edited.time) add("time")
        if (before.reminder != edited.reminder) add("reminder")
        if (before.recurrence != edited.recurrence) add("recurrence")
        if (before.priority != edited.priority) add("priority")
        if (before.flag != edited.flag) add("flag")
        if (before.tagIds != edited.tagIds) add("tags")
        if (before.assigneeIds != edited.assigneeIds) add("people")
        if (before.notes != edited.notes) add("notes")
        if (before.subtasks != edited.subtasks) add("subtasks")
        if (before.estimateMinutes != edited.estimateMinutes) add("estimate")
    }
    return copy(edits = edits + (index to MarkdownTaskEdit(edited, edits[index]?.fields.orEmpty() + changed)),
        excludedSubtasks = if ("subtasks" in changed) excludedSubtasks - index else excludedSubtasks)
}

private fun applyMarkdownEdit(base: Task, edit: MarkdownTaskEdit): Task {
    val value = edit.task
    val fields = edit.fields
    return base.copy(
        title = if ("title" in fields) value.title else base.title,
        projectId = if ("destination" in fields) value.projectId else base.projectId,
        listId = if ("destination" in fields) value.listId else base.listId,
        section = if ("section" in fields) value.section else base.section,
        due = if ("due" in fields) value.due else base.due,
        startDate = if ("start" in fields) value.startDate else base.startDate,
        time = if ("time" in fields) value.time else base.time,
        recurrence = if ("recurrence" in fields) value.recurrence else base.recurrence,
        reminder = if ("reminder" in fields) value.reminder else base.reminder,
        priority = if ("priority" in fields) value.priority else base.priority,
        flag = if ("flag" in fields) value.flag else base.flag,
        assigneeIds = if ("people" in fields) value.assigneeIds else base.assigneeIds,
        tagIds = if ("tags" in fields) value.tagIds else base.tagIds,
        subtasks = if ("subtasks" in fields) value.subtasks else base.subtasks,
        notes = if ("notes" in fields) value.notes else base.notes,
        estimateMinutes = if ("estimate" in fields) value.estimateMinutes else base.estimateMinutes
    )
}

fun MarkdownImportReview.duplicateAction(index: Int, target: Task?): MarkdownDuplicateAction =
    duplicateActions[index]?.takeIf { it.targetId == target?.id }?.action ?: MarkdownDuplicateAction.SKIP

/** A child of an excluded row is promoted to the closest retained ancestor. */
fun Task.withExcludedMarkdownSubtasks(excluded: Set<Int>): Task {
    if (excluded.isEmpty()) return this
    val byId = subtasks.associateBy { it.id }
    val kept = subtasks.filterIndexed { index, _ -> index !in excluded }
    val keptIds = kept.mapTo(hashSetOf()) { it.id }
    return copy(subtasks = kept.mapIndexed { index, subtask ->
        var parent = subtask.parentSubtaskId
        val visited = hashSetOf(subtask.id)
        while (parent != null && parent !in keptIds && visited.add(parent)) parent = byId[parent]?.parentSubtaskId
        subtask.copy(parentSubtaskId = parent?.takeIf { it in keptIds && it !in visited }, sortOrder = index)
    })
}

/** Exact repeated imports are indexed, including completed tasks. Near matches use the editor's
 * similarity rule on live unfinished tasks. Cache queries for repeated titles in one document. */
fun markdownDuplicateMatches(imported: List<Task>, existing: List<Task>): Map<Int, Task> {
    val live = existing.filter { !it.archived && it.deletedAt == null }
    val exact = live.groupBy { it.title.trim().lowercase() }
    val unfinished = live.filter { !it.done && it.title.length <= 256 }
    val words = Regex("[^\\p{L}\\p{N}]+")
    fun keys(title: String) = title.trim().lowercase().split(words).filter { it.isNotBlank() }.distinct()
    val byWord = unfinished.flatMapIndexed { index, task -> keys(task.title).map { it to index } }.groupBy({ it.first }, { it.second })
    val byPrefix = unfinished.indices.groupBy { unfinished[it].title.trim().lowercase().take(3) }
    fun near(task: Task): Task? {
        if (task.title.length > 256) return null
        val groups = keys(task.title).mapNotNull { byWord[it] }.sortedBy { it.size } +
            listOfNotNull(byPrefix[task.title.trim().lowercase().take(3)])
        val indices = linkedSetOf<Int>()
        // A bounded heuristic for near matches; every exact match is still indexed above.
        for (group in groups) {
            for (index in group) { indices += index; if (indices.size >= 200) break }
            if (indices.size >= 200) break
        }
        val candidates = indices.sorted().map { unfinished[it] }
        return findSimilarTask(task.title, candidates)
    }
    val memo = mutableMapOf<Triple<String, String?, String?>, Task?>()
    return buildMap {
        imported.forEachIndexed { index, task ->
            val key = Triple(task.title.trim().lowercase(), task.projectId, task.listId)
            if (!memo.containsKey(key)) {
                val matches = exact[key.first].orEmpty()
                memo[key] = matches.firstOrNull { it.projectId == task.projectId && it.listId == task.listId }
                    ?: matches.firstOrNull() ?: near(task)
            }
            memo[key]?.let { put(index, it) }
        }
    }
}

/** Add imported context without losing existing work or changing identity/history. Subtasks
 * with the same title under the same parent keep their IDs, including across repeat imports. */
fun mergeMarkdownTask(existing: Task, imported: Task): Task {
    val merged = existing.subtasks.toMutableList()
    val mappedParents = mutableMapOf<String, String>()
    imported.subtasks.forEach { incoming ->
        val parent = incoming.parentSubtaskId?.let { mappedParents[it] }
        val match = merged.indexOfFirst {
            it.parentSubtaskId == parent && it.title.trim().equals(incoming.title.trim(), ignoreCase = true)
        }
        if (match >= 0) {
            val old = merged[match]
            merged[match] = old.copy(done = old.done || incoming.done)
            mappedParents[incoming.id] = old.id
        } else {
            mappedParents[incoming.id] = incoming.id
            merged += incoming.copy(parentSubtaskId = parent, sortOrder = merged.size)
        }
    }
    val notes = listOfNotNull(existing.notes?.takeIf { it.isNotBlank() }, imported.notes?.takeIf { it.isNotBlank() })
        .distinct().let { parts ->
            if (parts.size == 2 && parts[0].contains(parts[1])) parts[0] else parts.joinToString("\n\n").ifBlank { null }
        }
    return existing.copy(
        title = imported.title, projectId = imported.projectId, listId = imported.listId,
        due = imported.due, startDate = imported.startDate, time = imported.time,
        recurrence = imported.recurrence, reminder = imported.reminder,
        priority = imported.priority, flag = imported.flag, done = existing.done || imported.done,
        completedAt = if (existing.done) existing.completedAt else if (imported.done) imported.completedAt else existing.completedAt,
        assigneeIds = imported.assigneeIds.ifEmpty { existing.assigneeIds },
        tagIds = (existing.tagIds + imported.tagIds).distinct(), notes = notes, subtasks = merged,
        estimateMinutes = imported.estimateMinutes ?: existing.estimateMinutes
    )
}

/** Undo only fields still equal to the batch result; later edits survive. */
fun undoMarkdownUpdate(before: Task, after: Task, current: Task): Task {
    fun <T> restore(old: T, applied: T, now: T): T = if (now == applied) old else now
    return current.copy(
        title = restore(before.title, after.title, current.title),
        projectId = restore(before.projectId, after.projectId, current.projectId),
        listId = restore(before.listId, after.listId, current.listId),
        due = restore(before.due, after.due, current.due),
        startDate = restore(before.startDate, after.startDate, current.startDate),
        time = restore(before.time, after.time, current.time),
        recurrence = restore(before.recurrence, after.recurrence, current.recurrence),
        reminder = restore(before.reminder, after.reminder, current.reminder),
        priority = restore(before.priority, after.priority, current.priority),
        flag = restore(before.flag, after.flag, current.flag),
        done = restore(before.done, after.done, current.done),
        completedAt = restore(before.completedAt, after.completedAt, current.completedAt),
        assigneeIds = restore(before.assigneeIds, after.assigneeIds, current.assigneeIds),
        tagIds = restore(before.tagIds, after.tagIds, current.tagIds),
        notes = restore(before.notes, after.notes, current.notes),
        subtasks = restore(before.subtasks, after.subtasks, current.subtasks),
        estimateMinutes = restore(before.estimateMinutes, after.estimateMinutes, current.estimateMinutes),
        postponementCount = if (current.due == after.due && current.postponementCount == after.postponementCount) before.postponementCount else current.postponementCount
    )
}
