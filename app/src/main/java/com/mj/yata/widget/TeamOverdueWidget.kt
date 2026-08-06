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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** At-a-glance "who's behind" — per-person overdue counts, the same data the
 * [OverdueEscalationWorker] notification is built from, just always visible on the home screen
 * instead of only surfacing once a day. */
class TeamOverdueWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val cornerRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
        val customLabel = prefs[WIDGET_LABEL_KEY]
        val opacity = prefs[WIDGET_OPACITY_KEY] ?: 1.0f
        val accentOverrideKey = prefs[WIDGET_ACCENT_OVERRIDE_KEY]

        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val tasks = repository.getTasks().first()
        val people = repository.getPeople().first().filter { !it.archived }
        val today = LocalDate.now()

        val overdueByPerson = people.mapNotNull { person ->
            val count = tasks.count { task ->
                if (task.done || person.id !in task.assigneeIds) return@count false
                val due = task.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@count false
                due.isBefore(today)
            }
            if (count > 0) person to count else null
        }.sortedByDescending { it.second }

        val theme = resolveWidgetTheme(context)
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }
        val health = WidgetHealthStore.read(context, TeamOverdueWidget::class.java.name)

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                TeamOverdueContent(
                    overdueByPerson = overdueByPerson,
                    colors = theme.colorScheme,
                    widgetBackground = theme.widgetBackground,
                    cornerRadius = cornerRadius,
                    customLabel = customLabel,
                    opacity = opacity,
                    accentOverride = accentOverride,
                    health = health
                )
            }
        }
    }
}

@Composable
private fun TeamOverdueContent(
    overdueByPerson: List<Pair<Person, Int>>,
    colors: androidx.compose.material3.ColorScheme,
    widgetBackground: androidx.compose.ui.graphics.Color,
    cornerRadius: Int,
    customLabel: String?,
    opacity: Float,
    accentOverride: androidx.compose.ui.graphics.Color?,
    health: WidgetHealth?
) {
    val maxRows = if (LocalSize.current.height > 180.dp) 8 else 5
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
            WidgetSectionHeader(customLabel ?: "Team Overdue", ColorProvider(accentOverride ?: colors.error))
            WidgetStaleBadge(health)
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (overdueByPerson.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nobody's behind. 🎉",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    overdueByPerson.take(maxRows).forEachIndexed { index, (person, count) ->
                        if (index > 0) Spacer(modifier = GlanceModifier.height(7.dp))
                        Row(
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                            modifier = GlanceModifier.fillMaxWidth().clickable(openPersonAction(person.id))
                        ) {
                            Text(
                                text = person.name,
                                maxLines = 1,
                                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "$count overdue",
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ColorProvider(accentOverride ?: colors.error))
                            )
                        }
                    }
                }
            }
        }
    }
}
