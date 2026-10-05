package com.mj.yata.util.nl

import com.mj.yata.domain.model.Recurrence
import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.QuickAddHighlightSpan
import java.time.LocalDate

internal data class ParseState(
    val due: LocalDate?,
    val startDate: LocalDate?,
    val time: String?,
    val recurrence: Recurrence?,
    val reminder: String?,
    val priority: String?,
    val flag: Boolean,
    val projectName: String?,
    val listName: String?,
    val tagNames: List<String>,
    val assigneeNames: List<String>
) {
    fun toParsedQuickAdd(
        title: String,
        highlightRanges: List<IntRange>,
        highlightSpans: List<QuickAddHighlightSpan>
    ): ParsedQuickAdd =
        ParsedQuickAdd(
            title = title,
            due = due?.toString(),
            startDate = startDate?.toString(),
            time = time,
            recurrence = recurrence,
            reminder = reminder,
            priority = priority,
            flag = flag,
            projectName = projectName,
            listName = listName,
            tagNames = tagNames,
            assigneeNames = assigneeNames,
            highlightRanges = highlightRanges,
            highlightSpans = highlightSpans
        )
}
