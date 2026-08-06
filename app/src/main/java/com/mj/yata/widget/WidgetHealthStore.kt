package com.mj.yata.widget

import android.content.Context

data class WidgetHealth(
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val lastReason: String?
) {
    val stale: Boolean
        get() = lastFailureAt != null && lastFailureAt > (lastSuccessAt ?: 0L)
}

object WidgetHealthStore {
    private const val PREFS = "widget_health"
    private const val SUCCESS_SUFFIX = "_success"
    private const val FAILURE_SUFFIX = "_failure"
    private const val REASON_SUFFIX = "_reason"

    fun read(context: Context, widgetKey: String): WidgetHealth {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val success = prefs.getLong(widgetKey + SUCCESS_SUFFIX, 0L).takeIf { it > 0L }
        val failure = prefs.getLong(widgetKey + FAILURE_SUFFIX, 0L).takeIf { it > 0L }
        val reason = prefs.getString(widgetKey + REASON_SUFFIX, null)
        return WidgetHealth(success, failure, reason)
    }

    fun markSuccess(context: Context, widgetKey: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(widgetKey + SUCCESS_SUFFIX, System.currentTimeMillis())
            .remove(widgetKey + REASON_SUFFIX)
            .apply()
    }

    fun markFailure(context: Context, widgetKey: String, throwable: Throwable) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(widgetKey + FAILURE_SUFFIX, System.currentTimeMillis())
            .putString(widgetKey + REASON_SUFFIX, throwable.message ?: throwable.javaClass.simpleName)
            .apply()
    }
}
