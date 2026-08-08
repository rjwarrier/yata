package com.mj.yata.ui.screen.person

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.effectiveTagIds
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.StatusBarColor
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUtils
import com.mj.yata.util.EstimateUtils
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAnalyticsScreen(
    viewModel: MainViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val assignedTasks by remember(personId) {
        viewModel.getTasksForPerson(personId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()

    val person = remember(people, personId) { people.find { it.id == personId } }
    val showMissingPerson = com.mj.yata.ui.widgets.rememberMissingContentVisible(personId, person == null)
    if (person == null) {
        if (showMissingPerson) {
            com.mj.yata.ui.widgets.MissingContentState(
                itemName = stringResource(R.string.entity_person),
                onNavigateBack = onNavigateBack
            )
        } else {
            com.mj.yata.ui.widgets.ListDetailShimmer()
        }
        return
    }

    var period by remember { mutableStateOf(AnalyticsPeriod.MONTH) }
    val today = com.mj.yata.util.AppClock.today
    val accents = LocalYataAccents.current
    val personColor = accents.getAccent(person.color)
    val periodTasks = remember(assignedTasks, period, today) {
        AnalyticsUtils.filterTasksByPeriod(assignedTasks, period, today)
    }
    val currentOpen = remember(assignedTasks) { assignedTasks.filter { !it.done } }
    val currentOverdue = remember(currentOpen, today) { currentOpen.filter { it.isOverdue(today) } }
    val completedInPeriod = remember(assignedTasks, period, today) {
        AnalyticsUtils.completedInPeriod(assignedTasks, period, today)
    }
    val previousCompleted = remember(assignedTasks, period, today) {
        AnalyticsUtils.previousPeriodCompleted(assignedTasks, period, today)
    }
    val onTimeTasks = remember(periodTasks) {
        periodTasks.filter { it.done && it.completedAt != null && it.due != null }
    }
    val onTimeRate = remember(onTimeTasks) {
        if (onTimeTasks.isEmpty()) null else {
            onTimeTasks.count { task ->
                val due = task.due?.toLocalDateOrNull() ?: return@count true
                !task.completedAt!!.toLocalDate().isAfter(due)
            }.toFloat() / onTimeTasks.size
        }
    }
    val openAges = remember(currentOpen, today) {
        currentOpen.mapNotNull { task -> task.createdAt?.ageInDays(today) }
    }
    val medianTurnaround = remember(periodTasks) {
        periodTasks.filter { it.done }.mapNotNull { it.turnaroundDays() }.sorted().median()
    }
    val plannedOpenMinutes = remember(currentOpen) { currentOpen.mapNotNull { it.estimateMinutes }.sum() }
    val unestimatedOpen = remember(currentOpen) { currentOpen.count { it.estimateMinutes == null } }
    val dueNext7 = remember(currentOpen, today) {
        currentOpen.count { task ->
            val due = task.due?.toLocalDateOrNull() ?: return@count false
            !due.isBefore(today) && !due.isAfter(today.plusDays(7))
        }
    }
    val activity = remember(assignedTasks, period, today) { dailyCompletionRows(assignedTasks, period, today) }
    val agingBuckets = remember(currentOverdue, today) { overdueBuckets(currentOverdue, today) }
    val projectRows = remember(periodTasks, projects, today) { projectBreakdown(periodTasks, projects, today) }
    val listRows = remember(periodTasks, lists, today) { listBreakdown(periodTasks, lists, today) }
    val tagRows = remember(periodTasks, projects, tags, today) { tagBreakdown(periodTasks, projects, tags, today) }

    StatusBarColor(personColor.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.background))
    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = 2,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.person_analytics_title),
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                            )
                        )
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = personColor.copy(alpha = 0.16f)
                )
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SegmentedControl(
                items = listOf(AnalyticsPeriod.WEEK, AnalyticsPeriod.MONTH, AnalyticsPeriod.ALL),
                selectedItem = period,
                onItemSelected = { period = it },
                labelProvider = { it.label() }
            )

            PersonPerformanceHeader(
                person = person,
                color = personColor,
                total = periodTasks.size,
                done = periodTasks.count { it.done },
                open = currentOpen.size,
                overdue = currentOverdue.size
            )

            SectionTitle(stringResource(R.string.person_analytics_summary))
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = currentOpen.size.toString(),
                            label = stringResource(R.string.analytics_still_open)
                        )
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = currentOverdue.size.toString(),
                            label = stringResource(R.string.search_filter_overdue),
                            emphasise = currentOverdue.isNotEmpty()
                        )
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = completedInPeriod.toString(),
                            label = stringResource(R.string.person_analytics_completed_period),
                            trend = previousCompleted?.let { completedInPeriod - it }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = onTimeRate?.let { "${(it * 100).roundToInt()}%" } ?: "-",
                            label = stringResource(R.string.analytics_on_time_rate)
                        )
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = medianTurnaround?.let { "${it}d" } ?: "-",
                            label = stringResource(R.string.analytics_median_turnaround)
                        )
                        MetricTile(
                            modifier = Modifier.weight(1f),
                            value = openAges.maxOrNull()?.let { "${it}d" } ?: "-",
                            label = stringResource(R.string.analytics_oldest_open)
                        )
                    }
                    if (onTimeTasks.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.person_analytics_on_time_sample, onTimeTasks.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.analytics_planned_effort))
            SurfaceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        value = if (plannedOpenMinutes > 0) EstimateUtils.format(plannedOpenMinutes) else "-",
                        label = stringResource(R.string.person_analytics_planned_open)
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        value = dueNext7.toString(),
                        label = stringResource(R.string.analytics_due_in_7_days)
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        value = unestimatedOpen.toString(),
                        label = stringResource(R.string.person_analytics_unestimated)
                    )
                }
            }

            if (activity.isNotEmpty()) {
                SectionTitle(stringResource(R.string.person_analytics_activity))
                SurfaceCard {
                    ActivityBars(rows = activity, color = personColor)
                }
            }

            if (agingBuckets.isNotEmpty()) {
                SectionTitle(stringResource(R.string.analytics_overdue_aging))
                SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
                    val max = agingBuckets.maxOf { it.count }.coerceAtLeast(1)
                    agingBuckets.forEachIndexed { index, bucket ->
                        ProgressRow(
                            name = bucket.label,
                            detail = bucket.count.toString(),
                            color = if (index == agingBuckets.lastIndex) MaterialTheme.colorScheme.error else personColor,
                            progress = bucket.count.toFloat() / max
                        )
                        if (index != agingBuckets.lastIndex) CardDivider()
                    }
                }
            }

            SectionTitle(stringResource(R.string.person_analytics_breakdown))
            BreakdownBlock(
                title = stringResource(R.string.analytics_by_project),
                rows = projectRows,
                emptyText = stringResource(R.string.person_analytics_no_breakdown)
            )
            BreakdownBlock(
                title = stringResource(R.string.analytics_by_list),
                rows = listRows,
                emptyText = stringResource(R.string.person_analytics_no_breakdown)
            )
            if (tagsFeatureEnabled) {
                BreakdownBlock(
                    title = stringResource(R.string.analytics_by_tag),
                    rows = tagRows,
                    emptyText = stringResource(R.string.person_analytics_no_breakdown)
                )
            }

            if (currentOverdue.isNotEmpty()) {
                SectionTitle(stringResource(R.string.person_analytics_overdue_tasks))
                SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
                    currentOverdue.sortedBy { it.due }.take(6).forEachIndexed { index, task ->
                        OverdueTaskRow(task = task, onClick = { onNavigateToTaskDetail(task.id) })
                        if (index != currentOverdue.take(6).lastIndex) CardDivider()
                    }
                }
            }

            if (assignedTasks.isEmpty()) {
                SurfaceCard {
                    Text(
                        text = stringResource(R.string.person_analytics_no_tasks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonPerformanceHeader(
    person: Person,
    color: Color,
    total: Int,
    done: Int,
    open: Int,
    overdue: Int
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PersonAvatar(
                initials = person.initials,
                accentKey = person.color,
                photoUri = person.photoUri,
                size = 64.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.person_analytics_open_overdue, open, overdue),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressRing(
                progress = if (total > 0) done.toFloat() / total else 0f,
                size = 64.dp,
                strokeWidth = 6.dp,
                activeColor = color
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SurfaceCard(
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

@Composable
private fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
    trend: Int? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (emphasise) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            trend?.takeIf { it != 0 }?.let {
                Text(
                    text = if (it > 0) "+$it" else "$it",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (it > 0) LocalYataAccents.current.accentE else MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun ActivityBars(rows: List<ActivityRow>, color: Color) {
    val max = rows.maxOf { it.completed }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            rows.forEach { row ->
                val progress = row.completed.toFloat() / max
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (row.completed == 0) 0.04f else progress.coerceAtLeast(0.08f))
                            .background(color)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { row ->
                Text(
                    text = row.date.format(DateTimeFormatter.ofPattern("EEE")).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BreakdownBlock(title: String, rows: List<BreakdownRow>, emptyText: String) {
    SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (rows.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        } else {
            val max = rows.maxOf { it.total }.coerceAtLeast(1)
            rows.take(6).forEachIndexed { index, row ->
                val color = LocalYataAccents.current.getAccent(row.colorKey)
                ProgressRow(
                    name = row.name,
                    detail = stringResource(R.string.person_analytics_done_total, row.done, row.total),
                    color = color,
                    progress = row.total.toFloat() / max,
                    trailing = if (row.overdue > 0) stringResource(R.string.analytics_overdue_count, row.overdue) else null
                )
                if (index != rows.take(6).lastIndex) CardDivider()
            }
        }
    }
}

@Composable
private fun ProgressRow(
    name: String,
    detail: String,
    color: Color,
    progress: Float,
    trailing: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "personAnalyticsProgress"
    )
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
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(color)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OverdueTaskRow(task: Task, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task.due?.let { TaskScheduleUtils.formatDueDate(it) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private data class ActivityRow(val date: LocalDate, val completed: Int)

private data class BreakdownRow(
    val name: String,
    val colorKey: String,
    val total: Int,
    val done: Int,
    val overdue: Int
)

private data class AgingRow(val label: String, val count: Int)

private fun dailyCompletionRows(tasks: List<Task>, period: AnalyticsPeriod, today: LocalDate): List<ActivityRow> {
    if (period == AnalyticsPeriod.ALL) return emptyList()
    val start = when (period) {
        AnalyticsPeriod.WEEK -> today.minusDays(6)
        AnalyticsPeriod.MONTH -> today.minusDays(29)
        AnalyticsPeriod.ALL -> today
    }
    val completedByDate = tasks.mapNotNull { it.completedAt?.toLocalDate() }.groupingBy { it }.eachCount()
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { ActivityRow(it, completedByDate[it] ?: 0) }
        .toList()
}

private fun overdueBuckets(tasks: List<Task>, today: LocalDate): List<AgingRow> {
    fun age(task: Task) = task.due?.toLocalDateOrNull()?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 0
    val buckets = listOf(
        AgingRow("1-3 days", tasks.count { age(it) in 1..3 }),
        AgingRow("4-7 days", tasks.count { age(it) in 4..7 }),
        AgingRow("8+ days", tasks.count { age(it) >= 8 })
    )
    return buckets.filter { it.count > 0 }
}

private fun projectBreakdown(tasks: List<Task>, projects: List<Project>, today: LocalDate): List<BreakdownRow> {
    val byProject = tasks.filter { it.projectId != null }.groupBy { it.projectId }
    return projects.mapNotNull { project ->
        val scoped = byProject[project.id].orEmpty()
        if (scoped.isEmpty()) return@mapNotNull null
        BreakdownRow(
            name = project.name,
            colorKey = project.color,
            total = scoped.size,
            done = scoped.count { it.done },
            overdue = scoped.count { it.isOverdue(today) }
        )
    }.sortedByDescending { it.total }
}

private fun listBreakdown(tasks: List<Task>, lists: List<YataList>, today: LocalDate): List<BreakdownRow> {
    val byList = tasks.filter { it.listId != null }.groupBy { it.listId }
    return lists.mapNotNull { list ->
        val scoped = byList[list.id].orEmpty()
        if (scoped.isEmpty()) return@mapNotNull null
        BreakdownRow(
            name = list.name,
            colorKey = list.color,
            total = scoped.size,
            done = scoped.count { it.done },
            overdue = scoped.count { it.isOverdue(today) }
        )
    }.sortedByDescending { it.total }
}

private fun tagBreakdown(
    tasks: List<Task>,
    projects: List<Project>,
    tags: List<Tag>,
    today: LocalDate
): List<BreakdownRow> {
    val byTag = mutableMapOf<String, MutableList<Task>>()
    tasks.forEach { task ->
        task.effectiveTagIds(projects).forEach { tagId ->
            byTag.getOrPut(tagId) { mutableListOf() }.add(task)
        }
    }
    return tags.mapNotNull { tag ->
        val scoped = byTag[tag.id].orEmpty()
        if (scoped.isEmpty()) return@mapNotNull null
        BreakdownRow(
            name = tag.name,
            colorKey = tag.color,
            total = scoped.size,
            done = scoped.count { it.done },
            overdue = scoped.count { it.isOverdue(today) }
        )
    }.sortedByDescending { it.total }
}

private fun Task.isOverdue(today: LocalDate): Boolean =
    !done && due?.toLocalDateOrNull()?.isBefore(today) == true

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun Long.ageInDays(today: LocalDate): Int =
    ChronoUnit.DAYS.between(toLocalDate(), today).toInt().coerceAtLeast(0)

private fun Task.turnaroundDays(): Int? {
    val created = createdAt?.toLocalDate() ?: return null
    val completed = completedAt?.toLocalDate() ?: return null
    return ChronoUnit.DAYS.between(created, completed).toInt().coerceAtLeast(0)
}

private fun List<Int>.median(): Int? =
    if (isEmpty()) null else this[size / 2]
