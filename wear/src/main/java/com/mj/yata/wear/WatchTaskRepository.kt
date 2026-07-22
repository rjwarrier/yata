package com.mj.yata.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class WatchTask(
    val id: String,
    val title: String,
    val done: Boolean,
    val time: String?,
    val due: String? = null
)

/** Local cache of the phone's "today" task list — updated whenever the phone pushes a fresh
 * list, and optimistically patched in between pushes so checkbox taps feel instant. There's no
 * Hilt/DI in this module (kept dependency-free), so this is a plain singleton object instead. */
object WatchTaskRepository {
    private const val PREFS = "yata_wear_prefs"
    private const val KEY_TASKS_JSON = "today_tasks_json"
    private const val KEY_LAST_SYNCED_AT = "last_synced_at"

    val tasks = MutableStateFlow<List<WatchTask>>(emptyList())

    /** Null means "this watch has never received a push from the phone" — distinct from a
     * synced-but-empty list, so the UI can tell "no tasks" apart from "never synced." */
    val lastSyncedAt = MutableStateFlow<Long?>(null)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TASKS_JSON, null)
        tasks.value = json?.let { parse(it) } ?: emptyList()
        lastSyncedAt.value = prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }
    }

    fun replaceAll(context: Context, newTasks: List<WatchTask>) {
        tasks.value = newTasks
        val now = System.currentTimeMillis()
        lastSyncedAt.value = now
        persist(context, newTasks)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNCED_AT, now)
            .apply()
    }

    /** Flips a task's done state immediately in the local cache, ahead of the phone's next full
     * re-push (which will arrive a moment later and reconcile any drift). */
    fun toggleLocally(context: Context, taskId: String) {
        val updated = tasks.value.map { if (it.id == taskId) it.copy(done = !it.done) else it }
        tasks.value = updated
        persist(context, updated)
    }

    private fun persist(context: Context, list: List<WatchTask>) {
        val arr = JSONArray()
        list.forEach { task ->
            arr.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("done", task.done)
                put("time", task.time ?: "")
                put("due", task.due ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TASKS_JSON, arr.toString())
            .apply()
    }

    private fun parse(json: String): List<WatchTask> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WatchTask(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    done = o.getBoolean("done"),
                    time = o.getString("time").takeIf { it.isNotEmpty() },
                    due = o.optString("due").takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
