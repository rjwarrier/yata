package com.mj.yata.ui.widgets

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/**
 * The app's single overflow/dropdown menu shell. Wraps [DropdownMenu] with the app's shape/tone
 * language (theme's `shapes.medium` corner, `surfaceContainer` fill, a touch more shadow to lift
 * it off the page) instead of the library default — a sharp 4dp-corner, flat-elevation popup that
 * reads as Material2 next to the rest of the app's rounded cards and sheets. Every three-dot menu
 * (task rows, detail screen overflow, settings pickers, sort menus) should go through this rather
 * than calling [DropdownMenu] directly, so a future tone change is one file, not twenty.
 */
@Composable
fun YataDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        content = content
    )
}

/**
 * Drop-in replacement for [DropdownMenuItem] with a rounded, inset hit/ripple area (theme's
 * `shapes.small` corner) instead of the library default full-bleed rectangle, so each row in a
 * [YataDropdownMenu] reads as its own tappable pill rather than a flat list row — the same
 * "rounded item inside a rounded container" look Material You settings/menus use elsewhere.
 * Same parameter signature as [DropdownMenuItem] on purpose, so every call site can swap the
 * function name with no other changes.
 */
@Composable
fun YataDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}
