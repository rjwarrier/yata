package com.mj.yata.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

object TaskScheduleUtils {
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The format times are *stored* in. `Task.time` is a display-shaped string in the database, so
     * this has to stay fixed no matter what the user's clock preference is: every task written
     * before the preference existed is in this form, and the 24-hour setting must never rewrite
     * them. [displayTime] converts on the way to the screen instead.
     */
    private val storageTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

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
     */
    fun parseTime(timeString: String?): LocalTime? {
        if (timeString.isNullOrBlank()) return null
        return try {
            LocalTime.parse(timeString)
        } catch (_: DateTimeParseException) {
            try {
                LocalTime.parse(timeString, storageTimeFormatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }
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
