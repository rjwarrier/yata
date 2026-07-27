package com.mj.yata.ui.widgets

import com.mj.yata.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

// The countdown length now comes from LocalUndoWindowSeconds (see UndoSnackbar.kt) rather than a
// constant, so it always matches the window showUndoSnackbar actually enforces. Hardcoding it
// meant the visible countdown and the real deadline could disagree once the window was made
// configurable — the worst failure here, since the number is a promise about when delete happens.

/** Custom rendering for a delete-undo snackbar — ticks a live countdown next to the Undo action
 * so it's clear the delete is about to become permanent, instead of a static label.
 *
 * The action's content color must be set explicitly: [TextButton]'s default content color
 * (colorScheme.primary) reads fine against a normal surface, but a [Snackbar] renders on
 * colorScheme.inverseSurface — on this app's theme that leaves the default "Undo" text nearly
 * invisible against the inverted background, so it's pinned to inversePrimary instead. */
@Composable
fun DeleteUndoSnackbar(data: SnackbarData) {
    val windowSeconds = LocalUndoWindowSeconds.current
    var remaining by remember(data, windowSeconds) { mutableIntStateOf(windowSeconds) }
    LaunchedEffect(data) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining--
        }
    }
    Snackbar(
        action = {
            TextButton(
                onClick = { data.performAction() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
            ) {
                Text(stringResource(R.string.snackbar_undo_countdown, remaining), fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(data.visuals.message)
    }
}
