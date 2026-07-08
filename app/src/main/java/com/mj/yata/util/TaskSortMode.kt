package com.mj.yata.util

import com.mj.yata.domain.model.Task

/** View-only sort override for a task list — doesn't touch [Task.sortOrder] or persist anywhere;
 * switching back to [MANUAL] simply resumes the screen's normal drag-ordered display. */
enum class TaskSortMode { MANUAL, DUE_DATE, PRIORITY, ALPHABETICAL }

private val priorityRank = mapOf("high" to 0, "med" to 1, "low" to 2, "none" to 3)

fun List<Task>.sortedByMode(mode: TaskSortMode): List<Task> = when (mode) {
    TaskSortMode.MANUAL -> this
    TaskSortMode.DUE_DATE -> sortedWith(compareBy(nullsLast()) { it.due })
    TaskSortMode.PRIORITY -> sortedBy { priorityRank[it.priority] ?: 4 }
    TaskSortMode.ALPHABETICAL -> sortedBy { it.title.lowercase() }
}
