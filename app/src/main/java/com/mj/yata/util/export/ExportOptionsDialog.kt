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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.widgets.SegmentedControl

/**
 * Confirms an entity export (Tag/Person/Project → PDF/Image) with several options, all applied
 * to the report's task list *and* its stats (done/total/overdue), not just what's shown:
 * whether to include completed tasks, an optional age cutoff (if included) so stale completed
 * tasks don't bloat an otherwise-current report, whether completed tasks get a strikethrough,
 * whether to show tag chips and assignee names on each row, and a Compact/Relaxed layout
 * density. A bottom sheet (matching the rest of the app's picker/editor sheets) rather than an
 * AlertDialog since it now holds enough options to need real sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionsDialog(
    entityName: String,
    onDismiss: () -> Unit,
    onConfirm: (
        includeCompleted: Boolean,
        excludeCompletedOlderThanDays: Int?,
        density: ExportDensity,
        strikeThroughCompleted: Boolean,
        showTags: Boolean,
        showAssignees: Boolean
    ) -> Unit
) {
    var includeCompleted by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("") }
    var density by remember { mutableStateOf(ExportDensity.RELAXED) }
    var strikeThroughCompleted by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(true) }
    var showAssignees by remember { mutableStateOf(true) }

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
                text = "Export $entityName",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            SectionLabel("Completed tasks")
            ToggleRow(
                title = "Include completed tasks",
                checked = includeCompleted,
                onCheckedChange = { includeCompleted = it }
            )
            if (includeCompleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Exclude completed tasks done more than this many days ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { value -> if (value.all { it.isDigit() } && value.length <= 4) daysText = value },
                    placeholder = { Text("No limit") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                ToggleRow(
                    title = "Strike off completed tasks",
                    checked = strikeThroughCompleted,
                    onCheckedChange = { strikeThroughCompleted = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel("Display")
            ToggleRow(title = "Show tags", checked = showTags, onCheckedChange = { showTags = it })
            ToggleRow(title = "Show assigned to", checked = showAssignees, onCheckedChange = { showAssignees = it })

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel("Layout")
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedControl(
                items = listOf(ExportDensity.RELAXED, ExportDensity.COMPACT),
                selectedItem = density,
                onItemSelected = { density = it },
                labelProvider = { if (it == ExportDensity.RELAXED) "Relaxed" else "Compact" }
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        onConfirm(includeCompleted, daysText.toIntOrNull(), density, strikeThroughCompleted, showTags, showAssignees)
                    }
                ) { Text("Export") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
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
