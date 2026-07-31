package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.ui.theme.ALL_ACCENT_KEYS
import com.mj.yata.ui.theme.LocalYataAccents

private val hexColorRegex = Regex("^#[0-9A-Fa-f]{6}$")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selectedColorKey: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    var showHexDialog by remember { mutableStateOf(false) }
    val isCustom = selectedColorKey.startsWith("#")

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ALL_ACCENT_KEYS.forEach { key ->
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
                        contentDescription = stringResource(R.string.cd_task_selected),
                        tint = accents.onAccentFor(color),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Custom hex swatch — shows the current custom color once picked, otherwise a
        // pipette icon inviting the user to enter one.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isCustom) accents.getAccent(selectedColorKey) else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isCustom) 3.dp else 0.dp,
                    color = if (isCustom) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .clickable { showHexDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCustom) Icons.Default.Check else Icons.Default.Colorize,
                contentDescription = stringResource(R.string.action_custom_color),
                tint = if (isCustom) {
                    accents.onAccentFor(selectedColorKey)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showHexDialog) {
        var hexInput by remember { mutableStateOf(selectedColorKey.takeIf { it.startsWith("#") } ?: "#") }
        val isValid = hexColorRegex.matches(hexInput)

        AlertDialog(
            onDismissRequest = { showHexDialog = false },
            title = { Text(stringResource(R.string.action_custom_color)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.uppercase() },
                        placeholder = { Text(stringResource(R.string.color_picker_rrggbb)) },
                        singleLine = true,
                        isError = hexInput.isNotEmpty() && !isValid
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isValid) Color(android.graphics.Color.parseColor(hexInput)) else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValid,
                    onClick = {
                        onColorSelected(hexInput)
                        showHexDialog = false
                    }
                ) { Text(stringResource(R.string.color_picker_use_color)) }
            },
            dismissButton = {
                TextButton(onClick = { showHexDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
