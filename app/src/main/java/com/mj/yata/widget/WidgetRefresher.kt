package com.mj.yata.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Refreshes every placed instance of every YATA widget. Called after any task write (from the
 * app), any in-widget action (checkbox toggle), or a configure-screen save — so all pinned
 * widgets, not just the one that changed, stay in sync (e.g. toggling a task from the Today
 * widget should also update the Progress widget's percentage and a pinned Single-list widget
 * showing the same task).
 *
 * Two independent problems have been confirmed here, empirically (see git history for how):
 * 1. Two independent triggers — [WidgetUpdaterImpl]'s task-change refresh and the config
 *    Activity's post-save refresh — can call [GlanceAppWidget.update] for the *same* widget
 *    instance close together; overlapping calls for the same [GlanceId] aren't guaranteed to
 *    finish in the order they started. A per-[GlanceId] [Mutex] makes updates for the same
 *    widget instance fully sequential.
 * 2. Independently of #1, a *single*, fully-awaited, error-free `update()` call has been proven
 *    (by reading the widget host's actual on-screen state against the app's own database) to
 *    sometimes just not repaint the tile at all — no exception, nothing in Logcat, the widget
 *    host silently drops the push. This reproduced with two saves 12 seconds apart, far too far
 *    apart to be explained by #1's race. [pushSerialized] retries the push a few times, still
 *    fully serialized against other calls for the same id, to make a dropped push recoverable
 *    without depending on some unrelated later event to "catch it up."
 */
object WidgetRefresher {
    private val widgetTypes = listOf(
        YataAppWidget(),
        QuickAddWidget(),
        ProgressStatsWidget(),
        SingleListWidget(),
        UpcomingWidget(),
        TeamOverdueWidget()
    )

    private val locks = ConcurrentHashMap<GlanceId, Mutex>()

    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        widgetTypes.forEach { widget ->
            val ids = manager.getGlanceIds(widget::class.java)
            ids.forEach { id -> pushSerialized(context, widget, id) }
        }
    }

    suspend fun refreshWidget(context: Context, widgetClass: Class<out GlanceAppWidget>) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(widgetClass)
        val widgetInstance = widgetTypes.find { it::class.java == widgetClass } ?: return
        ids.forEach { id -> pushSerialized(context, widgetInstance, id) }
    }

    private suspend fun pushSerialized(context: Context, widget: GlanceAppWidget, id: GlanceId) {
        val lock = locks.computeIfAbsent(id) { Mutex() }
        lock.withLock {
            // All 3 attempts read/render the exact same (already-written) state — there's no
            // "earlier" or "later" data between them, just repeated attempts to get one push to
            // actually land on the host. Held under the same lock acquisition throughout, so
            // nothing else touching this widget id can interleave with the retries.
            repeat(3) { attempt ->
                widget.update(context, id)
                if (attempt < 2) delay(400)
            }
        }
    }
}
