package com.mj.yata.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mj.yata.MainActivity
import com.mj.yata.domain.model.Task
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val widgetColors = ColorProviders(lightColorScheme(), darkColorScheme())
private val taskIdKey = ActionParameters.Key<String>("task_id")

class YataAppWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(260.dp, 200.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val todayStr = LocalDate.now().toString()
        val todayTasks = repository.getTasks().first()
            .filter { !it.done && it.due != null && it.due <= todayStr }
            .sortedBy { it.sortOrder }

        provideContent {
            GlanceTheme(colors = widgetColors) {
                WidgetContent(todayTasks)
            }
        }
    }
}

@Composable
private fun WidgetContent(tasks: List<Task>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            text = "Today",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface)
        )
        if (tasks.isEmpty()) {
            Text(
                text = "No tasks for today",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        CheckBox(
                            checked = false,
                            onCheckedChange = actionRunCallback<ToggleTaskDoneAction>(
                                actionParametersOf(taskIdKey to task.id)
                            )
                        )
                        Text(
                            text = task.title,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface)
                        )
                    }
                }
            }
        }
    }
}

class ToggleTaskDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        repository.toggleTaskDone(taskId)
        YataAppWidget().update(context, glanceId)
    }
}
