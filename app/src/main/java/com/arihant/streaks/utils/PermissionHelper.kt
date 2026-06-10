package com.arihant.streaks.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.arihant.streaks.notifications.Notifications

object PermissionHelper {

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /** Reminders still fire without this permission, just less punctually. */
    fun requestExactAlarmPermission(fragment: Fragment) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Exact Alarms")
            .setMessage(
                "Allow exact alarms so reminders arrive right on time. " +
                        "Without it, the system may delay them by a few minutes."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    fragment.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${fragment.requireContext().packageName}")
                        }
                    )
                } catch (e: Exception) {
                    fragment.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${fragment.requireContext().packageName}")
                        }
                    )
                }
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    fun showNotificationChannelSettings(fragment: Fragment) {
        val context = fragment.requireContext()
        try {
            fragment.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_ID)
                }
            )
        } catch (e: Exception) {
            try {
                fragment.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            } catch (e2: Exception) {
                fragment.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }
    }
}
