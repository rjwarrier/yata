package com.mj.yata.util.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.theme.LightAccents
import com.mj.yata.ui.theme.LightColors
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.Shapes
import com.mj.yata.ui.theme.createTypography

/** One resolved comment ready to render — author display name (null if the comment has no
 * resolvable author) and a pre-formatted timestamp, matching how TaskDetailScreen's own
 * comment list resolves `authorId` before display. */
data class ExportCommentRow(
    val authorLabel: String?,
    val timestampLabel: String,
    val body: String
)

data class ExportSubtaskRow(
    val title: String,
    val done: Boolean,
    val depth: Int = 0
)

private val CardWidth = 420.dp
private val HairlineColor = Color(0xFFE8E8EC)

/**
 * Branded, self-contained single-task report card — the task's own [BrandedExportCard]
 * equivalent. Rendered off-screen and rasterized by [captureComposableToBitmap]. Builds its
 * own fixed light MaterialTheme rather than inheriting the trigger screen's theme, for the same
 * reason as [BrandedExportCard] (avoids a status-bar flash from YataTheme's SideEffect running
 * against the real Activity window while this composes off-screen).
 *
 * The hero header carries everything needed to identify the task at a glance — status,
 * priority/flag, due date, its Project and List, who it's assigned to, and its tags — each line
 * only rendered when present, so a bare task doesn't show a wall of "None" rows. Notes and
 * Comments are separate, optional sections below the header, gated by [includeNotes]/
 * [includeComments] so the export dialog's toggles actually control what's on the page.
 *
 * [onRowBoundary] reports each comment row's (and the header/notes block's) bottom edge in px,
 * root-relative — the PDF export path uses these as the only legal page-break points, matching
 * [BrandedExportCard]'s pagination contract.
 */
@Composable
fun TaskExportCard(
    title: String,
    done: Boolean,
    priority: String,
    flagged: Boolean,
    dueLabel: String?,
    overdue: Boolean,
    completedAtLabel: String?,
    projectName: String?,
    listName: String?,
    assigneeNames: List<String>,
    tagChips: List<ExportTagChip>,
    notes: String?,
    includeNotes: Boolean,
    comments: List<ExportCommentRow>,
    includeComments: Boolean,
    recurrenceLabel: String? = null,
    reminderLabel: String? = null,
    subtasks: List<ExportSubtaskRow> = emptyList(),
    includeSubtasks: Boolean = true,
    includeScheduleDetails: Boolean = true,
    accentColor: Color,
    generatedOn: String,
    showMadeWithFooter: Boolean = true,
    cardWidth: Dp = CardWidth,
    onRowBoundary: (Float) -> Unit = {}
) {
    val showNotes = includeNotes && !notes.isNullOrBlank()
    val showComments = includeComments && comments.isNotEmpty()
    val showSubtasks = includeSubtasks && subtasks.isNotEmpty()
    val showScheduleDetails = includeScheduleDetails && (recurrenceLabel != null || reminderLabel != null)

    CompositionLocalProvider(LocalYataAccents provides LightAccents) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = createTypography(),
            shapes = Shapes
        ) {
        Surface(color = Color.White) {
            Column(modifier = Modifier.width(cardWidth)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(accentColor)
                )

                // Hero header — status, priority/flag, due date, Project/List, assignees, tags.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.10f).compositeOver(Color.White))
                        .padding(horizontal = 28.dp, vertical = 24.dp)
                        .onGloballyPositioned { onRowBoundary(it.boundsInRoot().bottom) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_monochrome),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.export_yata_wordmark),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ExportBadge(text = stringResource(R.string.export_task_badge), color = accentColor)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportBadge(
                            text = stringResource(if (done) R.string.export_done_badge else R.string.export_open_badge),
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (priority != "none") {
                            val priorityColor = when (priority) {
                                "high" -> MaterialTheme.colorScheme.error
                                "med" -> LightAccents.accentD
                                else -> LightAccents.accentE
                            }
                            ExportBadge(text = stringResource(R.string.export_priority_badge, priority.uppercase()), color = priorityColor)
                        }
                        if (flagged) {
                            ExportBadge(text = stringResource(R.string.export_flagged_badge), color = MaterialTheme.colorScheme.error)
                        }
                        if (overdue && !done) {
                            ExportBadge(text = stringResource(R.string.export_overdue_badge), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (completedAtLabel != null || dueLabel != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = completedAtLabel ?: stringResource(R.string.task_export_due_label, dueLabel ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val hasMeta = projectName != null ||
                        listName != null ||
                        assigneeNames.isNotEmpty() ||
                        tagChips.isNotEmpty() ||
                        showScheduleDetails
                    if (hasMeta) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(accentColor.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projectName?.let {
                            ExportLabeledPills(label = stringResource(R.string.export_project_label)) {
                                ExportPill(text = it, color = accentColor, fontSize = 10.sp)
                            }
                        }
                        listName?.let {
                            ExportLabeledPills(label = stringResource(R.string.export_list_label)) {
                                ExportPill(
                                    text = it,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        if (assigneeNames.isNotEmpty()) {
                            ExportLabeledPills(label = stringResource(R.string.export_assigned_to_label)) {
                                assigneeNames.forEach { name ->
                                    ExportPill(
                                        text = stringResource(R.string.export_assignee_pill, name),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        if (tagChips.isNotEmpty()) {
                            ExportLabeledPills(label = stringResource(R.string.export_tags_label)) {
                                tagChips.forEach { chip ->
                                    ExportPill(text = chip.name, color = chip.color, fontSize = 10.sp)
                                }
                            }
                        }
                        if (showScheduleDetails) {
                            ExportLabeledPills(label = stringResource(R.string.export_schedule_label)) {
                                recurrenceLabel?.let {
                                    ExportPill(
                                        text = it,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 10.sp
                                    )
                                }
                                reminderLabel?.let {
                                    ExportPill(
                                        text = stringResource(R.string.export_reminder_pill, it),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (showSubtasks) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                            .onGloballyPositioned { onRowBoundary(it.boundsInRoot().bottom) }
                    ) {
                        val doneSubtasks = subtasks.count { it.done }
                        Text(
                            text = stringResource(R.string.export_subtasks_heading, doneSubtasks, subtasks.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            subtasks.forEach { subtask ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Spacer(modifier = Modifier.width((subtask.depth * 14).dp))
                                    Icon(
                                        imageVector = if (subtask.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (subtask.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subtask.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (subtask.done) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (subtask.done) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes
                if (showNotes) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                            .onGloballyPositioned { onRowBoundary(it.boundsInRoot().bottom) }
                    ) {
                        Text(
                            text = stringResource(R.string.export_notes_heading),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = notes.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Comments
                if (showComments) {
                    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                        Text(
                            text = stringResource(R.string.export_comments_heading, comments.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        comments.forEachIndexed { index, comment ->
                            Column(
                                modifier = Modifier.onGloballyPositioned { onRowBoundary(it.boundsInRoot().bottom) }
                            ) {
                                Text(
                                    text = comment.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = listOfNotNull(comment.authorLabel, comment.timestampLabel).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (index != comments.lastIndex) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(HairlineColor)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }

                if (showMadeWithFooter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp)
                            .height(1.dp)
                            .background(HairlineColor)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { onRowBoundary(it.boundsInRoot().bottom) }
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.export_made_with_yata),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.export_generated_on, generatedOn),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ExportBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontSize = 10.sp),
            color = Color.White
        )
    }
}
