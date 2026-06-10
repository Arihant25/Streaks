package com.arihant.streaks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.StreakRepository

/**
 * Fires when a reminder alarm goes off. Always schedules the next occurrence first so the chain
 * survives any failure below, then posts the notification — unless notifications are disabled,
 * permission is missing, or the habit is already done for the current period.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val streakId = intent.getStringExtra(EXTRA_STREAK_ID) ?: return

        val repository = StreakRepository.getInstance(context)
        repository.ensureLoaded()

        val streak = repository.findStreak(streakId) ?: return
        val reminder = streak.reminder ?: return

        ReminderScheduler(context).scheduleNext(streakId, reminder)

        if (!SettingsStore.notificationsEnabledBlocking(context)) return
        if (!Notifications.canPost(context)) return
        if (repository.isCurrentPeriodSatisfied(streak)) return

        Notifications.showReminder(context, streak)
    }

    companion object {
        const val ACTION_REMINDER = "com.arihant.streaks.action.REMINDER"
        const val EXTRA_STREAK_ID = "extra_streak_id"
    }
}
