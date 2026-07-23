package com.mj.yata.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskRowDensity
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate

private fun TaskRowDensity.verticalPadding() = when (this) {
    TaskRowDensity.COMPACT -> 6.dp
    TaskRowDensity.COMFORTABLE -> 11.dp
    TaskRowDensity.SPACIOUS -> 16.dp
}

@Composable
private fun TaskHealthBadge(label: String) {
    val attention = label == "Needs date" || label == "Flagged no date"
    val containerColor = if (attention) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (attention) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskRow(
    task: Task,
    list: YataList?,
    assignees: List<Person>,
    tags: List<Tag>,
    onToggleDone: () -> Unit,
    onTaskClick: () -> Unit,
    modifier: Modifier = Modifier,
    showList: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onCommentClick: (() -> Unit)? = null,
    density: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    // Set by screens with no competing gesture (Today/Upcoming/Person/Tag/Search). Left null on
    // Project/List detail, which use DragDropReorderableColumn's long-press-drag on the same row —
    // mixing that with a horizontal swipe risks starving one gesture or the other.
    onSwipeToDelete: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
    // Project/List detail show this since tasks there carry arbitrary due dates with no
    // grouping context to imply one (unlike Today/Upcoming/NextDays, which already group by
    // date, or Tag/Person/Search, which mix tasks from many places at once).
    showDueDate: Boolean = false,
    onQuickSnooze: ((QuickSnoozePreset) -> Unit)? = null,
    onRenameTask: ((String) -> Unit)? = null
) {
    val accents = LocalYataAccents.current
    val listColor = list?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val hapticsEnabled = com.mj.yata.ui.theme.LocalHapticsEnabled.current
    val taskSwipeActionsEnabled = com.mj.yata.ui.theme.LocalTaskSwipeActionsEnabled.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showRenameDialog by remember { mutableStateOf(false) }

    val titleTextColor by animateColorAsState(
        targetValue = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = YataDur.fade, easing = YataEase.emphasized),
        label = "taskRowTitleColor"
    )

    val rowContent: @Composable (Modifier) -> Unit = { rowModifier ->
        Box(
            modifier = rowModifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
        // Priority color stripe, flush with the row's true left edge (outside the content
        // padding below) — M3-style state indicator, same color mapping as PriorityBars'
        // dots. IntrinsicSize.Min on this Box lets fillMaxHeight() below resolve against the
        // Row's own (otherwise unbounded, LazyColumn-item) height.
        if (!task.done && task.priority != "none") {
            val priorityStripeColor = when (task.priority) {
                "low" -> accents.accentE
                "med" -> accents.accentD
                "high" -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(priorityStripeColor)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onTaskClick, onLongClick = onLongClick)
                .padding(horizontal = horizontalPadding, vertical = density.verticalPadding()),
            verticalAlignment = Alignment.CenterVertically
        ) {
        if (selectionMode) {
            val selectionCheckboxBg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
                label = "taskSelectionCheckboxBg"
            )
            val selectionCheckScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                label = "taskSelectionCheckScale"
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(selectionCheckboxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = selectionCheckScale
                            scaleY = selectionCheckScale
                        }
                )
            }
        } else {
            // Left round checkbox
            SpringyCheck(
                checked = task.done,
                onCheckedChange = {
                    if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggleDone()
                },
                color = listColor,
                size = 24.dp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Middle area
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    color = titleTextColor,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (task.flag) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Flagged",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                PriorityBars(priority = task.priority)
            }

            // Overdue is independent of showDueDate — it's a warning, not a date display
            // preference, so it surfaces on every screen (Today, Upcoming, Tag, Person, Search)
            // rather than only the manual-order List/Project detail screens.
            val today = LocalDate.now()
            val overdue = task.due != null && !task.done && TaskScheduleUtils.parseDate(task.due)?.isBefore(today) == true
            val healthBadges = remember(task, overdue, today) {
                buildList {
                    if (!task.done && task.due == today.toString()) add("Due today")
                    if (!task.done && task.due == null && task.priority == "high") add("Needs date")
                    if (!task.done && task.due == null && task.flag) add("Flagged no date")
                    if (!task.done && task.due == null && task.time == null && task.recurrence == null && task.priority == "none" && !task.flag) add("Unplanned")
                }.take(2)
            }

            // Meta row below
            if (task.time != null || (showList && list != null) || task.recurrence != null || tags.isNotEmpty() || overdue || healthBadges.isNotEmpty() || (showDueDate && task.due != null) || (task.done && task.completedAt != null)) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (task.done && task.completedAt != null) {
                        Text(
                            text = TaskScheduleUtils.formatCompletedAt(task.completedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    } else if (overdue) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Overdue",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    } else if (showDueDate) {
                        task.due?.let { due ->
                            Text(
                                text = TaskScheduleUtils.formatDueDate(due),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                            )
                        }
                    }

                    task.time?.let { time ->
                        Text(
                            text = time,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }

                    if (showList && list != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(listColor, CircleShape)
                            )
                            Text(
                                text = list.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    task.recurrence?.let {
                        RecurrenceBadge(recurrence = it, compact = true)
                    }

                    healthBadges.forEach { badge ->
                        TaskHealthBadge(badge)
                    }

                    tags.take(2).forEach { t ->
                        TagChip(name = t.name, accentKey = t.color, size = "sm")
                    }
                }
            }
        }

        if (onCommentClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Comment,
                contentDescription = "Add comment",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onCommentClick() }
                    .padding(6.dp)
            )
        }

        if (onRenameTask != null && !selectionMode) {
            IconButton(
                onClick = { showRenameDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (onQuickSnooze != null && !task.done) {
            var showSnoozeMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showSnoozeMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Snooze",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showSnoozeMenu,
                    onDismissRequest = { showSnoozeMenu = false }
                ) {
                    QuickSnoozePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.label) },
                            onClick = {
                                showSnoozeMenu = false
                                onQuickSnooze(preset)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right assignee stack
        if (assignees.isNotEmpty()) {
            AssigneeStack(
                people = assignees,
                avatarSize = 24.dp
            )
        }
        }
        }
    }

    if (onSwipeToDelete != null && swipeEnabled && taskSwipeActionsEnabled) {
        // Both directions snap back immediately (confirmValueChange always returns false) —
        // the row never gets removed by the swipe box itself. Delete goes through the same
        // deferred-Undo-snackbar flow as everywhere else in the app (task data isn't touched
        // until the caller's snackbar times out, so Undo still works and the row disappears
        // naturally once the Flow re-emits without it, not via a dismiss animation here).
        val dismissState = rememberSwipeToDismissBoxState(
            // Default threshold is 50% of row width, which a quick flick can cross well
            // before it looks "intentional" — bumped to 75% so accidental swipes (e.g.
            // scrolling with a thumb that grazes a row) settle back instead of registering.
            positionalThreshold = { totalDistance -> totalDistance * 0.75f },
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSwipeToDelete()
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onToggleDone()
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
                false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val (color, icon, alignment) = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Default.Check, Alignment.CenterStart)
                    SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Default.Delete, Alignment.CenterEnd)
                    SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 24.dp),
                    contentAlignment = alignment
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            },
            content = { rowContent(Modifier) }
        )
    } else {
        rowContent(modifier)
    }

    if (showRenameDialog && onRenameTask != null) {
        var title by remember(task.id) { mutableStateOf(task.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Edit task") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameTask(title)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}
