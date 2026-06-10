package com.arihant.streaks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.data.Streak
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules reminder notifications with AlarmManager.
 *
 * Each streak has at most ONE pending alarm — the next occurrence of its reminder. When the alarm
 * fires, [ReminderReceiver] posts the notification and schedules the following occurrence, so the
 * chain never drifts. Exact alarms are used when the user has allowed them; otherwise the alarm
 * still fires in Doze, just with system batching.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNext(streakId: String, reminder: Reminder) {
        val triggerAtMillis = nextTriggerMillis(reminder) ?: return
        val pendingIntent = reminderPendingIntent(streakId)
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Exact alarm permission revoked mid-flight — fall back to inexact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(streakId: String) {
        val pendingIntent = reminderPendingIntent(streakId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Idempotent self-healing pass: one alarm per streak that has a reminder. */
    fun rescheduleAll(streaks: List<Streak>) {
        streaks.forEach { streak ->
            val reminder = streak.reminder
            if (reminder != null) scheduleNext(streak.id, reminder) else cancel(streak.id)
        }
    }

    /**
     * Next occurrence of the reminder strictly after now.
     * Day indices are 0=Monday … 6=Sunday; an empty list means every day.
     */
    private fun nextTriggerMillis(reminder: Reminder): Long? {
        val time = runCatching { LocalTime.parse(reminder.time) }.getOrNull() ?: return null
        val days = reminder.days.filter { it in 0..6 }.ifEmpty { (0..6).toList() }
        val now = LocalDateTime.now()
        for (offset in 0L..7L) {
            val date = LocalDate.now().plusDays(offset)
            val dayIndex = date.dayOfWeek.value - 1 // Mon=1..Sun=7 → 0..6
            if (dayIndex !in days) continue
            val candidate = date.atTime(time)
            if (candidate.isAfter(now)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun reminderPendingIntent(streakId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            data = Notifications.uniqueData("reminder", streakId)
            putExtra(ReminderReceiver.EXTRA_STREAK_ID, streakId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
