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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

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
        val todayStr = LocalDate.now().toString()
        val todayTasks = repository.getTasks().first()
            .filter { it.due != null && it.due!! <= todayStr }
            .sortedWith(compareBy({ it.done }, { it.sortOrder }))
        val listsById = repository.getLists().first().associateBy { it.id }
        val peopleById = repository.getPeople().first().associateBy { it.id }
        val theme = resolveWidgetTheme(context)
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                TodayWidgetContent(
                    context = context,
                    tasks = todayTasks,
                    listsById = listsById,
                    peopleById = peopleById,
                    colors = theme.colorScheme,
                    accents = theme.accents,
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
    listsById: Map<String, com.mj.yata.domain.model.YataList>,
    peopleById: Map<String, com.mj.yata.domain.model.Person>,
    colors: androidx.compose.material3.ColorScheme,
    accents: com.mj.yata.ui.theme.YataAccents,
    cornerRadius: Int,
    customLabel: String?,
    useM3Colors: Boolean,
    opacity: Float,
    accentOverride: androidx.compose.ui.graphics.Color?
) {
    val remaining = tasks.count { !it.done }
    val progress = if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size
    val chromeColor = accentOverride ?: colors.primary

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface.copy(alpha = opacity))
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
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(tasks) { task ->
                    val tint = if (useM3Colors) colors.primary else (listsById[task.listId]?.let { accents.getAccent(it.color) } ?: colors.primary)
                    val assignees = task.assigneeIds.mapNotNull { peopleById[it] }
                    WidgetTaskRow(task = task, tintColor = tint, onSurface = colors.onSurface, assignees = assignees)
                }
            }
        }
    }
}
