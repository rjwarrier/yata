package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.util.TaskScheduleUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YataDatePickerDialog(
    initialDate: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = TaskScheduleUtils.dateToPickerMillis(initialDate)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedDate = datePickerState.selectedDateMillis ?: return@TextButton
                    onConfirm(TaskScheduleUtils.pickerMillisToDateString(selectedDate))
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YataTimePickerLauncher(
    show: Boolean,
    initialTime: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!show) return

    val initial = TaskScheduleUtils.parseTime(initialTime)
    val timePickerState = rememberTimePickerState(
        initialHour = initial?.hour ?: 9,
        initialMinute = initial?.minute ?: 0,
        // Follows the clock preference. What onConfirm writes back is unaffected — the picker
        // hands back hour and minute, and formatTime always stores the 12-hour form.
        is24Hour = com.mj.yata.util.AppFormats.uses24Hour()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(TaskScheduleUtils.formatTime(timePickerState.hour, timePickerState.minute))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        text = {
            TimePicker(state = timePickerState, modifier = Modifier.padding(top = 8.dp))
        }
    )
}
