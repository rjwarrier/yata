package com.mj.yata.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface WidgetUpdater {
    /** Refreshes every placed instance of the home-screen widget. Called after any task write. */
    fun notifyTasksChanged()
}

@Singleton
class WidgetUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun notifyTasksChanged() {
        scope.launch {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(YataAppWidget::class.java)
            val widget = YataAppWidget()
            ids.forEach { id -> widget.update(context, id) }
        }
    }
}
