package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Handoff's Segmented (m3-widgets.jsx): pill track on surfaceContainerHigh, selected
 * segment is its own surface-colored pill with an L1 shadow — no borders, no dividers. */
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .shadow(
                        elevation = if (isSelected) 1.dp else 0.dp,
                        shape = RoundedCornerShape(percent = 50),
                        clip = false
                    )
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onItemSelected(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
