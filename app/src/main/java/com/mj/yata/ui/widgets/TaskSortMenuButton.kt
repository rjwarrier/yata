package com.mj.yata.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mj.yata.R
import com.mj.yata.util.TaskSortMode

private fun TaskSortMode.label() = when (this) {
    TaskSortMode.MANUAL -> "Manual order"
    TaskSortMode.DUE_DATE -> "Due date"
    TaskSortMode.PRIORITY -> "Priority"
    TaskSortMode.ALPHABETICAL -> "Alphabetical"
}

/** Sort-mode picker for a task list — a tap target that opens a dropdown of [TaskSortMode]s.
 * Shared across Today and the detail screens so "sort by" behaves identically everywhere. */
@Composable
fun TaskSortMenuButton(
    current: TaskSortMode,
    onSelect: (TaskSortMode) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Draws the shared circular top-bar container behind the icon. Opt-in rather than the default
     * because four of the five screens using this button sit in top bars whose other actions are
     * still plain icon buttons — filling only this one there would single it out.
     */
    filledContainer: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val active = current != TaskSortMode.MANUAL
    if (filledContainer) {
        YataTopBarIconButton(onClick = { expanded = true }, modifier = modifier, tinted = active) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.task_sort_menu_button_sort_tasks)
            )
        }
    } else {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.task_sort_menu_button_sort_tasks),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        TaskSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label()) },
                onClick = { onSelect(mode); expanded = false }
            )
        }
    }
}
