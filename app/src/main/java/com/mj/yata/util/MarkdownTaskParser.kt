package com.mj.yata.util

import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Task
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

const val MAX_MARKDOWN_BYTES = 1_048_576
private const val MAX_MARKDOWN_TASKS = 1_000
private const val MAX_MARKDOWN_SUBTASKS = 200

class MarkdownImportTooLargeException : IllegalArgumentException()

data class MarkdownSubtask(
    val title: String,
    val done: Boolean,
    val parentIndex: Int?
)

data class MarkdownTask(
    val title: String,
    val done: Boolean,
    val due: String?,
    val heading: String?,
    val notes: String?,
    val subtasks: List<MarkdownSubtask>
)

data class MarkdownDocument(
    val title: String?,
    val referenceDate: LocalDate,
    val tasks: List<MarkdownTask>
) {
    /** Creates fresh identities, including nested subtask references; never reuses file IDs. */
    fun createTasks(
        selectedIndices: Set<Int>,
        projectId: String?,
        listId: String?,
        useDates: Boolean,
        assigneeIds: List<String> = emptyList(),
        priority: String = "none",
        firstSortOrder: Int = 0
    ): List<Task> {
        require(projectId != null || listId != null)
        require(selectedIndices.isNotEmpty() && selectedIndices.all { it in tasks.indices })
        return tasks.withIndex().filter { it.index in selectedIndices }.mapIndexed { order, (_, task) ->
            val subtaskIds = task.subtasks.map { "s_${UUID.randomUUID()}" }
            Task(
                id = "t_${UUID.randomUUID()}",
                title = task.title,
                listId = listId,
                projectId = projectId,
                section = "",
                due = task.due.takeIf { useDates },
                time = null,
                reminder = null,
                priority = priority,
                flag = false,
                done = task.done,
                assigneeIds = assigneeIds,
                tagIds = emptyList(),
                recurrence = null,
                subtasks = task.subtasks.mapIndexed { index, subtask ->
                    Subtask(
                        id = subtaskIds[index],
                        title = subtask.title,
                        done = subtask.done,
                        parentSubtaskId = subtask.parentIndex?.let { subtaskIds[it] },
                        sortOrder = index
                    )
                },
                notes = task.notes,
                sortOrder = firstSortOrder + order
            )
        }
    }
}

