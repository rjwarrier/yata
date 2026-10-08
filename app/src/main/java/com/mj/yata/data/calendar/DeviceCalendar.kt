package com.mj.yata.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One occurrence of a device calendar event, shown alongside tasks. YATA only ever reads these. */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int
)

object DeviceCalendar {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Occurrences from every visible calendar overlapping [from]..[to] (local dates, inclusive),
     * keyed by each local day they cover, so a three-day event shows on all three. Reads
     * Instances rather than Events, which means recurring events arrive already expanded. Empty
     * without the permission, or if the provider refuses the query.
     */
    fun eventsByDay(context: Context, from: LocalDate, to: LocalDate): Map<LocalDate, List<CalendarEvent>> {
        if (!hasPermission(context)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.atStartOfDay(zone).toInstant().toEpochMilli())
            ContentUris.appendId(it, to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR
        )
        val byDay = sortedMapOf<LocalDate, MutableList<CalendarEvent>>()
        runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, null)
        }.getOrNull()?.use { cursor ->
            while (cursor.moveToNext()) {
                val event = CalendarEvent(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = cursor.getLong(2),
                    end = cursor.getLong(3),
                    allDay = cursor.getInt(4) == 1,
                    color = cursor.getInt(5)
                )
                // All-day events are stored at UTC midnight with an exclusive end; timed ones are
                // instants to read in the device's zone.
                val eventZone = if (event.allDay) ZoneOffset.UTC else zone
                val first = Instant.ofEpochMilli(event.begin).atZone(eventZone).toLocalDate()
                val last = Instant.ofEpochMilli(maxOf(event.begin, event.end - 1)).atZone(eventZone).toLocalDate()
                var day = maxOf(first, from)
                while (!day.isAfter(minOf(last, to))) {
                    byDay.getOrPut(day) { mutableListOf() } += event
                    day = day.plusDays(1)
                }
            }
        }
        return byDay.mapValues { (_, events) -> events.sortedWith(compareBy({ !it.allDay }, { it.begin })) }
    }

    /** Opens the event in the user's calendar app, at this occurrence. */
    fun openIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
}
