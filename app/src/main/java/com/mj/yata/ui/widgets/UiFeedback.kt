package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class FeedbackTone { INFO, SUCCESS, ERROR }

private data class FeedbackVisuals(
    override val message: String,
    val tone: FeedbackTone,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

suspend fun SnackbarHostState.showSuccess(message: String) =
    showSnackbar(FeedbackVisuals(message = message, tone = FeedbackTone.SUCCESS))

suspend fun SnackbarHostState.showError(message: String) =
    showSnackbar(
        FeedbackVisuals(
            message = message,
            tone = FeedbackTone.ERROR,
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
    )

suspend fun SnackbarHostState.showInfo(message: String) =
    showSnackbar(FeedbackVisuals(message = message, tone = FeedbackTone.INFO))

@Composable
fun YataSnackbar(data: SnackbarData) {
    if (data.visuals.actionLabel == UNDO_ACTION_LABEL) {
        DeleteUndoSnackbar(data)
        return
    }

    val visuals = data.visuals as? FeedbackVisuals
    if (visuals == null) {
        Snackbar(data)
        return
    }

    val icon = when (visuals.tone) {
        FeedbackTone.INFO -> Icons.Default.Info
        FeedbackTone.SUCCESS -> Icons.Default.CheckCircle
        FeedbackTone.ERROR -> Icons.Default.ErrorOutline
    }
    val containerColor = when (visuals.tone) {
        FeedbackTone.INFO -> MaterialTheme.colorScheme.inverseSurface
        FeedbackTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        FeedbackTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (visuals.tone) {
        FeedbackTone.INFO -> MaterialTheme.colorScheme.inverseOnSurface
        FeedbackTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        FeedbackTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    Snackbar(
        containerColor = containerColor,
        contentColor = contentColor,
        dismissActionContentColor = contentColor,
        dismissAction = if (visuals.withDismissAction) {
            {
                IconButton(onClick = data::dismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(visuals.message, modifier = Modifier.weight(1f))
        }
    }
}
