package com.mj.yata.util

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Task

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
