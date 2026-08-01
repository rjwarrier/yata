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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.hiddenFromMainTaskListIds
import com.mj.yata.domain.model.hiddenFromMainTaskProjectIds
import com.mj.yata.domain.model.isActionableToday
import com.mj.yata.domain.model.wasPendingAsOf
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// See SingleListWidget's MAX_VISIBLE_TASKS for why this cap exists.
private const val MAX_VISIBLE_TASKS = 40

/** "Today's tasks" — medium (3 rows) / large (6 rows). Mirrors the in-app Today tab's own
 * definition of "today" (due today or overdue, per TodayTab.kt) rather than a narrower one. */
class YataAppWidget : GlanceAppWidget() {

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

        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val today = LocalDate.now()
        val todayStr = today.toString()
        val people = repository.getPeople().first()
        val myId = people.firstOrNull { it.isMe }?.id
        // Same exclusions as the Today tab: a project/list marked "Exclude from Today" (or an
        // archived project) keeps its tasks off the day view entirely, on top of the per-task
        // isActionableToday check below.
        val excludedProjectIds = repository.getProjects().first().hiddenFromMainTaskProjectIds()
        val lists = repository.getLists().first()
        val excludedListIds = lists.hiddenFromMainTaskListIds()
        val todayTasks = repository.getTasks().first()
            .filter {
                it.isActionableToday(todayStr, System.currentTimeMillis(), myId) &&
                    it.projectId !in excludedProjectIds && it.listId !in excludedListIds
            }
            .sortedWith(compareBy({ it.done }, { it.sortOrder }))
        // wasPendingAsOf drops tasks done on an earlier day from the ring/"X to go" count only —
        // the row list above still shows every today-or-overdue task, done or not.
        val progressTasks = todayTasks.filter { it.wasPendingAsOf(today) }
        val remaining = progressTasks.count { !it.done }
        val progress = if (progressTasks.isEmpty()) 0f else progressTasks.count { it.done }.toFloat() / progressTasks.size
        val listsById = lists.associateBy { it.id }
        val peopleById = people.associateBy { it.id }
        val theme = resolveWidgetTheme(context)
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                TodayWidgetContent(
                    context = context,
                    tasks = todayTasks,
                    remaining = remaining,
                    progress = progress,
                    listsById = listsById,
                    peopleById = peopleById,
                    colors = theme.colorScheme,
                    accents = theme.accents,
                    widgetBackground = theme.widgetBackground,
                    cornerRadius = cornerRadius,
                    customLabel = customLabel,
                    useM3Colors = useM3Colors,
                    opacity = opacity,
                    accentOverride = accentOverride
                )
            }
        }
    }
}

@Composable
private fun TodayWidgetContent(
    context: Context,
    tasks: List<Task>,
    remaining: Int,
    progress: Float,
    listsById: Map<String, com.mj.yata.domain.model.YataList>,
    peopleById: Map<String, com.mj.yata.domain.model.Person>,
    colors: androidx.compose.material3.ColorScheme,
    accents: com.mj.yata.ui.theme.YataAccents,
    widgetBackground: androidx.compose.ui.graphics.Color,
    cornerRadius: Int,
    customLabel: String?,
    useM3Colors: Boolean,
    opacity: Float,
    accentOverride: androidx.compose.ui.graphics.Color?
) {
    val chromeColor = accentOverride ?: colors.primary

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground.copy(alpha = opacity))
            .appWidgetBackground()
            .cornerRadius(cornerRadius.dp)
            .padding(16.dp)
            .clickable(openAppAction())
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetSectionHeader(customLabel ?: "Today", ColorProvider(chromeColor))
                Text(
                    text = "$remaining to go",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
                )
            }
            WidgetProgressRingImage(
                context = context,
                progress = progress,
                sizeDp = 38.dp,
                strokeDp = 4.dp,
                activeColor = chromeColor,
                trackColor = colors.surfaceContainerHighest,
                centerText = (progress * 100).toInt().toString(),
                centerTextSizeSp = 11f
            )
        }
        Spacer(modifier = GlanceModifier.height(9.dp))
        WidgetDivider()
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (tasks.isEmpty()) {
            Text(
                text = "Nothing due today.",
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
        } else {
            // Cap what's actually drawn — a heavy overdue backlog can otherwise push the
            // RemoteViews payload past Android's ~1MB Binder IPC limit and silently fail to
            // render at all (see SingleListWidget's MAX_VISIBLE_TASKS for the full story).
            val visibleTasks = tasks.take(MAX_VISIBLE_TASKS)
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(visibleTasks) { task ->
                    val tint = if (useM3Colors) colors.primary else (listsById[task.listId]?.let { accents.getAccent(it.color) } ?: colors.primary)
                    val assignees = task.assigneeIds.mapNotNull { peopleById[it] }
                    WidgetTaskRow(task = task, tintColor = tint, onSurface = colors.onSurface, assignees = assignees)
                }
                if (tasks.size > visibleTasks.size) {
                    item {
                        Text(
                            text = "+${tasks.size - visibleTasks.size} more in the app",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
