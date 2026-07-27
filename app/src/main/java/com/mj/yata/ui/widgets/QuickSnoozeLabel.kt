package com.mj.yata.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mj.yata.R
import com.mj.yata.domain.model.QuickSnoozePreset

@Composable
fun quickSnoozeLabel(preset: QuickSnoozePreset): String = when (preset) {
    QuickSnoozePreset.TONIGHT -> stringResource(R.string.settings_snooze_tonight)
    QuickSnoozePreset.TOMORROW_MORNING -> stringResource(R.string.settings_snooze_tomorrow)
    QuickSnoozePreset.NEXT_WEEKDAY -> stringResource(R.string.snooze_next_weekday)
}
