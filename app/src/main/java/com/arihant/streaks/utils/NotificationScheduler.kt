package com.arihant.streaks.utils

import android.content.Context
import androidx.work.*
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.workers.ReminderWorker
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * NotificationScheduler handles reliable notification scheduling using WorkManager.
 *
 * This replaces the previous AlarmManager-based approach to provide:
 * - Better reliability on modern Android versions (API 23+)
 * - Automatic handling of Doze mode and App Standby
 * - Persistence across device reboots
 * - Battery optimization resilience
 * - Exact timing when possible with proper permissions
 */
class NotificationScheduler(private val context: Context) {

    companion object {
        private const val REMINDER_WORK_PREFIX = "reminder_work_"
    }

    fun scheduleReminder(streakId: String, streakName: String, reminder: Reminder) {
        // Cancel existing reminders for this streak
        cancelReminder(streakId)

        val time = LocalTime.parse(reminder.time)

        if (reminder.days.isNotEmpty()) {
            // Schedule for specific days
            for (day in reminder.days) {
                scheduleWeeklyReminder(streakId, streakName, time, day, reminder)
            }
        } else {
            // Schedule daily
            scheduleDailyReminder(streakId, streakName, time, reminder)
        }
    }

    private fun scheduleDailyReminder(
            streakId: String,
            streakName: String,
            time: LocalTime,
            reminder: Reminder
    ) {
        val now = LocalDateTime.now()
        var triggerTime = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)

        // If the time has passed today, schedule for tomorrow
        if (triggerTime.isBefore(now)) {
            triggerTime = triggerTime.plusDays(1)
        }

        val delayMillis =
                triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        System.currentTimeMillis()

        val inputData =
                Data.Builder()
                        .putString(ReminderWorker.STREAK_ID_KEY, streakId)
                        .putString(ReminderWorker.STREAK_NAME_KEY, streakName)
                        .putString(
                                ReminderWorker.REMINDER_TEXT_KEY,
                                "Time to work on your $streakName streak!"
                        )
                        .build()

        val workRequest =
                PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .setInputData(inputData)
                        .addTag("${REMINDER_WORK_PREFIX}${streakId}_daily")
                        .setConstraints(
                                Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                        .setRequiresBatteryNotLow(false)
                                        .setRequiresCharging(false)
                                        .setRequiresDeviceIdle(false)
                                        .build()
                        )
                        .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    private fun scheduleWeeklyReminder(
            streakId: String,
            streakName: String,
            time: LocalTime,
            dayOfWeek: Int,
            reminder: Reminder
    ) {
        val now = LocalDateTime.now()
        var triggerDate = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)

        // Calculate days until the target day (0=Monday, 6=Sunday)
        val currentDayOfWeek = (now.dayOfWeek.value + 6) % 7 // Convert to 0=Monday
        var daysUntil = (dayOfWeek - currentDayOfWeek + 7) % 7

        // If it's the same day but time has passed, schedule for next week
        if (daysUntil == 0 && triggerDate.isBefore(now)) {
            daysUntil = 7
        }

        triggerDate = triggerDate.plusDays(daysUntil.toLong())

        val delayMillis =
                triggerDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        System.currentTimeMillis()

        val inputData =
                Data.Builder()
                        .putString(ReminderWorker.STREAK_ID_KEY, streakId)
                        .putString(ReminderWorker.STREAK_NAME_KEY, streakName)
                        .putString(
                                ReminderWorker.REMINDER_TEXT_KEY,
                                "Time to work on your $streakName streak!"
                        )
                        .build()

        val workRequest =
                PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .setInputData(inputData)
                        .addTag("${REMINDER_WORK_PREFIX}${streakId}_$dayOfWeek")
                        .setConstraints(
                                Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                        .setRequiresBatteryNotLow(false)
                                        .setRequiresCharging(false)
                                        .setRequiresDeviceIdle(false)
                                        .build()
                        )
                        .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelReminder(streakId: String) {
        // Cancel all work with tags containing this streak ID
        WorkManager.getInstance(context)
                .cancelAllWorkByTag("${REMINDER_WORK_PREFIX}${streakId}_daily")

        // Cancel weekly reminders for all days
        for (day in 0..6) {
            WorkManager.getInstance(context)
                    .cancelAllWorkByTag("${REMINDER_WORK_PREFIX}${streakId}_$day")
        }
    }

    fun cancelAllReminders() {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
