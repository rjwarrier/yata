package com.mj.yata.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utility functions for formatting dates into human-readable relative strings.
 */
object DateFormatter {

    private val dayOfWeekFmt = SimpleDateFormat("EEE", Locale.getDefault())
    private val shortDateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    private val fullDateFmt  = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val timeFmt      = SimpleDateFormat("h:mm a", Locale.getDefault())

    /**
     * Returns a relative date label:
     * "Today", "Tomorrow", "Yesterday", weekday name within 7 days,
     * or a short date like "15 Apr" / "15 Apr 2024".
     */
    fun formatDate(epochMs: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = epochMs }

        val nowYear  = now.get(Calendar.YEAR)
        val nowDay   = now.get(Calendar.DAY_OF_YEAR)
        val tgtYear  = target.get(Calendar.YEAR)
        val tgtDay   = target.get(Calendar.DAY_OF_YEAR)

        return when {
            tgtYear == nowYear && tgtDay == nowDay         -> "Today"
            tgtYear == nowYear && tgtDay == nowDay + 1     -> "Tomorrow"
            tgtYear == nowYear && tgtDay == nowDay - 1     -> "Yesterday"
            tgtYear == nowYear && tgtDay in nowDay + 2..nowDay + 6 -> dayOfWeekFmt.format(Date(epochMs))
            tgtYear == nowYear                              -> shortDateFmt.format(Date(epochMs))
            else                                            -> fullDateFmt.format(Date(epochMs))
        }
    }

    /** Returns time string like "3:30 PM". */
    fun formatTime(epochMs: Long): String = timeFmt.format(Date(epochMs))

    /** Epoch-ms for start of today (midnight). */
    fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Epoch-ms for end of today (23:59:59.999). */
    fun endOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /** Epoch-ms for end of today + [days] days. */
    fun endOfDayPlusDays(days: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /** Start of today minus [days] days. */
    fun startOfDayMinusDays(days: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Format total seconds as "Xh Ym" or "Ym" if under an hour. */
    fun formatDuration(totalSeconds: Long): String {
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0  -> "${hours}h ${minutes}m"
            else       -> "${minutes}m"
        }
    }

    /** Short relative time like "2h ago", "yesterday", "Mon". */
    fun formatRelative(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        val mins = diff / 60_000
        val hours = mins / 60
        val days = hours / 24
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            hours < 24 -> "${hours}h ago"
            days < 2 -> "yesterday"
            days < 7 -> dayOfWeekFmt.format(Date(epochMs))
            else -> shortDateFmt.format(Date(epochMs))
        }
    }

    /** Format epoch-ms as "dd MMM, h:mm a" for session history items. */
    fun formatSessionTime(epochMs: Long): String {
        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }
}
