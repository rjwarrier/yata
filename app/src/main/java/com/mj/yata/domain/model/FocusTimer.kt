package com.mj.yata.domain.model

/**
 * The one running focus timer, if any. Device-local (see UserPreferences.NON_PORTABLE_KEYS): a
 * timer started on this phone means nothing on another device, and only its logged minutes
 * ([Task.trackedMinutes]) are synced.
 */
data class FocusTimer(val taskId: String, val startedAt: Long)

// ponytail: one session logs at most 12h, so a timer left running overnight can't add a day.
// Anything it still gets wrong can be corrected with the tracked-time editor on task detail.
const val MAX_FOCUS_SESSION_MINUTES = 12 * 60

/** Whole minutes to log for a session from [startedAt] to [now], rounded to the nearest minute. */
fun focusSessionMinutes(startedAt: Long, now: Long): Int {
    val elapsed = (now - startedAt).coerceAtLeast(0)
    return ((elapsed + 30_000) / 60_000).coerceAtMost(MAX_FOCUS_SESSION_MINUTES.toLong()).toInt()
}
