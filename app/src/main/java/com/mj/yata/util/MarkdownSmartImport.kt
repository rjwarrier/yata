package com.mj.yata.util

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeLists
import com.mj.yata.domain.model.activeProjects
import java.util.TreeMap

/** Snapshot of the same local entities and feature settings used by individual smart add. */
data class MarkdownImportContext(
    val projects: List<Project> = emptyList(),
    val lists: List<YataList> = emptyList(),
    val people: List<Person> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val projectsEnabled: Boolean = true,
    val peopleEnabled: Boolean = true,
    val tagsEnabled: Boolean = true,
    val defaultPriority: String = "none",
    val defaultAssigneeIds: List<String> = emptyList()
)

data class MarkdownTaskPreview(val source: MarkdownTask, val task: Task, val unmatched: List<String>)

/** Resolves a cached parse; saving receives these exact tasks and does not parse again. */
fun MarkdownDocument.prepareSmartImport(
    context: MarkdownImportContext,
    projectId: String?,
    listId: String?,
    useDates: Boolean
): List<MarkdownTaskPreview> {
    if (tasks.isEmpty()) return emptyList()
    val lookup = QuickAddEntityLookup(context.lists, context.projects, context.people, context.tags)
    val manualDestination = projectId != null || listId != null
    val ignored = buildSet {
        if (manualDestination) { add("project"); add("list") }
        if (!useDates) addAll(listOf("due", "start", "time", "recurrence", "reminder"))
    }
    val headingProjects = TreeMap<String, Project>(String.CASE_INSENSITIVE_ORDER).apply {
        context.projects.activeProjects().forEach { putIfAbsent(it.name, it) }
    }
    val headingLists = TreeMap<String, YataList>(String.CASE_INSENSITIVE_ORDER).apply {
        context.lists.activeLists().forEach { putIfAbsent(it.name, it) }
    }
    val headingDestinations = mutableMapOf<List<String>, Pair<String?, String?>?>()
    fun headingDestination(headings: List<String>): Pair<String?, String?>? {
        if (headingDestinations.containsKey(headings)) return headingDestinations[headings]
        val match = headings.asReversed().firstNotNullOfOrNull { heading ->
            val key = heading.trim()
            val project = if (context.projectsEnabled) headingProjects[key] else null
            val list = headingLists[key]
            when { project != null -> project.name to null; list != null -> null to list.name; else -> null }
        }
        headingDestinations[headings] = match
        return match
    }
    val base = createTasks(tasks.indices.toSet(), projectId, listId, useDates,
        context.defaultAssigneeIds, context.defaultPriority, allowInbox = true)
    return tasks.mapIndexed { index, source ->
        val task = base[index]
        val parsed = source.quickAdd ?: emptyMarkdownQuickAdd(source.title)
        // A bare heading that exactly names a local project/list is also useful context.
        val parsedDestination = parsed.projectName != null || parsed.listName != null
        val headingDestination = if (!parsedDestination && !manualDestination) headingDestination(source.headings) else null
        val quickAdd = parsed.copy(
            projectName = if (parsedDestination) parsed.projectName else headingDestination?.first,
            listName = if (parsedDestination) parsed.listName else headingDestination?.second
        )
        var resolved = task.copy(title = quickAdd.title.ifBlank { source.title }).withParsedQuickAdd(
            quickAdd, ignored, context.lists, context.projects, context.people, context.tags,
            context.projectsEnabled, context.tagsEnabled, context.peopleEnabled, entityLookup = lookup
        )
        if (!useDates) resolved = resolved.copy(due = null)
        // A reminder without a due date cannot be scheduled (same constraint as the editor).
        if (resolved.due == null) resolved = resolved.copy(reminder = null)
        val unmatched = buildList {
            if (!manualDestination && context.projectsEnabled) quickAdd.projectName?.let { name ->
                if (lookup.project(name) == null) add("+$name")
            }
            if (!manualDestination) quickAdd.listName?.let { name ->
                if (lookup.list(name) == null) add("=$name")
            }
            if (context.tagsEnabled) quickAdd.tagNames.forEach { name ->
                if (lookup.tag(name) == null) add("#$name")
            }
            if (context.peopleEnabled) quickAdd.assigneeNames.forEach { name ->
                if (lookup.person(name) == null) add("@$name")
            }
        }.distinct()
        MarkdownTaskPreview(source, resolved, unmatched)
    }
}

