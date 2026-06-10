package com.arihant.streaks.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arihant.streaks.MainActivity
import com.arihant.streaks.R
import com.arihant.streaks.data.Streak

/** Builds and posts all app notifications; owns the notification channel. */
object Notifications {

    const val CHANNEL_ID = "streak_reminder_channel"
    private const val TEST_NOTIFICATION_ID = 12345

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Streak Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for streak reminders"
            enableVibration(true)
            enableLights(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun reminderNotificationId(streakId: String): Int = streakId.hashCode()

    fun showReminder(context: Context, streak: Streak) {
        ensureChannel(context)
        val notificationId = reminderNotificationId(streak.id)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_STREAK_ID, streak.id)
        }
        val tapPending = PendingIntent.getActivity(
            context, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePending = StreakActionReceiver.completePendingIntent(context, streak.id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle("${streak.emoji} ${streak.name}")
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPending)
            .addAction(0, context.getString(R.string.mark_as_done), donePending)
            .build()

        notify(context, notificationId, notification)
    }

    fun cancelReminder(context: Context, streakId: String) {
        NotificationManagerCompat.from(context).cancel(reminderNotificationId(streakId))
    }

    fun showTest(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle("Test Notification")
            .setContentText("This is a test notification to verify that notifications are working!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        notify(context, TEST_NOTIFICATION_ID, notification)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and post — nothing useful to do.
        }
    }

    /** Stable broadcast intent helper so PendingIntents stay unique per streak. */
    fun uniqueData(prefix: String, streakId: String): Uri = Uri.parse("streaks://$prefix/$streakId")
}
