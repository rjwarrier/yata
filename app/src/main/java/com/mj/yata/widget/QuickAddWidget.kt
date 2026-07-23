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

    // 250x180 is a real bucket (not just 250x110 twice) so the "isTall" 2-row list-chip layout
    // in QuickAddWidgetContent is actually reachable by resizing taller, matching the XML's
    // maxResizeHeight of 180dp.
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val targetType = prefs[QUICK_ADD_TARGET_TYPE_KEY]
        val targetId = prefs[QUICK_ADD_TARGET_ID_KEY]

        val cornerRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
        val customLabel = prefs[WIDGET_LABEL_KEY]
        val useM3Colors = prefs[WIDGET_USE_M3_COLORS_KEY] ?: false
        val opacity = prefs[WIDGET_OPACITY_KEY] ?: 1.0f
        val accentOverrideKey = prefs[WIDGET_ACCENT_OVERRIDE_KEY]

        android.util.Log.d(
            "QuickAddWidget",
            "provideGlance id=$id label=$customLabel radius=$cornerRadius targetType=$targetType targetId=$targetId accent=$accentOverrideKey"
        )

        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val allLists = repository.getLists().first()

        val targetName = when (targetType) {
            "list" -> allLists.find { it.id == targetId }?.name
            "project" -> repository.getProjects().first().find { it.id == targetId }?.name
            else -> null
        }
        // Preset was set (targetId non-null) but no longer resolves — its list/project got
        // deleted from the main app. Drop it entirely rather than handing the stale id to the
        // quick-add dialog, which would otherwise try to create a task pointing at a row that no
        // longer exists (Room enforces the FK, so that insert fails outright).
        val presetWasDeleted = targetId != null && targetName == null
        val effectiveTargetType = if (presetWasDeleted) null else targetType
        val effectiveTargetId = if (presetWasDeleted) null else targetId

        val theme = resolveWidgetTheme(context)
        val accentOverride = accentOverrideKey?.let { theme.accents.getAccent(it) }

        provideContent {
            GlanceTheme(colors = theme.glanceColors) {
                QuickAddWidgetContent(
                    context = context,
                    allLists = allLists,
                    accents = theme.accents,
                    colors = theme.colorScheme,
                    widgetBackground = theme.widgetBackground,
                    targetType = effectiveTargetType,
                    targetId = effectiveTargetId,
                    targetName = targetName,
                    presetWasDeleted = presetWasDeleted,
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
private fun QuickAddWidgetContent(
    context: Context,
    allLists: List<com.mj.yata.domain.model.YataList>,
    accents: com.mj.yata.ui.theme.YataAccents,
    colors: androidx.compose.material3.ColorScheme,
    widgetBackground: Color,
    targetType: String?,
    targetId: String?,
    targetName: String?,
    presetWasDeleted: Boolean,
    cornerRadius: Int,
    customLabel: String?,
    useM3Colors: Boolean,
    opacity: Float,
    accentOverride: Color?
) {
    val isSmall = LocalSize.current.width < 180.dp
    val isTall = LocalSize.current.height > 140.dp
    val lists = allLists.take(if (isTall) 6 else 3)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground.copy(alpha = opacity))
            .appWidgetBackground()
            .cornerRadius(cornerRadius.dp)
            .clickable(openQuickAddDialogAction(targetType, targetId, targetName))
    ) {
        if (isSmall) {
            val plusColor = accentOverride ?: colors.primary
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "+",
                    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ColorProvider(plusColor))
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = if (!customLabel.isNullOrBlank()) customLabel else "Add task",
                    maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
                WidgetSectionHeader(customLabel ?: "Quick add", ColorProvider(accentOverride ?: colors.primary))
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
                if (presetWasDeleted) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "Preset deleted — long-press to reconfigure",
                        maxLines = 1,
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(colors.error))
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
