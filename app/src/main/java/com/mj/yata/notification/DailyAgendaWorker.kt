package com.mj.yata.notification

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.widget.resolveWidgetTheme
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

        val dueToday = tasks.filter { !it.done && it.due == today }
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
        val accentColor = resolveWidgetTheme(applicationContext).colorScheme.primary.toArgb()
        val notification = NotificationHelper.buildDailyAgendaNotification(applicationContext, accentColor, dueToday.size, lines)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationHelper.DAILY_AGENDA_NOTIFICATION_ID, notification)

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_agenda"
        private val TARGET_TIME: LocalTime = LocalTime.of(7, 30)

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = LocalDateTime.of(now.toLocalDate(), TARGET_TIME)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun)

            val request = PeriodicWorkRequestBuilder<DailyAgendaWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
