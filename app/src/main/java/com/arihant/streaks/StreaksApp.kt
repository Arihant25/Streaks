package com.arihant.streaks

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.notifications.Notifications
import com.arihant.streaks.notifications.RefreshReceiver
import com.arihant.streaks.notifications.ReminderScheduler
import com.google.android.material.color.DynamicColors

class StreaksApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply the saved theme before any activity inflates, so cold starts don't flash
        // the wrong theme (previously the preference was only applied on the settings screen).
        applyTheme(SettingsStore.themeBlocking(this))

        DynamicColors.applyToActivitiesIfAvailable(this)
        Notifications.ensureChannel(this)

        // Warm up data off the main thread, then make alarms self-heal: reschedule every
        // reminder and arm the after-midnight refresh.
        val repository = StreakRepository.getInstance(this)
        repository.ensureLoadedAsync {
            ReminderScheduler(this).rescheduleAll(repository.getStreaks())
            RefreshReceiver.scheduleNextDailyRefresh(this)
        }
    }

    companion object {
        fun applyTheme(theme: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    SettingsStore.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    SettingsStore.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
