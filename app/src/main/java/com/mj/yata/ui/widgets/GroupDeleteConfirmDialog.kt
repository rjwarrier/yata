package com.mj.yata.ui.widgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
        title = { Text("Delete \"$groupTitle\" group?") },
        text = { Text("$entityLabel stay, they're just ungrouped.") },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
