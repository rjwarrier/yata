package com.mj.yata.ui.widgets

import android.app.TimePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun YataTimePickerLauncher(
    show: Boolean,
    initialTime: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    DisposableEffect(context, initialTime) {
        val initial = TaskScheduleUtils.parseTime(initialTime)
        val dialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onConfirm(TaskScheduleUtils.formatTime(hourOfDay, minute))
            },
            initial?.hour ?: 9,
            initial?.minute ?: 0,
            false
        )
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
