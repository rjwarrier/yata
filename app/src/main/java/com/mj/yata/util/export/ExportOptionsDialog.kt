package com.mj.yata.util.export

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.widgets.SegmentedControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionsDialog(
    entityName: String,
    format: ExportFormat,
    itemPreviews: List<ExportItemPreview>,
    onDismiss: () -> Unit,
    onConfirm: (EntityExportOptions) -> Unit
) {
    val context = LocalContext.current
    val defaults = remember(entityName) { defaultEntityExportOptions(context, entityName) }
    var includeCompleted by remember { mutableStateOf(defaults.includeCompleted) }
    var daysText by remember { mutableStateOf(defaults.excludeCompletedOlderThanDays?.toString().orEmpty()) }
    var density by remember { mutableStateOf(defaults.density) }
    var strikeThroughCompleted by remember { mutableStateOf(defaults.strikeThroughCompleted) }
    var showTags by remember { mutableStateOf(defaults.showTags) }
    var showAssignees by remember { mutableStateOf(defaults.showAssignees) }
    var showMadeWithFooter by remember { mutableStateOf(defaults.showMadeWithFooter) }
    var privacyMode by remember { mutableStateOf(defaults.privacyMode) }
    var destination by remember { mutableStateOf(defaults.destination) }
    var pdfPageSize by remember { mutableStateOf(defaults.pdfPageSize) }
    var imageScale by remember { mutableStateOf(defaults.imageScale) }
    var fileNameText by remember { mutableStateOf(defaults.fileNameBase) }

    val selectedCount = filteredExportPreviewCount(itemPreviews, includeCompleted, daysText.toIntOrNull())
    val doneCount = itemPreviews.count { it.done }
    val estimatedPages = estimateExportPdfPages(selectedCount, density)
    val privacyTags = if (privacyMode) false else showTags
    val privacyAssignees = if (privacyMode) false else showAssignees

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
            Text(
                text = if (format == ExportFormat.PDF) {
                    "$selectedCount task(s), about $estimatedPages page(s)"
                } else {
                    "$selectedCount task(s), ${imageScale.label.lowercase()} image"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            SectionLabel("File")
            OutlinedTextField(
                value = fileNameText,
                onValueChange = { fileNameText = it.take(64) },
                label = { Text("Filename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedControl(
                items = listOf(ExportDestination.SHARE, ExportDestination.SAVE_TO_DOWNLOADS),
                selectedItem = destination,
                onItemSelected = { destination = it },
                labelProvider = { if (it == ExportDestination.SHARE) "Share" else "Save" }
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

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
                    placeholder = { Text(stringResource(R.string.export_options_no_limit)) },
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
            if (doneCount > 0) {
                Text(
                    text = "$doneCount completed task(s) available before filters.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel("Display")
            ToggleRow(title = "Privacy mode", checked = privacyMode, onCheckedChange = { privacyMode = it })
            ToggleRow(
                title = "Show tags",
                checked = privacyTags,
                enabled = !privacyMode,
                onCheckedChange = { showTags = it }
            )
            ToggleRow(
                title = "Show assigned to",
                checked = privacyAssignees,
                enabled = !privacyMode,
                onCheckedChange = { showAssignees = it }
            )
            ToggleRow(
                title = "Show Made with YATA footer",
                checked = showMadeWithFooter,
                onCheckedChange = { showMadeWithFooter = it }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel("Format")
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
            Spacer(modifier = Modifier.height(8.dp))
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    enabled = fileNameText.isNotBlank(),
                    onClick = {
                        val options = EntityExportOptions(
                            includeCompleted = includeCompleted,
                            excludeCompletedOlderThanDays = daysText.toIntOrNull(),
                            density = density,
                            strikeThroughCompleted = strikeThroughCompleted,
                            showTags = privacyTags,
                            showAssignees = privacyAssignees,
                            showMadeWithFooter = showMadeWithFooter,
                            privacyMode = privacyMode,
                            destination = destination,
                            fileNameBase = fileNameText,
                            pdfPageSize = pdfPageSize,
                            imageScale = imageScale
                        )
                        rememberEntityExportOptions(context, options)
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
