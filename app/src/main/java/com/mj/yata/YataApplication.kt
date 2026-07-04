package com.mj.yata

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@HiltAndroidApp
class YataApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                Log.e("YataCrash", "Uncaught exception on ${thread.name}:\n$sw")
                File(filesDir, "last_crash.txt").writeText(sw.toString())
            } catch (_: Throwable) {
                // Never let crash logging itself throw during a crash.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
