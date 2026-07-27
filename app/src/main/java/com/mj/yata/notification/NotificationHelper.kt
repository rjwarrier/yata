package com.mj.yata.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mj.yata.MainActivity
import com.mj.yata.R

object NotificationHelper {
    // v2: dropped to IMPORTANCE_DEFAULT (was HIGH) so the actions row doesn't get collapsed into
    // a single auto-promoted round icon button — that's tied to heads-up/HIGH-importance
    // rendering, not something the app draws. Channel importance is immutable once created, so a
    // new id was needed rather than just changing the old channel's constant.
    const val REMINDER_CHANNEL_ID = "task_reminders_channel_v2"
    const val ESCALATION_CHANNEL_ID = "overdue_escalation_channel"
    const val ESCALATION_NOTIFICATION_ID = 900001
    const val AGENDA_CHANNEL_ID = "daily_agenda_channel"
    const val DAILY_AGENDA_NOTIFICATION_ID = 900002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for your due tasks"
                enableVibration(true)
            }

            val escalationChannel = NotificationChannel(
                ESCALATION_CHANNEL_ID,
                "Team Overdue Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily check for teammates' overdue tasks"
            }

            val agendaChannel = NotificationChannel(
                AGENDA_CHANNEL_ID,
                "Daily Agenda",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A once-a-morning summary of what's due today"
            }

            nm.createNotificationChannels(listOf(reminderChannel, escalationChannel, agendaChannel))
        }
    }

    /** One grouped notification summarizing which people have tasks overdue past the escalation
     * threshold — tapping it opens the People tab rather than any single task, since this is a
     * cross-person summary, not a per-task reminder. */
    fun buildEscalationNotification(
        context: Context,
        accentColor: Int,
        lines: List<String>
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context, ESCALATION_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "people")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }

        return NotificationCompat.Builder(context, ESCALATION_CHANNEL_ID)
            .setContentTitle("Overdue tasks in your team")
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(accentColor)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /** Build a reminder notification with Complete and Snooze actions. No `setLargeIcon` — on
     * the classic (non-BigPicture) notification layout that draws a large circular image at the
     * notification's trailing edge, which reads as a stray "extra icon" rather than a deliberate
     * avatar/photo here, so it's better left off. `setAllowSystemGeneratedContextualActions(false)`
     * stays regardless, so Android's own inferred actions don't stack on top of our explicit ones.
     * [accentColor] should be the app's current effective M3 primary color — see
     * [com.mj.yata.widget.resolveNotificationAccentColor] — not a hardcoded value, so the
     * notification icon matches whatever theme the user is on. */
    fun buildReminderNotification(
        context: Context,
        taskId: String,
        taskTitle: String,
        accentColor: Int
    ): Notification {
        val notifId = taskId.hashCode()

        val openIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "task_detail")
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, NotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_reminder_title, taskTitle))
            // getString, not stringResource: notifications are built outside composition.
            .setContentText(context.getString(R.string.notification_helper_this_task_is_due))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(accentColor)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.notification_action_complete),
                actionPendingIntent(NotificationActionReceiver.ACTION_COMPLETE_TASK, notifId + 1000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.notification_action_snooze_15m),
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_15M, notifId + 2000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.notification_action_snooze_1h),
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_TASK, notifId + 3000)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.notification_action_snooze_tomorrow),
                actionPendingIntent(NotificationActionReceiver.ACTION_SNOOZE_TOMORROW, notifId + 4000)
            )
            .build()
    }

    /** Once-a-morning "what's due today" summary — total count plus a per-person breakdown line,
     * tapping opens Today rather than any single task. */
    fun buildDailyAgendaNotification(
        context: Context,
        accentColor: Int,
        totalDueToday: Int,
        lines: List<String>
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context, DAILY_AGENDA_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("shortcut_action", "today")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }

        return NotificationCompat.Builder(context, AGENDA_CHANNEL_ID)
            .setContentTitle("$totalDueToday task${if (totalDueToday == 1) "" else "s"} due today")
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(accentColor)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
