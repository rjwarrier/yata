package com.mj.yata.notification

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.domain.model.isDeferredOn
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.widget.resolveNotificationAccentColor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * A once-a-morning "here's what's due today" digest — yours plus every teammate's, since the
 * owner is the one who needs the whole-team picture. Complements [OverdueEscalationWorker]
 * (which only fires for work that's already slipped) and per-task due reminders (which fire at
 * the task's own reminder time, one at a time, not as a single daily overview).
 */
@HiltWorker
class DailyAgendaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: YataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tasks = repository.getTasks().first()
        val people = repository.getPeople().first()
        val today = LocalDate.now().toString()

        // Deferred tasks drop out — a start date in the future means it isn't actionable yet, so
        // it has no business in a "here's today" digest. Deliberately *not* filtered by
        // isWaitingOn: that rule is about your own day view, and this digest is the whole-team
        // picture, so a task you're waiting on someone for still belongs under their line.
        val dueToday = tasks.filter { !it.done && it.due == today && !it.isDeferredOn(today) }
        if (dueToday.isEmpty()) return Result.success()

        val peopleById = people.associateBy { it.id }
        val lines = mutableListOf<String>()
        val unassigned = dueToday.count { it.assigneeIds.isEmpty() }
        if (unassigned > 0) lines.add("Unassigned: $unassigned")
        people.forEach { person ->
            val count = dueToday.count { person.id in it.assigneeIds }
            if (count > 0) lines.add("${person.name}: $count")
        }

        NotificationHelper.createChannels(applicationContext)
        val accentColor = resolveNotificationAccentColor(applicationContext)
        val notification = NotificationHelper.buildDailyAgendaNotification(applicationContext, accentColor, dueToday.size, lines)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationHelper.DAILY_AGENDA_NOTIFICATION_ID, notification)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_agenda"

        /**
         * [hour]/[minute] come from the user's setting (7:30 by default, the time this was
         * previously hardcoded to). UPDATE rather than KEEP: with KEEP an already-enqueued job
         * wins, so changing the time in Settings would silently never take effect.
         */
        fun schedule(context: Context, hour: Int = 7, minute: Int = 30) {
            val now = LocalDateTime.now()
            var nextRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun)

            val request = PeriodicWorkRequestBuilder<DailyAgendaWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
