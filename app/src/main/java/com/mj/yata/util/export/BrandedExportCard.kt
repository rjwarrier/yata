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
import androidx.compose.runtime.remember
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
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.theme.LightAccents
import com.mj.yata.ui.theme.LightColors
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.Shapes
import com.mj.yata.ui.theme.createTypography
import com.mj.yata.util.TaskScheduleUtils
import java.time.LocalDate

data class ExportTagChip(val name: String, val color: Color)

data class ExportTaskRow(
    val title: String,
    val done: Boolean,
    val dueLabel: String? = null,
    val overdue: Boolean = false,
    val completedAtLabel: String? = null,
    /** Project or list name this task belongs to; blank when it has neither. */
    val groupLabel: String = "",
    val tagChips: List<ExportTagChip> = emptyList(),
    val assigneeNames: List<String> = emptyList()
)

fun Task.toExportRow(
    groupLabel: String = "",
    tagChips: List<ExportTagChip> = emptyList(),
    assigneeNames: List<String> = emptyList()
): ExportTaskRow {
    val overdue = due != null && !done && TaskScheduleUtils.parseDate(due)?.isBefore(LocalDate.now()) == true
    return ExportTaskRow(
        title = title,
        done = done,
        dueLabel = due?.let { TaskScheduleUtils.formatDueDate(it) },
        overdue = overdue,
        completedAtLabel = if (done) completedAt?.let { TaskScheduleUtils.formatCompletedAt(it) } else null,
        groupLabel = groupLabel,
        tagChips = tagChips,
        assigneeNames = assigneeNames
    )
}

private const val UNGROUPED_LABEL = "No project or list"
private val CardWidth = 420.dp
private val HairlineColor = Color(0xFFE8E8EC)

enum class ExportDensity { COMPACT, RELAXED }

private data class ExportSpacing(
    val headerVerticalPadding: androidx.compose.ui.unit.Dp,
    val listVerticalPadding: androidx.compose.ui.unit.Dp,
    val groupTopGap: androidx.compose.ui.unit.Dp,
    val groupBottomGap: androidx.compose.ui.unit.Dp,
    val rowGap: androidx.compose.ui.unit.Dp,
    val footerVerticalPadding: androidx.compose.ui.unit.Dp
)

private fun spacingFor(density: ExportDensity) = when (density) {
    ExportDensity.RELAXED -> ExportSpacing(
        headerVerticalPadding = 24.dp,
        listVerticalPadding = 22.dp,
        groupTopGap = 22.dp,
        groupBottomGap = 12.dp,
        rowGap = 12.dp,
        footerVerticalPadding = 18.dp
    )
    ExportDensity.COMPACT -> ExportSpacing(
        headerVerticalPadding = 16.dp,
        listVerticalPadding = 14.dp,
        groupTopGap = 12.dp,
        groupBottomGap = 6.dp,
        rowGap = 6.dp,
        footerVerticalPadding = 10.dp
    )
}

/**
 * Branded, self-contained report card used for both Tag and Person "Export as PDF/Image" —
 * one shared layout so both entity types get the same look. Rendered off-screen and
 * rasterized by [captureComposableToBitmap]. Builds its own fixed light MaterialTheme (brand
 * colors, not [YataTheme]) rather than inheriting the trigger screen's theme: YataTheme's
 * status/nav-bar SideEffect would run against the *real* Activity window while this hidden
 * card composes off-screen, visibly flashing the on-screen status bar for a frame.
 *
 * [onRowBoundary] reports each task row's bottom edge (in px, root-relative) as it's laid
 * out — the PDF export path uses these as the only legal page-break points, so pagination
 * never slices a task row (or its wrapped due-date line) across two pages.
 */
