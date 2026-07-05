package com.mj.yata.wear

import android.content.Context

/** Last known "tasks due today" count, pushed from the phone — cached locally so the
 * complication has something to show immediately on watch boot, before any fresh push arrives. */
object TaskCountStore {
    private const val PREFS = "yata_wear_prefs"
    private const val KEY_COUNT = "today_count"

    fun getCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COUNT, 0)

    fun setCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, count).apply()
    }
}
