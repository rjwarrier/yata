package com.mj.yata.domain.model

import java.time.LocalDate

/** Longest label persisted for a holiday. Bounds DataStore/backup payload size against a
 * pathologically long paste — mirrors the per-field caps [com.mj.yata.util.export.TaskTransferLink]
 * already applies to shared-task text fields for the same reason. */
const val MAX_HOLIDAY_LABEL_LENGTH = 120

/** A user-entered non-working day (national/regional holiday), independent of [DEFAULT_WEEKEND_DAYS].
 * No bundled or fetched holiday data exists — country/region coverage and movable-date holidays
 * (Diwali, Eid, Easter, ...) vary too much to compute offline, so the user maintains their own list.
 * [date] is an ISO "YYYY-MM-DD" string, matching [Task.due] — for a [recurring] holiday it's just the
 * anchor year the entry was created with; matching against a task's due date ignores the year and
 * compares month-day only, so a fixed-date holiday (Independence Day) never needs re-adding, while a
 * movable one (Diwali, Easter) is added as non-recurring and re-added each year with its real date. */
data class Holiday(
    val date: String,
    val label: String,
    val recurring: Boolean = false
) {
    fun encode(): String =
        "$date|${if (recurring) "1" else "0"}|${label.trim().replace("|", " ").take(MAX_HOLIDAY_LABEL_LENGTH)}"

    /** True when [dateIso] (a task's due date) falls on this holiday — exact date match normally,
     * or same month-day regardless of year when [recurring]. Both sides are already validated ISO
     * dates by the time a [Holiday] exists ([decode] rejects anything that isn't), so a plain
     * substring compare is safe here without re-parsing on every call. */
    fun matches(dateIso: String): Boolean =
        if (recurring) monthDay(dateIso) == monthDay(date) else dateIso == date

    /** True when this holiday and [other] would collide as the same calendar identity — either
     * one's stored [date] falls on the other's occurrence. Used to dedupe on add so a recurring
     * holiday re-saved from a different displayed year (or a one-off that happens to land on an
     * existing recurring day) replaces the existing entry instead of creating a second one that
     * would independently match and double up the warning/marker. */
    fun conflictsWith(other: Holiday): Boolean = matches(other.date) || other.matches(date)

    private fun monthDay(iso: String) = iso.takeLast(5)

    companion object {
        /** O(1)-per-lookup index over a holiday list, in place of scanning the whole list per
         * calendar cell — cheap either way at realistic list sizes, but a 6-row grid still means
         * up to 42 lookups per composition (more with the due-date picker's month nav), and this
         * makes that cost independent of how many holidays are configured. */
        fun index(holidays: List<Holiday>): (String) -> Holiday? {
            val byExactDate = holidays.filter { !it.recurring }.associateBy { it.date }
            val byMonthDay = holidays.filter { it.recurring }.associateBy { it.date.takeLast(5) }
            return { dateIso -> byExactDate[dateIso] ?: byMonthDay[dateIso.takeLast(5)] }
        }

        fun decode(raw: String): Holiday? {
            val parts = raw.split("|")
            if (parts.size < 2) return null
            val date = parts[0].trim()
            if (date.isBlank() || runCatching { LocalDate.parse(date) }.isFailure) return null
            return if (parts.size >= 3) {
                val label = parts[2].trim().take(MAX_HOLIDAY_LABEL_LENGTH)
                if (label.isBlank()) return null
                Holiday(date, label, recurring = parts[1] == "1")
            } else {
                // Legacy 2-field encoding (date|label), predating the recurring flag.
                val label = parts[1].trim().take(MAX_HOLIDAY_LABEL_LENGTH)
                if (label.isBlank()) return null
                Holiday(date, label)
            }
        }
    }
}
