package com.mj.yata.util

import java.time.Instant
import java.time.LocalDate
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
}
