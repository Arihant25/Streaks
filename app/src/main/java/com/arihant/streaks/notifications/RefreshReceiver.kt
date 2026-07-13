package com.arihant.streaks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.arihant.streaks.data.StreakRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps derived state fresh outside of normal UI flow:
 * - after boot and app updates: alarms are wiped by the system, so reschedule them
 * - after time/timezone changes: alarm wall-clock times are stale
 * - just after midnight: streak counters, "done today" flags and the widget must roll over
 *   (negative habits literally grow at midnight, so this is what makes them tick)
 *
 * Each run reloads + recalculates all streaks (which also refreshes the widget), reschedules
 * every reminder and arms the next after-midnight refresh.
 */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_DAILY_REFRESH -> refresh(context)
        }
    }

    private fun refresh(context: Context) {
        val repository = StreakRepository.getInstance()
        repository.ensureLoaded(context)
        repository.recalculateAllStreaks(context)
        ReminderScheduler(context).rescheduleAll(repository.streaks.value ?: emptyList())
        scheduleNextDailyRefresh(context)
        // Receiver-only processes die right after onReceive — flush the recalculated state
        repository.flushPendingWrites()
    }

    companion object {
        const val ACTION_DAILY_REFRESH = "com.arihant.streaks.action.DAILY_REFRESH"

        /** Arms an inexact alarm shortly after the next midnight. Safe to call repeatedly. */
        fun scheduleNextDailyRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RefreshReceiver::class.java).apply {
                action = ACTION_DAILY_REFRESH
                data = Uri.parse("streaks://daily-refresh")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli()
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pendingIntent)
        }
    }
}
