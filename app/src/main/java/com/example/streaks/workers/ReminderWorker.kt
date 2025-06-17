package com.example.streaks.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.streaks.R
import kotlinx.coroutines.flow.first

class ReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val STREAK_ID_KEY = "streak_id"
        const val STREAK_NAME_KEY = "streak_name"
        const val REMINDER_TEXT_KEY = "reminder_text"
        const val NOTIFICATION_CHANNEL_ID = "streak_reminder_channel"
        
        private val Context.dataStore by preferencesDataStore("settings")
        private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
    }

    override suspend fun doWork(): Result {
        return try {
            // Check if notifications are enabled in app settings
            val notificationsEnabled = context.dataStore.data.first()[NOTIFICATIONS_KEY] ?: false
            if (!notificationsEnabled) {
                return Result.success()
            }

            // Check notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!permissionGranted) {
                    return Result.success()
                }
            }

            val streakId = inputData.getString(STREAK_ID_KEY) ?: return Result.failure()
            val streakName = inputData.getString(STREAK_NAME_KEY) ?: "Your Streak"
            val reminderText = inputData.getString(REMINDER_TEXT_KEY) ?: "Time to work on your streak!"

            createNotificationChannel()
            showNotification(streakId, streakName, reminderText)
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Streak Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for streak reminders"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(streakId: String, streakName: String, reminderText: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24) // You'll need to add this icon
            .setContentTitle("Streak Reminder: $streakName")
            .setContentText(reminderText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(streakId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission denied, ignore
        }
    }
}
