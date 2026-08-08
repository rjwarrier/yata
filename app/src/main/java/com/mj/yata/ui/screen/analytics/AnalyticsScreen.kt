package com.mj.yata.ui.screen.analytics

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.DayActivity
import com.mj.yata.util.EntityStat
import com.mj.yata.util.EstimateUtils
import com.mj.yata.util.PriorityStat
import com.mj.yata.util.label
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

/**
 * Every number on this screen is about some set of tasks, and until now none of them said *which*.
 * A row becomes tappable when there is an exact destination for the tasks behind it — an entity's
 * own detail screen, or a search filter that selects precisely the set being counted. Rows with no
 * exact match stay inert on purpose: sending someone to an almost-right list is worse than leaving
 * them to look, because they'd act on the wrong tasks believing they were the right ones.
 */
private fun Modifier.drillDown(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else this.clickable(onClick = onClick)

/** The affordance that tells a tappable row apart from an inert one, given they otherwise look
 * identical. Occupies no space when there's nothing to drill into, so the inert rows keep their
 * existing layout exactly. */
@Composable
private fun DrillDownChevron(visible: Boolean) {
    if (!visible) return
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToSearch: (String) -> Unit = {},
    onNavigateToProject: (String) -> Unit = {},
    onNavigateToPerson: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {},
    onNavigateToList: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()

    val period by viewModel.analyticsPeriod.collectAsStateWithLifecycle()
    val stats by viewModel.analyticsUiState.collectAsStateWithLifecycle()

    val totalCount = stats.totalCount
    val doneCount = stats.doneCount
    val completionPct = stats.completionPct
    val previousPct = stats.previousPeriodCompletionPct
    val streak = stats.currentStreak
    val overdue = stats.overdueCount
    val zeroOverdueStreak = stats.zeroOverdueStreakDays
    val overallOnTimeRate = stats.overallOnTimeRate
    val dueNext7 = stats.dueNext7
    val dueNext30 = stats.dueNext30
    val agingBuckets = stats.agingBuckets
    val workloadShares = stats.workloadShares
    val dailyActivity = stats.dailyActivity
    val priorityStats = stats.priorityStats
    val projectStats = stats.projectStats
    val personStats = stats.personStats
    val tagStats = stats.tagStats
    val listStats = stats.listStats
    val delegationStats = stats.delegationStats
    val delegationSummary = stats.delegationSummary
    val insights = stats.insights

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
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
            val context = androidx.compose.ui.platform.LocalContext.current
            TopAppBar(
                title = {
                    Text(stringResource(R.string.analytics_analytics),
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val markdown = com.mj.yata.util.buildAnalyticsMarkdown(
                            periodLabel = period.label(),
                            totalCount = totalCount,
                            doneCount = doneCount,
                            overdueCount = overdue,
                            priorityStats = priorityStats,
                            projectStats = projectStats,
                            personStats = personStats,
                            tagStats = tagStats,
                            overallOnTimeRate = overallOnTimeRate,
                            agingBuckets = agingBuckets,
                            workloadShares = workloadShares,
                            dueNext7 = dueNext7,
                            dueNext30 = dueNext30
                        )
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, markdown)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(
                                shareIntent,
                                context.getString(R.string.analytics_share_analytics)
                            )
                        )
                    }) {
                        Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.analytics_share_analytics))
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
                onItemSelected = { viewModel.setAnalyticsPeriod(it) },
                labelProvider = { it.label() }
            )

            // Ranked callouts, above the tables. The breakdowns below say what the numbers are;
            // these say which of them is worth looking at, which is otherwise a scan across
            // several sections once there are more than a handful of projects/tags/people.
            if (insights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.forEach { insight ->
                        InsightBanner(
                            insight = insight,
                            onClick = insight.searchFilter?.let { filter ->
                                { onNavigateToSearch(filter) }
                            }
                        )
                    }
                }
            }

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
                    label = stringResource(R.string.analytics_day_streak)
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WarningAmber,
                    iconTint = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$overdue",
                    label = pluralStringResource(R.plurals.analytics_overdue_tasks_label, overdue),
                    onClick = if (overdue > 0) {
                        { onNavigateToSearch(com.mj.yata.util.SEARCH_FILTER_OVERDUE) }
                    } else null,
                    trend = stats.overdueTrend
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = MaterialTheme.colorScheme.primary,
                    value = "$zeroOverdueStreak",
                    label = stringResource(R.string.analytics_days_clean)
                )
            }

            // MIS forecast/quality row — also always "right now", independent of the period filter.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WarningAmber,
                    iconTint = MaterialTheme.colorScheme.primary,
                    value = overallOnTimeRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    label = stringResource(R.string.analytics_on_time_rate),
                    trend = stats.onTimeRateTrend,
                    trendUnit = "pp"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$dueNext7",
                    label = stringResource(R.string.analytics_due_in_7_days)
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$dueNext30",
                    label = stringResource(R.string.analytics_due_in_30_days)
                )
            }

            // What the on-time rate rests on. Without it, a rate over four tasks and a rate over
            // four hundred are the same number on screen, and tasks finished before completion
            // timestamps existed are silently excluded with no way to tell.
            if (overallOnTimeRate != null) {
                Text(
                    text = pluralStringResource(
                        R.plurals.analytics_finished_with_due_date,
                        stats.onTimeRateSampleSize,
                        stats.onTimeRateSampleSize
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Effort rather than task count — the one section in terms of the thing that runs
            // out. Hidden entirely when nothing open is estimated, since a "0h" total would say
            // the opposite of "not estimated yet".
            stats.capacity?.let { capacity ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_planned_effort),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = EstimateUtils.format(capacity.openMinutes),
                                    label = stringResource(R.string.analytics_still_open)
                                )
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = EstimateUtils.format(capacity.dueNext7Minutes),
                                    label = stringResource(R.string.analytics_due_in_7_days)
                                )
                                if (capacity.overdueMinutes > 0) {
                                    MiniStat(
                                        modifier = Modifier.weight(1f),
                                        value = EstimateUtils.format(capacity.overdueMinutes),
                                        label = stringResource(R.string.analytics_already_late),
                                        emphasise = true,
                                        onClick = { onNavigateToSearch(com.mj.yata.util.SEARCH_FILTER_OVERDUE) }
                                    )
                                }
                            }
                            // The total is only as good as its coverage; say how much of the
                            // backlog it actually saw rather than implying it saw all of it.
                            Text(
                                text = if (capacity.unestimatedOpenCount == 0) {
                                    stringResource(R.string.analytics_every_open_task_estimated)
                                } else {
                                    pluralStringResource(
                                        R.plurals.analytics_estimated_tasks,
                                        capacity.estimatedOpenCount,
                                        capacity.estimatedOpenCount
                                    ) + " · " + pluralStringResource(
                                        R.plurals.analytics_unestimated_tasks,
                                        capacity.unestimatedOpenCount,
                                        capacity.unestimatedOpenCount
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                            text = stringResource(R.string.analytics_done_of_total, doneCount, totalCount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (totalCount == 0) {
                                stringResource(R.string.analytics_no_tasks_due_in_period)
                            } else {
                                stringResource(R.string.analytics_count_still_open, totalCount - doneCount)
                            },
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
                                    text = stringResource(
                                        R.string.analytics_delta_pp_vs_previous,
                                        "${if (deltaPoints > 0) "+" else ""}$deltaPoints"
                                    ),
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
                        text = stringResource(R.string.analytics_daily_activity),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Created counts only exist for tasks added since DB 27; on a database
                            // that predates it every day would read as zero created, which would
                            // look like "nothing came in" rather than "not recorded". Show the
                            // second series only once there's something real to compare against.
                            val createdTotal = dailyActivity.sumOf { it.createdCount }
                            val completedTotal = dailyActivity.sumOf { it.completedCount }
                            val showCreated = createdTotal > 0
                            Text(
                                text = stringResource(
                                    if (showCreated) {
                                        R.string.analytics_tasks_completed_vs_created_by_day
                                    } else {
                                        R.string.analytics_tasks_completed_by_day
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (showCreated) {
                                Spacer(modifier = Modifier.height(8.dp))
                                ChartLegend(
                                    completedTotal = completedTotal,
                                    createdTotal = createdTotal
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            DailyActivityChart(
                                days = dailyActivity,
                                showLabels = period == AnalyticsPeriod.WEEK,
                                showCreated = showCreated
                            )
                            if (showCreated) {
                                Spacer(modifier = Modifier.height(10.dp))
                                // The whole point of the second series: whether more went out
                                // than came in. Deliberately worded as the arithmetic rather than
                                // as a verdict — when the gap is big enough to matter, the
                                // insight banner at the top of the screen says so in those terms,
                                // and the two shouldn't print the same sentence twice.
                                val net = completedTotal - createdTotal
                                Text(
                                    text = when {
                                        net > 0 -> stringResource(R.string.analytics_more_finished_than_created, net)
                                        net < 0 -> stringResource(R.string.analytics_more_created_than_finished, -net)
                                        else -> stringResource(R.string.analytics_as_much_finished_as_came_in)
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        net > 0 -> LocalYataAccents.current.accentE
                                        net < 0 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (priorityStats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_by_priority),
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
                                // Only "high" has an exact search filter; the other buckets have
                                // no equivalent chip, so they stay inert rather than approximating.
                                PriorityStatRow(
                                    stat = stat,
                                    onClick = if (stat.priority == "high") {
                                        { onNavigateToSearch(com.mj.yata.util.SEARCH_FILTER_HIGH_PRIORITY) }
                                    } else null
                                )
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

            if (agingBuckets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_overdue_aging),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val maxAging = agingBuckets.maxOf { it.count }
                            agingBuckets.forEachIndexed { index, bucket ->
                                // Every bucket is a slice of "overdue"; the filter can't express
                                // the age range, so this lands on all overdue work rather than
                                // exactly this bucket. Close enough to be useful and honest —
                                // the bucket is a subset of what you'll see, not a different set.
                                AgingBucketRow(
                                    bucket = bucket,
                                    maxCount = maxAging,
                                    onClick = { onNavigateToSearch(com.mj.yata.util.SEARCH_FILTER_OVERDUE) }
                                )
                                if (index != agingBuckets.lastIndex) {
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

            // Where open work sits relative to you — the question the rest of the screen never
            // answered, since every other breakdown is per-entity rather than "how much have I
            // actually handed off".
            if (peopleFeatureEnabled && delegationSummary.totalOpen > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_delegation),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DelegationSplitBar(delegationSummary)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = "${delegationSummary.delegatedOpen}",
                                    label = stringResource(R.string.analytics_delegated)
                                )
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = "${delegationSummary.selfOpen}",
                                    label = stringResource(R.string.analytics_yours)
                                )
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = "${delegationSummary.unassignedOpen}",
                                    label = stringResource(R.string.analytics_unassigned),
                                    emphasise = delegationSummary.unassignedOpen > 0
                                )
                            }
                            if (stats.medianTurnaroundDays != null || stats.oldestOpenAgeDays != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    stats.medianTurnaroundDays?.let {
                                        MiniStat(modifier = Modifier.weight(1f), value = "${it}d", label = stringResource(R.string.analytics_median_turnaround))
                                    }
                                    stats.oldestOpenAgeDays?.let {
                                        MiniStat(modifier = Modifier.weight(1f), value = "${it}d", label = stringResource(R.string.analytics_oldest_open))
                                    }
                                    if (stats.openWithoutDueDate > 0) {
                                        MiniStat(
                                            modifier = Modifier.weight(1f),
                                            value = "${stats.openWithoutDueDate}",
                                            label = stringResource(R.string.analytics_open_no_date),
                                            onClick = { onNavigateToSearch(com.mj.yata.util.SEARCH_FILTER_NO_DUE_DATE) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Per-assignee health. Deliberately separate from "By Person" below, which is a
            // progress breakdown scoped to the period — this one is about whether delegated work
            // is actually moving, and includes people whose work all sits outside the window.
            if (peopleFeatureEnabled && delegationStats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_per_assignee),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            delegationStats.forEachIndexed { index, stat ->
                                DelegationStatRow(
                                    stat = stat,
                                    onClick = { onNavigateToPerson(stat.person.id) }
                                )
                                if (index != delegationStats.lastIndex) {
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

            if (peopleFeatureEnabled && workloadShares.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.analytics_workload_share),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            workloadShares.forEachIndexed { index, share ->
                                val accents = LocalYataAccents.current
                                WorkloadShareRow(
                                    share = share,
                                    color = accents.getAccent(share.person.color),
                                    onClick = { onNavigateToPerson(share.person.id) }
                                )
                                if (index != workloadShares.lastIndex) {
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

            // Each breakdown row names an entity that already has a detail screen listing exactly
            // the tasks it counted, so the row is a link to it rather than a dead number.
            if (projectsFeatureEnabled) {
                AnalyticsSection(title = stringResource(R.string.analytics_by_project), stats = projectStats) { stat ->
                    val accents = LocalYataAccents.current
                    EntityStatRow(
                        stat = stat,
                        color = accents.getAccent(stat.colorKey),
                        onClick = { onNavigateToProject(stat.id) }
                    )
                }
            }

            if (peopleFeatureEnabled) {
                AnalyticsSection(title = stringResource(R.string.analytics_by_person), stats = personStats) { stat ->
                    val accents = LocalYataAccents.current
                    EntityStatRow(
                        stat = stat,
                        color = accents.getAccent(stat.colorKey),
                        subtitle = insightSubtitle(stat),
                        leading = {
                            PersonAvatar(
                                initials = com.mj.yata.util.initialsFor(stat.name),
                                accentKey = stat.colorKey,
                                size = 28.dp
                            )
                        },
                        onClick = { onNavigateToPerson(stat.id) }
                    )
                }
            }

            if (tagsFeatureEnabled) {
                AnalyticsSection(title = stringResource(R.string.analytics_by_tag), stats = tagStats) { stat ->
                    val accents = LocalYataAccents.current
                    val color = if (stat.colorKey == "error") MaterialTheme.colorScheme.error else accents.getAccent(stat.colorKey)
                    EntityStatRow(
                        stat = stat,
                        color = color,
                        subtitle = insightSubtitle(stat),
                        onClick = { onNavigateToTag(stat.id) }
                    )
                }
            }

            // Lists had no breakdown at all, despite being one of the three organising axes
            // alongside projects and tags. Not feature-flagged — lists can't be switched off.
            AnalyticsSection(title = stringResource(R.string.analytics_by_list), stats = listStats) { stat ->
                val accents = LocalYataAccents.current
                EntityStatRow(
                    stat = stat,
                    color = accents.getAccent(stat.colorKey),
                    onClick = { onNavigateToList(stat.id) }
                )
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trend: com.mj.yata.util.MetricTrend? = null,
    trendUnit: String = ""
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.drillDown(onClick)
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
                // A flat trend prints nothing rather than "0": three chips each announcing "no
                // change" is noise, and the absence already says it.
                if (trend != null && !trend.isFlat) {
                    Spacer(modifier = Modifier.height(2.dp))
                    TrendLabel(trend = trend, unit = trendUnit)
                }
            }
        }
    }
}

/** The direction a headline figure moved, coloured by whether that direction is good for *this*
 * metric — which is why the judgement travels in [com.mj.yata.util.MetricTrend] rather than being
 * inferred from the sign here. */
@Composable
private fun TrendLabel(trend: com.mj.yata.util.MetricTrend, unit: String) {
    val color = if (trend.improved) LocalYataAccents.current.accentE else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = if (trend.delta > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "${kotlin.math.abs(trend.delta)}$unit",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

/** Names the two series and carries their period totals, so the chart can be read without
 * counting bars. */
@Composable
private fun ChartLegend(completedTotal: Int, createdTotal: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.analytics_completed_count, completedTotal)
        )
        LegendSwatch(
            color = MaterialTheme.colorScheme.tertiary,
            label = stringResource(R.string.analytics_created_count, createdTotal)
        )
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyActivityChart(days: List<DayActivity>, showLabels: Boolean, showCreated: Boolean) {
    // Both series share one scale, otherwise "created" and "completed" bars of equal height would
    // mean different counts and the comparison the chart exists for would be a lie.
    val maxCount = days.maxOfOrNull {
        maxOf(it.completedCount, if (showCreated) it.createdCount else 0)
    }?.coerceAtLeast(1) ?: 1
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val doneColor = MaterialTheme.colorScheme.primary
    val createdColor = MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val animatedDonePct by animateFloatAsState(
                targetValue = day.completedCount.toFloat() / maxCount,
                animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
                label = "chartBarHeight"
            )
            val animatedCreatedPct by animateFloatAsState(
                targetValue = if (showCreated) day.createdCount.toFloat() / maxCount else 0f,
                animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
                label = "chartCreatedBarHeight"
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Two bars share the slot when created counts are shown, so a day reads as a
                // pair (in vs out) rather than needing the eye to track across two charts.
                val slotWidth = if (showCreated) size.width * 0.42f else size.width * 0.55f
                val gap = if (showCreated) size.width * 0.10f else 0f
                val totalWidth = if (showCreated) slotWidth * 2 + gap else slotWidth
                val startX = (size.width - totalWidth) / 2f

                fun bar(x: Float, pct: Float, color: androidx.compose.ui.graphics.Color, drawTrack: Boolean) {
                    if (drawTrack) {
                        // Faint full-height track so a zero-count day still reads as a bar slot.
                        drawRoundRect(
                            color = trackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(slotWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(slotWidth / 2f, slotWidth / 2f)
                        )
                    }
                    val barHeight = size.height * pct
                    if (barHeight > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                            size = androidx.compose.ui.geometry.Size(slotWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(slotWidth / 2f, slotWidth / 2f)
                        )
                    }
                }

                bar(startX, animatedDonePct, doneColor, drawTrack = true)
                if (showCreated) {
                    bar(startX + slotWidth + gap, animatedCreatedPct, createdColor, drawTrack = true)
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
private fun PriorityStatRow(stat: PriorityStat, onClick: (() -> Unit)? = null) {
    val accents = LocalYataAccents.current
    val (label, color) = when (stat.priority) {
        "high" -> stringResource(R.string.analytics_priority_high) to MaterialTheme.colorScheme.error
        "med" -> stringResource(R.string.analytics_priority_medium) to accents.accentD
        "low" -> stringResource(R.string.analytics_priority_low) to accents.accentE
        else -> stringResource(R.string.analytics_priority_none) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animatedPct by animateFloatAsState(
        targetValue = stat.pct,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "priorityStatProgress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drillDown(onClick)
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
                progress = { animatedPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.16f)
            )
        }
        Text(
            text = stringResource(R.string.analytics_ratio, stat.done, stat.total),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DrillDownChevron(visible = onClick != null)
    }
}

@Composable
private fun AgingBucketRow(
    bucket: com.mj.yata.util.AgingBucket,
    maxCount: Int,
    onClick: (() -> Unit)? = null
) {
    val color = when (bucket.label) {
        "0-3 days" -> LocalYataAccents.current.accentD
        "4-7 days" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val animatedPct by animateFloatAsState(
        targetValue = bucket.count.toFloat() / maxCount,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "agingBucketProgress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drillDown(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bucket.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.16f)
            )
        }
        Text(
            text = bucket.count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DrillDownChevron(visible = onClick != null)
    }
}

@Composable
private fun WorkloadShareRow(
    share: com.mj.yata.util.WorkloadShare,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    val animatedPct by animateFloatAsState(
        targetValue = share.share,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "workloadShareProgress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drillDown(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(
            initials = com.mj.yata.util.initialsFor(share.person.name),
            accentKey = share.person.color,
            size = 28.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = share.person.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.16f)
            )
        }
        Text(
            text = stringResource(R.string.analytics_count_percent, share.openCount, (share.share * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DrillDownChevron(visible = onClick != null)
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
                    text = stringResource(R.string.analytics_nothing_in_period),
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
@Composable
private fun insightSubtitle(stat: EntityStat): String? {
    val parts = mutableListOf<String>()
    if (stat.overdue > 0) parts += stringResource(R.string.analytics_overdue_count, stat.overdue)
    stat.onTimeRate?.let { parts += stringResource(R.string.analytics_on_time_percent, (it * 100).roundToInt()) }
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun EntityStatRow(
    stat: EntityStat,
    color: Color,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val animatedPct by animateFloatAsState(
        targetValue = stat.pct,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "entityStatProgress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drillDown(onClick)
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
                        contentDescription = stringResource(R.string.analytics_has_overdue_tasks),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedPct },
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
            text = stringResource(R.string.analytics_ratio, stat.done, stat.total),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DrillDownChevron(visible = onClick != null)
    }
}

/** One ranked callout from [com.mj.yata.util.AnalyticsUtils.buildInsights]. Styled by severity so
 * the thing that needs attention reads differently from the thing that's merely true. */
@Composable
private fun InsightBanner(
    insight: com.mj.yata.util.AnalyticsInsight,
    onClick: (() -> Unit)? = null
) {
    val accents = LocalYataAccents.current
    val accent = when (insight.severity) {
        com.mj.yata.util.InsightSeverity.WARN -> MaterialTheme.colorScheme.error
        com.mj.yata.util.InsightSeverity.GOOD -> accents.accentE
        com.mj.yata.util.InsightSeverity.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().drillDown(onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // A 3dp bar rather than an icon: four of these can stack, and four icons in a column
            // reads as a toolbar rather than a list of statements.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = insight.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DrillDownChevron(visible = onClick != null)
        }
    }
}

/** Delegated / yours / unassigned as one proportional bar, so the split reads at a glance rather
 * than as three numbers to compare mentally. */
@Composable
private fun DelegationSplitBar(summary: com.mj.yata.util.DelegationSummary) {
    val accents = LocalYataAccents.current
    val total = summary.totalOpen.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        // weight(fill = false) with a zero weight is an error, so each segment is only emitted
        // when it actually has tasks in it.
        if (summary.delegatedOpen > 0) {
            Box(
                modifier = Modifier
                    .weight(summary.delegatedOpen.toFloat() / total)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (summary.selfOpen > 0) {
            Box(
                modifier = Modifier
                    .weight(summary.selfOpen.toFloat() / total)
                    .fillMaxHeight()
                    .background(accents.accentE)
            )
        }
        if (summary.unassignedOpen > 0) {
            Box(
                modifier = Modifier
                    .weight(summary.unassignedOpen.toFloat() / total)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/** Compact number + caption, for the stat clusters inside the delegation card. */
@Composable
private fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(modifier = modifier.drillDown(onClick)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (emphasise) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

/** One assignee's delegation health: how much they hold, how much is late, how fast it moves. */
@Composable
private fun DelegationStatRow(
    stat: com.mj.yata.util.DelegationStat,
    onClick: (() -> Unit)? = null
) {
    val accents = LocalYataAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drillDown(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(
            initials = com.mj.yata.util.initialsFor(stat.person.name),
            accentKey = stat.person.color,
            size = 32.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.person.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Only the facts that exist for this person — an assignee with no completions yet
            // shouldn't get a row of em-dashes.
            val parts = buildList {
                add("${stat.openCount} open")
                if (stat.completedInPeriod > 0) add("${stat.completedInPeriod} done")
                stat.onTimeRate?.let { add("${(it * 100).roundToInt()}% on time") }
                stat.medianTurnaroundDays?.let { add("~${it}d turnaround") }
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (stat.overdueCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.analytics_count_late, stat.overdueCount),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (stat.openCount > 0) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accents.accentE,
                modifier = Modifier.size(18.dp)
            )
        }
        DrillDownChevron(visible = onClick != null)
    }
}
