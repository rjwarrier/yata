package com.mj.yata.wear

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mj.yata.domain.model.Task
import com.mj.yata.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private const val TOGGLE_TASK_PATH = "/toggle_task"
private const val QUICK_ADD_TASK_PATH = "/quick_add_task"
private const val MAX_QUICK_ADD_TITLE_LENGTH = 500

/** Handles commands sent FROM the watch companion app — checking a task off, or quick-adding a
 * new one — both just call the same repository the phone UI uses, so the normal task-changed
 * flow (widgets, wear re-sync) fires automatically afterward.
 *
 * This service must stay `exported` for Play Services to deliver Wearable Data Layer messages,
 * but that also means any other app on the device could target it directly with a crafted
 * intent/binder call. [isFromConnectedNode] closes that off by requiring the message's reported
 * source to actually be one of the phone's currently-paired nodes before acting on it. */
class WearMessageListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("YataWear", "phone received message: path=${event.path} data='${String(event.data)}'")
        scope.launch {
            if (!isFromConnectedNode(event.sourceNodeId)) {
                Log.w("YataWear", "ignoring message from untrusted/unpaired node: ${event.sourceNodeId}")
                return@launch
            }
            val repository = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java).repository()
            when (event.path) {
                TOGGLE_TASK_PATH -> {
                    val taskId = String(event.data)
                    if (taskId.isNotBlank()) repository.toggleTaskDone(taskId)
                }
                QUICK_ADD_TASK_PATH -> {
                    val title = String(event.data).trim().take(MAX_QUICK_ADD_TITLE_LENGTH)
                    if (title.isNotEmpty()) {
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

    /** Verifies the message actually came from a node currently paired with this phone, rather
     * than trusting `event.sourceNodeId` blindly — this is the only real check available since
     * the service's exported status can't be removed without breaking legitimate delivery. */
    private fun isFromConnectedNode(sourceNodeId: String): Boolean {
        return try {
            val connectedNodes = Tasks.await(Wearable.getNodeClient(this).connectedNodes)
            connectedNodes.any { it.id == sourceNodeId }
        } catch (e: Exception) {
            Log.w("YataWear", "isFromConnectedNode check failed, denying by default", e)
            false
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
