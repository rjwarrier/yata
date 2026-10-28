package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.mj.yata.R
import com.mj.yata.util.SyncFailureHelp

const val SYNC_FAQ_URL = "https://github.com/rjwarrier/yata/blob/master/docs/SYNC_FAQ.md"

@Composable
fun SyncFailureHelpDialog(help: SyncFailureHelp, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val body = when (help) {
        SyncFailureHelp.EMPTY_REPOSITORY -> R.string.sync_fix_empty
        SyncFailureHelp.ACCESS -> R.string.sync_fix_access
        SyncFailureHelp.RATE_LIMIT -> R.string.sync_fix_rate
        SyncFailureHelp.CONFLICT -> R.string.sync_fix_conflict
        SyncFailureHelp.HISTORY -> R.string.sync_fix_history
        SyncFailureHelp.PASSPHRASE -> R.string.sync_fix_passphrase
        SyncFailureHelp.NETWORK -> R.string.sync_fix_network
        SyncFailureHelp.OTHER -> R.string.sync_fix_other
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_how_to_fix)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(body))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = { uriHandler.openUri(SYNC_FAQ_URL) }) {
                Text(stringResource(R.string.sync_faq))
            }
        }
    )
}
