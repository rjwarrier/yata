package com.mj.yata

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mj.yata.data.cloud.CloudBackupWorker
import com.mj.yata.data.local.backup.LocalBackupWorker
import com.mj.yata.notification.DailyAgendaWorker
import com.mj.yata.notification.OverdueEscalationWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class YataApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var userPreferences: com.mj.yata.data.local.datastore.UserPreferences

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

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val wifiOnly = userPreferences.cloudBackupWifiOnlyFlow.first()
            val interval = userPreferences.cloudBackupIntervalMinutesFlow.first()
            CloudBackupWorker.schedule(this@YataApplication, interval, androidx.work.ExistingPeriodicWorkPolicy.KEEP, wifiOnly)

            val localInterval = userPreferences.localBackupIntervalMinutesFlow.first()
            LocalBackupWorker.schedule(this@YataApplication, localInterval, androidx.work.ExistingPeriodicWorkPolicy.KEEP)

            // Both notification workers are now user-controllable. Reconciled on every launch so
            // a preference change applies from the next start even though these are scheduled
            // here rather than at the moment the switch is flipped.
            if (userPreferences.overdueNudgesEnabledFlow.first()) {
                OverdueEscalationWorker.schedule(this@YataApplication)
            } else {
                OverdueEscalationWorker.cancel(this@YataApplication)
            }

            if (userPreferences.dailyAgendaEnabledFlow.first()) {
                DailyAgendaWorker.schedule(
                    this@YataApplication,
                    userPreferences.dailyAgendaHourFlow.first(),
                    userPreferences.dailyAgendaMinuteFlow.first()
                )
            } else {
                DailyAgendaWorker.cancel(this@YataApplication)
            }
        }
    }
}
