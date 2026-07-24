package com.mj.yata.util.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    accentColor: Color,
    generatedOn: String,
    onRowBoundary: (Float) -> Unit = {}
) {
    val showNotes = includeNotes && !notes.isNullOrBlank()
    val showComments = includeComments && comments.isNotEmpty()

    CompositionLocalProvider(LocalYataAccents provides LightAccents) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = createTypography(),
            shapes = Shapes
        ) {
        Surface(color = Color.White) {
            Column(modifier = Modifier.width(CardWidth)) {
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
                            text = "YATA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ExportBadge(text = "TASK", color = accentColor)
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
                            text = if (done) "DONE" else "OPEN",
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (priority != "none") {
                            val priorityColor = when (priority) {
                                "high" -> MaterialTheme.colorScheme.error
                                "med" -> LightAccents.accentD
                                else -> LightAccents.accentE
                            }
                            ExportBadge(text = "${priority.uppercase()} PRIORITY", color = priorityColor)
                        }
                        if (flagged) {
                            ExportBadge(text = "FLAGGED", color = MaterialTheme.colorScheme.error)
                        }
                        if (overdue && !done) {
                            ExportBadge(text = "OVERDUE", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (completedAtLabel != null || dueLabel != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = completedAtLabel ?: "Due $dueLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val hasMeta = projectName != null || listName != null || assigneeNames.isNotEmpty() || tagChips.isNotEmpty()
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
                        projectName?.let { ExportMetaLine(label = "Project", value = it) }
                        listName?.let { ExportMetaLine(label = "List", value = it) }
                        if (assigneeNames.isNotEmpty()) {
                            ExportMetaLine(label = "Assigned to", value = assigneeNames.joinToString(", "))
                        }
                        if (tagChips.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                tagChips.forEach { chip ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(chip.color.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = chip.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = chip.color
                                        )
                                    }
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
                            text = "NOTES",
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
                            text = "COMMENTS (${comments.size})",
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
                        text = "Made with YATA",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Generated $generatedOn",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
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

@Composable
private fun ExportMetaLine(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
