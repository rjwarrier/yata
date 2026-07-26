package com.mj.yata.ui.widgets

import com.mj.yata.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Confirms deleting a group (of people, tags, etc.) — members/items stay, they're just
 * ungrouped. Shared across People/Tags group headers, which only differ in [groupTitle] and
 * [entityLabel] (e.g. "Members"/"Tags"). */
@Composable
fun GroupDeleteConfirmDialog(
    groupTitle: String,
    entityLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_delete_confirm_title, groupTitle)) },
        text = { Text(stringResource(R.string.group_delete_confirm_body, entityLabel)) },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
