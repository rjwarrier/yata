package com.mj.yata.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

/** Refreshes every placed instance of every YATA widget. Called after any task write (from the
 * app) or any in-widget action (checkbox toggle) so all pinned widgets — not just the one that
 * was tapped — stay in sync (e.g. toggling a task from the Today widget should also update the
 * Progress widget's percentage and a pinned Single-list widget showing the same task). */
object WidgetRefresher {
    private val widgetTypes = listOf(
        YataAppWidget(),
        QuickAddWidget(),
        ProgressStatsWidget(),
        SingleListWidget(),
        UpcomingWidget()
    )

    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        widgetTypes.forEach { widget ->
            val ids = manager.getGlanceIds(widget::class.java)
            ids.forEach { id -> widget.update(context, id) }
        }
    }
}
