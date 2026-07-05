package com.mj.yata.ui.screen.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUtils
import com.mj.yata.util.DayActivity
import com.mj.yata.util.EntityStat
import com.mj.yata.util.PriorityStat
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()

    var period by remember { mutableStateOf(AnalyticsPeriod.WEEK) }

    val periodTasks = remember(tasks, period) { AnalyticsUtils.filterTasksByPeriod(tasks, period) }
    val totalCount = periodTasks.size
    val doneCount = periodTasks.count { it.done }
    val completionPct = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val previousPct = remember(tasks, period) { AnalyticsUtils.previousPeriodCompletionPct(tasks, period) }

    val streak = remember(tasks) { AnalyticsUtils.currentStreak(tasks) }
    val overdue = remember(tasks) { AnalyticsUtils.overdueCount(tasks) }
    val dailyActivity = remember(tasks, period) { AnalyticsUtils.dailyActivity(tasks, period) }
    val priorityStats = remember(periodTasks) { AnalyticsUtils.byPriority(periodTasks) }

    val projectStats = remember(periodTasks, projects) { AnalyticsUtils.byProject(periodTasks, projects) }
    val personStats = remember(periodTasks, tasks, people) { AnalyticsUtils.byPerson(periodTasks, tasks, people) }
    val tagStats = remember(periodTasks, tasks, projects, tags) { AnalyticsUtils.byTag(periodTasks, tasks, projects, tags) }

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SegmentedControl(
                items = listOf(AnalyticsPeriod.WEEK, AnalyticsPeriod.MONTH, AnalyticsPeriod.ALL),
                selectedItem = period,
                onItemSelected = { period = it },
                labelProvider = {
                    when (it) {
                        AnalyticsPeriod.WEEK -> "7 Days"
                        AnalyticsPeriod.MONTH -> "30 Days"
                        AnalyticsPeriod.ALL -> "All Time"
                    }
                }
            )

            // Streak & overdue — always "right now", independent of the period filter above.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    value = "$streak",
                    label = "day streak"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WarningAmber,
                    iconTint = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$overdue",
                    label = if (overdue == 1) "overdue task" else "overdue tasks"
                )
            }

            // Summary card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ProgressRing(progress = completionPct, size = 72.dp, strokeWidth = 6.dp)
                    Column {
                        Text(
                            text = "$doneCount of $totalCount completed",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (totalCount == 0) "No tasks due in this period." else "${totalCount - doneCount} still open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (previousPct != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val deltaPoints = ((completionPct - previousPct) * 100).roundToInt()
                            val trendColor = when {
                                deltaPoints > 0 -> LocalYataAccents.current.accentE
                                deltaPoints < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (deltaPoints != 0) {
                                    Icon(
                                        imageVector = if (deltaPoints > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = trendColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "${if (deltaPoints > 0) "+" else ""}$deltaPoints pp vs previous period",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = trendColor
                                )
                            }
                        }
                    }
                }
            }

            if (dailyActivity.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DAILY ACTIVITY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Tasks completed, by day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DailyActivityChart(days = dailyActivity, showLabels = period == AnalyticsPeriod.WEEK)
                        }
                    }
                }
            }

            if (priorityStats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BY PRIORITY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            priorityStats.forEachIndexed { index, stat ->
                                PriorityStatRow(stat)
                                if (index != priorityStats.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (projectsFeatureEnabled) {
                AnalyticsSection(title = "By Project", stats = projectStats) { stat ->
                    val accents = LocalYataAccents.current
                    EntityStatRow(stat = stat, color = accents.getAccent(stat.colorKey))
                }
            }

            if (peopleFeatureEnabled) {
                AnalyticsSection(title = "By Person", stats = personStats) { stat ->
                    val accents = LocalYataAccents.current
                    EntityStatRow(
                        stat = stat,
                        color = accents.getAccent(stat.colorKey),
                        subtitle = insightSubtitle(stat),
                        leading = {
                            PersonAvatar(
                                initials = stat.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                                accentKey = stat.colorKey,
                                size = 28.dp
                            )
                        }
                    )
                }
            }

            if (tagsFeatureEnabled) {
                AnalyticsSection(title = "By Tag", stats = tagStats) { stat ->
                    val accents = LocalYataAccents.current
                    val color = if (stat.colorKey == "error") MaterialTheme.colorScheme.error else accents.getAccent(stat.colorKey)
                    EntityStatRow(stat = stat, color = color, subtitle = insightSubtitle(stat))
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun InsightChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyActivityChart(days: List<DayActivity>, showLabels: Boolean) {
    val maxCount = (days.maxOfOrNull { it.completedCount } ?: 0).coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val doneColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val barWidth = size.width * 0.55f
                val x = (size.width - barWidth) / 2f
                val barHeight = size.height * (day.completedCount.toFloat() / maxCount)

                // Faint full-height track so a zero-completion day still reads as a bar slot.
                drawRoundRect(
                    color = trackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                )
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = doneColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
    if (showLabels) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                Text(
                    text = day.date.format(DateTimeFormatter.ofPattern("EEE")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PriorityStatRow(stat: PriorityStat) {
    val accents = LocalYataAccents.current
    val (label, color) = when (stat.priority) {
        "high" -> "High" to MaterialTheme.colorScheme.error
        "med" -> "Medium" to accents.accentD
        "low" -> "Low" to accents.accentE
        else -> "No priority" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = stat.pct,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.16f)
            )
        }
        Text(
            text = "${stat.done}/${stat.total}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalyticsSection(
    title: String,
    stats: List<EntityStat>,
    row: @Composable (EntityStat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (stats.isEmpty()) {
                Text(
                    text = "Nothing in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    stats.forEachIndexed { index, stat ->
                        row(stat)
                        if (index != stats.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "N overdue" (if any) plus the on-time completion rate, e.g. "1 overdue · 80% on-time" —
 * null pieces (no overdue work, or nothing in the period has a completion timestamp to judge)
 * are dropped rather than shown as zero. */
private fun insightSubtitle(stat: EntityStat): String? {
    val parts = mutableListOf<String>()
    if (stat.overdue > 0) parts += if (stat.overdue == 1) "1 overdue" else "${stat.overdue} overdue"
    stat.onTimeRate?.let { parts += "${(it * 100).roundToInt()}% on-time" }
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun EntityStatRow(
    stat: EntityStat,
    color: Color,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stat.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (stat.overdue > 0) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Has overdue tasks",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = stat.pct,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.16f)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stat.overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${stat.done}/${stat.total}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
