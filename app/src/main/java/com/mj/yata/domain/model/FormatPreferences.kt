package com.mj.yata.domain.model

/**
 * Whether clock times are shown as "5:00 PM" or "17:00".
 *
 * [SYSTEM] follows the device's own 12/24-hour setting, which is what almost everyone wants and
 * why it's the default. The explicit stops exist because the device setting is buried and some
 * people want the app to disagree with it.
 *
 * This is a *display* preference only. Times are persisted in 12-hour form (see
 * `TaskScheduleUtils.formatTime`) regardless of what's set here — a stored task carries the same
 * string whichever way it's being rendered, so switching the setting can never rewrite data.
 */
enum class TimeFormat {
    SYSTEM,
    TWELVE_HOUR,
    TWENTY_FOUR_HOUR
}

/**
 * The order the parts of a date are written in.
 *
 * [SYSTEM] resolves to [DAY_FIRST] or [MONTH_FIRST] by asking the current locale, so a UK or
 * Indian device reads "20 Jul" and a US one reads "Jul 20" with nothing to configure. The app used
 * to hardcode month-first everywhere, which `Locale.getDefault()` did not fix: the locale changes
 * the month's *name*, not the order of the fields around it.
 *
 * This also settles what smart add does with an ambiguous typed date — "3/4" is the 3rd of April
 * under [DAY_FIRST] and the 4th of March under [MONTH_FIRST].
 */
enum class DateFormat {
    SYSTEM,
    DAY_FIRST,
    MONTH_FIRST,
    ISO
}
