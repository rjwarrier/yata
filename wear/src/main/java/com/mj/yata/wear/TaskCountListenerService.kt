package com.mj.yata.wear

import android.content.ComponentName
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

private const val TODAY_COUNT_PATH = "/today_count"
private const val KEY_COUNT = "count"

private const val TODAY_TASKS_PATH = "/today_tasks"
private const val KEY_TASKS = "tasks"
private const val KEY_TASK_ID = "id"
private const val KEY_TASK_TITLE = "title"
private const val KEY_TASK_DONE = "done"
private const val KEY_TASK_TIME = "time"

/** Receives the phone's pushed "tasks due today" count/list and forces an immediate complication
 * refresh — the complication itself never polls (UPDATE_PERIOD_SECONDS=0), it only reflects
 * whatever was last cached here. The full task list is cached separately for the companion app's
 * own list screen (see [WatchTaskRepository]). */
class TaskCountListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("YataWear", "watch onDataChanged: ${dataEvents.count} event(s)")
        dataEvents.forEach { event ->
            Log.d("YataWear", "watch event: type=${event.type} path=${event.dataItem.uri.path}")
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                TODAY_COUNT_PATH -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    TaskCountStore.setCount(applicationContext, map.getInt(KEY_COUNT, 0))

                    ComplicationDataSourceUpdateRequester.create(
                        context = applicationContext,
                        complicationDataSourceComponent = ComponentName(applicationContext, TodayCountComplicationService::class.java)
                    ).requestUpdateAll()
                }
                TODAY_TASKS_PATH -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val taskMaps = map.getDataMapArrayList(KEY_TASKS).orEmpty()
                    val tasks = taskMaps.map { m ->
                        WatchTask(
                            id = m.getString(KEY_TASK_ID) ?: "",
                            title = m.getString(KEY_TASK_TITLE) ?: "",
                            done = m.getBoolean(KEY_TASK_DONE),
                            time = m.getString(KEY_TASK_TIME)?.takeIf { it.isNotEmpty() }
                        )
                    }
                    Log.d("YataWear", "watch: received ${tasks.size} tasks: ${tasks.map { it.title }}")
                    WatchTaskRepository.replaceAll(applicationContext, tasks)
                }
            }
        }
        dataEvents.release()
    }
}
