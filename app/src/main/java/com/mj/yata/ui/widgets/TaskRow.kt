package com.mj.yata.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.QuickSnoozePreset
import com.mj.yata.domain.model.SwipeAction
import com.mj.yata.domain.model.isDeferredOn
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskRowDensity
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate

/** Inset from the screen edge to a task card, when card mode is on. Same on every list screen. */
private val TASK_CARD_MARGIN = 12.dp

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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val swipeRightAction = com.mj.yata.ui.theme.LocalSwipeRightAction.current
    val swipeLeftAction = com.mj.yata.ui.theme.LocalSwipeLeftAction.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showRenameDialog by remember { mutableStateOf(false) }

    val titleTextColor by animateColorAsState(
        targetValue = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = YataDur.fade, easing = YataEase.emphasized),
        label = "taskRowTitleColor"
    )

    // Card mode gives the card a fixed outer margin and the content its own smaller inset, so the
    // two paddings don't stack. The margin is deliberately *not* the caller's `horizontalPadding`:
    // that is a content inset chosen per screen (Today passes 12dp where everything else takes the
    // 20dp default), and borrowing it made cards different widths from one screen to the next.
    // Flat mode keeps the caller's padding exactly as it was.
    //
    // The gap between cards is not part of the tap target — the card is the thing you press, which
    // is why the margin is kept narrow rather than matching the old full-width row.
    val cardBackground = com.mj.yata.ui.theme.LocalTaskCardBackground.current
    val contentHorizontalPadding = if (cardBackground) 14.dp else horizontalPadding

    // Press feedback. Deliberately not PressableScaleBox, which every other pressable surface in
    // the app uses: that wraps `clickable`, so it would drop this row's long-press (selection,
    // drag, rename), swap the ripple for nothing, and fire a haptic on every tap of the most
    // tapped thing in the app. Sharing the interaction source with the existing combinedClickable
    // gets the same scale while leaving all of that intact.
    //
    // Small on purpose — the row is full width, so what reads as a gentle press on a 56dp card is
    // a lot of travel at the screen edges. Tokenised rather than sprung so Reduce Motion applies.
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphasized),
        label = "taskRowPressScale"
    )

    val rowContent: @Composable (Modifier) -> Unit = { rowModifier ->
        Box(
            modifier = rowModifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (cardBackground) {
                        Modifier
                            .padding(horizontal = TASK_CARD_MARGIN, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    } else {
                        Modifier
                    }
                )
                .height(IntrinsicSize.Min)
        ) {
        // Priority color stripe, flush with the row's true left edge (outside the content
        // padding below) — M3-style state indicator, same color mapping as PriorityBars'
        // dots. IntrinsicSize.Min on this Box lets fillMaxHeight() below resolve against the
        // Row's own (otherwise unbounded, LazyColumn-item) height.
        val isEnhancedM3 = com.mj.yata.ui.theme.LocalEnhancedM3Theming.current
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
                    .width(if (isEnhancedM3) 4.dp else 3.dp)
                    .background(
                        color = priorityStripeColor,
                        shape = if (isEnhancedM3) RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp) else RoundedCornerShape(0.dp)
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = pressSource,
                    indication = LocalIndication.current,
                    onClick = onTaskClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = contentHorizontalPadding, vertical = density.verticalPadding()),
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
                    contentDescription = stringResource(R.string.cd_task_selected),
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
                size = 24.dp,
                modifier = Modifier.testTag("task_check:${task.title}")
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = task.flag,
                    enter = androidx.compose.animation.scaleIn(
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = stringResource(R.string.cd_task_flagged),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                PriorityBars(priority = task.priority)
            }

            // Overdue is independent of showDueDate — it's a warning, not a date display
            // preference, so it surfaces on every screen (Today, Upcoming, Tag, Person, Search)
            // rather than only the manual-order List/Project detail screens.
            val today = com.mj.yata.util.AppClock.today
            val overdue = task.due != null && !task.done && TaskScheduleUtils.parseDate(task.due)?.isBefore(today) == true
            // A deferred task is filtered out of Today, but still listed in its project/list and
            // in search. Without a marker it reads as an ordinary task that Today is inexplicably
            // ignoring, so it gets a badge naming the date it becomes actionable. Takes precedence
            // over "Overdue": a task that is both is waiting, not late — the start date is the
            // reason it hasn't been done, and showing red here would be blaming the user for it.
            val deferred = remember(task, today) { task.isDeferredOn(today.toString()) }
            val healthBadges = remember(task, overdue, today) {
                buildList {
                    if (!task.done && task.due == today.toString()) add("Due today")
                    if (!task.done && task.due == null && task.priority == "high") add("Needs date")
                    if (!task.done && task.due == null && task.flag) add("Flagged no date")
                    if (!task.done && task.due == null && task.time == null && task.recurrence == null && task.priority == "none" && !task.flag) add("Unplanned")
                }.take(2)
            }

            // Meta row below
            if (task.time != null || (showList && list != null) || task.recurrence != null || task.subtasks.isNotEmpty() || task.estimateMinutes != null || tags.isNotEmpty() || overdue || deferred || healthBadges.isNotEmpty() || (showDueDate && task.due != null) || (task.done && task.completedAt != null)) {
                Spacer(modifier = Modifier.height(4.dp))
                // FlowRow, not Row: the number of things in here varies (completed-at or due date,
                // time, list, recurrence, up to two health badges, up to two tags) and a plain Row
                // hands whatever is left of the width to each child in order. Once the width ran
                // out, later children were measured at ~0dp and their text wrapped one character
                // per line — the list name rendered as a vertical "W o r k". Anything that doesn't
                // fit now moves to the next line at its natural width instead.
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    // Two lines covers every realistic combination; beyond that the row would
                    // dominate the list, so the tail is dropped rather than allowed to grow.
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (task.done && task.completedAt != null) {
                        Text(
                            text = TaskScheduleUtils.formatCompletedAt(task.completedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    } else if (deferred) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.task_starts_on,
                                    task.startDate?.let { TaskScheduleUtils.formatDueDate(it) }.orEmpty()
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
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
                            // Stored 12-hour, shown however the clock preference asks for.
                            text = com.mj.yata.util.TaskScheduleUtils.displayTime(time) ?: time,
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
                                ),
                                // A list name long enough to fill a whole line ellipsizes rather
                                // than breaking across two.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    task.recurrence?.let {
                        RecurrenceBadge(recurrence = it, compact = true)
                    }

                    // Subtask progress. Only worth the space once there is actually a checklist
                    // to track, so a task with no subtasks shows nothing rather than "0/0".
                    if (task.subtasks.isNotEmpty()) {
                        val doneSubtasks = task.subtasks.count { it.done }
                        val allDone = doneSubtasks == task.subtasks.size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = if (allDone) listColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$doneSubtasks/${task.subtasks.size}",
                                color = if (allDone) listColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Planned effort, when it's been set. Deliberately quiet — same weight as the
                    // subtask counter beside it, since an estimate is context for the row, not a
                    // status worth competing with the overdue/priority signals.
                    task.estimateMinutes?.let { minutes ->
                        val estimateLabel = com.mj.yata.util.EstimateUtils.format(minutes)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.cd_task_estimate, estimateLabel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = estimateLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
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
                contentDescription = stringResource(R.string.cd_task_add_comment),
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
                    contentDescription = stringResource(R.string.cd_task_edit_title),
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
                        contentDescription = stringResource(R.string.cd_task_snooze),
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
                            text = { Text(quickSnoozeLabel(preset)) },
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

    // Which of the configured actions this particular row can actually carry out. Delete and
    // snooze depend on callbacks the caller may not have passed, and an action whose callback is
    // missing has to fall back to doing nothing rather than silently mapping to something else.
    fun resolveAction(action: SwipeAction): (() -> Unit)? = when (action) {
        SwipeAction.NONE -> null
        SwipeAction.COMPLETE -> onToggleDone
        SwipeAction.DELETE -> onSwipeToDelete
        SwipeAction.SNOOZE_TOMORROW -> onQuickSnooze?.let { snooze -> { snooze(QuickSnoozePreset.TOMORROW_MORNING) } }
        SwipeAction.EDIT_TITLE -> onRenameTask?.let { { showRenameDialog = true } }
    }

    val rightHandler = resolveAction(swipeRightAction)
    val leftHandler = resolveAction(swipeLeftAction)

    if ((rightHandler != null || leftHandler != null) && swipeEnabled && taskSwipeActionsEnabled) {
        // Both directions snap back immediately (confirmValueChange always returns false) —
        // the row never gets removed by the swipe box itself. Delete goes through the same
        // deferred-Undo-snackbar flow as everywhere else in the app (task data isn't touched
        // until the caller's snackbar times out, so Undo still works and the row disappears
        // naturally once the Flow re-emits without it, not via a dismiss animation here).
        // rememberSwipeToDismissBoxState bakes confirmValueChange into the returned state via a
        // keyless rememberSaveable, so it would otherwise keep running the handlers captured at
        // first composition forever — the background icon updates live (read fresh below), but
        // the swipe would keep performing whatever action was configured when the row first
        // composed. Keying on the two actions forces a fresh state (and fresh handlers) exactly
        // when the setting actually changes.
        val dismissState = key(swipeRightAction, swipeLeftAction) {
            rememberSwipeToDismissBoxState(
                // Default threshold is 50% of row width, which a quick flick can cross well
                // before it looks "intentional" — bumped to 75% so accidental swipes (e.g.
                // scrolling with a thumb that grazes a row) settle back instead of registering.
                positionalThreshold = { totalDistance -> totalDistance * 0.75f },
                confirmValueChange = { value ->
                    val handler = when (value) {
                        SwipeToDismissBoxValue.EndToStart -> leftHandler
                        SwipeToDismissBoxValue.StartToEnd -> rightHandler
                        SwipeToDismissBoxValue.Settled -> null
                    }
                    if (handler != null) {
                        if (hapticsEnabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        handler()
                    }
                    false
                }
            )
        }
        SwipeToDismissBox(
            state = dismissState,
            // A direction with no action must not swipe at all, or the row slides open onto a
            // blank background and springs back having done nothing.
            enableDismissFromStartToEnd = rightHandler != null,
            enableDismissFromEndToStart = leftHandler != null,
            modifier = modifier,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val action = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
                    SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
                    SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
                }
                // Destructive actions get the error colour, the rest the primary container, so the
                // backdrop still reads as a warning when it should and only when it should.
                val isDestructive = action == SwipeAction.DELETE
                val color = when {
                    action == SwipeAction.NONE -> Color.Transparent
                    isDestructive -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val icon = when (action) {
                    SwipeAction.NONE -> null
                    SwipeAction.COMPLETE -> Icons.Default.Check
                    SwipeAction.DELETE -> Icons.Default.Delete
                    SwipeAction.SNOOZE_TOMORROW -> Icons.Default.Snooze
                    SwipeAction.EDIT_TITLE -> Icons.Default.Edit
                }
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
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
                            tint = if (isDestructive) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
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
            title = { Text(stringResource(R.string.task_row_edit_title)) },
            text = {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameTask(title)
                    showRenameDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
