package com.mj.yata.util

import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Task
import java.time.LocalDate
import java.time.LocalTime
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
    val subtasks: List<MarkdownSubtask>,
    val quickAdd: ParsedQuickAdd? = null,
    val headings: List<String> = emptyList()
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
        firstSortOrder: Int = 0,
        allowInbox: Boolean = false
    ): List<Task> {
        require(allowInbox || projectId != null || listId != null)
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

/** Structural Markdown extraction, followed by the same smart-add parser used by the editor. */
object MarkdownTaskParser {
    private val checkbox = Regex("^(\\s*)(?:[-+*]|\\d+[.)])\\s+\\[([ xX])]\\s+(.+)$")
    private val bullet = Regex("^(\\s*)(?:[-+*•]|\\d+[.)])\\s+(.+)$")
    private val property = Regex("^\\s*(?:[-+*]\\s+)?[*_`]*([\\p{L} ]+?)[*_`]*\\s*:\\s*(.+)$")
    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
    private val taskColumnNames = setOf("task", "tasks", "title", "todo", "to-do", "to do", "action", "action item", "task name", "task title")
    private data class Item(val indent: Int, val body: String, val done: Boolean, val metadata: List<String> = emptyList(), val note: String? = null)
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
        val stack: MutableList<Pair<Int, Int>> = mutableListOf(),
        val body: String,
        val metadata: MutableList<String> = mutableListOf()
    )

    fun parse(
        markdown: String,
        today: LocalDate = LocalDate.now(),
        referenceTime: LocalTime = LocalTime.now(),
        dayFirst: Boolean = AppFormats.dayFirstDates()
    ): MarkdownDocument {
        if (markdown.toByteArray(Charsets.UTF_8).size > MAX_MARKDOWN_BYTES) throw MarkdownImportTooLargeException()
        val lines = markdown.removePrefix("\uFEFF")
            .replace(Regex("<!--[\\s\\S]*?(?:-->|\\z)"), "")
            .replace('\u2028', '\n').replace('\u2029', '\n').lines()
        var scanFence: String? = null
        val visibleLines = lines.filter { line ->
            if (scanFence != null) {
                if (closesFence(line, scanFence!!)) scanFence = null
                false
            } else if (fence.containsMatchIn(line)) {
                scanFence = fence.find(line)!!.groupValues[1]
                false
            } else true
        }
        // Checklist documents often have descriptive bullets below tasks. Preserve those as
        // notes; in a bullet-only document the same markers represent tasks and subtasks.
        val checklistMode = visibleLines.any { checkbox.matches(it) }
        val hasListItems = visibleLines.any { bullet.matches(it) }
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
        var tableColumns: List<String>? = null
        var skipSetextUnderline = false

        lines.forEachIndexed { lineIndex, line ->
            if (activeFence != null) {
                current?.notes?.add(line)
                if (closesFence(line, activeFence!!)) activeFence = null
                return@forEachIndexed
            }
            fence.find(line)?.let {
                activeFence = it.groupValues[1]
                current?.notes?.add(line)
                return@forEachIndexed
            }
            if (skipSetextUnderline) { skipSetextUnderline = false; return@forEachIndexed }
            val setext = line.isNotBlank() && !bullet.matches(line) && '|' !in line &&
                !line.trimStart().startsWith('#') && lines.getOrNull(lineIndex + 1)?.matches(Regex("^ {0,3}(?:=+|-+)\\s*$")) == true
            val headingLine = if (setext) {
                val marker = if (lines[lineIndex + 1].trimStart().startsWith('=')) "#" else "##"
                "$marker ${line.trim()}"
            } else line
            heading.matchEntire(headingLine)?.let { match ->
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
                tableColumns = null
                skipSetextUnderline = setext
                return@forEachIndexed
            }
            val cells = if ('|' in line) tableCells(line) else emptyList()
            if (cells.isNotEmpty() && lines.getOrNull(lineIndex + 1)?.matches(tableSeparator) == true) {
                tableColumns = cells.map { plainTitle(it).lowercase() }.takeIf { columns -> columns.any { it in taskColumnNames } }
                current = null
                if (tableColumns == null) section.prelude += line
                return@forEachIndexed
            }
            if (tableColumns != null && tableSeparator.matches(line)) return@forEachIndexed
            val columns = tableColumns
            if (cells.isEmpty()) tableColumns = null
            val taskProperty = property.matchEntire(line)
            if (current != null && taskProperty != null &&
                (!bullet.matches(line) || indentation(line) > current!!.indent)) {
                val metadata = markdownMetadata(taskProperty.groupValues[1], taskProperty.groupValues[2])
                if (metadata != null) {
                    current!!.metadata += metadata
                    current!!.notes += line
                    return@forEachIndexed
                }
            }
            val checked = checkbox.matchEntire(line)
            val listed = if (!checklistMode) bullet.matchEntire(line) else null
            val item = when {
                checked != null -> Item(indentation(line), checked.groupValues[3].trim(), checked.groupValues[2].equals("x", true))
                columns != null && cells.isNotEmpty() -> tableItem(columns, cells)
                listed != null -> Item(indentation(line), listed.groupValues[2].trim(), false)
                !checklistMode && !hasListItems && line.isNotBlank() &&
                    !line.trimStart().startsWith('>') && '|' !in line &&
                    !line.trim().matches(Regex("[-*_]{3,}")) && !updated.matches(line) -> Item(0, line.trim(), false)
                else -> null
            }
            if (item != null) {
                val indent = item.indent
                val body = item.body
                val title = plainTitle(body)
                if (title.isBlank()) return@forEachIndexed
                val done = item.done
                val parent = current
                if (parent != null && indent > parent.indent) {
                    if (parent.subtasks.size >= MAX_MARKDOWN_SUBTASKS) throw MarkdownImportTooLargeException()
                    while (parent.stack.isNotEmpty() && parent.stack.last().first >= indent) parent.stack.removeAt(parent.stack.lastIndex)
                    parent.subtasks += MarkdownSubtask(title, done, parent.stack.lastOrNull()?.second)
                    parent.stack += indent to parent.subtasks.lastIndex
                } else {
                    if (tasks.size >= MAX_MARKDOWN_TASKS) throw MarkdownImportTooLargeException()
                    val due = explicitDue.find(title)?.groupValues?.get(1)?.let { dateIn(it, lastDate, referenceDate) }
                        ?: headings.lastOrNull { it.due != null }?.due
                    current = PendingTask(title, done, due, section, indent, body = body)
                    current!!.metadata += item.metadata
                    item.note?.let { current!!.notes += it }
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
        val parseMemo = mutableMapOf<String, ParsedQuickAdd>()
        val parseSmart: (String) -> ParsedQuickAdd = { text ->
            parseMemo.getOrPut(text) { NaturalLanguageParser.parse(text, referenceDate, referenceTime, dayFirst) }
        }
        return MarkdownDocument(documentTitle, referenceDate, tasks.map { task ->
            val notes = (listOf(sectionNotes(task.section), "- [${if (task.done) "x" else " "}] ${task.body}", task.notes.joinToString("\n").trim()) + references)
                .filter(String::isNotBlank).distinct().joinToString("\n\n").takeIf(String::isNotBlank)
            // Shared reference text is copied into task notes. Bound that expansion as well.
            totalNotesLength += notes?.length ?: 0
            if (totalNotesLength > MAX_MARKDOWN_BYTES * 4) throw MarkdownImportTooLargeException()
            var defaults = emptyMarkdownQuickAdd()
            task.section.headings.forEach { h ->
                val labelled = property.matchEntire(h.text)
                val input = labelled?.let { markdownMetadata(it.groupValues[1], it.groupValues[2]) } ?: h.text
                defaults = parseMarkdownSmartText(input, parseSmart).withMarkdownDefaults(defaults)
            }
            task.section.prelude.forEach { line ->
                property.matchEntire(line)?.let { field ->
                    markdownMetadata(field.groupValues[1], field.groupValues[2])?.let { metadata ->
                        defaults = parseSmart(metadata).withMarkdownDefaults(defaults)
                    }
                }
            }
            // Concrete heading dates retain their source year; inline task fields override them.
            defaults = defaults.copy(due = task.due?.toString() ?: defaults.due)
            var parsed = parseMarkdownSmartText(task.body, parseSmart)
            task.metadata.forEach { metadata ->
                parsed = parseSmart(metadata).withMarkdownDefaults(parsed).copy(title = parsed.title)
            }
            parsed = parsed.withMarkdownDefaults(defaults)
            MarkdownTask(task.title, task.done, task.due?.toString(), task.section.headings.lastOrNull()?.text,
                notes, task.subtasks.toList(), parsed, task.section.headings.map { plainTitle(it.text) })
        })
    }

    private fun indentation(line: String): Int = line.takeWhile(Char::isWhitespace)
        .fold(0) { width, char -> width + if (char == '\t') 4 else 1 }

    private fun tableCells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|")
        .split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }

    private fun tableItem(columns: List<String>, cells: List<String>): Item? {
        val titleIndex = columns.indexOfFirst { it in taskColumnNames }
        val body = cells.getOrNull(titleIndex)?.takeIf(String::isNotBlank) ?: return null
        val fields = columns.zip(cells).toMap()
        val status = fields["status"] ?: fields["done"] ?: fields["completed"]
        val done = status?.lowercase() in setOf("done", "completed", "true", "yes", "x", "[x]")
        val metadata = fields.mapNotNull { (label, value) -> markdownMetadata(label, value) }
        val notes = fields.filterKeys { it !in taskColumnNames }.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        return Item(0, body, done, metadata, notes)
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
