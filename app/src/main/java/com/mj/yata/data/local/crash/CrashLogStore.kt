package com.mj.yata.data.local.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.mj.yata.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One stored report. [id] is the file name; [body] is read separately so a long list stays cheap. */
data class CrashLogEntry(
    val id: String,
    val timestampMillis: Long,
    /** First line of the throwable — the exception class and message. */
    val summary: String,
    /** False for a failure that was caught and reported rather than one that killed the process. */
    val fatal: Boolean
)

/**
 * On-device crash history, written to `filesDir/crash_logs/`.
 *
 * Everything here is plain synchronous file IO with no coroutines, no Room and no DataStore,
 * because the main caller is the uncaught-exception handler: by the time it runs the process is
 * already going down, and anything that defers work to another thread or scope would simply never
 * complete. It also means [record] must never throw — a crash reporter that crashes replaces a
 * useful stack trace with a useless one — so every entry point swallows its own failures.
 */
@Singleton
class CrashLogStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dir: File get() = File(context.filesDir, DIR_NAME)

    /**
     * Appends a report. [fatal] false records something that was caught and handled (see
     * MainViewModel.safeLaunch) — those no longer kill the app, which is exactly why they need
     * recording: without this they are invisible outside logcat.
     */
    fun record(throwable: Throwable, threadName: String, fatal: Boolean) {
        try {
            if (!dir.exists() && !dir.mkdirs()) return

            val now = System.currentTimeMillis()
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val report = buildString {
                appendLine("Time:      ${fileStamp(now)}")
                appendLine("Type:      ${if (fatal) "Crash (uncaught)" else "Handled failure"}")
                appendLine("Thread:    $threadName")
                appendLine("App:       ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android:   ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device:    ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                append(stackTrace)
            }

            File(dir, "${if (fatal) "crash" else "handled"}_$now.txt").writeText(report)

            // Kept alongside the numbered files purely so the long-standing
            // `adb shell run-as com.mj.yata cat files/last_crash.txt` still works.
            if (fatal) File(context.filesDir, "last_crash.txt").writeText(report)

            trimToLimit()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not record crash log", t)
        }
    }

    /** Newest first. Cheap — reads file names and lengths, never file contents. */
    fun list(): List<CrashLogEntry> = try {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            .orEmpty()
            .mapNotNull { file ->
                val fatal = file.name.startsWith("crash_")
                val millis = file.name.removePrefix(if (fatal) "crash_" else "handled_")
                    .removeSuffix(".txt")
                    .toLongOrNull() ?: return@mapNotNull null
                CrashLogEntry(
                    id = file.name,
                    timestampMillis = millis,
                    summary = summaryOf(file),
                    fatal = fatal
                )
            }
            .sortedByDescending { it.timestampMillis }
    } catch (t: Throwable) {
        Log.e(TAG, "Could not list crash logs", t)
        emptyList()
    }

    fun read(id: String): String = try {
        File(dir, id).takeIf { it.isFile }?.readText().orEmpty()
    } catch (t: Throwable) {
        Log.e(TAG, "Could not read crash log $id", t)
        ""
    }

    fun delete(id: String) {
        try {
            File(dir, id).takeIf { it.isFile }?.delete()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not delete crash log $id", t)
        }
    }

    fun clear() {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not clear crash logs", t)
        }
    }

    /** The exception line, skipping the header block written above it. */
    private fun summaryOf(file: File): String = try {
        file.useLines { lines ->
            lines.firstOrNull { it.isNotBlank() && !it.startsWith(" ") && !HEADER_KEYS.any(it::startsWith) }
        }?.trim().orEmpty()
    } catch (t: Throwable) {
        ""
    }

    /**
     * Bounds disk use. A crash loop can write a report every few seconds, and this directory is
     * never cleaned by anything else; the newest reports are the ones worth keeping.
     */
    private fun trimToLimit() {
        val files = dir.listFiles()?.sortedByDescending { it.name } ?: return
        files.drop(MAX_LOGS).forEach { it.delete() }
    }

    private fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    companion object {
        private const val TAG = "CrashLogStore"
        private const val DIR_NAME = "crash_logs"
        private const val MAX_LOGS = 30
        private val HEADER_KEYS = listOf("Time:", "Type:", "Thread:", "App:", "Android:", "Device:")
    }
}
