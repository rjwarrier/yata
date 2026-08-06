package com.mj.yata.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import com.mj.yata.data.local.crash.CrashLogStore
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Broadcast receivers run outside the ViewModel/UI safety net. A thrown exception here can kill
 * the process from a notification tap, boot event, or alarm delivery, so receiver entry points use
 * this tiny envelope: log locally, record a handled Diagnostics report, and never skip finish().
 */
inline fun BroadcastReceiver.onReceiveSafely(
    context: Context,
    tag: String,
    operationId: String? = null,
    block: () -> Unit
) {
    try {
        block()
    } catch (t: Throwable) {
        recordBackgroundFailure(context, tag, operationId, t)
    }
}

fun BroadcastReceiver.goAsyncSafely(
    context: Context,
    tag: String,
    operationId: String? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (t: Throwable) {
            recordBackgroundFailure(context, tag, operationId, t)
        } finally {
            pendingResult.finish()
        }
    }
}

fun recordBackgroundFailure(
    context: Context,
    tag: String,
    operationId: String?,
    throwable: Throwable
) {
    Log.e(tag, "Handled background failure", throwable)
    val appContext = context.applicationContext
    CrashLogStore(appContext).record(throwable, tag, fatal = false)
    if (operationId != null) {
        OperationHistoryStore(appContext).recordFailure(operationId, throwable, "$tag failed")
    }
}

suspend inline fun CoroutineWorker.runOperationSafely(
    operationHistoryStore: OperationHistoryStore,
    operationId: String,
    tag: String,
    runReason: String,
    fallback: ListenableWorker.Result = ListenableWorker.Result.retry(),
    block: suspend () -> ListenableWorker.Result
): ListenableWorker.Result {
    operationHistoryStore.recordRun(operationId, runReason)
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        recordBackgroundFailure(applicationContext, tag, operationId, t)
        fallback
    }
}
