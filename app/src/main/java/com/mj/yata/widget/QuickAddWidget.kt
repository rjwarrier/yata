package com.mj.yata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/** "Quick add" — small (FAB) / medium (pill field + list shortcuts). */
class QuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val lists = repository.getLists().first().take(3)
        val theme = resolveWidgetTheme(context)

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                QuickAddWidgetContent(lists, theme.accents)
            }
        }
    }
}

@Composable
private fun QuickAddWidgetContent(lists: List<com.mj.yata.domain.model.YataList>, accents: com.mj.yata.ui.theme.YataAccents) {
    val isSmall = LocalSize.current.width < 180.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .clickable(openQuickAddAction())
    ) {
        if (isSmall) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(52.dp)
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer)
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
                Text(
                    text = "Add task",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
                WidgetSectionHeader("Quick add", GlanceTheme.colors.primary)
                Spacer(modifier = GlanceModifier.height(8.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Add a task…",
                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    lists.forEachIndexed { index, list ->
                        if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                        val color = accents.getAccent(list.color)
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(13.dp)
                                .background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .clickable(openQuickAddAction(listId = list.id)),
                        ) {
                            Text(
                                text = list.name,
                                maxLines = 1,
                                style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = ColorProvider(color))
                            )
                        }
                    }
                }
            }
        }
    }
}
