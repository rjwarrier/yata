package com.mj.yata

import com.mj.yata.util.TaskScheduleUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CrossLocaleTaskTimeTest {
    @Test fun storedAndLegacyTimesSurviveLanguageChanges() {
        val old = Locale.getDefault()
        try {
            for (source in listOf("sv-SE", "en-US", "de-DE", "hi-IN", "zh-CN", "ar")) {
                val locale = Locale.forLanguageTag(source)
                val legacy = LocalTime.of(15, 0).format(DateTimeFormatter.ofPattern("h:mm a", locale))
                Locale.setDefault(Locale.US)
                assertEquals("Legacy $source: $legacy", LocalTime.of(15, 0), TaskScheduleUtils.parseTime(legacy))
                Locale.setDefault(locale)
                assertEquals("3:00 PM", TaskScheduleUtils.formatTime(15, 0))
                assertEquals(LocalTime.of(15, 0), TaskScheduleUtils.parseTime("3:00 PM"))
            }
        } finally { Locale.setDefault(old) }
    }
}
