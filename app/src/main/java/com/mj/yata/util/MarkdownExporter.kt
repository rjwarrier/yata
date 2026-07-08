package com.mj.yata.util

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
    personStats: List<EntityStat>,
    tagStats: List<EntityStat>
): String {
    val sb = StringBuilder()
    sb.append("# YATA Analytics — $periodLabel\n\n")
    sb.append("$doneCount of $totalCount tasks completed. $overdueCount overdue.\n\n")

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
    if (personStats.isNotEmpty()) {
        sb.append("## By Person\n")
        personStats.forEach { stat ->
            val onTime = stat.onTimeRate?.let { " · ${(it * 100).toInt()}% on-time" } ?: ""
            sb.append("- ${stat.name}: ${stat.done}/${stat.total}, ${stat.overdue} overdue$onTime\n")
        }
        sb.append("\n")
    }
    if (tagStats.isNotEmpty()) {
        sb.append("## By Tag\n")
        tagStats.forEach { sb.append("- ${it.name}: ${it.done}/${it.total}, ${it.overdue} overdue\n") }
    }
    return sb.toString()
}

/** Renders a project's pending tasks as a plain markdown checklist, for clipboard/share export. */
fun buildPendingTasksMarkdown(project: Project, tasks: List<Task>): String {
    val pending = tasks.filter { !it.done }.sortedBy { it.sortOrder }
    val sb = StringBuilder()
    sb.append("# ${project.name}\n\n")
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
