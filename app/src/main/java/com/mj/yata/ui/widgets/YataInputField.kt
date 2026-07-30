package com.mj.yata.ui.widgets

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared look for the app's free-text inputs — notes, comments, subtask entry.
 *
 * These were outlined text fields, which is a valid M3 variant but the wrong one here: every
 * surface around them is a tonal card (`surfaceContainerLow`), so a hard 1dp stroke with a
 * 4-to-12dp corner read as a form control dropped onto a soft layout. Filled and tonal, with a
 * generous corner and no indicator line, is the Expressive treatment and matches what the
 * settings search field and the top-bar buttons already use.
 *
 * `surfaceContainerHigh` for the same reason it was chosen there: it's the one role that stays
 * legible in light, dark and AMOLED without a per-theme branch (AMOLED maps it to #141414 against
 * true black rather than flattening it away).
 */
val YataFieldShape: Shape = RoundedCornerShape(20.dp)

/** Smaller radius for single-line inline fields, which are too short to carry a 20dp corner. */
val YataCompactFieldShape: Shape = RoundedCornerShape(16.dp)

@Composable
fun yataFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    // The container itself is the affordance; an underline on top of it is the "filled text
    // field" default that Expressive drops.
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
