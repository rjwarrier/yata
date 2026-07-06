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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

val SINGLE_LIST_ID_KEY = stringPreferencesKey("list_id")

/** "Single list" (pinned) — medium (2 rows) / large (5 rows + ring). Which list it shows is
 * chosen once via [SingleListWidgetConfigActivity] at add-time, stored per widget instance. */
class SingleListWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val listId = prefs[SINGLE_LIST_ID_KEY]
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val list = listId?.let { lid -> repository.getLists().first().find { it.id == lid } }
        val tasks = if (list != null) {
            repository.getTasks().first().filter { it.listId == list.id }.sortedBy { it.sortOrder }
        } else {
            emptyList()
        }

        val theme = resolveWidgetTheme(context)
        // Distinguishes "never configured" from "the list this widget was pointed at got
        // deleted from the main app" — both used to show the identical "Not set up yet" message,
        // giving no clue that re-adding the widget (to pick a new list) is actually needed.
        val listWasDeleted = listId != null && list == null

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                SingleListWidgetContent(context, list, listWasDeleted, tasks, theme.colorScheme, theme.accents)
            }
        }
    }
}

@Composable
private fun SingleListWidgetContent(
    context: Context,
    list: com.mj.yata.domain.model.YataList?,
    listWasDeleted: Boolean,
    tasks: List<Task>,
    colors: androidx.compose.material3.ColorScheme,
    accents: com.mj.yata.ui.theme.YataAccents
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(openAppAction())
    ) {
        if (list == null) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (listWasDeleted) "List was deleted — remove and re-add this widget" else "Not set up yet",
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            val color = accents.getAccent(list.color)
            val isLarge = LocalSize.current.height > 180.dp
            val done = tasks.count { it.done }
            val shown = tasks.take(if (isLarge) 5 else 2)

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
                            provider = ImageProvider(R.drawable.ic_widget_list),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(color))
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = list.name,
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
                if (shown.isEmpty()) {
                    Text(
                        text = "No tasks in this list.",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        shown.forEach { task -> WidgetTaskRow(task = task, tintColor = color, onSurface = colors.onSurface) }
                    }
                }
            }
        }
    }
}
