package com.example.streaks.ui.home

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.streaks.utils.NotificationScheduler
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.example.streaks.data.Streak
import com.example.streaks.data.Reminder
import com.example.streaks.databinding.FragmentStreakDetailsBinding
import com.example.streaks.ui.dialogs.AddStreakDialog
import com.example.streaks.ui.home.HomeViewModel
import android.app.AlarmManager
import android.app.PendingIntent

class StreakDetailsFragment : Fragment() {
    private val args: StreakDetailsFragmentArgs by navArgs()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private var reminder: Reminder? = null // In-memory for now
    private lateinit var notificationScheduler: NotificationScheduler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val streak = args.streak
        val binding = FragmentStreakDetailsBinding.inflate(inflater, container, false)
        notificationScheduler = NotificationScheduler(requireContext())
        binding.textEmoji.text = streak.emoji
        binding.textName.text = streak.name
        binding.textFrequency.text = formatFrequency(streak.frequency, streak.frequencyCount)
        binding.textCurrentStreak.text = "Current: ${streak.currentStreak}"
        binding.textBestStreak.text = "Best: ${streak.currentStreak}" // TODO: Replace with real best streak

        // --- GitHub-style year graph ---
        val yearGraph = createYearGraphView(streak)
        binding.yearGraphContainer.removeAllViews()
        binding.yearGraphContainer.addView(yearGraph)

        // --- Monthly view ---
        val monthlyView = createMonthlyView(streak)
        binding.monthlyViewContainer.removeAllViews()
        binding.monthlyViewContainer.addView(monthlyView)

        // --- Reminder section ---
        this.reminder = streak.reminder
        updateReminderSummary(binding)
        if (reminder != null) {
            val removeBtn = android.widget.Button(requireContext())
            removeBtn.text = "Remove Reminder"
            removeBtn.setOnClickListener {                homeViewModel.removeStreakReminder(streak.id, requireContext())
                this.reminder = null
                updateReminderSummary(binding)
                notificationScheduler.cancelReminder(streak.id)
            }
            (binding.reminderSection as android.widget.LinearLayout).addView(removeBtn)
        }
        binding.buttonSetReminder.setOnClickListener {
            showReminderDialog(binding)
        }

        // --- Edit button ---
        binding.buttonEdit.setOnClickListener {
            val dialog = AddStreakDialog(
                onStreakAdded = { name, emoji, _, _ ->
                    // Only update name and emoji
                    homeViewModel.updateStreakNameEmoji(streak.id, name, emoji, requireContext())
                    Toast.makeText(requireContext(), "Streak updated", Toast.LENGTH_SHORT).show()
                    binding.textName.text = name
                    binding.textEmoji.text = emoji
                },
                isEditMode = true,
                initialFrequency = streak.frequency,
                initialFrequencyCount = streak.frequencyCount
            )
            dialog.show(parentFragmentManager, "EditStreakDialog")
        }

