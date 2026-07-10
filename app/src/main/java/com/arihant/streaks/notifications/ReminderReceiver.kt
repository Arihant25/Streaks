package com.arihant.streaks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.StreakRepository

/**
 * Fires when a reminder alarm goes off. Always schedules the next occurrence first so the chain
 * survives any failure below, then posts the notification — unless notifications are disabled,
 * permission is missing, or a reminder would be noise:
 * - positive habits: the period's requirement is already met;
 * - negative habits: a slip-up is already marked today (the check-in stays useful while clean).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val streakId = intent.getStringExtra(EXTRA_STREAK_ID) ?: return

        // The app process may be dead, so hydrate the repository from disk first
        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(context)

        val streak = repository.streaks.value?.find { it.id == streakId } ?: return
        val reminder = streak.reminder ?: return

        ReminderScheduler(context).scheduleNext(streakId, reminder)

        if (!SettingsStore.notificationsEnabledBlocking(context)) return
        if (!Notifications.canPost(context)) return
        if (streak.isNegative) {
            if (streak.isCompletedToday) return // already marked the slip-up today
        } else {
            if (repository.isCurrentPeriodSatisfied(streak)) return
        }

        Notifications.showReminder(context, streak)
    }

    companion object {
        const val ACTION_REMINDER = "com.arihant.streaks.action.REMINDER"
        const val EXTRA_STREAK_ID = "extra_streak_id"
    }
}
