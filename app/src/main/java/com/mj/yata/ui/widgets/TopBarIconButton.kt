package com.mj.yata.ui.widgets

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The circular container behind every top-bar icon button.
 *
 * `surfaceContainerHigh` rather than a fixed colour or a hand-rolled alpha, because it is the one
 * role that stays legible in all three themes without a per-theme branch: it sits a step above
 * `background` in light and dark, and AMOLED maps it to #141414 against true black (see
 * `YataTheme`'s `toAmoled`), which is quiet but not invisible. An `onSurface.copy(alpha = …)`
 * overlay would have looked right in light and washed out on black.
 */
private val containerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

private val contentColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Plain action button — search, calendar, sync. */
@Composable
fun YataTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tinted: Boolean = false,
    content: @Composable () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            // `tinted` marks a button whose icon is already carrying meaning through colour (the
            // sort button when a non-default sort is active). The container stays the same either
            // way, so an active state never changes the shape of the row.
            contentColor = if (tinted) MaterialTheme.colorScheme.primary else contentColor
        )
    ) {
        content()
    }
}

/**
 * Toggle variant, for controls with an on/off state rather than an action — currently
 * hide-completed. Unchecked it is indistinguishable from [YataTopBarIconButton]; checked it fills
 * with `secondaryContainer`, which reads as "this is on" without the shout of a primary fill.
 */
@Composable
fun YataTopBarIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        content()
    }
}
