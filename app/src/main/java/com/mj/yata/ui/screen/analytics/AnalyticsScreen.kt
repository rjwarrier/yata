package com.mj.yata.ui.screen.analytics

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mj.yata.util.PriorityStat
import com.mj.yata.util.label
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
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
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share analytics"))
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
                    insights.forEach { insight -> InsightBanner(insight) }
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
                    label = "day streak"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WarningAmber,
                    iconTint = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$overdue",
                    label = if (overdue == 1) "overdue task" else "overdue tasks"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = MaterialTheme.colorScheme.primary,
                    value = "$zeroOverdueStreak",
                    label = "days clean"
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
                    label = "on-time rate"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$dueNext7",
                    label = "due in 7 days"
                )
                InsightChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ArrowUpward,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    value = "$dueNext30",
                    label = "due in 30 days"
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

            if (agingBuckets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "OVERDUE AGING",
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
                                AgingBucketRow(bucket, maxAging)
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
                        text = "DELEGATION",
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
                                    label = "delegated"
                                )
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = "${delegationSummary.selfOpen}",
                                    label = "yours"
                                )
                                MiniStat(
                                    modifier = Modifier.weight(1f),
                                    value = "${delegationSummary.unassignedOpen}",
                                    label = "unassigned",
                                    emphasise = delegationSummary.unassignedOpen > 0
                                )
                            }
                            if (stats.medianTurnaroundDays != null || stats.oldestOpenAgeDays != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    stats.medianTurnaroundDays?.let {
                                        MiniStat(modifier = Modifier.weight(1f), value = "${it}d", label = "median turnaround")
                                    }
                                    stats.oldestOpenAgeDays?.let {
                                        MiniStat(modifier = Modifier.weight(1f), value = "${it}d", label = "oldest open")
                                    }
                                    if (stats.openWithoutDueDate > 0) {
                                        MiniStat(
                                            modifier = Modifier.weight(1f),
                                            value = "${stats.openWithoutDueDate}",
                                            label = "open, no date"
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
                        text = "PER ASSIGNEE",
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
                                DelegationStatRow(stat)
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
                        text = "WORKLOAD SHARE",
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
                                WorkloadShareRow(share, accents.getAccent(share.person.color))
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
                                initials = com.mj.yata.util.initialsFor(stat.name),
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

            // Lists had no breakdown at all, despite being one of the three organising axes
            // alongside projects and tags. Not feature-flagged — lists can't be switched off.
            AnalyticsSection(title = "By List", stats = listStats) { stat ->
                val accents = LocalYataAccents.current
                EntityStatRow(stat = stat, color = accents.getAccent(stat.colorKey))
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
            val targetPct = day.completedCount.toFloat() / maxCount
            val animatedPct by animateFloatAsState(
                targetValue = targetPct,
                animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
                label = "chartBarHeight"
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val barWidth = size.width * 0.55f
                val x = (size.width - barWidth) / 2f
                val barHeight = size.height * animatedPct

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
    val animatedPct by animateFloatAsState(
        targetValue = stat.pct,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "priorityStatProgress"
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
            text = "${stat.done}/${stat.total}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AgingBucketRow(bucket: com.mj.yata.util.AgingBucket, maxCount: Int) {
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
            text = "${bucket.count}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkloadShareRow(share: com.mj.yata.util.WorkloadShare, color: Color) {
    val animatedPct by animateFloatAsState(
        targetValue = share.share,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "workloadShareProgress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            text = "${share.openCount} (${(share.share * 100).roundToInt()}%)",
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
    val animatedPct by animateFloatAsState(
        targetValue = stat.pct,
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "entityStatProgress"
    )
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
            text = "${stat.done}/${stat.total}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One ranked callout from [com.mj.yata.util.AnalyticsUtils.buildInsights]. Styled by severity so
 * the thing that needs attention reads differently from the thing that's merely true. */
@Composable
private fun InsightBanner(insight: com.mj.yata.util.AnalyticsInsight) {
    val accents = LocalYataAccents.current
    val accent = when (insight.severity) {
        com.mj.yata.util.InsightSeverity.WARN -> MaterialTheme.colorScheme.error
        com.mj.yata.util.InsightSeverity.GOOD -> accents.accentE
        com.mj.yata.util.InsightSeverity.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
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
    emphasise: Boolean = false
) {
    Column(modifier = modifier) {
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
private fun DelegationStatRow(stat: com.mj.yata.util.DelegationStat) {
    val accents = LocalYataAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    text = "${stat.overdueCount} late",
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
    }
}
