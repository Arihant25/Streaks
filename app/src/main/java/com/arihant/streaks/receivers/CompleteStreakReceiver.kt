package com.arihant.streaks.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.arihant.streaks.data.StreakRepository

/**
 * Handles notification actions so a streak can be updated without opening the app:
 * - [ACTION_MARK_DONE] — "Mark done" on positive reminders and "Yes, I slipped" on negative
 *   check-ins (both record today's completion/slip-up);
 * - [ACTION_DISMISS] — "No, all clean" on negative check-ins: an unmarked day already counts
 *   as clean, so this only clears the notification.
 */
class CompleteStreakReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.arihant.streaks.MARK_DONE"
        const val ACTION_DISMISS = "com.arihant.streaks.DISMISS_CHECK_IN"
        const val EXTRA_STREAK_ID = "streak_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun markDonePendingIntent(
            context: Context,
            streakId: String,
            notificationId: Int
        ): PendingIntent = pendingIntent(context, streakId, notificationId, ACTION_MARK_DONE, "done")

        fun dismissPendingIntent(
            context: Context,
            streakId: String,
            notificationId: Int
        ): PendingIntent = pendingIntent(context, streakId, notificationId, ACTION_DISMISS, "dismiss")

        private fun pendingIntent(
            context: Context,
            streakId: String,
            notificationId: Int,
            action: String,
            uriPrefix: String
        ): PendingIntent {
            val intent = Intent(context, CompleteStreakReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("streaks://$uriPrefix/$streakId")
                putExtra(EXTRA_STREAK_ID, streakId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val streakId = intent.getStringExtra(EXTRA_STREAK_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {
            ACTION_MARK_DONE -> {
                // The app process may be dead, so hydrate the repository from disk first
                val repository = StreakRepository.getInstance()
                repository.ensureLoaded(context)
                repository.completeStreak(streakId, context)
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            ACTION_DISMISS -> {
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
        }
    }
}
