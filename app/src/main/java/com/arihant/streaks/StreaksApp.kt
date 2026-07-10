package com.arihant.streaks

import android.app.Application
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.notifications.Notifications
import com.arihant.streaks.notifications.RefreshReceiver
import com.arihant.streaks.notifications.ReminderScheduler

class StreaksApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)

        // Alarms don't survive reboots, force-stops or app updates on some OEMs — make every
        // process start a self-healing point: reload data, re-arm one alarm per reminder and
        // the after-midnight refresh that rolls streak counters (and the widget) over.
        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(this)
        ReminderScheduler(this).rescheduleAll(repository.streaks.value ?: emptyList())
        RefreshReceiver.scheduleNextDailyRefresh(this)
    }
}
