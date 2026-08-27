package com.mj.yata

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mj.yata.notification.DailyAgendaWorker
import com.mj.yata.notification.OverdueEscalationWorker
import com.mj.yata.util.SecurityRedactor
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

import com.mj.yata.util.AppClock
import com.mj.yata.util.AppFormats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

@HiltAndroidApp
class YataApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var userPreferences: com.mj.yata.data.local.datastore.UserPreferences
    @Inject lateinit var crashLogStore: com.mj.yata.data.local.crash.CrashLogStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Bouncy Castle registration for SFTP used to live here. It now happens lazily on the
        // first SFTP connection instead — see BouncyCastleSupport for why both the timing and the
        // provider position mattered.

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                Log.e("YataCrash", "Uncaught exception on ${thread.name}:\n${SecurityRedactor.redact(sw.toString())}")
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

        // The single writer for AppClock.today — see that object's KDoc for why every screen
        // reading LocalDate.now() into a keyless `remember` needed one. Sleeping until the next
        // local midnight and looping (rather than a fixed-interval poll) is what catches the
        // rollover even if the app is never backgrounded across it; MainActivity's onStart refresh
        // is the belt for the case where the process wasn't alive to run this loop at all.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            while (true) {
                val now = LocalDateTime.now()
                val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
                delay(untilMidnight.toMillis().coerceAtLeast(1000L))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    AppClock.refresh()
                }
                com.mj.yata.widget.WidgetRefresher.refreshAll(this@YataApplication)
            }
        }

        // The single writer for AppClock.minute, driving the due-date countdown. Unlike the
        // midnight loop above this is gated on the setting: it wakes 1440x a day rather than once,
        // which is not worth doing at all when nothing is reading the value. collectLatest cancels
        // the running loop the moment the toggle flips off.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                userPreferences.dueCountdownEnabledFlow.collectLatest { enabled ->
                    if (!enabled) return@collectLatest
                    while (true) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            AppClock.refreshMinute()
                        }
                        // Sleep to the next minute boundary rather than a flat 60s, so the
                        // displayed value changes when the clock's minute actually changes
                        // instead of drifting up to a minute behind it.
                        val now = LocalDateTime.now()
                        val nextMinute = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusMinutes(1)
                        delay(Duration.between(now, nextMinute).toMillis().coerceAtLeast(1000L))
                    }
                }
            } catch (t: Throwable) {
                Log.e("YataApplication", "Failed to observe due-countdown preference", t)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN and above means the app has nothing on screen — the avatar cache exists to
        // save re-decoding bitmaps between recompositions of a visible screen, which isn't a
        // benefit for a process the system may be about to reclaim memory from anyway.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.mj.yata.ui.widgets.trimAvatarCache()
        }
    }

    private suspend fun reconcileBackgroundJobs() {
        // One scheduled backup covering every enabled destination, replacing the four that used to
        // run per destination. Cancelling the old unique work names first is what stops their
        // already-enqueued periodic jobs from continuing to fire after an upgrade — without it a
        // device would back everything up on five overlapping schedules.
        com.mj.yata.data.backup.UnifiedBackupWorker.cancelLegacyWorkers(this)
        val backupInterval = userPreferences.backupIntervalMinutesFlow.first()
        com.mj.yata.data.backup.UnifiedBackupWorker.schedule(
            this, backupInterval, androidx.work.ExistingPeriodicWorkPolicy.KEEP
        )

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

        // Not user-controllable, so no cancel branch — this repaints widget content that would
        // otherwise stay stale for a full day if the process dies before any task write happens
        // to trigger WidgetUpdater's own refresh (see WidgetDailyRefreshWorker's KDoc).
        com.mj.yata.widget.WidgetDailyRefreshWorker.schedule(this)
    }
}
