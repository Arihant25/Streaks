package com.arihant.streaks.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.arihant.streaks.R
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.data.settingsDataStore
import com.arihant.streaks.receivers.CompleteStreakReceiver
import com.arihant.streaks.utils.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when a streak's reminder alarm goes off: re-arms the alarm for the
 * next occurrence, then shows the notification if reminders are enabled.
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "streak_reminder_channel"
        private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationScheduler.ACTION_REMINDER) return
        val streakId = intent.getStringExtra(NotificationScheduler.EXTRA_STREAK_ID) ?: return
        val streakName =
                intent.getStringExtra(NotificationScheduler.EXTRA_STREAK_NAME) ?: "Your Streak"
        val time = intent.getStringExtra(NotificationScheduler.EXTRA_TIME) ?: return
        val days = intent.getIntArrayExtra(NotificationScheduler.EXTRA_DAYS) ?: intArrayOf()

        // Re-arm first so a failure below can't break the chain of reminders
        NotificationScheduler(context)
                .scheduleReminder(streakId, streakName, Reminder(time, days.toList()))

        // The DataStore read must not block onReceive; goAsync keeps the
        // process alive until finish()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled =
                        context.settingsDataStore.data.first()[NOTIFICATIONS_KEY] ?: false
                if (enabled && hasNotificationPermission(context)) {
                    createNotificationChannel(context)
                    showNotification(context, streakId, streakName)
                }
            } catch (e: Exception) {
                // Never crash the process over a reminder, but leave a trace
                android.util.Log.w("ReminderReceiver", "Failed to show reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel(context: Context) {
        val channel =
                NotificationChannel(
                                NOTIFICATION_CHANNEL_ID,
                                "Streak Reminders",
                                NotificationManager.IMPORTANCE_HIGH
                        )
                        .apply {
                            description = "Notifications for streak reminders"
                            enableVibration(true)
                            enableLights(true)
                        }
        context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
    }

    private fun showNotification(context: Context, streakId: String, streakName: String) {
        val notificationId = streakId.hashCode()

        // "Mark done" action completes the streak without opening the app
        val markDoneIntent =
                Intent(context, CompleteStreakReceiver::class.java).apply {
                    action = CompleteStreakReceiver.ACTION_MARK_DONE
                    putExtra(CompleteStreakReceiver.EXTRA_STREAK_ID, streakId)
                    putExtra(CompleteStreakReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
        val markDonePendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        notificationId,
                        markDoneIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

        val notification =
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_24)
                        .setContentTitle("Streak Reminder: $streakName")
                        .setContentText("Time to work on your $streakName streak!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setVibrate(longArrayOf(0, 250, 250, 250))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .addAction(R.drawable.ic_check_24, "Mark done", markDonePendingIntent)
                        .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the notify; ignore
        }
    }
}
