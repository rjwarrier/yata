package com.mj.yata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.hiddenFromMainTaskListIds
import com.mj.yata.domain.model.hiddenFromMainTaskProjectIds
import com.mj.yata.domain.model.isActionableToday
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// See SingleListWidget's MAX_VISIBLE_TASKS for why this cap exists — kept lower than that one
// since this applies per-day across up to 3 days in the same payload.
private const val MAX_VISIBLE_TASKS_PER_DAY = 20

/** "Upcoming / agenda" — medium (Today/Tomorrow counts) / large (day-grouped agenda for the
 * next few days that actually have tasks, using real `task.due` dates). */
class UpcomingWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val cornerRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
        val customLabel = prefs[WIDGET_LABEL_KEY]
        val useM3Colors = prefs[WIDGET_USE_M3_COLORS_KEY] ?: false
        val opacity = prefs[WIDGET_OPACITY_KEY] ?: 1.0f
        val accentOverrideKey = prefs[WIDGET_ACCENT_OVERRIDE_KEY]
        val health = WidgetHealthStore.read(context, UpcomingWidget::class.java.name)

        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val allTasks = repository.getTasks().first()
        val lists = repository.getLists().first()
        val people = repository.getPeople().first()
        val peopleById = people.associateBy { it.id }
        val myId = people.firstOrNull { it.isMe }?.id
        // Applied only to the widget's "Today" row, matching the in-app Today tab — the
        // future-dated groups below it deliberately keep showing an excluded container's tasks
        // on their due date, same as the Upcoming/Calendar tab does.
        val excludedProjectIds = repository.getProjects().first().hiddenFromMainTaskProjectIds()
        val excludedListIds = lists.hiddenFromMainTaskListIds()
        val theme = resolveWidgetTheme(context)
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                UpcomingWidgetContent(
                    allTasks = allTasks,
                    lists = lists,
                    peopleById = peopleById,
                    myId = myId,
                    excludedProjectIds = excludedProjectIds,
                    excludedListIds = excludedListIds,
                    colors = theme.colorScheme,
                    accents = theme.accents,
                    widgetBackground = theme.widgetBackground,
                    cornerRadius = cornerRadius,
                    customLabel = customLabel,
                    useM3Colors = useM3Colors,
                    opacity = opacity,
                    accentOverride = accentOverride,
                    health = health
                )
            }
        }
    }
}

private data class AgendaDay(val label: String, val tasks: List<Task>)

@Composable
private fun UpcomingWidgetContent(
    allTasks: List<Task>,
    lists: List<YataList>,
    peopleById: Map<String, com.mj.yata.domain.model.Person>,
    myId: String?,
    excludedProjectIds: Set<String>,
    excludedListIds: Set<String>,
    colors: androidx.compose.material3.ColorScheme,
    accents: com.mj.yata.ui.theme.YataAccents,
    widgetBackground: androidx.compose.ui.graphics.Color,
    cornerRadius: Int,
    customLabel: String?,
    useM3Colors: Boolean,
    opacity: Float,
    accentOverride: androidx.compose.ui.graphics.Color?,
    health: WidgetHealth?
) {
    val isLarge = LocalSize.current.height > 180.dp
    val listsById = lists.associateBy { it.id }
    val today = LocalDate.now()
    val todayStr = today.toString()
    val chromeColor = accentOverride ?: colors.tertiary
    val chromeColorProvider = ColorProvider(chromeColor)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground.copy(alpha = opacity))
            .appWidgetBackground()
            .cornerRadius(cornerRadius.dp)
            .padding(16.dp)
            .clickable(openAppAction())
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetSectionHeader(customLabel ?: "Upcoming", chromeColorProvider)
            WidgetStaleBadge(health)
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (isLarge) {
                val days = upcomingAgendaDays(allTasks, today, myId, excludedProjectIds, excludedListIds)
                if (days.isEmpty()) {
                    Text(
                        text = "Nothing coming up.",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        days.forEachIndexed { index, day ->
                            item {
                                if (index > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                                Text(
                                    text = day.label.uppercase(),
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chromeColorProvider)
                                )
                            }
                            // See SingleListWidget's MAX_VISIBLE_TASKS for why this cap exists —
                            // an overdue-heavy "Today" bucket is the realistic risk here.
                            items(day.tasks.take(MAX_VISIBLE_TASKS_PER_DAY)) { task ->
                                val tint = if (useM3Colors) colors.primary else (listsById[task.listId]?.let { accents.getAccent(it.color) } ?: colors.primary)
                                val assignees = task.assigneeIds.mapNotNull { peopleById[it] }
                                WidgetTaskRow(task = task, tintColor = tint, onSurface = colors.onSurface, assignees = assignees)
                            }
                        }
                    }
                }
            } else {
                val todayTasks = allTasks.filter {
                    !it.done && it.isActionableToday(todayStr, System.currentTimeMillis(), myId) &&
                        it.projectId !in excludedProjectIds && it.listId !in excludedListIds
                }
                val tomorrowStr = today.plusDays(1).toString()
                val tomorrowTasks = allTasks.filter { !it.done && it.due == tomorrowStr }

                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    AgendaSummaryRow("Today", todayTasks, listsById, accents, useM3Colors, colors.primary, openTodayShortcutAction())
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    AgendaSummaryRow("Tomorrow", tomorrowTasks, listsById, accents, useM3Colors, colors.primary, openUpcomingAction())
                }
            }
        }
    }
}

