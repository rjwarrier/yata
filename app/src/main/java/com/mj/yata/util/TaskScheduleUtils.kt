package com.mj.yata.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

object TaskScheduleUtils {
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The format times are *stored* in. `Task.time` is a display-shaped string in the database, so
     * this has to stay fixed no matter what the user's clock preference is: every task written
     * before the preference existed is in this form, and the 24-hour setting must never rewrite
     * them. [displayTime] converts on the way to the screen instead.
     */
    private val storageTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    /**
     * Parse-only companions to [storageTimeFormatter], both case-insensitive.
     *
     * `Task.time` is written by two paths that disagree about case: [formatTime] emits whatever
     * the default locale produces (on en-IN that is lowercase "3:00 pm"), while the natural
     * language parser's own writer uppercases the result ("3:00 PM"). A case-sensitive parse
     * therefore rejected roughly half the rows on any locale whose AM/PM marker isn't already
     * uppercase — silently, because [displayTime] falls back to echoing the raw stored text, so
     * the times still *looked* right while everything that needed the parsed value (reminder
     * offset checks, and now the due countdown) quietly took its "no time set" branch.
     *
     * [FALLBACK] additionally pins Locale.US so the canonical English "3:00 PM" a backup, shared
     * task link, or NL parse can carry still parses on a device whose locale renders AM/PM
     * differently. Both are parse-only; formatting still goes through [storageTimeFormatter], so
     * nothing about what gets *written* changes here.
     */
    private val storageTimeParser: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("h:mm a")
        .toFormatter(Locale.getDefault())

    private val storageTimeParserFallback: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("h:mm a")
        .toFormatter(Locale.US)

    private fun shortDateFormatter() = AppFormats.shortDateFormatter()
    private fun longDateFormatter() = AppFormats.longDateFormatter()

    val reminderOptions = listOf(
        "At time",
        "5 min before",
        "15 min before",
        "30 min before",
        "1 hour before",
        "1 day before"
    )