@Composable
fun BrandedExportCard(
    entityKind: String,
    entityName: String,
    accentColor: Color,
    doneCount: Int,
    totalCount: Int,
    overdueCount: Int,
    tasks: List<ExportTaskRow>,
    generatedOn: String,
    density: ExportDensity = ExportDensity.RELAXED,
    strikeThroughCompleted: Boolean = false,
    showTags: Boolean = true,
    showAssignees: Boolean = true,
    onRowBoundary: (Float) -> Unit = {}
) {
    val spacing = spacingFor(density)
    CompositionLocalProvider(LocalYataAccents provides LightAccents) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = createTypography(),
            shapes = Shapes
        ) {
        Surface(color = Color.White) {
            Column(modifier = Modifier.width(CardWidth)) {
                // Letterhead stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(accentColor)
                )

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.10f).compositeOver(Color.White))
                        .padding(horizontal = 28.dp, vertical = spacing.headerVerticalPadding)
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accentColor)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = entityKind.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = entityName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExportStatChip(label = "Total", value = "$totalCount")
                        ExportStatChip(label = "Done", value = "$doneCount", accentColor = MaterialTheme.colorScheme.primary)
                        if (overdueCount > 0) {
                            ExportStatChip(label = "Overdue", value = "$overdueCount", accentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% complete",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accentColor.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(accentColor)
                        )
                    }
                }

                // Task list, grouped under a project/list subheading — grouping is a stable
                // sort by groupLabel (so within-group order is untouched) with the ungrouped
                // bucket pushed last, then Kotlin's groupBy (which preserves first-encounter
                // key order) forms the sections.
                val grouped = remember(tasks) {
                    tasks
                        .sortedBy { if (it.groupLabel.isBlank()) "￿" else it.groupLabel }
                        .groupBy { it.groupLabel }
                }
                Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = spacing.listVerticalPadding)) {
                    if (tasks.isEmpty()) {
                        Text(
                            text = "No tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        grouped.entries.forEachIndexed { index, (label, rows) ->
                            Column(
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else spacing.groupTopGap)
                            ) {
                                // Blank label means "no project/list" — that heading would just
                                // read "No project or list" over the whole report when the
                                // exported entity itself has no sub-grouping to offer (e.g. most
                                // tasks in a Project export have no separate list), so it's
                                // skipped entirely rather than shown as a redundant heading.
                                if (label.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(bottom = spacing.groupBottomGap)
                                            // Reported as a break candidate too (its *top*, not
                                            // bottom) — otherwise a page could end right after
                                            // this heading, stranding it alone with its tasks
                                            // pushed to the next page.
                                            .onGloballyPositioned { coordinates ->
                                                onRowBoundary(coordinates.boundsInRoot().top)
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.2.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${rows.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                rows.forEachIndexed { rowIndex, row ->
                                    Column(
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            onRowBoundary(coordinates.boundsInRoot().bottom)
                                        }
                                    ) {
                                        ExportTaskLine(row, strikeThroughCompleted, showTags, showAssignees)
                                        Spacer(modifier = Modifier.height(spacing.rowGap))
                                        if (rowIndex != rows.lastIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(HairlineColor)
                                            )
                                            Spacer(modifier = Modifier.height(spacing.rowGap))
                                        }
                                    }
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

                // Footer — centered, small subtext. Also reports its own bottom edge as a
                // break candidate so PDF pagination can't land inside it (it's the last
                // content, but a page's budget could otherwise run out a few px into it).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            onRowBoundary(coordinates.boundsInRoot().bottom)
                        }
                        .padding(horizontal = 28.dp, vertical = spacing.footerVerticalPadding),
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
private fun ExportStatChip(label: String, value: String, accentColor: Color = Color.Unspecified) {
    val tint = if (accentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else accentColor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = tint
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun ExportTaskLine(
    row: ExportTaskRow,
    strikeThroughCompleted: Boolean,
    showTags: Boolean,
    showAssignees: Boolean
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (row.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (row.done) {
                MaterialTheme.colorScheme.primary
            } else if (row.overdue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (row.done && strikeThroughCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (row.done) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (row.done && row.completedAtLabel != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Completed ${row.completedAtLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else if (row.dueLabel != null || row.overdue) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.dueLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (row.overdue) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "OVERDUE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (showTags && row.tagChips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.tagChips.take(4).forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(chip.color.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = chip.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = chip.color
                            )
                        }
                    }
                }
            }
            if (showAssignees && row.assigneeNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Assigned to: ${row.assigneeNames.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}
