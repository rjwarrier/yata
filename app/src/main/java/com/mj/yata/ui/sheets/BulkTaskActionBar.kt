package com.mj.yata.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.ui.widgets.quickSnoozeLabel
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activePeople
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.PersonAvatar

/** Top bar shown in place of the normal header once one or more tasks are selected. */
@Composable
fun TaskSelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onAddTag: () -> Unit,
    onMove: () -> Unit,
    onReschedule: () -> Unit = {},
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onAssign: () -> Unit = {},
    tagsEnabled: Boolean = true,
    peopleEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_bulk_cancel_selection), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_bulk_mark_done), tint = MaterialTheme.colorScheme.onSurface)
            }
            if (tagsEnabled) {
                IconButton(onClick = onAddTag) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.cd_bulk_add_tag), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (peopleEnabled) {
                IconButton(onClick = onAssign) {
                    Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.cd_bulk_assign_person), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.cd_bulk_move), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onReschedule) {
                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.cd_bulk_reschedule), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_duplicate), tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TaskBulkRescheduleSheet(
    onSelectPreset: (QuickSnoozePreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.bulk_reschedule_selected),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        QuickSnoozePreset.entries.forEach { preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectPreset(preset) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(quickSnoozeLabel(preset), style = MaterialTheme.typography.bodyLarge)
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

/** Bottom sheet: move every currently-selected task to a different project and/or list. */
@Composable
fun TaskBulkMoveSheet(
    projects: List<Project>,
    lists: List<YataList>,
    onSelectProject: (String?) -> Unit,
    onSelectList: (String?) -> Unit,
    onDismiss: () -> Unit,
    projectsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    val activeProjects = projects.activeProjects()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Move selected tasks",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
        )

        if (projectsEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PROJECT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectProject(null) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.action_none_remove_from_project), style = MaterialTheme.typography.bodyLarge)
                }
                activeProjects.forEach { pr ->
                    val color = accents.getAccent(pr.color)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectProject(pr.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(pr.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "LIST",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectList(null) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.action_none_remove_from_list), style = MaterialTheme.typography.bodyLarge)
            }
            lists.forEach { l ->
                val color = accents.getAccent(l.color)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectList(l.id) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(l.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Bottom sheet: pick a tag to apply to every currently-selected task. */
@Composable
fun TaskBulkTagPickerSheet(
    tags: List<Tag>,
    onSelectTag: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Add tag to selected tasks",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (tags.isEmpty()) {
            Text(
                text = "No tags yet — create one from the Tags tab first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        tags.forEach { tag ->
            val color = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTag(tag.id) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Bottom sheet: assign every currently-selected task to a person (delegation). */
@Composable
fun TaskBulkAssignPersonSheet(
    people: List<Person>,
    onSelectPerson: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePeople = people.activePeople()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Assign selected tasks to",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (activePeople.isEmpty()) {
            Text(
                text = "No people yet — add one from the People tab first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        activePeople.forEach { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectPerson(person.id) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonAvatar(initials = person.initials, accentKey = person.color, photoUri = person.photoUri, size = 32.dp)
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
