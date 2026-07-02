package com.arihant.streaks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.arihant.streaks.data.StreakRepository

/**
 * Handles the "Mark done" action on reminder notifications so a streak can be
 * completed without opening the app.
 */
class CompleteStreakReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.arihant.streaks.MARK_DONE"
        const val EXTRA_STREAK_ID = "streak_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return
        val streakId = intent.getStringExtra(EXTRA_STREAK_ID) ?: return

        // The app process may be dead, so hydrate the repository from disk first
        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(context)
        repository.completeStreak(streakId, context)

        NotificationManagerCompat.from(context)
                .cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }
}
