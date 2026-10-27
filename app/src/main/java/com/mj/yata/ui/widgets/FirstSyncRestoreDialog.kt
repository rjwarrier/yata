package com.mj.yata.ui.widgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mj.yata.R

/** Separate confirmation because a first-sync restore replaces populated local data. */
@Composable
fun FirstSyncRestoreDialog(onDismiss: () -> Unit, onRestore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sftp_restore_confirm_title)) },
        text = { Text(stringResource(R.string.settings_sftp_restore_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.settings_empty_local_sync_restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
