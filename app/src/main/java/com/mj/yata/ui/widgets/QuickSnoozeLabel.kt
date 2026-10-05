package com.mj.yata.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.mj.yata.R
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.QuickSnoozeSettings
import com.mj.yata.domain.model.isAvailableAt
import com.mj.yata.domain.model.resolve
import com.mj.yata.util.AppClock
import com.mj.yata.util.AppFormats
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDateTime

/** The user's quick-snooze times and working-day calendar. Provided from preferences in
 * MainActivity, so every snooze menu can preview the date a choice resolves to. */
val LocalQuickSnoozeSettings = staticCompositionLocalOf { QuickSnoozeSettings() }

@Composable
fun quickSnoozeLabel(preset: QuickSnoozePreset): String = when (preset) {
    QuickSnoozePreset.TONIGHT -> stringResource(R.string.settings_snooze_tonight)
    QuickSnoozePreset.TOMORROW_MORNING -> stringResource(R.string.settings_snooze_tomorrow)
    QuickSnoozePreset.NEXT_WEEKDAY -> stringResource(R.string.snooze_next_weekday)
}

/**
 * Where [preset] will actually move a task if picked now — "Mon, 5 Oct · 9:00 AM" — so the
 * configured snooze time, and any weekend or holiday "Next business day" skips over, are visible
 * before tapping rather than discovered afterwards. [includeTime] is off when the caller keeps
 * each task's own time and only the date will change.
 */
@Composable
fun quickSnoozePreview(preset: QuickSnoozePreset, includeTime: Boolean = true): String {
    val settings = LocalQuickSnoozeSettings.current
    val today = AppClock.today
    // dateFormat is read so a date-format change re-renders an open menu, as everywhere else.
    val dateFormat = AppFormats.dateFormat
    return remember(preset, settings, today, dateFormat, includeTime, AppFormats.uses24Hour()) {
        val target = preset.resolve(settings, today)
        val date = target.toLocalDate().format(AppFormats.shortDateFormatter())
        if (includeTime) "$date · ${TaskScheduleUtils.displayTime(target.hour, target.minute)}" else date
    }
}

/** See [isAvailableAt]: "Tonight" is offered only until tonight's snooze time has passed. */
@Composable
fun quickSnoozeAvailable(preset: QuickSnoozePreset): Boolean =
    preset.isAvailableAt(LocalQuickSnoozeSettings.current, LocalDateTime.now())
