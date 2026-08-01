package com.mj.yata.util

import com.mj.yata.domain.model.Task

/**
 * Planned-effort helpers. Estimates are stored as whole minutes on [Task.estimateMinutes], with
 * null meaning "unestimated" — distinct from 0, so a day's planned total only sums tasks somebody
 * actually put a number against.
 */
object EstimateUtils {

    /** The presets offered on the task detail screen, in minutes. */
    val PRESETS: List<Int> = listOf(5, 15, 30, 60, 120, 240)

    /**
     * "45m", "2h", "2h 30m" — the compact form used on task rows and in the Today header.
     *
     * Deliberately not localized through a resource: these are numeral + unit-letter pairs that
     * every screen renders identically, and the alternative (a plural per unit, composed at each
     * call site) buys nothing until the app actually ships a second language. Flagged here so
     * whoever adds one knows this is a spot to revisit rather than an oversight.
     */
    fun format(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours == 0 -> "${mins}m"
            mins == 0 -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }

    /**
     * Total planned minutes across [tasks], ignoring unestimated ones entirely.
     *
     * Returns null when nothing in the list carries an estimate, so callers can hide the whole
     * capacity readout rather than show a confident "0m planned" for a day nobody has estimated —
     * an empty plan and a zero-effort plan look the same in a number but mean opposite things.
     */
    fun plannedMinutes(tasks: List<Task>): Int? {
        val estimates = tasks.mapNotNull { it.estimateMinutes }
        return if (estimates.isEmpty()) null else estimates.sum()
    }

    /** How many of [tasks] still have no estimate — drives the "3 unestimated" hint. */
    fun unestimatedCount(tasks: List<Task>): Int = tasks.count { it.estimateMinutes == null }
}
