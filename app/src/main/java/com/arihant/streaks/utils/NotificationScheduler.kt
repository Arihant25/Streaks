package com.arihant.streaks.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.notifications.ReminderReceiver
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules reminder notifications with AlarmManager.
 *
 * One alarm per streak, always set for the next occurrence and re-armed by
 * [ReminderReceiver] when it fires. Exact alarms fire on time even in Doze;
 * WorkManager periodic work (the previous approach) drifts and gets deferred,
 * so a "remind me at 21:00" arrived minutes to hours late.
 */
class NotificationScheduler(private val context: Context) {

    companion object {
        const val ACTION_REMINDER = "com.arihant.streaks.REMINDER_ALARM"
        const val EXTRA_STREAK_ID = "streak_id"
        const val EXTRA_STREAK_NAME = "streak_name"
        const val EXTRA_TIME = "time"
        const val EXTRA_DAYS = "days"

        /**
         * Next occurrence of [time] strictly after [now], restricted to [days]
         * (0=Monday..6=Sunday) when non-empty.
         */
        fun nextTrigger(now: LocalDateTime, time: LocalTime, days: List<Int>): LocalDateTime {
            var candidate = now.toLocalDate().atTime(time)
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
            if (days.isEmpty()) return candidate
            repeat(7) {
                val dayIndex = (candidate.dayOfWeek.value + 6) % 7 // 0=Monday
                if (dayIndex in days) return candidate
                candidate = candidate.plusDays(1)
            }
            return candidate
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(streakId: String, streakName: String, reminder: Reminder) {
        val next =
                nextTrigger(LocalDateTime.now(), LocalTime.parse(reminder.time), reminder.days)
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = reminderIntent(streakId, streakName, reminder)
        // Exact when the user has allowed it (Settings prompts for it); the
        // while-idle fallback still beats deferred periodic work by hours
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            )
        }
    }

    fun cancelReminder(streakId: String) {
        alarmManager.cancel(reminderIntent(streakId, "", null))
    }

    /**
     * The streak id rides in both the data URI and the request code so each
     * streak keeps exactly one alarm: scheduling replaces, cancel matches.
     */
    private fun reminderIntent(
            streakId: String,
            streakName: String,
            reminder: Reminder?
    ): PendingIntent {
        val intent =
                Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_REMINDER
                    data = Uri.parse("streaks://reminder/$streakId")
                    putExtra(EXTRA_STREAK_ID, streakId)
                    putExtra(EXTRA_STREAK_NAME, streakName)
                    reminder?.let {
                        putExtra(EXTRA_TIME, it.time)
                        putExtra(EXTRA_DAYS, it.days.toIntArray())
                    }
                }
        return PendingIntent.getBroadcast(
                context,
                streakId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
