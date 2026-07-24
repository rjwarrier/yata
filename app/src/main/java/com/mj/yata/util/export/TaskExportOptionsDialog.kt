package com.mj.yata.util.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirms a single-task export (Task → PDF/Image) — its only options are whether to include
 * Notes and Comments, and each toggle only appears when the task actually has that content
 * (an empty toggle would just be a confusing no-op). Skipped entirely by the caller when the
 * task has neither, since there'd be nothing left to configure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskExportOptionsDialog(
    hasNotes: Boolean,
    hasComments: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (includeNotes: Boolean, includeComments: Boolean) -> Unit
) {
    var includeNotes by remember { mutableStateOf(true) }
    var includeComments by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Export task",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (hasNotes) {
                ToggleRow(title = "Include notes", checked = includeNotes, onCheckedChange = { includeNotes = it })
            }
            if (hasComments) {
                ToggleRow(title = "Include comments", checked = includeComments, onCheckedChange = { includeComments = it })
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        onConfirm(hasNotes && includeNotes, hasComments && includeComments)
                    }
                ) { Text("Export") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