@Composable
private fun AgendaSummaryRow(
    label: String,
    tasks: List<Task>,
    listsById: Map<String, YataList>,
    accents: com.mj.yata.ui.theme.YataAccents,
    useM3Colors: Boolean,
    m3Color: androidx.compose.ui.graphics.Color,
    onClick: androidx.glance.action.Action
) {
    val dotColors = if (useM3Colors) {
        if (tasks.isNotEmpty()) listOf(m3Color) else emptyList()
    } else {
        tasks.mapNotNull { listsById[it.listId]?.color }.distinct().take(2).map { accents.getAccent(it) }
    }
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().clickable(onClick)
    ) {
        Row {
            dotColors.forEachIndexed { index, color ->
                if (index > 0) Spacer(modifier = GlanceModifier.width(3.dp))
                Box(modifier = GlanceModifier.size(7.dp).cornerRadius(3.5.dp).background(androidx.glance.unit.ColorProvider(color))) {}
            }
        }
        if (dotColors.isNotEmpty()) Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = if (tasks.size == 1) "1 task" else "${tasks.size} tasks",
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}

/**
 * Up to the next 3 distinct due-dates (today or later) that actually have tasks.
 *
 * The Today bucket uses the same predicate (and the same container exclusions) as the Today tab,
 * so a deferred/waiting-on task or one in an excluded project/list doesn't show there — but the
 * future-dated groups deliberately don't apply either, matching the in-app Upcoming/Calendar tab
 * where that task still appears on the date it's actually due.
 */
private fun upcomingAgendaDays(
    allTasks: List<Task>,
    today: LocalDate,
    myId: String?,
    excludedProjectIds: Set<String>,
    excludedListIds: Set<String>
): List<AgendaDay> {
    val todayStr = today.toString()
    val byDate = allTasks
        .filter { !it.done }
        .mapNotNull { task -> task.due?.let { due -> due to task } }
        .groupBy({ it.first }, { it.second })

    val todayTasks = allTasks.filter {
        !it.done && it.isActionableToday(todayStr, System.currentTimeMillis(), myId) &&
            it.projectId !in excludedProjectIds && it.listId !in excludedListIds
    }
    val futureDates = byDate.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { it.isAfter(today) }
        .sorted()
        .take(2)

    val days = mutableListOf<AgendaDay>()
    if (todayTasks.isNotEmpty()) {
        days += AgendaDay("Today", todayTasks.sortedBy { it.sortOrder })
    }
    futureDates.forEach { date ->
        val label = when {
            date == today.plusDays(1) -> "Tomorrow"
            date.isBefore(today.plusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEEE"))
            else -> date.format(com.mj.yata.util.AppFormats.dayMonthFormatter())
        }
        days += AgendaDay(label, byDate[date.toString()].orEmpty())
    }
    return days.take(3)
}
