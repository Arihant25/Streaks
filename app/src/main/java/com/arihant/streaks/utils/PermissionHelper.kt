package com.arihant.streaks.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PermissionHelper {

    companion object {
        fun checkExactAlarmPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        }

        fun requestExactAlarmPermission(fragment: Fragment) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle("Exact Alarms Permission")
                        .setMessage(
                                "This app needs permission to schedule exact alarms for precise reminder notifications. Please grant this permission in the next screen."
                        )
                        .setPositiveButton("Open Settings") { _, _ ->
                            try {
                                val intent =
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data =
                                                    Uri.parse(
                                                            "package:${fragment.requireContext().packageName}"
                                                    )
                                        }
                                fragment.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to general alarms settings
                                val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data =
                                                    Uri.parse(
                                                            "package:${fragment.requireContext().packageName}"
                                                    )
                                        }
                                fragment.startActivity(intent)
                            }
                        }
                        .setNegativeButton("Skip", null)
                        .show()
            }
        }

        fun showNotificationChannelSettings(fragment: Fragment) {
            try {
                val intent =
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(
                                    Settings.EXTRA_APP_PACKAGE,
                                    fragment.requireContext().packageName
                            )
                            putExtra(Settings.EXTRA_CHANNEL_ID, "streak_reminder_channel")
                        }
                fragment.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app notification settings
                try {
                    val intent =
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        fragment.requireContext().packageName
                                )
                            }
                    fragment.startActivity(intent)
                } catch (e2: Exception) {
                    // Final fallback to app settings
                    val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${fragment.requireContext().packageName}")
                            }
                    fragment.startActivity(intent)
                }
            }
        }
    }
}
