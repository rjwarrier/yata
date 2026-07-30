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
import com.mj.yata.util.EntitySortMode

@Composable
private fun EntitySortMode.label() = when (this) {
    EntitySortMode.NAME_ASC -> "Name (A-Z)"
    EntitySortMode.NAME_DESC -> "Name (Z-A)"
    EntitySortMode.TASK_COUNT_DESC -> "Most tasks"
    EntitySortMode.TASK_COUNT_ASC -> "Fewest tasks"
    EntitySortMode.OPEN_TASK_COUNT_DESC -> stringResource(R.string.sort_most_open_tasks)
    EntitySortMode.STARRED_FIRST -> "Starred first"
}

/** Sort-mode picker for the People/Tags tabs — a tap target that opens a dropdown of
 * [EntitySortMode]s. Mirrors [TaskSortMenuButton]'s shape for the task lists. */
@Composable
fun EntitySortMenuButton(
    current: EntitySortMode,
    onSelect: (EntitySortMode) -> Unit,
    contentDescription: String = "Sort",
    modifier: Modifier = Modifier,
    /** Draws the shared circular top-bar container — see TaskSortMenuButton for why it's opt-in. */
    filledContainer: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val active = current != EntitySortMode.NAME_ASC
    if (filledContainer) {
        YataTopBarIconButton(onClick = { expanded = true }, modifier = modifier, tinted = active) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = contentDescription)
        }
    } else {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        EntitySortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label()) },
                onClick = { onSelect(mode); expanded = false }
            )
        }
    }
}