/** Checklist-only import: prose, ordinary bullets, tables and code examples are never tasks. */
object MarkdownTaskParser {
    private val checkbox = Regex("^(\\s*)(?:[-+*]|\\d+[.)])\\s+\\[([ xX])]\\s+(.+)$")
    private val heading = Regex("^ {0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,}).*$")
    private val isoDate = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")
    private const val MONTH = "(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)"
    private val dayMonth = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH\\b(?:[ ,]+(\\d{4})\\b)?", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("\\b$MONTH\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:[ ,]+(\\d{4})\\b)?", RegexOption.IGNORE_CASE)
    private val updated = Regex("^\\s*[_*]*Updated\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val explicitDue = Regex("\\s+\\(due\\s+(.+)\\)\\s*$", RegexOption.IGNORE_CASE)
    private val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private data class Heading(val level: Int, val text: String, val due: LocalDate?)
    private data class Section(val headings: List<Heading>, val prelude: MutableList<String> = mutableListOf(), var hasTasks: Boolean = false)
    private data class PendingTask(
        val title: String, val done: Boolean, val due: LocalDate?, val section: Section,
        val indent: Int, val notes: MutableList<String> = mutableListOf(),
        val subtasks: MutableList<MarkdownSubtask> = mutableListOf(),
        val stack: MutableList<Pair<Int, Int>> = mutableListOf()
    )

    fun parse(markdown: String, today: LocalDate = LocalDate.now()): MarkdownDocument {
        if (markdown.toByteArray(Charsets.UTF_8).size > MAX_MARKDOWN_BYTES) throw MarkdownImportTooLargeException()
        val lines = markdown.removePrefix("\uFEFF")
            .replace(Regex("<!--[\\s\\S]*?(?:-->|\\z)"), "").lines()
        // Only metadata outside fenced examples can anchor relative dates.
        var metadataFence: String? = null
        val referenceDate = lines.firstNotNullOfOrNull { line ->
            val marker = fence.find(line)?.groupValues?.get(1)
            if (marker != null) {
                if (metadataFence == null) metadataFence = marker
                else if (closesFence(line, metadataFence!!)) metadataFence = null
                null
            } else if (metadataFence == null) {
                updated.find(line)?.groupValues?.get(1)?.let { dateIn(it, today) }
            } else null
        } ?: today

        val headings = mutableListOf<Heading>()
        val sections = mutableListOf(Section(emptyList()))
        val tasks = mutableListOf<PendingTask>()
        var section = sections.last()
        var current: PendingTask? = null
        var activeFence: String? = null
        var lastDate = referenceDate
        var documentTitle: String? = null

        lines.forEach { line ->
            if (activeFence != null) {
                current?.notes?.add(line)
                if (closesFence(line, activeFence!!)) activeFence = null
                return@forEach
            }
            fence.find(line)?.let {
                activeFence = it.groupValues[1]
                current?.notes?.add(line)
                return@forEach
            }
            heading.matchEntire(line)?.let { match ->
                val level = match.groupValues[1].length
                val text = match.groupValues[2]
                if (level == 1 && documentTitle == null) documentTitle = plainTitle(text)
                headings.removeAll { it.level >= level }
                val date = dateIn(text, lastDate, referenceDate)
                if (date != null) lastDate = date
                headings += Heading(level, text, date)
                section = Section(headings.toList())
                sections += section
                current = null
                return@forEach
            }
            val match = checkbox.matchEntire(line)
            if (match != null) {
                val indent = match.groupValues[1].fold(0) { width, char -> width + if (char == '\t') 4 else 1 }
                val body = match.groupValues[3].trim()
                val title = plainTitle(body)
                if (title.isBlank()) return@forEach
                val done = match.groupValues[2].equals("x", ignoreCase = true)
                val parent = current
                if (parent != null && indent > parent.indent) {
                    if (parent.subtasks.size >= MAX_MARKDOWN_SUBTASKS) throw MarkdownImportTooLargeException()
                    while (parent.stack.isNotEmpty() && parent.stack.last().first >= indent) parent.stack.removeAt(parent.stack.lastIndex)
                    parent.subtasks += MarkdownSubtask(title, done, parent.stack.lastOrNull()?.second)
                    parent.stack += indent to parent.subtasks.lastIndex
                } else {
                    if (tasks.size >= MAX_MARKDOWN_TASKS) throw MarkdownImportTooLargeException()
                    // Dates in ordinary task prose describe events, not necessarily deadlines.
                    val due = explicitDue.find(title)?.groupValues?.get(1)?.let { dateIn(it, lastDate, referenceDate) }
                        ?: headings.lastOrNull { it.due != null }?.due
                    current = PendingTask(title, done, due, section, indent)
                    section.hasTasks = true
                    tasks += current!!
                }
            } else if (current != null) {
                current!!.notes += line.trimEnd()
            } else {
                section.prelude += line.trimEnd()
            }
        }

        // Retain reference sections (e.g. an event table) without turning them into extra tasks.
        val references = sections.filter { !it.hasTasks && it.prelude.any(String::isNotBlank) }
            .map { reference -> sectionNotes(reference) }.filter(String::isNotBlank)
        var totalNotesLength = 0
        return MarkdownDocument(documentTitle, referenceDate, tasks.map { task ->
            val notes = (listOf(sectionNotes(task.section), task.notes.joinToString("\n").trim()) + references)
                .filter(String::isNotBlank).distinct().joinToString("\n\n").takeIf(String::isNotBlank)
            // Shared reference text is copied into task notes. Bound that expansion as well.
            totalNotesLength += notes?.length ?: 0
            if (totalNotesLength > MAX_MARKDOWN_BYTES * 4) throw MarkdownImportTooLargeException()
            MarkdownTask(task.title, task.done, task.due?.toString(), task.section.headings.lastOrNull()?.text, notes, task.subtasks.toList())
        })
    }

    private fun sectionNotes(section: Section): String =
        (section.headings.map { "${"#".repeat(it.level)} ${it.text}" } + section.prelude)
            .joinToString("\n").trim()

    private fun closesFence(line: String, marker: String): Boolean {
        val closing = line.trim()
        return closing.length >= marker.length && closing.all { it == marker.first() }
    }

    private fun plainTitle(value: String): String = value
        .replace(Regex("!\\[([^]]*)]\\(([^)]+)\\)"), "$1 ($2)")
        .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)")
        .replace("**", "").replace("__", "").replace("~~", "")
        .replace(Regex("(?<!\\w)[*_](\\S(?:.*?\\S)?)[*_](?!\\w)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1").trim()

    private fun dateIn(value: String, anchor: LocalDate, relativeAnchor: LocalDate = anchor): LocalDate? {
        val text = value.trim().trim('*', '_', '`')
        isoDate.find(text)?.let { match ->
            return validDate(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }
        dayMonth.find(text)?.let { match ->
            return namedDate(match.groupValues[3], match.groupValues[2], match.groupValues[1], anchor)
        }
        monthDay.find(text)?.let { match ->
            return namedDate(match.groupValues[3], match.groupValues[1], match.groupValues[2], anchor)
        }
        return when {
            Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> relativeAnchor.plusDays(1)
            Regex("\\btoday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> relativeAnchor
            Regex("\\byesterday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> relativeAnchor.minusDays(1)
            else -> null
        }
    }

    private fun namedDate(yearText: String, monthText: String, dayText: String, anchor: LocalDate): LocalDate? {
        val month = monthNames.indexOf(monthText.take(3).lowercase(Locale.ROOT)) + 1
        val year = yearText.toIntOrNull() ?: (anchor.year + if (anchor.monthValue - month > 6) 1 else 0)
        return validDate(year, month, dayText.toInt())
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
}
