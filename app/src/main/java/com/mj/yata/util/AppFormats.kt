package com.mj.yata.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mj.yata.domain.model.DateFormat
import com.mj.yata.domain.model.TimeFormat
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The date/time display settings, readable from anywhere.
 *
 * Formatting happens in [TaskScheduleUtils], which is a plain object called from widgets,
 * notification receivers, WorkManager jobs and the Tasker plugin as well as from Compose — so the
 * settings can't live in a `CompositionLocal` or a ViewModel, or three of those four callers would
 * have no way to reach them.
 *
 * They are Compose *snapshot* state even so. Reading one inside composition registers a snapshot
 * read, so every screen already showing a date re-renders the moment the setting changes, without
 * a single call site having to subscribe to anything. Reading from a widget or a receiver, outside
 * composition, just returns the current value. [YataApplication] owns the single writer.
 */
object AppFormats {

    var timeFormat: TimeFormat by mutableStateOf(TimeFormat.SYSTEM)
        private set

    var dateFormat: DateFormat by mutableStateOf(DateFormat.SYSTEM)
        private set

    /**
     * What the device's own 12/24-hour setting says. Seeded at startup and refreshed when the app
     * comes back to the foreground, since it can be changed in system settings while we're away.
     */
    var systemUses24Hour: Boolean by mutableStateOf(false)
        private set

    fun update(timeFormat: TimeFormat, dateFormat: DateFormat) {
        this.timeFormat = timeFormat
        this.dateFormat = dateFormat
    }

    fun updateSystemClock(is24Hour: Boolean) {
        systemUses24Hour = is24Hour
    }

    fun uses24Hour(): Boolean = when (timeFormat) {
        TimeFormat.SYSTEM -> systemUses24Hour
        TimeFormat.TWELVE_HOUR -> false
        TimeFormat.TWENTY_FOUR_HOUR -> true
    }

    /** [DateFormat.SYSTEM] resolved against the current locale; never returns SYSTEM itself. */
    fun resolvedDateFormat(): DateFormat = when (dateFormat) {
        DateFormat.SYSTEM -> if (localeIsDayFirst()) DateFormat.DAY_FIRST else DateFormat.MONTH_FIRST
        else -> dateFormat
    }

    /** True when smart add should read a bare "3/4" as the 3rd of April rather than March 4th. */
    fun dayFirstDates(): Boolean = resolvedDateFormat() != DateFormat.MONTH_FIRST

    /**
     * Asks the locale which of the day and the month it writes first, by looking at where each
     * lands in its own medium date pattern. This is the part `Locale.getDefault()` on a hardcoded
     * pattern could never give us — it localizes the names, not the running order.
     */
    private fun localeIsDayFirst(): Boolean {
        val pattern = runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.MEDIUM,
                null,
                IsoChronology.INSTANCE,
                Locale.getDefault()
            )
        }.getOrNull() ?: return false
        val dayAt = pattern.indexOf('d')
        val monthAt = pattern.indexOfFirst { it == 'M' || it == 'L' }
        if (dayAt < 0 || monthAt < 0) return false
        return dayAt < monthAt
    }

    // Patterns are rebuilt per call rather than cached, because the setting they depend on can
    // change at runtime and DateTimeFormatter construction is cheap next to the recomposition
    // that's already happening around it.

    fun timeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (uses24Hour()) "HH:mm" else "h:mm a", Locale.getDefault())

    /** Weekday plus day and month, e.g. "Sat, 4 Jul" or "Sat, Jul 4". */
    fun shortDateFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(
        when (resolvedDateFormat()) {
            DateFormat.DAY_FIRST -> "EEE, d MMM"
            DateFormat.ISO -> "EEE, MM-dd"
            else -> "EEE, MMM d"
        },
        Locale.getDefault()
    )

    /** Day, month and year, e.g. "4 Jul 2026" or "Jul 4, 2026". */
    fun longDateFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(
        when (resolvedDateFormat()) {
            DateFormat.DAY_FIRST -> "d MMM yyyy"
            DateFormat.ISO -> "yyyy-MM-dd"
            else -> "MMM d, yyyy"
        },
        Locale.getDefault()
    )

    /** Full weekday and date, for screen headers, e.g. "Saturday, 4 July". */
    fun headerDateFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(
        when (resolvedDateFormat()) {
            DateFormat.DAY_FIRST -> "EEEE, d MMMM"
            DateFormat.ISO -> "EEEE, MM-dd"
            else -> "EEEE, MMMM d"
        },
        Locale.getDefault()
    )

    /** Day and month only, no weekday, e.g. "4 Jul". */
    fun dayMonthFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(
        when (resolvedDateFormat()) {
            DateFormat.DAY_FIRST -> "d MMM"
            DateFormat.ISO -> "MM-dd"
            else -> "MMM d"
        },
        Locale.getDefault()
    )
}
