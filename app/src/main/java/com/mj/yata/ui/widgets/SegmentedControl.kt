package com.mj.yata.ui.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Handoff's Segmented (m3-widgets.jsx): pill track on surfaceContainerHigh, selected segment is
 * its own surface-colored pill with an L1 shadow — no borders, no dividers. One shared pill
 * slides between measured item positions (same technique as the bottom nav's tab indicator)
 * instead of each item flashing its own background on/off with no motion in between. */
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() }
) {
    val density = LocalDensity.current
    var containerCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // Keyed on `items` — reusing this composable for a different items list (or a different
    // length) used to leave stale recorded positions behind, which could flash the pill to a
    // wrong-sized slot for a frame before onGloballyPositioned caught up.
    val itemPositions = remember(items) { mutableStateMapOf<Int, Offset>() }
    var pillSize by remember(items) { mutableStateOf(IntSize.Zero) }

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val targetOffset = itemPositions[selectedIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
            .onGloballyPositioned { containerCoords = it }
    ) {
        if (targetOffset != null && pillSize != IntSize.Zero) {
            val animatedX by animateDpAsState(
                targetValue = with(density) { targetOffset.x.toDp() },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "segmentedPillX"
            )
            Box(
                modifier = Modifier
                    .offset(x = animatedX)
                    .size(width = with(density) { pillSize.width.toDp() }, height = with(density) { pillSize.height.toDp() })
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(percent = 50), clip = false)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            pillSize = coords.size
                            containerCoords?.let { parent ->
                                itemPositions[index] = parent.localPositionOf(coords, Offset.Zero)
                            }
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