        // --- Delete button ---
        binding.buttonDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Streak")
                .setMessage("Are you sure you want to delete this streak?")
                .setPositiveButton("Delete") { _, _ ->                    homeViewModel.deleteStreak(streak.id, requireContext())
                    Toast.makeText(requireContext(), "Streak deleted", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    notificationScheduler.cancelReminder(streak.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return binding.root
    }

    private fun formatFrequency(frequency: com.example.streaks.data.FrequencyType, count: Int): String {
        return when (frequency) {
            com.example.streaks.data.FrequencyType.DAILY -> "Every day"
            com.example.streaks.data.FrequencyType.WEEKLY -> "$count Days a Week"
            com.example.streaks.data.FrequencyType.MONTHLY -> "$count Days a Month"
            com.example.streaks.data.FrequencyType.YEARLY -> "$count Days a Year"
        }
    }

    private fun createYearGraphView(streak: com.example.streaks.data.Streak): View {
        // Simple grid: 7 rows (days), 53 columns (weeks)
        val context = requireContext()
        val completions = streak.asLocalDateCompletions().toSet()
        val year = java.time.LocalDate.now().year
        val start = java.time.LocalDate.of(year, 1, 1)
        val end = java.time.LocalDate.of(year, 12, 31)
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
        val grid = android.widget.TableLayout(context)
        grid.layoutParams = android.widget.TableLayout.LayoutParams(
            android.widget.TableLayout.LayoutParams.MATCH_PARENT,
            android.widget.TableLayout.LayoutParams.MATCH_PARENT
        )
        val dayMatrix = Array(7) { Array(53) { false } }
        for (i in 0 until days) {
            val date = start.plusDays(i.toLong())
            val week = date.get(java.time.temporal.WeekFields.ISO.weekOfYear()) - 1
            val dayOfWeek = date.dayOfWeek.value % 7 // Sunday=0
            if (completions.contains(date)) {
                dayMatrix[dayOfWeek][week] = true
            }
        }
        for (row in 0..6) {
            val tableRow = android.widget.TableRow(context)
            for (col in 0..52) {
                val cell = android.view.View(context)
                val size = 14
                val params = android.widget.TableRow.LayoutParams(size, size)
                params.setMargins(2, 2, 2, 2)
                cell.layoutParams = params
                cell.setBackgroundResource(if (dayMatrix[row][col]) android.R.color.holo_green_dark else android.R.color.darker_gray)
                tableRow.addView(cell)
            }
            grid.addView(tableRow)
        }
        return grid
    }

    private fun createMonthlyView(streak: com.example.streaks.data.Streak): View {
        val context = requireContext()
        val now = java.time.LocalDate.now()
        val completions = streak.asLocalDateCompletions().toSet()
        val daysInMonth = now.lengthOfMonth()
        val firstDayOfWeek = now.withDayOfMonth(1).dayOfWeek.value % 7 // 0=Sunday
        val monthName = now.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + now.year

        val container = android.widget.LinearLayout(context)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Month name
        val monthText = android.widget.TextView(context)
        monthText.text = monthName
        monthText.textSize = 18f
        monthText.setPadding(0, 0, 0, 8)
        monthText.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        container.addView(monthText)

        // Days of week row
        val daysOfWeek = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weekRow = android.widget.TableRow(context)
        for (day in daysOfWeek) {
            val tv = android.widget.TextView(context)
            tv.text = day
            tv.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            tv.setPadding(8, 0, 8, 0)
            weekRow.addView(tv)
        }
        val table = android.widget.TableLayout(context)
        table.addView(weekRow)

        // Calendar grid
        var dayNum = 1
        val totalCells = ((daysInMonth + firstDayOfWeek - 1) / 7 + 1) * 7
        for (row in 0 until totalCells / 7) {
            val tr = android.widget.TableRow(context)
            for (col in 0..6) {
                val cell = android.widget.LinearLayout(context)
                cell.orientation = android.widget.LinearLayout.VERTICAL
                cell.layoutParams = android.widget.TableRow.LayoutParams(0, android.widget.TableRow.LayoutParams.WRAP_CONTENT, 1f)
                if ((row == 0 && col < firstDayOfWeek) || dayNum > daysInMonth) {
                    cell.addView(android.widget.Space(context), android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 32))
                    cell.addView(android.widget.Space(context), android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 12))
                } else {
                    val dayTv = android.widget.TextView(context)
                    dayTv.text = dayNum.toString()
                    dayTv.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    cell.addView(dayTv)
                    val date = now.withDayOfMonth(dayNum)
                    val dot = android.view.View(context)
                    val dotParams = android.widget.LinearLayout.LayoutParams(12, 12)
                    dotParams.topMargin = 2
                    dot.layoutParams = dotParams
                    dot.background = if (completions.contains(date)) {
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(android.graphics.Color.parseColor("#43A047"))
                        }
                    } else {
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(android.graphics.Color.LTGRAY)
                        }
                    }
                    cell.addView(dot)
                    dayNum++
                }
                tr.addView(cell)
            }
            table.addView(tr)
        }
        container.addView(table)
        return container
    }

    private fun updateReminderSummary(binding: FragmentStreakDetailsBinding) {
        binding.textReminderSummary.text = reminder?.toSummary() ?: "No reminder set"
    }

    private fun showReminderDialog(binding: FragmentStreakDetailsBinding) {
        var selectedTime = java.time.LocalTime.of(8, 0)
        val selectedDays = mutableSetOf<Int>()
        val daysOfWeek = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val checkedDays = BooleanArray(7) { false }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Set Reminder")
        builder.setPositiveButton("Pick Time") { dialog, _ ->
            AlertDialog.Builder(requireContext())
                .setTitle("Select Days")
                .setMultiChoiceItems(daysOfWeek, checkedDays) { _, which, isChecked ->
                    checkedDays[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    selectedDays.clear()
                    checkedDays.forEachIndexed { idx, checked -> if (checked) selectedDays.add(idx) }
                    showTimePicker(binding, selectedTime, selectedDays.toList())
                }
                .setNegativeButton("Cancel", null)                .show()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showTimePicker(binding: FragmentStreakDetailsBinding, initialTime: java.time.LocalTime, days: List<Int>) {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val selectedTime = java.time.LocalTime.of(hour, minute)
            val reminder = Reminder(selectedTime.toString(), days)
            val updatedStreak = homeViewModel.setStreakReminder(args.streak.id, reminder, requireContext())
            this.reminder = updatedStreak?.reminder
            updateReminderSummary(binding)
            // Use new WorkManager-based scheduling
            updatedStreak?.reminder?.let { 
                notificationScheduler.scheduleReminder(args.streak.id, args.streak.name, it)
            }
        }, initialTime.hour, initialTime.minute, true).show()
    }

    private fun Reminder.toSummary(): String {
        val daysStr = if (days.isEmpty()) "" else " on " + days.joinToString { idx ->
            arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[idx]
        }
        return "at ${time.toString()}$daysStr"
    }

    companion object {
        const val ARG_STREAK = "streak"
        
        fun scheduleReminderAlarm(context: Context, streakId: String, streakName: String, reminder: Reminder?) {
            val scheduler = NotificationScheduler(context)
            if (reminder != null) {
                scheduler.scheduleReminder(streakId, streakName, reminder)
            } else {
                scheduler.cancelReminder(streakId)
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // Check if this is from the old alarm system - handle for backward compatibility
            val reminderDay = intent.getIntExtra("reminderDay", -1)
            if (reminderDay != -1) {
                val today = (java.time.LocalDate.now().dayOfWeek.value + 6) % 7 // 0=Mon, 6=Sun
                if (today != reminderDay) return // Not the right day
            }
            
            // Create notification channel if needed (Android 8+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "streak_reminder_channel",
                    "Streak Reminders",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for streak reminders"
                    enableVibration(true)
                    enableLights(true)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(channel)
            }
            
            // Check notification permission before showing notification
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionGranted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!permissionGranted) return
            }
            
            val reminderText = intent.getStringExtra("reminderText") ?: "Time to work on your streak!"
            val builder = NotificationCompat.Builder(context, "streak_reminder_channel")
                .setSmallIcon(com.example.streaks.R.drawable.ic_notification_24)
                .setContentTitle("Streak Reminder")
                .setContentText(reminderText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            try {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: SecurityException) {
                // Permission denied, ignore silently
            }
        } catch (e: Exception) {
            // Handle any unexpected errors gracefully
        }
    }
}

// New file: BootReceiver.kt will be created for BOOT_COMPLETED handling 