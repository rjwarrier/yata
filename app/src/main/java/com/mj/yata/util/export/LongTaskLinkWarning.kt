package com.mj.yata.util.export

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.mj.yata.R

class LongTaskLinkWarningGate internal constructor() {
    private var pending by mutableStateOf<PendingLongTaskLinkWarning?>(null)

    fun runOrConfirm(link: TaskTransferLink?, taskCount: Int, action: () -> Unit) {
        if (link?.mayNotAutoLinkEverywhere == true) {
            pending = PendingLongTaskLinkWarning(
                linkLength = link.uri.length,
                taskCount = taskCount,
                action = action
            )
        } else {
            action()
        }
    }

    fun dialogState(): LongTaskLinkWarningInfo? = pending?.let {
        LongTaskLinkWarningInfo(linkLength = it.linkLength, taskCount = it.taskCount)
    }

    internal fun dismiss() {
        pending = null
    }

    internal fun continueAnyway() {
        val action = pending?.action ?: return
        pending = null
        action()
    }
}

private data class PendingLongTaskLinkWarning(
    val linkLength: Int,
    val taskCount: Int,
    val action: () -> Unit
)

data class LongTaskLinkWarningInfo(
    val linkLength: Int,
    val taskCount: Int
)

@Composable
fun rememberLongTaskLinkWarningGate(): LongTaskLinkWarningGate =
    remember { LongTaskLinkWarningGate() }

@Composable
fun LongTaskLinkWarningDialog(gate: LongTaskLinkWarningGate) {
    val pending = gate.dialogState() ?: return
    AlertDialog(
        onDismissRequest = gate::dismiss,
        title = { Text(stringResource(R.string.task_link_long_warning_title)) },
        text = {
            Text(
                stringResource(
                    R.string.task_link_long_warning_body,
                    pending.linkLength,
                    pending.taskCount
                )
            )
        },
        confirmButton = {
            TextButton(onClick = gate::continueAnyway) {
                Text(stringResource(R.string.task_link_long_warning_share_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = gate::dismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
