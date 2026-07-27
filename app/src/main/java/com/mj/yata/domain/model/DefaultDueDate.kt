package com.mj.yata.domain.model

import java.time.LocalDate

/**
 * What due date a brand-new task starts with. Previously hardcoded to today, which quietly made
 * every task ever created due immediately — fine for a daily-driver list, wrong for anyone using
 * projects as a backlog.
 *
 * [NONE] leaves the task undated; it then appears in its list/project but not in Today.
 */
enum class DefaultDueDate {
    TODAY, TOMORROW, NONE;

    /** Resolved against [today] so callers stay testable rather than reading the clock inline. */
    fun resolve(today: LocalDate = LocalDate.now()): String? = when (this) {
        TODAY -> today.toString()
        TOMORROW -> today.plusDays(1).toString()
        NONE -> null
    }
}
