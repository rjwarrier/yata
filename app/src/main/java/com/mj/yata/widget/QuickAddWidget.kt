package com.mj.yata.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

val QUICK_ADD_TARGET_TYPE_KEY = stringPreferencesKey("quick_add_target_type") // "list" | "project"
val QUICK_ADD_TARGET_ID_KEY = stringPreferencesKey("quick_add_target_id")

/** "Quick add" — small (FAB) / medium (pill field + list shortcuts). Optionally preset to a
 * project/list via [QuickAddWidgetConfigActivity] — every task created through this instance
 * (the main pill/FAB, not the per-list shortcut chips) drops straight into it. */
class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val targetType = prefs[QUICK_ADD_TARGET_TYPE_KEY]
        val targetId = prefs[QUICK_ADD_TARGET_ID_KEY]

        val cornerRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
        val customLabel = prefs[WIDGET_LABEL_KEY]
        val useM3Colors = prefs[WIDGET_USE_M3_COLORS_KEY] ?: false

        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val allLists = repository.getLists().first()

        val targetName = when (targetType) {
            "list" -> allLists.find { it.id == targetId }?.name
            "project" -> repository.getProjects().first().find { it.id == targetId }?.name
            else -> null
        }

        val theme = resolveWidgetTheme(context)

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                QuickAddWidgetContent(
                    context = context,
                    allLists = allLists,
                    accents = theme.accents,
                    colors = theme.colorScheme,
                    targetType = targetType,
                    targetId = targetId,
                    targetName = targetName,
                    cornerRadius = cornerRadius,
                    customLabel = customLabel,
                    useM3Colors = useM3Colors
                )
            }
        }
    }
}

@Composable
private fun QuickAddWidgetContent(
    context: Context,
    allLists: List<com.mj.yata.domain.model.YataList>,
    accents: com.mj.yata.ui.theme.YataAccents,
    colors: androidx.compose.material3.ColorScheme,
    targetType: String?,
    targetId: String?,
    targetName: String?,
    cornerRadius: Int,
    customLabel: String?,
    useM3Colors: Boolean
) {
    val isSmall = LocalSize.current.width < 180.dp
    val isTall = LocalSize.current.height > 140.dp
    val lists = allLists.take(if (isTall) 6 else 3)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(cornerRadius.dp)
            .clickable(openQuickAddDialogAction(targetType, targetId, targetName))
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
                WidgetSectionHeader(customLabel ?: "Quick add", GlanceTheme.colors.primary)
                Spacer(modifier = GlanceModifier.height(8.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .cornerRadius(20.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .padding(horizontal = 14.dp)
                        .clickable(openQuickAddDialogAction(targetType, targetId, targetName)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = targetName?.let { "Add to $it…" } ?: "Add a task…",
                        maxLines = 1,
                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                if (targetName != null) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Destination: ",
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                        val badgeColor = if (useM3Colors) colors.primary else GlanceTheme.colors.primary.getColor(context)
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(4.dp)
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = targetName,
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorProvider(badgeColor))
                            )
                        }
                    }
                }
                if (lists.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    if (isTall) {
                        val chunked = lists.chunked(3)
                        chunked.forEachIndexed { rowIndex, rowLists ->
                            if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                rowLists.forEachIndexed { index, list ->
                                    if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                                    val color = if (useM3Colors) colors.primary else accents.getAccent(list.color)
                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(13.dp)
                                            .background(color.copy(alpha = 0.16f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .clickable(openQuickAddDialogAction("list", list.id, list.name)),
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
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            lists.forEachIndexed { index, list ->
                                if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                                val color = if (useM3Colors) colors.primary else accents.getAccent(list.color)
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(13.dp)
                                        .background(color.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .clickable(openQuickAddDialogAction("list", list.id, list.name)),
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
    }
}
