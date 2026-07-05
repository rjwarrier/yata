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
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** "Progress / stats" — small (one big ring) / medium (ring + per-list today breakdown). Scoped
 * to today's tasks (due today or overdue), same definition as the Today widget/tab. */
class ProgressStatsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val todayStr = LocalDate.now().toString()
        val todayTasks = repository.getTasks().first().filter { it.due != null && it.due!! <= todayStr }
        val lists = repository.getLists().first()
        val theme = resolveWidgetTheme(context)

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                ProgressStatsContent(context, todayTasks, lists, theme.colorScheme, theme.accents)
            }
        }
    }
}

@Composable
private fun ProgressStatsContent(
    context: Context,
    tasks: List<Task>,
    lists: List<YataList>,
    colors: androidx.compose.material3.ColorScheme,
    accents: com.mj.yata.ui.theme.YataAccents
) {
    val isSmall = LocalSize.current.width < 180.dp
    val done = tasks.count { it.done }
    val total = tasks.size
    val progress = if (total == 0) 0f else done.toFloat() / total

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(openAppAction())
    ) {
        if (isSmall) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                WidgetProgressRingImage(
                    context = context,
                    progress = progress,
                    sizeDp = 78.dp,
                    strokeDp = 8.dp,
                    activeColor = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    centerText = "${(progress * 100).toInt()}%",
                    centerTextSizeSp = 18f
                )
                Spacer(modifier = GlanceModifier.height(10.dp))
                Text(
                    text = "$done/$total done today",
                    style = TextStyle(fontSize = 12.5.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            val byList = lists
                .map { list -> list to tasks.filter { it.listId == list.id } }
                .filter { it.second.isNotEmpty() }
                .sortedByDescending { it.second.size }
                .take(4)

            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetProgressRingImage(
                    context = context,
                    progress = progress,
                    sizeDp = 64.dp,
                    strokeDp = 7.dp,
                    activeColor = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    centerText = "${(progress * 100).toInt()}%",
                    centerTextSizeSp = 15f
                )
                Spacer(modifier = GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    byList.forEachIndexed { index, (list, listTasks) ->
                        if (index > 0) Spacer(modifier = GlanceModifier.height(7.dp))
                        val color = accents.getAccent(list.color)
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                            Box(modifier = GlanceModifier.size(7.dp).cornerRadius(3.5.dp).background(color)) {}
                            Spacer(modifier = GlanceModifier.width(7.dp))
                            Text(
                                text = list.name,
                                maxLines = 1,
                                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "${listTasks.count { it.done }}/${listTasks.size}",
                                style = TextStyle(fontSize = 11.5.sp, color = GlanceTheme.colors.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
        }
    }
}
