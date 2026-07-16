package com.arihant.streaks.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.utils.NotificationScheduler

/**
 * Alarms don't survive a reboot or an app update, and RTC alarms fire at the
 * wrong wall-clock time after a time/timezone change — re-arm them all.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val repository = StreakRepository.getInstance()
                // Fresh process: the repository is empty until loaded from disk
                repository.loadStreaksFromFile(context)
                val scheduler = NotificationScheduler(context)
                for (streak in repository.streaks.value ?: emptyList()) {
                    streak.reminder?.let { reminder ->
                        scheduler.scheduleReminder(streak.id, streak.name, reminder)
                    }
                }
            }
        }
    }
}
