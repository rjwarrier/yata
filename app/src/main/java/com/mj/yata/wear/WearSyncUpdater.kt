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
const val KEY_TASK_DUE = "due"

private const val MAX_TASKS_SENT_TO_WATCH = 50

/** Pushes "tasks due today" to any paired Wear OS watch via the Wearable Data Layer — a count
 * for the complication, and the full list (id/title/done/time) for the watch companion app's
 * task list. Best-effort — silently no-ops if there's no paired watch or Play Services isn't
 * available, same as the app working fine with no widget. */
interface WearSyncUpdater {
    fun notifyTasksChanged()

    /** Blocking check (call off the main thread) — true if any watch node is currently
     * reachable via the Wearable Data Layer, false if none is paired/reachable or the check
     * itself fails (e.g. Play Services unavailable). */
    fun isWatchConnected(): Boolean
}

@Singleton
class WearSyncUpdaterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearSyncUpdater {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun notifyTasksChanged() {
        Log.d("YataWear", "notifyTasksChanged: starting")
        scope.launch {
            try {
                val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
                val todayStr = LocalDate.now().toString()
                val allTasks = repository.getTasks().first()
                Log.d("YataWear", "notifyTasksChanged: loaded ${allTasks.size} total tasks from repository")

                // Complication count stays "due today-or-earlier and not done" specifically,
                // independent of the broader list below sent for the watch's own task screen.
                val todayCount = allTasks.count { it.deletedAt == null && !it.done && it.due != null && it.due!! <= todayStr }

                // Everything pending (not just today) for the watch's own list — capped like
                // every widget in this codebase already is, since the Wearable Data Layer has a
                // practical payload size ceiling that a large DataMap array can silently exceed
                // without the phone side ever seeing an error. Completed tasks drop off the next
                // push instead of accumulating forever.
                val pendingTasks = allTasks
                    .filter { it.deletedAt == null && !it.done }
                    .sortedWith(compareBy(nullsLast()) { it.due })
                    .take(MAX_TASKS_SENT_TO_WATCH)

                val countRequest = PutDataMapRequest.create(TODAY_COUNT_PATH).apply {
                    dataMap.putInt(KEY_TODAY_COUNT, todayCount)
                    // Forces onDataChanged to fire watch-side even if the count is unchanged
                    // from last push (DataItems only notify listeners when their content differs).
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                val countResult = com.google.android.gms.tasks.Tasks.await(Wearable.getDataClient(context).putDataItem(countRequest))
                Log.d("YataWear", "notifyTasksChanged: count DataItem put succeeded, uri=${countResult.uri}, pendingCount=${pendingTasks.size}")

                val taskMaps = ArrayList(pendingTasks.map { task ->
                    DataMap().apply {
                        putString(KEY_TASK_ID, task.id)
                        putString(KEY_TASK_TITLE, task.title)
                        putBoolean(KEY_TASK_DONE, task.done)
                        putString(KEY_TASK_TIME, task.time ?: "")
                        putString(KEY_TASK_DUE, task.due ?: "")
                    }
                })
                val tasksRequest = PutDataMapRequest.create(TODAY_TASKS_PATH).apply {
                    dataMap.putDataMapArrayList(KEY_TASKS, taskMaps)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                val tasksResult = com.google.android.gms.tasks.Tasks.await(Wearable.getDataClient(context).putDataItem(tasksRequest))
                Log.d("YataWear", "notifyTasksChanged: tasks DataItem put succeeded, uri=${tasksResult.uri}")
            } catch (e: Exception) {
                Log.e("YataWear", "notifyTasksChanged: failed", e)
            }
        }
    }

    override fun isWatchConnected(): Boolean {
        return try {
            com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(context).connectedNodes).isNotEmpty()
        } catch (e: Exception) {
            Log.w("YataWear", "isWatchConnected check failed", e)
            false
        }
    }
}