internal fun emptyMarkdownQuickAdd(title: String = "") = ParsedQuickAdd(
    title = title, due = null, time = null, recurrence = null, highlightRanges = emptyList()
)

internal fun ParsedQuickAdd.withMarkdownDefaults(defaults: ParsedQuickAdd): ParsedQuickAdd = copy(
    due = due ?: defaults.due,
    startDate = startDate ?: defaults.startDate,
    time = time ?: defaults.time,
    recurrence = recurrence ?: defaults.recurrence,
    reminder = reminder ?: defaults.reminder,
    priority = priority ?: defaults.priority,
    flag = flag || defaults.flag,
    projectName = if (projectName != null || listName != null) projectName else defaults.projectName,
    listName = if (projectName != null || listName != null) listName else defaults.listName,
    tagNames = (defaults.tagNames + tagNames).distinctBy { it.lowercase() },
    assigneeNames = assigneeNames.ifEmpty { defaults.assigneeNames }
)

/** Adapt Markdown presentation only; field recognition remains NaturalLanguageParser's job. */
private val markdownInlineCode = Regex("`+([^`]+)`+")
private val markdownUrl = Regex("https?://[^\\s<>)\\]]+")
private val markdownLink = Regex("!?\\[([^]]+)]\\(([^)]+)\\)")
private val markdownEmphasis = Regex("(?<!\\w)[*_](\\S(?:.*?\\S)?)[*_](?!\\w)")
private val markdownMetadataName = Regex("[*_` ]+")
private val markdownTagSeparator = Regex("[,\\s]+")

internal fun parseMarkdownSmartText(raw: String, parse: (String) -> ParsedQuickAdd): ParsedQuickAdd {
    val protected = mutableListOf<String>()
    fun protect(value: String): String {
        protected += value
        return "YATAMDPROTECTEDTOKEN${protected.lastIndex}END"
    }
    var text = raw.replace(markdownInlineCode) { protect(it.groupValues[1]) }
    text = text.replace(markdownUrl) { protect(it.value) }
        .replace(markdownLink, "$1 ($2)")
        .replace("**", "").replace("__", "").replace("~~", "")
        .replace(markdownEmphasis, "$1")
    val result = parse(text)
    var title = result.title
    protected.forEachIndexed { index, value -> title = title.replace("YATAMDPROTECTEDTOKEN${index}END", value) }
    return result.copy(title = title)
}

/** Metadata labels used by headings, indented properties and task-table columns. */
internal fun markdownMetadata(label: String, value: String): String? {
    val clean = value.trim().trim('*', '_', '`')
    if (clean.isBlank() || clean == "-") return ""
    val name = label.lowercase().trim().replace(markdownMetadataName, " ").trim()
    fun names(trigger: String): String = clean.split(',').map(String::trim).filter(String::isNotEmpty)
        .joinToString(" ") { "$trigger\"${it.trimStart('#', '@', '+', '=').replace("\"", "") }\"" }
    return when (name) {
        "due", "due date", "deadline", "date" -> "due $clean"
        "start", "start date", "starts" -> "starts $clean"
        "time" -> "at $clean"
        "project" -> names("+")
        "list" -> names("=")
        "tag", "tags", "labels" -> clean.split(markdownTagSeparator)
            .filter(String::isNotBlank).joinToString(" ") { "#${it.trimStart('#')}" }
        "person", "people", "assignee", "assignees", "assigned to", "owner" -> names("@")
        "priority" -> when (clean.lowercase()) {
            "high", "urgent", "1", "p1", "!1" -> "!1"
            "medium", "med", "2", "p2", "!2" -> "!2"
            "low", "3", "p3", "!3" -> "!3"
            else -> clean
        }
        "repeat", "recurrence" -> clean
        "reminder", "remind" -> "remind $clean"
        "flag", "flagged", "important" -> if (clean.lowercase() in setOf("true", "yes", "x", "1")) "important" else ""
        else -> null
    }
}
