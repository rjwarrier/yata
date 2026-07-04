package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mj.yata.ui.theme.LocalYataAccents

@Composable
fun ColorPicker(
    selectedColorKey: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val swatches = listOf(
        "accentA", "accentB", "accentC", "accentD",
        "accentE", "accentF", "accentG", "accentH"
    )
    val accents = LocalYataAccents.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { key ->
            val color = accents.getAccent(key)
            val isSelected = key == selectedColorKey

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) Color.White else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(key) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (key == "accentC" || key == "accentG") Color.White else Color.Black, // Ensure icon contrast
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
