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
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val longDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

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
                if (date.year == today.year) shortDateFormatter.format(date) else longDateFormatter.format(date)
            }
        }
    }

    fun formatDueDateTime(dateString: String?, timeString: String?): String {
        val dueDate = formatDueDate(dateString)
        if (dateString == null || timeString.isNullOrBlank()) return dueDate
        return "$dueDate at $timeString"
    }

    fun formatReminder(reminder: String?): String = reminder ?: "None"

    fun parseDate(dateString: String?): LocalDate? {
        if (dateString.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateString, isoDateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseTime(timeString: String?): LocalTime? {
        if (timeString.isNullOrBlank()) return null
        return try {
            LocalTime.parse(timeString)
        } catch (_: DateTimeParseException) {
            try {
                LocalTime.parse(timeString, displayTimeFormatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        return LocalTime.of(hour, minute).format(displayTimeFormatter)
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
}
