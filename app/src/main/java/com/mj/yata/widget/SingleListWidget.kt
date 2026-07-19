package com.mj.yata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mj.yata.R
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.effectiveTagIds
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

val SINGLE_LIST_ID_KEY = stringPreferencesKey("list_id") // holds the id regardless of source type below
val SINGLE_SOURCE_TYPE_KEY = stringPreferencesKey("source_type") // "list" | "project" | "tag"

/** What a single instance of this widget is pinned to — chosen once via
 * [SingleListWidgetConfigActivity] at add-time. Missing/unrecognized [SINGLE_SOURCE_TYPE_KEY]
 * (widgets configured before Project/Tag support existed) falls back to "list". */
private enum class SingleWidgetSourceType(val prefValue: String) {
    LIST("list"),
    PROJECT("project"),
    TAG("tag");

    companion object {
        fun fromPref(value: String?): SingleWidgetSourceType = entries.find { it.prefValue == value } ?: LIST
    }
}

// Way beyond what's ever visible on a home-screen widget — just a hard ceiling so a large
// pinned list/project/tag can't blow the RemoteViews payload past Android's Binder transaction
// limit (see SingleListWidgetContent).
private const val MAX_VISIBLE_TASKS = 40

private data class SingleWidgetSource(
    val name: String,
    val colorKey: String,
    val iconRes: Int
)

/** "Single list/project/tag" (pinned) — medium (2 rows) / large (5 rows + ring). Which source it
 * shows is chosen once via [SingleListWidgetConfigActivity] at add-time, stored per widget instance. */
class SingleListWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val sourceId = prefs[SINGLE_LIST_ID_KEY]
        val sourceType = SingleWidgetSourceType.fromPref(prefs[SINGLE_SOURCE_TYPE_KEY])
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()

        var source: SingleWidgetSource? = null
        var tasks: List<Task> = emptyList()

        if (sourceId != null) {
            val allTasks = repository.getTasks().first()
            when (sourceType) {
                SingleWidgetSourceType.PROJECT -> {
                    val project = repository.getProjectById(sourceId).first()
                    if (project != null) {
                        source = SingleWidgetSource(project.name, project.color, R.drawable.ic_widget_project)
                        tasks = allTasks.filter { it.projectId == project.id }.sortedBy { it.sortOrder }
                    }
                }
                SingleWidgetSourceType.TAG -> {
                    val tag = repository.getTagById(sourceId).first()
                    if (tag != null) {
                        val projects = repository.getProjects().first()
                        source = SingleWidgetSource(tag.name, tag.color, R.drawable.ic_widget_tag)
                        tasks = allTasks.filter { it.effectiveTagIds(projects).contains(tag.id) }.sortedBy { it.sortOrder }
                    }
                }
                SingleWidgetSourceType.LIST -> {
                    val list = repository.getListById(sourceId).first()
                    if (list != null) {
                        source = SingleWidgetSource(list.name, list.color, R.drawable.ic_widget_list)
                        tasks = allTasks.filter { it.listId == list.id }.sortedBy { it.sortOrder }
                    }
                }
            }
        }

        val peopleById = repository.getPeople().first().associateBy { it.id }
        val theme = resolveWidgetTheme(context)
        // Distinguishes "never configured" from "the list/project/tag this widget was pointed at
        // got deleted from the main app" — both used to show the identical "Not set up yet"
        // message, giving no clue that re-adding the widget (to pick a new source) is actually needed.
        val sourceWasDeleted = sourceId != null && source == null

        val cornerRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
        val customLabel = prefs[WIDGET_LABEL_KEY]
        val useM3Colors = prefs[WIDGET_USE_M3_COLORS_KEY] ?: false
        val opacity = prefs[WIDGET_OPACITY_KEY] ?: 1.0f
        val accentOverrideKey = prefs[WIDGET_ACCENT_OVERRIDE_KEY]
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                SingleListWidgetContent(
                    context = context,
                    source = source,
                    sourceWasDeleted = sourceWasDeleted,
                    tasks = tasks,
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
private fun SingleListWidgetContent(
    context: Context,
    source: SingleWidgetSource?,
    sourceWasDeleted: Boolean,
    tasks: List<Task>,
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
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground.copy(alpha = opacity))
            .appWidgetBackground()
            .cornerRadius(cornerRadius.dp)
            .padding(16.dp)
            .clickable(openAppAction())
    ) {
        if (source == null) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (sourceWasDeleted) "Deleted — remove and re-add this widget" else "Not set up yet",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            val color = accentOverride ?: if (useM3Colors) colors.primary else accents.getAccent(source.colorKey)
            val headerText = if (!customLabel.isNullOrBlank()) customLabel else source.name
            val isLarge = LocalSize.current.height > 180.dp
            val done = tasks.count { it.done }

            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                    Box(
                        modifier = GlanceModifier
                            .size(26.dp)
                            .cornerRadius(12.dp)
                            .background(color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(source.iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(color))
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = headerText,
                            maxLines = 1,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface)
                        )
                        Text(
                            text = "$done/${tasks.size} done",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                    if (isLarge) {
                        WidgetProgressRingImage(
                            context = context,
                            progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size,
                            sizeDp = 32.dp,
                            strokeDp = 4.dp,
                            activeColor = color,
                            trackColor = colors.surfaceContainerHighest
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(9.dp))
                WidgetDivider()
                Spacer(modifier = GlanceModifier.height(4.dp))
                if (tasks.isEmpty()) {
                    Text(
                        text = "No tasks here.",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                } else {
                    // A pinned list/project/tag has no natural bound the way "today" or "next 3
                    // agenda days" does — rendering every row inflates the RemoteViews payload
                    // (each row is real embedded views, not a lazily-paged reference), and a
                    // large enough source pushes the whole update past Android's ~1MB Binder IPC
                    // limit, which fails the push silently and leaves the widget stuck showing
                    // stale/blank content. Cap what's actually drawn; the header counts above
                    // still reflect the true total.
                    val visibleTasks = tasks.take(MAX_VISIBLE_TASKS)
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(visibleTasks) { task ->
                            WidgetTaskRow(task = task, tintColor = color, onSurface = colors.onSurface, assignees = task.assigneeIds.mapNotNull { peopleById[it] })
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
    }
}
