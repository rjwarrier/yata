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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Task
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.StatusBarColor
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUtils
import com.mj.yata.util.EstimateUtils
import com.mj.yata.util.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAnalyticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPerson: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsStateWithLifecycle()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf(AnalyticsPeriod.MONTH) }
    val today = com.mj.yata.util.AppClock.today
    val activePeople = remember(people) { people.filter { !it.archived } }
    val staffRows = remember(tasks, activePeople, period, today) {
        staffAnalyticsRows(tasks, activePeople, period, today)
    }
    val activeIds = remember(activePeople) { activePeople.map { it.id }.toSet() }
    val assignedOpen = remember(tasks, activeIds) {
        tasks.count { task -> !task.done && task.assigneeIds.any { it in activeIds } }
    }
    val assignedOverdue = remember(tasks, activeIds, today) {
        tasks.count { task -> !task.done && task.assigneeIds.any { it in activeIds } && task.isOverdue(today) }
    }
    val completedInPeriod = remember(tasks, activeIds, period, today) {
        AnalyticsUtils.completedInPeriod(
            tasks.filter { task -> task.assigneeIds.any { it in activeIds } },
            period,
            today
        )
    }
    val dueNext7 = remember(tasks, activeIds, today) {
        tasks.count { task ->
            !task.done &&
                task.assigneeIds.any { it in activeIds } &&
                task.due?.toLocalDateOrNull()?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(7)) } == true
        }
    }
    val unassignedOpen = remember(tasks) { tasks.count { !it.done && it.assigneeIds.isEmpty() } }
    val peopleWithOverdue = remember(staffRows) { staffRows.count { it.overdueCount > 0 } }
    val topLoad = remember(staffRows) { staffRows.maxByOrNull { it.openCount } }
    val weakestOnTime = remember(staffRows) {
        staffRows.filter { it.onTimeRate != null }.minByOrNull { it.onTimeRate ?: 1f }
    }
    val accents = LocalYataAccents.current

    StatusBarColor(MaterialTheme.colorScheme.surface)
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
                            text = stringResource(R.string.staff_analytics_title),
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                            )
                        )
                        Text(
                            text = stringResource(R.string.staff_analytics_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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

            SectionTitle(stringResource(R.string.staff_analytics_team_snapshot))
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(activePeople.size.toString(), stringResource(R.string.staff_analytics_staff), Modifier.weight(1f))
                        MetricTile(assignedOpen.toString(), stringResource(R.string.analytics_still_open), Modifier.weight(1f))
                        MetricTile(assignedOverdue.toString(), stringResource(R.string.search_filter_overdue), Modifier.weight(1f), assignedOverdue > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(completedInPeriod.toString(), stringResource(R.string.person_analytics_completed_period), Modifier.weight(1f))
                        MetricTile(dueNext7.toString(), stringResource(R.string.analytics_due_in_7_days), Modifier.weight(1f))
                        MetricTile(unassignedOpen.toString(), stringResource(R.string.analytics_unassigned), Modifier.weight(1f), unassignedOpen > 0)
                    }
                }
            }

            if (staffRows.isNotEmpty()) {
                SectionTitle(stringResource(R.string.staff_analytics_attention))
                SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
                    InsightLine(
                        label = stringResource(R.string.staff_analytics_people_with_overdue),
                        value = peopleWithOverdue.toString(),
                        emphasise = peopleWithOverdue > 0
                    )
                    CardDivider()
                    InsightLine(
                        label = stringResource(R.string.staff_analytics_heaviest_load),
                        value = topLoad?.let { "${it.person.name} - ${it.openCount}" } ?: "-"
                    )
                    if (weakestOnTime != null) {
                        CardDivider()
                        InsightLine(
                            label = stringResource(R.string.staff_analytics_lowest_on_time),
                            value = "${weakestOnTime.person.name} - ${(weakestOnTime.onTimeRate!! * 100).roundToInt()}%",
                            emphasise = weakestOnTime.onTimeRate < 0.7f
                        )
                    }
                }

                SectionTitle(stringResource(R.string.staff_analytics_staff_table))
                SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
                    staffRows.forEachIndexed { index, row ->
                        StaffRowItem(
                            row = row,
                            color = accents.getAccent(row.person.color),
                            onClick = { onNavigateToPerson(row.person.id) }
                        )
                        if (index != staffRows.lastIndex) CardDivider()
                    }
                }
            } else {
                SurfaceCard {
                    Text(
                        text = stringResource(R.string.staff_analytics_no_staff_work),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffRowItem(row: StaffAnalyticsRow, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(
            initials = row.person.initials,
            accentKey = row.person.color,
            photoUri = row.person.photoUri,
            size = 44.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.person.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (row.overdueCount > 0) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            WorkloadBar(progress = row.openShare, color = color)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("${row.openCount} open")
                    append(" - ${row.completedInPeriod} done")
                    append(" - ")
                    append(row.onTimeRate?.let { "${(it * 100).roundToInt()}% on-time" } ?: "no on-time sample")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Text(
                text = buildString {
                    append("${row.dueNext7} due soon")
                    append(" - ${row.plannedOpenMinutes.formatEffort()} planned")
                    row.oldestOpenAgeDays?.let { append(" - oldest ${it}d") }
                    row.medianTurnaroundDays?.let { append(" - median ${it}d") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (row.overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.overdueCount.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = if (row.overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.search_filter_overdue),
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
private fun InsightLine(label: String, value: String, emphasise: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (emphasise) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WorkloadBar(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized),
        label = "staffWorkloadProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
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

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private data class StaffAnalyticsRow(
    val person: Person,
    val openCount: Int,
    val overdueCount: Int,
    val completedInPeriod: Int,
    val onTimeRate: Float?,
    val oldestOpenAgeDays: Int?,
    val medianTurnaroundDays: Int?,
    val plannedOpenMinutes: Int,
    val dueNext7: Int,
    val openShare: Float
)

private fun staffAnalyticsRows(
    tasks: List<Task>,
    people: List<Person>,
    period: AnalyticsPeriod,
    today: LocalDate
): List<StaffAnalyticsRow> {
    val byPerson = mutableMapOf<String, MutableList<Task>>()
    tasks.forEach { task ->
        task.assigneeIds.forEach { personId ->
            byPerson.getOrPut(personId) { mutableListOf() }.add(task)
        }
    }
    val maxOpen = people.maxOfOrNull { person ->
        byPerson[person.id].orEmpty().count { !it.done }
    }?.coerceAtLeast(1) ?: 1
    return people.map { person ->
        val theirs = byPerson[person.id].orEmpty()
        val open = theirs.filter { !it.done }
        val completedInPeriod = AnalyticsUtils.completedInPeriod(theirs, period, today)
        val onTimeSample = theirs.filter { it.done && it.completedAt != null && it.due != null }
        val onTimeRate = if (onTimeSample.isEmpty()) null else {
            onTimeSample.count { task ->
                val due = task.due?.toLocalDateOrNull() ?: return@count true
                !task.completedAt!!.toLocalDate().isAfter(due)
            }.toFloat() / onTimeSample.size
        }
        StaffAnalyticsRow(
            person = person,
            openCount = open.size,
            overdueCount = open.count { it.isOverdue(today) },
            completedInPeriod = completedInPeriod,
            onTimeRate = onTimeRate,
            oldestOpenAgeDays = open.mapNotNull { it.createdAt?.ageInDays(today) }.maxOrNull(),
            medianTurnaroundDays = theirs.filter { it.done }.mapNotNull { it.turnaroundDays() }.sorted().median(),
            plannedOpenMinutes = open.mapNotNull { it.estimateMinutes }.sum(),
            dueNext7 = open.count { task ->
                task.due?.toLocalDateOrNull()?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(7)) } == true
            },
            openShare = open.size.toFloat() / maxOpen
        )
    }.filter { it.openCount > 0 || it.completedInPeriod > 0 }
        .sortedWith(
            compareByDescending<StaffAnalyticsRow> { it.overdueCount }
                .thenByDescending { it.openCount }
                .thenByDescending { it.completedInPeriod }
                .thenBy { it.person.name.lowercase() }
        )
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

private fun Int.formatEffort(): String =
    if (this > 0) EstimateUtils.format(this) else "-"
