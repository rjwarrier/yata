package com.mj.yata.util.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.yataFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskExportOptionsDialog(
    taskTitle: String,
    format: ExportFormat,
    hasNotes: Boolean,
    hasComments: Boolean,
    hasSubtasks: Boolean,
    hasScheduleDetails: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TaskExportOptions) -> Unit
) {
    val context = LocalContext.current
    val defaults = remember(taskTitle) { defaultTaskExportOptions(context, taskTitle) }
    var includeNotes by remember { mutableStateOf(defaults.includeNotes) }
    var includeComments by remember { mutableStateOf(defaults.includeComments) }
    var includeSubtasks by remember { mutableStateOf(defaults.includeSubtasks) }
    var includeScheduleDetails by remember { mutableStateOf(defaults.includeScheduleDetails) }
    var showMadeWithFooter by remember { mutableStateOf(defaults.showMadeWithFooter) }
    var privacyMode by remember { mutableStateOf(defaults.privacyMode) }
    var destination by remember { mutableStateOf(defaults.destination) }
    var pdfPageSize by remember { mutableStateOf(defaults.pdfPageSize) }
    var imageScale by remember { mutableStateOf(defaults.imageScale) }
    var fileNameText by remember { mutableStateOf(defaults.fileNameBase) }

    val privateNotes = if (privacyMode) false else includeNotes
    val privateComments = if (privacyMode) false else includeComments
    val contentParts = listOfNotNull(
        "details",
        "schedule".takeIf { hasScheduleDetails && includeScheduleDetails },
        "subtasks".takeIf { hasSubtasks && includeSubtasks },
        "notes".takeIf { hasNotes && privateNotes },
        "comments".takeIf { hasComments && privateComments }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        // Outer column pins the action row; the options scroll above it. Without the split the
        // options were measured first and the button row got whatever height was left over — on a
        // short screen that was a few dp of squashed button.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val shareLabel = stringResource(R.string.action_share)
                val saveLabel = stringResource(R.string.action_save)
                Text(
                    text = stringResource(R.string.export_task_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (format == ExportFormat.PDF) {
                        "${contentParts.joinToString()} - about 1 page"
                    } else {
                        "${contentParts.joinToString()} - ${imageScale.label.lowercase()} image"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                SectionLabel(stringResource(R.string.export_section_file))
                TextField(
                    value = fileNameText,
                    onValueChange = { fileNameText = it.take(64) },
                    label = { Text(stringResource(R.string.export_filename)) },
                    singleLine = true,
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf(ExportDestination.SHARE, ExportDestination.SAVE_TO_DOWNLOADS),
                    selectedItem = destination,
                    onItemSelected = { destination = it },
                    labelProvider = { if (it == ExportDestination.SHARE) shareLabel else saveLabel }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                SectionLabel(stringResource(R.string.export_section_content))
                ToggleRow(title = stringResource(R.string.export_privacy_mode), checked = privacyMode, onCheckedChange = { privacyMode = it })
                if (hasScheduleDetails) {
                    ToggleRow(
                        title = stringResource(R.string.export_include_schedule),
                        checked = includeScheduleDetails,
                        onCheckedChange = { includeScheduleDetails = it }
                    )
                }
                if (hasSubtasks) {
                    ToggleRow(title = stringResource(R.string.export_include_subtasks), checked = includeSubtasks, onCheckedChange = { includeSubtasks = it })
                }
                if (hasNotes) {
                    ToggleRow(
                        title = stringResource(R.string.export_include_notes),
                        checked = privateNotes,
                        enabled = !privacyMode,
                        onCheckedChange = { includeNotes = it }
                    )
                }
                if (hasComments) {
                    ToggleRow(
                        title = stringResource(R.string.export_include_comments),
                        checked = privateComments,
                        enabled = !privacyMode,
                        onCheckedChange = { includeComments = it }
                    )
                }
                ToggleRow(
                    title = stringResource(R.string.export_show_footer),
                    checked = showMadeWithFooter,
                    onCheckedChange = { showMadeWithFooter = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))

                SectionLabel(stringResource(R.string.export_section_format))
                if (format == ExportFormat.PDF) {
                    SegmentedControl(
                        items = listOf(ExportPdfPageSize.A4, ExportPdfPageSize.LETTER),
                        selectedItem = pdfPageSize,
                        onItemSelected = { pdfPageSize = it },
                        labelProvider = { it.label }
                    )
                } else {
                    SegmentedControl(
                        items = listOf(ExportImageScale.STANDARD, ExportImageScale.LARGE),
                        selectedItem = imageScale,
                        onItemSelected = { imageScale = it },
                        labelProvider = { it.label }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    enabled = fileNameText.isNotBlank(),
                    onClick = {
                        val options = TaskExportOptions(
                            includeNotes = hasNotes && privateNotes,
                            includeComments = hasComments && privateComments,
                            includeSubtasks = hasSubtasks && includeSubtasks,
                            includeScheduleDetails = hasScheduleDetails && includeScheduleDetails,
                            showMadeWithFooter = showMadeWithFooter,
                            privacyMode = privacyMode,
                            destination = destination,
                            fileNameBase = fileNameText,
                            pdfPageSize = pdfPageSize,
                            imageScale = imageScale
                        )
                        rememberTaskExportOptions(context, options)
                        onConfirm(options)
                    }
                ) { Text(stringResource(R.string.action_export)) }
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
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
