package com.mj.yata.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mj.yata.MainActivity
import com.mj.yata.domain.model.Task
import dagger.hilt.android.EntryPointAccessors

val taskIdKey = ActionParameters.Key<String>("task_id")
val navigateToKey = ActionParameters.Key<String>("navigate_to")
val shortcutActionKey = ActionParameters.Key<String>("shortcut_action")
val listIdKey = ActionParameters.Key<String>("list_id")
val entityIdKey = ActionParameters.Key<String>("entity_id")
val quickAddTargetTypeKey = ActionParameters.Key<String>("target_type")
val quickAddTargetIdKey = ActionParameters.Key<String>("target_id")
val quickAddTargetNameKey = ActionParameters.Key<String>("target_name")

val WIDGET_CORNER_RADIUS_KEY = intPreferencesKey("widget_corner_radius")
val WIDGET_LABEL_KEY = stringPreferencesKey("widget_label")
val WIDGET_USE_M3_COLORS_KEY = booleanPreferencesKey("widget_use_m3_colors")
val WIDGET_OPACITY_KEY = floatPreferencesKey("widget_opacity")
// Null/absent = follow the app-wide accent (unchanged default behavior).
val WIDGET_ACCENT_OVERRIDE_KEY = stringPreferencesKey("widget_accent_override")

/** Plain "open the app" tap target — for widget background/whitespace areas that aren't a more
 * specific row or button (those have their own actions, which win over this one). */
fun openAppAction(): Action = actionStartActivity<MainActivity>()

/** Opens the app at a specific task's detail screen — MainActivity already knows how to route
 * `navigate_to=task_detail` + `task_id` (same path a reminder notification tap uses). */
fun openTaskAction(taskId: String): Action = actionStartActivity<MainActivity>(
    actionParametersOf(navigateToKey to "task_detail", taskIdKey to taskId)
)

/** Opens the app at a specific list's detail screen — for tap-through on a widget row that's
 * already grouped/scoped to one list (e.g. Progress Stats' per-list breakdown). */
fun openListAction(listId: String): Action = actionStartActivity<MainActivity>(
    actionParametersOf(navigateToKey to "list_detail", entityIdKey to listId)
)

/** Opens the app at a specific person's detail screen — for tap-through on a per-person row
 * (e.g. Team Overdue). */
fun openPersonAction(personId: String): Action = actionStartActivity<MainActivity>(
    actionParametersOf(navigateToKey to "person_detail", entityIdKey to personId)
)

/** Opens the app's Upcoming tab — for tap-through on the Upcoming widget's compact "Today" /
 * "Tomorrow" summary rows. */
fun openUpcomingAction(): Action = actionStartActivity<MainActivity>(
    actionParametersOf(navigateToKey to "upcoming")
)

/** Opens the app's Today tab — reuses the same `shortcut_action=today` path the launcher
 * shortcut uses (see MainActivity), for tap-through on the Upcoming widget's "Today" row. */
fun openTodayShortcutAction(): Action = actionStartActivity<MainActivity>(
    actionParametersOf(shortcutActionKey to "today")
)

/** Opens the app straight into the quick-add sheet — reuses the same `shortcut_action=quick_add`
 * path the launcher shortcut uses, optionally pre-selecting a list (for the Quick Add widget's
 * list chips). */
fun openQuickAddAction(listId: String? = null): Action = actionStartActivity<MainActivity>(
    if (listId != null) {
        actionParametersOf(shortcutActionKey to "quick_add", listIdKey to listId)
    } else {
        actionParametersOf(shortcutActionKey to "quick_add")
    }
)

/** Opens the lightweight overlay dialog (not the full app) to add a task straight into
 * [targetType]/[targetId] ("list"/"project", or null for no preset) — used by the Quick Add
 * widget so a one-off task doesn't require launching the whole app window. */
fun openQuickAddDialogAction(targetType: String?, targetId: String?, targetName: String?): Action =
    actionStartActivity<QuickAddDialogActivity>(
        actionParametersOf(
            quickAddTargetTypeKey to (targetType ?: ""),
            quickAddTargetIdKey to (targetId ?: ""),
            quickAddTargetNameKey to (targetName ?: "")
        )
    )

/** Compact task row shared by the Today / Single-list / Upcoming widgets: a round checkbox that
 * toggles done in place (no app launch), and a title+time area that deep-links to the task.
 * Matches the main app's own done-task treatment (onSurface @ 60% + strikethrough) rather than a
 * flat gray, so it's the same M3 palette, not a widget-only lookalike. */
@Composable
fun WidgetTaskRow(task: Task, tintColor: Color, onSurface: Color, assignees: List<com.mj.yata.domain.model.Person> = emptyList()) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        CheckBox(
            checked = task.done,
            onCheckedChange = actionRunCallback<ToggleTaskDoneAction>(actionParametersOf(taskIdKey to task.id)),
            colors = CheckboxDefaults.colors(
                checkedColor = ColorProvider(tintColor),
                uncheckedColor = ColorProvider(tintColor)
            )
        )
        Row(
            modifier = GlanceModifier.defaultWeight().clickable(openTaskAction(task.id)),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = task.title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = if (task.done) ColorProvider(onSurface.copy(alpha = 0.6f)) else ColorProvider(onSurface),
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (assignees.isNotEmpty()) {
                Row {
                    assignees.take(2).forEach { person ->
                        Box(
                            modifier = GlanceModifier
                                .size(16.dp)
                                .cornerRadius(8.dp)
                                .background(ColorProvider(tintColor.copy(alpha = 0.22f)))
                                .padding(horizontal = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = person.initials.take(1).uppercase(),
                                maxLines = 1,
                                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ColorProvider(tintColor))
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            val time = task.time
            if (!time.isNullOrBlank()) {
                Text(
                    text = time,
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = androidx.glance.GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

/** Thin uppercase section label with a leading icon-less color accent, e.g. "TODAY" / "UPCOMING". */
@Composable
fun WidgetSectionHeader(label: String, color: ColorProvider) {
    Text(
        text = label.uppercase(),
        maxLines = 1,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    )
}

@Composable
fun WidgetDivider() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(androidx.glance.GlanceTheme.colors.outline)
    ) {}
}

@Composable
fun WidgetProgressRingImage(
    context: Context,
    progress: Float,
    sizeDp: Dp,
    strokeDp: Dp,
    activeColor: Color,
    trackColor: Color,
    centerText: String? = null,
    centerTextSizeSp: Float = 11f
) {
    val bitmap = WidgetRing.render(context, sizeDp, strokeDp, progress, activeColor, trackColor)
    Box(modifier = GlanceModifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(sizeDp))
        if (centerText != null) {
            Text(
                text = centerText,
                style = TextStyle(
                    fontSize = centerTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.glance.GlanceTheme.colors.onSurface
                )
            )
        }
    }
}

/** Shared by every widget that shows a task checkbox — toggles done, no app launch, then
 * refreshes every widget instance so the change reflects immediately everywhere. */
class ToggleTaskDoneAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[taskIdKey] ?: return
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        repository.toggleTaskDone(taskId)
        WidgetRefresher.refreshAll(context)
    }
}
