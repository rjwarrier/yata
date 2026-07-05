package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataDatePickerDialog
import com.mj.yata.ui.widgets.YataStepper
import com.mj.yata.util.RecurrenceEvaluator
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceSheet(
    initialRecurrence: Recurrence?,
    onSave: (Recurrence?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enabled by remember { mutableStateOf(initialRecurrence != null) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var r by remember {
        mutableStateOf(
            initialRecurrence ?: Recurrence(
                freq = "weekly",
                interval = 1,
                byday = listOf("MO"),
                bymonthday = null,
                ends = RecurrenceEnds.Never
            )
        )
    }

    val liveSummary = remember(enabled, r) {
        if (enabled) RecurrenceEvaluator.recurrenceSummary(r) else "Does not repeat"
    }

    val liveRrule = remember(enabled, r) {
        if (enabled) RecurrenceEvaluator.toRRULE(r) else ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .size(32.dp, 4.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                .align(Alignment.CenterHorizontally)
        )

        // Summary Banner
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = liveSummary,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
                if (enabled && liveRrule.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = liveRrule,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = com.mj.yata.ui.theme.JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Enable Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repeat task",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        if (enabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Frequency
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Frequency",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedControl(
                    items = listOf("daily", "weekly", "monthly", "yearly"),
                    selectedItem = r.freq,
                    onItemSelected = { r = r.copy(freq = it) },
                    labelProvider = { it.uppercase() }
                )
            }

            // Interval Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Every",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                YataStepper(
                    value = r.interval,
                    onChange = { r = r.copy(interval = it) },
                    min = 1,
                    max = 30
                )
            }

            // Weekday toggles (Only for Weekly freq)
            if (r.freq == "weekly") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "On days",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val weekdays = listOf(
                            Pair("MO", "M"), Pair("TU", "T"), Pair("WE", "W"),
                            Pair("TH", "T"), Pair("FR", "F"), Pair("SA", "S"), Pair("SU", "S")
                        )
                        val activeDays = r.byday ?: emptyList()

                        weekdays.forEach { (key, label) ->
                            val isSelected = activeDays.contains(key)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        val newDays = if (isSelected) {
                                            activeDays.filter { it != key }
                                        } else {
                                            activeDays + key
                                        }
                                        r = r.copy(byday = newDays)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Monthly day picker (Only for Monthly freq)
            if (r.freq == "monthly") {
                val isLastDay = r.bymonthday == -1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Day of month",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        if (!isLastDay) {
                            YataStepper(
                                value = r.bymonthday?.takeIf { it > 0 } ?: 1,
                                onChange = { r = r.copy(bymonthday = it) },
                                min = 1,
                                max = 31
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { r = r.copy(bymonthday = if (isLastDay) 1 else -1) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(checked = isLastDay, onCheckedChange = { r = r.copy(bymonthday = if (it) -1 else 1) })
                        Text(
                            text = "Last day of month",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Ends Criteria
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Ends",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                EndsOptionCard(
                    label = "Never ends",
                    selected = r.ends is RecurrenceEnds.Never,
                    onClick = { r = r.copy(ends = RecurrenceEnds.Never) }
                )

                EndsOptionCard(
                    label = "Ends after",
                    selected = r.ends is RecurrenceEnds.After,
                    onClick = { r = r.copy(ends = RecurrenceEnds.After((r.ends as? RecurrenceEnds.After)?.count ?: 5)) }
                ) {
                    val curCount = (r.ends as RecurrenceEnds.After).count
                    YataStepper(
                        value = curCount,
                        onChange = { r = r.copy(ends = RecurrenceEnds.After(it)) },
                        min = 1,
                        max = 99
                    )
                }

                EndsOptionCard(
                    label = "Ends on date",
                    selected = r.ends is RecurrenceEnds.On,
                    onClick = {
                        val nextDate = (r.ends as? RecurrenceEnds.On)?.date ?: LocalDate.now().plusMonths(1).toString()
                        r = r.copy(ends = RecurrenceEnds.On(nextDate))
                    }
                ) {
                    val date = (r.ends as RecurrenceEnds.On).date
                    Text(
                        text = date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showEndDatePicker = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSave(if (enabled) r else null) }
            ) {
                Text("Save")
            }
        }
    }

    if (showEndDatePicker) {
        val currentEndDate = (r.ends as? RecurrenceEnds.On)?.date
        YataDatePickerDialog(
            initialDate = currentEndDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = {
                r = r.copy(ends = RecurrenceEnds.On(it))
                showEndDatePicker = false
            }
        )
    }
}

/** Bordered selection card for the Ends radio group — handoff sheets.jsx PTRecurrenceSheet Ends rows. */
@Composable
private fun EndsOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (selected && trailing != null) {
            trailing()
        }
    }
}
