package com.mj.yata.util

import com.mj.yata.R
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Task

/** Renders the Analytics screen's current period breakdown as plain markdown, for
 * clipboard/share export — lets a managing partner hand a period's on-time/overdue
 * breakdown to someone outside the app. */
fun buildAnalyticsMarkdown(
    periodLabel: String,
    totalCount: Int,
    doneCount: Int,
    overdueCount: Int,
    priorityStats: List<PriorityStat>,
    projectStats: List<EntityStat>,
    tagStats: List<EntityStat>,
    overallOnTimeRate: Float? = null,
    agingBuckets: List<AgingBucket> = emptyList(),
    dueNext7: Int = 0,
    dueNext30: Int = 0
): String {
    val sb = StringBuilder()
    sb.append("# YATA Analytics — $periodLabel\n\n")
    sb.append("$doneCount of $totalCount tasks completed. $overdueCount overdue.\n\n")
    overallOnTimeRate?.let { sb.append("Overall on-time rate: ${(it * 100).toInt()}%. ") }
    sb.append("Due in next 7 days: $dueNext7. Due in next 30 days: $dueNext30.\n\n")

    if (agingBuckets.isNotEmpty()) {
        sb.append("## Overdue Aging\n")
        agingBuckets.forEach { sb.append("- ${it.label}: ${it.count}\n") }
        sb.append("\n")
    }
    if (priorityStats.isNotEmpty()) {
        sb.append("## By Priority\n")
        priorityStats.forEach { sb.append("- ${it.priority}: ${it.done}/${it.total}\n") }
        sb.append("\n")
    }
    if (projectStats.isNotEmpty()) {
        sb.append("## By Project\n")
        projectStats.forEach { sb.append("- ${it.name}: ${it.done}/${it.total}\n") }
        sb.append("\n")
    }
    if (tagStats.isNotEmpty()) {
        sb.append("## By Tag\n")
        tagStats.forEach { sb.append("- ${it.name}: ${it.done}/${it.total}, ${it.overdue} overdue\n") }
    }
    return sb.toString()
}

/**
 * Copies a markdown checklist of [tasks] to the clipboard and opens the share sheet.
 *
 * The copy *and* the share both happen, deliberately: the share sheet can be dismissed, and the
 * clipboard is what makes that not a wasted trip. This lives here rather than in each screen
 * because all three did the same six lines, one of which — the share-sheet title — was the only
 * part that varied and was hardcoded English.
 */
fun shareTasksAsMarkdown(context: android.content.Context, heading: String, tasks: List<Task>) {
    val markdown = buildPendingTasksMarkdown(heading, tasks)
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    // getString rather than stringResource: this is called from an onClick, not a composable.
    clipboard.setPrimaryClip(
        android.content.ClipData.newPlainText(context.getString(R.string.export_pending_tasks), markdown)
    )
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            shareIntent,
            context.getString(R.string.export_share_tasks_title, heading)
        )
    )
}

/** Renders a project's pending tasks as a plain markdown checklist, for clipboard/share export. */
fun buildPendingTasksMarkdown(project: Project, tasks: List<Task>): String =
    buildPendingTasksMarkdown(project.name, tasks)

/**
 * The same checklist for anything a list of tasks can be grouped under — a project, a tag, a
 * person. Takes a heading rather than an entity so the three don't need three copies of it; the
 * output is identical either way, since a markdown checklist has nothing entity-specific in it.
 */
fun buildPendingTasksMarkdown(heading: String, tasks: List<Task>): String {
    val pending = tasks.filter { !it.done }.sortedBy { it.sortOrder }
    val sb = StringBuilder()
    sb.append("# $heading\n\n")
    if (pending.isEmpty()) {
        sb.append("_No pending tasks._\n")
    } else {
        pending.forEach { task ->
            sb.append("- [ ] ${task.title}")
            if (!task.due.isNullOrBlank()) {
                sb.append(" (due ${TaskScheduleUtils.formatDueDate(task.due)})")
            }
            sb.append("\n")
        }
    }
    return sb.toString()
}
