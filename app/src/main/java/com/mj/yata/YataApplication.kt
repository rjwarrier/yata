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

import com.mj.yata.util.AppFormats
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class YataApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var userPreferences: com.mj.yata.data.local.datastore.UserPreferences
    @Inject lateinit var crashLogStore: com.mj.yata.data.local.crash.CrashLogStore

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
                // Kept as history rather than a single overwritten file, and readable in-app from
                // Settings → Crash Logs. The process is already going down here, so the store
                // writes synchronously and swallows its own errors.
                crashLogStore.record(throwable, thread.name, fatal = true)
            } catch (_: Throwable) {
                // Never let crash logging itself throw during a crash.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Background-job reconciliation is best-effort: it reads DataStore (which throws IOException
        // on a corrupted prefs file) and enqueues WorkManager jobs (which throws if the process is
        // being shut down mid-enqueue). Uncaught, either would kill the app during onCreate on
        // *every* launch, with no way back short of clearing app data — and none of this work is
        // needed for the app to be usable. Log and carry on; the next start retries all of it.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                reconcileBackgroundJobs()
            } catch (t: Throwable) {
                Log.e("YataApplication", "Failed to reconcile background jobs on startup", t)
            }
        }

        // The single writer for AppFormats. It lives here rather than in an Activity because
        // widgets, notification receivers and workers format dates too, and they run with no
        // Activity alive. Writes are on the main thread since the values are Compose state.
        // Best-effort for the same reason as above: a corrupt prefs file must not kill startup,
        // and the formats simply stay at their system-following defaults if it does.
        AppFormats.updateSystemClock(android.text.format.DateFormat.is24HourFormat(this))
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                combine(
                    userPreferences.timeFormatFlow,
                    userPreferences.dateFormatFlow
                ) { time, date -> time to date }.collect { (time, date) ->
                    AppFormats.update(time, date)
                }
            } catch (t: Throwable) {
                Log.e("YataApplication", "Failed to observe date/time format preferences", t)
            }
        }
    }

    private suspend fun reconcileBackgroundJobs() {
        val wifiOnly = userPreferences.cloudBackupWifiOnlyFlow.first()
        val interval = userPreferences.cloudBackupIntervalMinutesFlow.first()
        CloudBackupWorker.schedule(this, interval, androidx.work.ExistingPeriodicWorkPolicy.KEEP, wifiOnly)

        val localInterval = userPreferences.localBackupIntervalMinutesFlow.first()
        LocalBackupWorker.schedule(this, localInterval, androidx.work.ExistingPeriodicWorkPolicy.KEEP)

        // Both notification workers are now user-controllable. Reconciled on every launch so
        // a preference change applies from the next start even though these are scheduled
        // here rather than at the moment the switch is flipped.
        if (userPreferences.overdueNudgesEnabledFlow.first()) {
            OverdueEscalationWorker.schedule(this)
        } else {
            OverdueEscalationWorker.cancel(this)
        }

        if (userPreferences.dailyAgendaEnabledFlow.first()) {
            DailyAgendaWorker.schedule(
                this,
                userPreferences.dailyAgendaHourFlow.first(),
                userPreferences.dailyAgendaMinuteFlow.first()
            )
        } else {
            DailyAgendaWorker.cancel(this)
        }
    }
}
