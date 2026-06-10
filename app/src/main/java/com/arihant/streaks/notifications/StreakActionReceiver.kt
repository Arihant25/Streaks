package com.arihant.streaks.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arihant.streaks.data.StreakRepository

/**
 * Handles quick actions that complete a streak without opening the app:
 * the "Mark as done" notification button and taps on widget columns.
 */
class StreakActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val streakId = intent.getStringExtra(EXTRA_STREAK_ID) ?: return
        val repository = StreakRepository.getInstance(context)
        repository.ensureLoaded()
        val streak = repository.findStreak(streakId) ?: return

        when (intent.action) {
            ACTION_COMPLETE -> {
                repository.completeStreak(streakId)
                Notifications.cancelReminder(context, streakId)
            }
            ACTION_TOGGLE -> {
                if (streak.isCompletedToday) {
                    repository.uncompleteStreak(streakId)
                } else {
                    repository.completeStreak(streakId)
                    Notifications.cancelReminder(context, streakId)
                }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.arihant.streaks.action.COMPLETE"
        const val ACTION_TOGGLE = "com.arihant.streaks.action.TOGGLE"
        const val EXTRA_STREAK_ID = "extra_streak_id"

        fun completePendingIntent(context: Context, streakId: String): PendingIntent =
            pendingIntent(context, streakId, ACTION_COMPLETE, "complete")

        fun togglePendingIntent(context: Context, streakId: String): PendingIntent =
            pendingIntent(context, streakId, ACTION_TOGGLE, "toggle")

        private fun pendingIntent(
            context: Context,
            streakId: String,
            action: String,
            uriPrefix: String
        ): PendingIntent {
            val intent = Intent(context, StreakActionReceiver::class.java).apply {
                this.action = action
                data = Notifications.uniqueData(uriPrefix, streakId)
                putExtra(EXTRA_STREAK_ID, streakId)
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
