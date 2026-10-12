package com.mj.yata

import android.net.Uri
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.export.buildCalendarImportLink
import com.mj.yata.util.export.parseIcsTasks
import com.mj.yata.util.export.parseTransferLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class IcsTaskImportTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test
    fun utcEventBecomesLocalDueTimeWithUnfoldedNotes() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART:20261012T083000Z
            SUMMARY:Team sync\, weekly
            DESCRIPTION;ALTREP="cid:x@y":Agenda:\n1. Plans
             and more
            BEGIN:VALARM
            DESCRIPTION:Reminder
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val task = parseIcsTasks(ics, kolkata).single()
        assertEquals("Team sync, weekly", task.title)
        assertEquals("2026-10-12", task.due)
        assertEquals(TaskScheduleUtils.formatTime(14, 0), task.time)
        assertEquals("Agenda:\n1. Plansand more", task.notes)
    }

    @Test
    fun allDayTzidTodoAndRecurrenceOverride() {
        val ics = """
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261231
            SUMMARY:New Year's Eve
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/New_York:20261012T090000
            SUMMARY:Standup
            END:VEVENT
            BEGIN:VEVENT
            RECURRENCE-ID:20261013T090000Z
            SUMMARY:Standup (moved)
            END:VEVENT
            BEGIN:VTODO
            DTSTART:20261001T100000
            DUE:20261020T170000
            SUMMARY:File taxes
            END:VTODO
            BEGIN:VEVENT
            DTSTART:20261012T090000
            END:VEVENT
        """.trimIndent()

        val tasks = parseIcsTasks(ics, kolkata)
        assertEquals(listOf("New Year's Eve", "Standup", "File taxes"), tasks.map { it.title })
        assertEquals("2026-12-31", tasks[0].due)
        assertNull(tasks[0].time)
        assertEquals(TaskScheduleUtils.formatTime(18, 30), tasks[1].time)
        assertEquals("2026-10-20", tasks[2].due)
        assertEquals(TaskScheduleUtils.formatTime(17, 0), tasks[2].time)
    }

    @Test
    fun vCalendarQuotedPrintableDecodes() {
        val vcs = "BEGIN:VCALENDAR\r\nVERSION:1.0\r\nBEGIN:VEVENT\r\n" +
            "SUMMARY;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Caf=C3=A9\r\n" +
            "DESCRIPTION;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Line one=0D=0A=\r\nLine two\r\n" +
            "DTSTART:20261012T140000\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

        val task = parseIcsTasks(vcs, kolkata).single()
        assertEquals("Café", task.title)
        assertEquals("Line one\nLine two", task.notes)
        assertEquals(TaskScheduleUtils.formatTime(14, 0), task.time)
    }

    @Test
    fun importLinkRoundTripsAndEmptyFileHasNone() {
        val link = buildCalendarImportLink(
            "BEGIN:VEVENT\nDTSTART:20261012T140000\nSUMMARY:Dentist\nDESCRIPTION:Bring card\nEND:VEVENT"
        )!!
        val shared = parseTransferLink(Uri.parse(link)).tasks.single()
        assertEquals("Dentist", shared.title)
        assertEquals("Bring card", shared.notes)
        assertEquals("2026-10-12", shared.due)
        assertEquals(TaskScheduleUtils.formatTime(14, 0), shared.time)

        assertNull(buildCalendarImportLink("BEGIN:VCALENDAR\nEND:VCALENDAR"))
    }
}
