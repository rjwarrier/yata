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

/** Matches the SnackbarDuration.Short used by delete-undo snackbars, so the visible countdown
 * lands on zero right as the snackbar actually auto-dismisses. */
const val DELETE_UNDO_SECONDS = 4

/** Custom rendering for a delete-undo snackbar — ticks a live countdown next to the Undo action
 * so it's clear the delete is about to become permanent, instead of a static label.
 *
 * The action's content color must be set explicitly: [TextButton]'s default content color
 * (colorScheme.primary) reads fine against a normal surface, but a [Snackbar] renders on
 * colorScheme.inverseSurface — on this app's theme that leaves the default "Undo" text nearly
 * invisible against the inverted background, so it's pinned to inversePrimary instead. */
@Composable
fun DeleteUndoSnackbar(data: SnackbarData) {
    var remaining by remember(data) { mutableIntStateOf(DELETE_UNDO_SECONDS) }
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
