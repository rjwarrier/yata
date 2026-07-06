package com.mj.yata.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mj.yata.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val TODAY_COUNT_PATH = "/today_count"
const val KEY_TODAY_COUNT = "count"

const val TODAY_TASKS_PATH = "/today_tasks"
const val KEY_TASKS = "tasks"
const val KEY_TASK_ID = "id"
const val KEY_TASK_TITLE = "title"
const val KEY_TASK_DONE = "done"
const val KEY_TASK_TIME = "time"

private const val MAX_TASKS_SENT_TO_WATCH = 50

/** Pushes "tasks due today" to any paired Wear OS watch via the Wearable Data Layer — a count
 * for the complication, and the full list (id/title/done/time) for the watch companion app's
 * task list. Best-effort — silently no-ops if there's no paired watch or Play Services isn't
 * available, same as the app working fine with no widget. */
interface WearSyncUpdater {
    fun notifyTasksChanged()
}

@Singleton
class WearSyncUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearSyncUpdater {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun notifyTasksChanged() {
        scope.launch {
            try {
                val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
                val todayStr = LocalDate.now().toString()
                // Capped like every widget in this codebase already is — this list is unbounded
                // by design (any never-completed task due today-or-earlier accumulates forever),
                // and the Wearable Data Layer has a practical payload size ceiling that a large
                // DataMap array can silently exceed without the phone side ever seeing an error.
                val todayTasks = repository.getTasks().first()
                    .filter { it.due != null && it.due!! <= todayStr }
                    .sortedWith(compareBy({ it.done }, { it.sortOrder }))
                    .take(MAX_TASKS_SENT_TO_WATCH)
                Log.d("YataWear", "notifyTasksChanged: pushing ${todayTasks.size} today-tasks: ${todayTasks.map { it.title }}")

                val countRequest = PutDataMapRequest.create(TODAY_COUNT_PATH).apply {
                    dataMap.putInt(KEY_TODAY_COUNT, todayTasks.count { !it.done })
                    // Forces onDataChanged to fire watch-side even if the count is unchanged
                    // from last push (DataItems only notify listeners when their content differs).
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                val countResult = com.google.android.gms.tasks.Tasks.await(Wearable.getDataClient(context).putDataItem(countRequest))
                Log.d("YataWear", "notifyTasksChanged: count DataItem put -> ${countResult.uri}")

                val taskMaps = ArrayList(todayTasks.map { task ->
                    DataMap().apply {
                        putString(KEY_TASK_ID, task.id)
                        putString(KEY_TASK_TITLE, task.title)
                        putBoolean(KEY_TASK_DONE, task.done)
                        putString(KEY_TASK_TIME, task.time ?: "")
                    }
                })
                val tasksRequest = PutDataMapRequest.create(TODAY_TASKS_PATH).apply {
                    dataMap.putDataMapArrayList(KEY_TASKS, taskMaps)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                val tasksResult = com.google.android.gms.tasks.Tasks.await(Wearable.getDataClient(context).putDataItem(tasksRequest))
                Log.d("YataWear", "notifyTasksChanged: tasks DataItem put -> ${tasksResult.uri}")
            } catch (e: Exception) {
                Log.e("YataWear", "notifyTasksChanged: failed", e)
            }
        }
    }
}
