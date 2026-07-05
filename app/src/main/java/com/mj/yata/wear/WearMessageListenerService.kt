package com.mj.yata.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.mj.yata.domain.model.Task
import com.mj.yata.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private const val TOGGLE_TASK_PATH = "/toggle_task"
private const val QUICK_ADD_TASK_PATH = "/quick_add_task"

/** Handles commands sent FROM the watch companion app — checking a task off, or quick-adding a
 * new one — both just call the same repository the phone UI uses, so the normal task-changed
 * flow (widgets, wear re-sync) fires automatically afterward. */
class WearMessageListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("YataWear", "phone received message: path=${event.path} data='${String(event.data)}'")
        val repository = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java).repository()
        when (event.path) {
            TOGGLE_TASK_PATH -> {
                val taskId = String(event.data)
                scope.launch { repository.toggleTaskDone(taskId) }
            }
            QUICK_ADD_TASK_PATH -> {
                val title = String(event.data).trim()
                if (title.isNotEmpty()) {
                    scope.launch {
                        Log.d("YataWear", "phone: creating quick-add task '$title'")
                        repository.upsertTask(
                            Task(
                                id = "t_" + UUID.randomUUID().toString(),
                                title = title,
                                listId = null,
                                projectId = null,
                                section = "Afternoon",
                                due = LocalDate.now().toString(),
                                time = null,
                                reminder = null,
                                priority = "none",
                                flag = false,
                                done = false,
                                assigneeIds = emptyList(),
                                tagIds = emptyList(),
                                recurrence = null,
                                subtasks = emptyList(),
                                notes = null
                            )
                        )
                    }
                }
            }
        }
    }
}
