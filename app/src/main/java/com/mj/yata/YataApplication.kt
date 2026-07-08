package com.mj.yata

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mj.yata.data.cloud.CloudBackupWorker
import com.mj.yata.notification.DailyAgendaWorker
import com.mj.yata.notification.OverdueEscalationWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class YataApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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

        // Cheap to enqueue unconditionally — enqueueUniquePeriodicWork(KEEP) is a no-op if
        // already scheduled, and the worker itself checks cloudBackupEnabledFlow before doing
        // anything.
        CloudBackupWorker.schedule(this)
        OverdueEscalationWorker.schedule(this)
        DailyAgendaWorker.schedule(this)
    }
}
