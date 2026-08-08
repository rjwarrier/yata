package com.mj.yata.ui.widgets

import com.mj.yata.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Free-form HSV color picker (hue strip + saturation/value box + hex input) for the "Custom
 * color" theme option — presets are curated seeds, but a user should be able to pick literally
 * any color to seed the theme from. */
@Composable
fun CustomColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and initialColor.toArgb())) }

    val currentColor = Color.hsv(hue, saturation, value)

    fun syncHexFromHsv() {
        hexText = String.format("#%06X", 0xFFFFFF and currentColor.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_custom_color), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    TextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            val cleaned = text.removePrefix("#")
                            if (cleaned.length == 6 && cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                try {
                                    val argb = android.graphics.Color.parseColor("#$cleaned")
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb, hsv)
                                    hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                                } catch (_: IllegalArgumentException) { /* keep typing */ }
                            }
                        },
                        label = { Text(stringResource(R.string.custom_color_picker_hex)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Saturation (x) / Value (y) box for the current hue.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .pointerInputSv { pos, size ->
                                saturation = (pos.x / size.width).coerceIn(0f, 1f)
                                value = (1f - pos.y / size.height).coerceIn(0f, 1f)
                                syncHexFromHsv()
                            }
                    ) {
                        val hueColor = Color.hsv(hue, 1f, 1f)
                        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val indicator = Offset(saturation * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = indicator, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = indicator, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                    }
                }

                // Hue strip.
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInputHue { pos, size ->
                            hue = (pos.x / size.width * 360f).coerceIn(0f, 359.999f)
                            syncHexFromHsv()
                        }
                ) {
                    val hueColors = (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) }
                    drawRect(Brush.horizontalGradient(hueColors))
                    val x = (hue / 360f) * size.width
                    drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text(stringResource(R.string.custom_color_picker_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun Modifier.pointerInputSv(onOffset: (Offset, androidx.compose.ui.geometry.Size) -> Unit): Modifier =
    this
        .then(
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset -> onOffset(offset, size.toSize()) }
            }
        )
        .then(
            Modifier.pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onOffset(change.position, size.toSize())
                }
            }
        )

private fun Modifier.pointerInputHue(onOffset: (Offset, androidx.compose.ui.geometry.Size) -> Unit): Modifier =
    this
        .then(
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset -> onOffset(offset, size.toSize()) }
            }
        )
        .then(
            Modifier.pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onOffset(change.position, size.toSize())
                }
            }
        )

private fun androidx.compose.ui.unit.IntSize.toSize(): androidx.compose.ui.geometry.Size =
    androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
