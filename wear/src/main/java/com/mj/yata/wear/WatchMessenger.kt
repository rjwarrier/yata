package com.mj.yata.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

private const val TOGGLE_TASK_PATH = "/toggle_task"
private const val QUICK_ADD_TASK_PATH = "/quick_add_task"

/** Sends commands back to the phone — checking a task off, or adding a new one — via the
 * Wearable MessageClient. Best-effort: if the phone isn't reachable the call just no-ops, same
 * as the rest of the sync in this module. */
object WatchMessenger {
    fun sendToggle(context: Context, taskId: String) = send(context, TOGGLE_TASK_PATH, taskId)

    fun sendQuickAdd(context: Context, title: String) = send(context, QUICK_ADD_TASK_PATH, title)

    private fun send(context: Context, path: String, payload: String) {
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d("YataWear", "send($path): connected nodes = ${nodes.map { it.id + "/" + it.displayName }}")
            val nodeId = nodes.firstOrNull()?.id
            if (nodeId == null) {
                Log.d("YataWear", "send($path): no connected node, aborting")
                return
            }
            Tasks.await(Wearable.getMessageClient(context).sendMessage(nodeId, path, payload.toByteArray()))
            Log.d("YataWear", "send($path): message sent to $nodeId, payload='$payload'")
        } catch (e: Exception) {
            Log.e("YataWear", "send($path): failed", e)
        }
    }
}