    fun formatDueDate(dateString: String?): String {
        val date = parseDate(dateString) ?: return "No due date"
        val today = LocalDate.now()
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> {
                if (date.year == today.year) shortDateFormatter().format(date) else longDateFormatter().format(date)
            }
        }
    }

    fun formatDueDateTime(dateString: String?, timeString: String?): String {
        val dueDate = formatDueDate(dateString)
        if (dateString == null || timeString.isNullOrBlank()) return dueDate
        return "$dueDate at ${displayTime(timeString)}"
    }

    /**
     * A stored time string rendered the way the user asked for it. Falls back to the stored text
     * when it can't be parsed, so an unexpected value degrades to showing something rather than
     * blanking the time out.
     */
    fun displayTime(storedTime: String?): String? {
        if (storedTime.isNullOrBlank()) return storedTime
        val parsed = parseTime(storedTime) ?: return storedTime
        return parsed.format(AppFormats.timeFormatter())
    }

    /** As [formatTime], but for showing rather than storing. */
    fun displayTime(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(AppFormats.timeFormatter())

    fun formatReminder(reminder: String?): String = reminder ?: "None"

    /**
     * How far off a task's due date/time is. [span] is the bare magnitude ("2h 15m", "3d"); the
     * caller wraps it in the localized "in %s" / "Overdue by %s" phrasing and picks a color off
     * [isOverdue] — deliberately not a pre-formatted sentence, since deciding "is this late?" by
     * string-matching the rendered text breaks the moment the text is translated.
     */
    data class DueCountdown(val isOverdue: Boolean, val span: String)

    /**
     * Countdown to [dueDate]/[dueTime], or null when there's nothing useful to count down to.
     *
     * Granularity follows what the user actually specified. With a time, the countdown is exact
     * ("2h 15m"). *Without* one, it is whole calendar days ("3d") — an untimed task has no hour
     * attached to it, so anything finer would be reporting the precision of an internal
     * end-of-day sentinel rather than something the user typed. That also makes "due tomorrow"
     * read as "in 1d" all day instead of counting 37h down to 14h as the current day wears on.
     *
     * Returns null for an untimed task due today: there is no sub-day answer to give, and the
     * "Due today" badge already says it.
     */
    fun dueCountdown(dueDate: String?, dueTime: String?, now: LocalDateTime = LocalDateTime.now()): DueCountdown? {
        val date = parseDate(dueDate) ?: return null
        val time = dueTime?.let { parseTime(it) }

        if (time == null) {
            val days = ChronoUnit.DAYS.between(now.toLocalDate(), date)
            if (days == 0L) return null
            return DueCountdown(isOverdue = days < 0, span = "${abs(days)}d")
        }

        val duration = Duration.between(now, date.atTime(time))
        val overdue = duration.isNegative
        val magnitude = if (overdue) duration.negated() else duration
        val totalHours = magnitude.toHours()
        val totalMinutes = magnitude.toMinutes()
        val span = when {
            magnitude.toDays() >= 1L -> {
                val days = magnitude.toDays()
                val hours = totalHours % 24
                if (hours > 0) "${days}d ${hours}h" else "${days}d"
            }
            totalHours >= 1L -> {
                val minutes = totalMinutes % 60
                if (minutes > 0) "${totalHours}h ${minutes}m" else "${totalHours}h"
            }
            totalMinutes >= 1L -> "${totalMinutes}m"
            else -> "<1m"
        }
        return DueCountdown(isOverdue = overdue, span = span)
    }

    fun formatCompletedAt(completedAt: Long?): String {
        if (completedAt == null) return ""
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(completedAt), ZoneId.systemDefault())
        val date = dateTime.toLocalDate()
        val today = LocalDate.now()
        val time = AppFormats.timeFormatter().format(dateTime)
        val dayLabel = when (ChronoUnit.DAYS.between(today, date)) {
            0L -> return "Completed today at $time"
            -1L -> "yesterday"
            else -> if (date.year == today.year) shortDateFormatter().format(date) else longDateFormatter().format(date)
        }
        return "Completed $dayLabel at $time"
    }

    fun parseDate(dateString: String?): LocalDate? {
        if (dateString.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateString, isoDateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Accepts both the ISO form and the stored 12-hour form. Both are tried regardless of the
     * user's clock preference: the database holds 12-hour strings, and a task saved before the
     * preference existed has to keep parsing after it's switched to 24-hour.
     *
     * The 12-hour attempts are case-insensitive and fall back to a US-locale marker — see
     * [storageTimeParser] for the two-writers-disagree-on-case problem that requires.
     */
    fun parseTime(timeString: String?): LocalTime? {
        if (timeString.isNullOrBlank()) return null
        val trimmed = timeString.trim()
        // Some locales/JDKs render the AM/PM separator as a narrow no-break space; normalize it
        // so a value formatted on one device still parses on another.
        val normalized = trimmed.replace(Regex("""\p{Zs}"""), " ")
        for (candidate in listOf(trimmed, normalized).distinct()) {
            try {
                return LocalTime.parse(candidate)
            } catch (_: DateTimeParseException) {
                // Not ISO — fall through to the 12-hour attempts below.
            }
            for (formatter in listOf(storageTimeParser, storageTimeParserFallback)) {
                try {
                    return LocalTime.parse(candidate, formatter)
                } catch (_: DateTimeParseException) {
                    // Try the next formatter/candidate.
                }
            }
        }
        return null
    }

    /** The canonical string written to `Task.time`. See [storageTimeFormatter]. */
    fun formatTime(hour: Int, minute: Int): String {
        return LocalTime.of(hour, minute).format(storageTimeFormatter)
    }

    fun dateToPickerMillis(dateString: String?): Long? {
        val date = parseDate(dateString) ?: return null
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun pickerMillisToDateString(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(isoDateFormatter)
    }

    fun reminderOffsetMillis(reminder: String?): Long {
        return when (reminder) {
            "At time" -> 0L
            "5 min before" -> 5L * 60_000L
            "15 min before" -> 15L * 60_000L
            "30 min before" -> 30L * 60_000L
            "1 hour before" -> 60L * 60_000L
            "1 day before" -> 24L * 60L * 60_000L
            else -> 0L
        }
    }

    /**
     * A custom reminder is a literal clock time on the task's due date (see [ReminderScheduler]) —
     * it only makes sense strictly before the due time, otherwise the "reminder" would fire after
     * (or exactly at, if no due time is set — treated as end of day) the task is already due.
     */
    fun isCustomReminderBeforeDue(customReminderTime: String, dueTime: String?): Boolean {
        val reminderClock = parseTime(customReminderTime) ?: return false
        val dueClock = parseTime(dueTime) ?: LocalTime.of(23, 59)
        return reminderClock.isBefore(dueClock)
    }

    /**
     * ReminderScheduler fires custom reminders at [dueDate] + [customReminderTime]; if that instant
     * has already passed by the time the alarm gets scheduled, it silently drops the reminder with
     * no user-visible error. Check this at input time (where we can actually warn the user) instead.
     */
    fun isReminderTimeInFuture(dueDate: String?, customReminderTime: String): Boolean {
        val date = parseDate(dueDate) ?: return true
        val time = parseTime(customReminderTime) ?: return false
        return date.atTime(time).isAfter(LocalDateTime.now())
    }

    /**
     * Same check as [isReminderTimeInFuture], but for a preset relative reminder ("15 min
     * before", etc.) instead of a literal custom time — the preset picker used to skip this
     * check entirely, silently accepting a reminder on an already-overdue task that would never
     * actually fire.
     */
    fun isPresetReminderInFuture(dueDate: String?, dueTime: String?, reminder: String): Boolean {
        val date = parseDate(dueDate) ?: return true
        val time = parseTime(dueTime) ?: LocalTime.of(23, 59)
        val fireInstant = date.atTime(time).minusNanos(reminderOffsetMillis(reminder) * 1_000_000L)
        return fireInstant.isAfter(LocalDateTime.now())
    }
}
