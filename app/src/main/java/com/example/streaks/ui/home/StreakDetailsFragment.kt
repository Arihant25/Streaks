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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
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
import androidx.navigation.fragment.findNavController
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.example.streaks.R
import android.widget.LinearLayout
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.TimePicker

class StreakDetailsFragment : Fragment() {
    private val args: StreakDetailsFragmentArgs by navArgs()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private var reminder: Reminder? = null // In-memory for now
    private lateinit var notificationScheduler: NotificationScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Predictive back: Material motion for enter/return
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        // Optional: For a smoother transition, you can also set exit and reenter transitions
        // exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        // reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

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

        setupStreakStatsLayout(binding, streak)

        // --- GitHub-style year graph ---
        val yearGraph = createYearGraphView(streak)
        binding.yearGraphContainer.removeAllViews()
        binding.yearGraphContainer.addView(yearGraph)

        // Adjust the height of the yearGraphContainer to wrap its content
        val yearGraphContainerLayoutParams = binding.yearGraphContainer.layoutParams
        yearGraphContainerLayoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        binding.yearGraphContainer.layoutParams = yearGraphContainerLayoutParams

        // --- Monthly view ---
        val monthlyView = createMonthlyView(streak)
        binding.monthlyViewContainer.removeAllViews()
        binding.monthlyViewContainer.addView(monthlyView)

        // --- Reminder section ---
        this.reminder = streak.reminder
        updateReminderSummary(binding)
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
                initialFrequencyCount = streak.frequencyCount,
                initialName = streak.name,
                initialEmoji = streak.emoji
            )
            dialog.show(parentFragmentManager, "EditStreakDialog")
        }

        // --- Delete button ---
        binding.buttonDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Streak")
                .setMessage("Are you sure you want to delete this streak?")
                .setPositiveButton("Delete") { _, _ ->
                    homeViewModel.deleteStreak(streak.id, requireContext())
                    Toast.makeText(requireContext(), "Streak deleted", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    notificationScheduler.cancelReminder(streak.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Set transitionName for shared element
        binding.root.transitionName = "streak_card_${streak.id}"

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle predictive back gesture
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        })
    }

    private fun setupStreakStatsLayout(binding: FragmentStreakDetailsBinding, streak: Streak) {
        // Create a new horizontal container for the streak stats
        val streakStatsContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val streakUnit = getStreakUnit(streak.frequency)

        // Current streak (left side)
        val currentStreakLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.START
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val currentStreakNumberLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM // Align to bottom for different text sizes
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val currentStreakNumber = android.widget.TextView(requireContext()).apply {
            text = "${streak.currentStreak}" // Number only
            textSize = 54f
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            // gravity = android.view.Gravity.START // Gravity handled by parent
        }

        val currentStreakUnitText = android.widget.TextView(requireContext()).apply {
            text = streakUnit
            textSize = 14f // Smaller font size for unit
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dpToPx(4), 0, 0, dpToPx(4)) // Add some padding and adjust bottom padding for alignment
        }
        currentStreakNumberLayout.addView(currentStreakNumber)
        currentStreakNumberLayout.addView(currentStreakUnitText)
        
        val currentStreakLabel = android.widget.TextView(requireContext()).apply {
            text = "Current Streak"
            textSize = 14f
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = android.view.Gravity.START
            setPadding(0, 8, 0, 0)
        }
        
        currentStreakLayout.addView(currentStreakNumberLayout) // Add the new layout
        currentStreakLayout.addView(currentStreakLabel)
        
        // Best streak (right side)
        val bestStreakLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val bestStreakNumberLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM // Align to bottom
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val bestStreakNumber = android.widget.TextView(requireContext()).apply {
            text = "${streak.bestStreak}" // Number only
            textSize = 54f
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            // gravity = android.view.Gravity.END // Gravity handled by parent
        }

        val bestStreakUnitText = android.widget.TextView(requireContext()).apply {
            text = streakUnit
            textSize = 14f // Smaller font size for unit
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dpToPx(4), 0, 0, dpToPx(4)) // Add some padding and adjust bottom padding
        }
        bestStreakNumberLayout.addView(bestStreakNumber)
        bestStreakNumberLayout.addView(bestStreakUnitText)
        
        val bestStreakLabel = android.widget.TextView(requireContext()).apply {
            text = "Best Streak"
            textSize = 14f
            setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = android.view.Gravity.END
            setPadding(0, 8, 0, 0)
        }
        
        bestStreakLayout.addView(bestStreakNumberLayout) // Add the new layout
        bestStreakLayout.addView(bestStreakLabel)  // Then add label
        
        // Add both layouts to the container
        streakStatsContainer.addView(currentStreakLayout)
        streakStatsContainer.addView(bestStreakLayout)
        
        // Replace the existing streak stats in the binding
        // Find the parent container and replace the old stats
        val parentContainer = binding.textCurrentStreak.parent as ViewGroup
        val grandParent = parentContainer.parent as ViewGroup
        val parentIndex = grandParent.indexOfChild(parentContainer)
        
        // Remove old layout and add new one
        grandParent.removeView(parentContainer)
        grandParent.addView(streakStatsContainer, parentIndex)
    }

    private fun getStreakUnit(frequency: com.example.streaks.data.FrequencyType): String {
        return when (frequency) {
            com.example.streaks.data.FrequencyType.DAILY -> "Days"
            com.example.streaks.data.FrequencyType.WEEKLY -> "Weeks"
            com.example.streaks.data.FrequencyType.MONTHLY -> "Months"
            com.example.streaks.data.FrequencyType.YEARLY -> "Years"
        }
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
        val context = requireContext()
        val completions = streak.asLocalDateCompletions().toSet()
        val year = java.time.LocalDate.now().year
        val startDate = java.time.LocalDate.of(year, 1, 1)
        val endDate = java.time.LocalDate.of(year, 12, 31)
        
        val container = android.widget.LinearLayout(context)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24)) 
        container.setBackgroundColor(resolveThemeColor(context, com.google.android.material.R.attr.colorSurface))
        
        val title = android.widget.TextView(context)
        title.text = "$year Activity"
        title.textSize = 16f
        title.setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        title.setPadding(0, 0, 0, dpToPx(16))
        container.addView(title)
        
        // Changed gridContainer to be vertical, removed HorizontalScrollView
        val gridContainer = android.widget.LinearLayout(context)
        gridContainer.orientation = android.widget.LinearLayout.VERTICAL 
        // Padding/margins will be handled by cells and columns directly

        val cellSizeDp = 10
        val spaceBetweenCellsDp = 2

        val cellSizePx = dpToPx(cellSizeDp)
        val cellMarginPx = dpToPx(spaceBetweenCellsDp / 2) 

        val colorEmpty = Color.parseColor("#EBEDF0")
        val colorCompleted = Color.parseColor("#40C463")

        var dayToDraw = startDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
        
        val totalWeeksToDisplay = 53 
        val weeksPerRow = 26 // Display roughly 6 months per row

        var currentSuperRow: android.widget.LinearLayout? = null

        for (weekIndex in 0 until totalWeeksToDisplay) {
            if (weekIndex % weeksPerRow == 0) {
                currentSuperRow = android.widget.LinearLayout(context)
                currentSuperRow.orientation = android.widget.LinearLayout.HORIZONTAL
                val superRowParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (gridContainer.childCount > 0) { // Add top margin for subsequent super rows
                    superRowParams.topMargin = dpToPx(spaceBetweenCellsDp * 2) // Space between super rows
                }
                currentSuperRow.layoutParams = superRowParams
                gridContainer.addView(currentSuperRow)
            }

            val weekColumn = android.widget.LinearLayout(context)
            weekColumn.orientation = android.widget.LinearLayout.VERTICAL
            val columnLayoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Add start margin if not the first column in the current superRow
            if (currentSuperRow != null && currentSuperRow.childCount > 0) {
                columnLayoutParams.marginStart = dpToPx(spaceBetweenCellsDp) 
            }
            weekColumn.layoutParams = columnLayoutParams

            for (dayOfWeekIndex in 0..6) { 
                val cellView = android.view.View(context)
                val cellParams = android.widget.LinearLayout.LayoutParams(cellSizePx, cellSizePx)
                cellParams.setMargins(cellMarginPx, cellMarginPx, cellMarginPx, cellMarginPx)
                cellView.layoutParams = cellParams

                if (dayToDraw.year == year && !dayToDraw.isBefore(startDate) && !dayToDraw.isAfter(endDate)) {
                    if (completions.contains(dayToDraw)) {
                        cellView.setBackgroundColor(colorCompleted)
                    } else {
                        cellView.setBackgroundColor(colorEmpty)
                    }
                } else {
                    cellView.setBackgroundColor(Color.TRANSPARENT) 
                }
                weekColumn.addView(cellView)
                dayToDraw = dayToDraw.plusDays(1)
            }
            currentSuperRow?.addView(weekColumn)
        }
        
        // Add gridContainer (which is now vertical and contains horizontal rows of weeks) to the main container
        container.addView(gridContainer)
        
        return container
    }

    private fun createMonthlyView(streak: com.example.streaks.data.Streak): View {
        val context = requireContext()
        val now = java.time.LocalDate.now()
        val completions = streak.asLocalDateCompletions().toSet()
        val daysInMonth = now.lengthOfMonth()
        
        val firstOfMonth = now.withDayOfMonth(1)
        val firstDayOfWeek = when (firstOfMonth.dayOfWeek) {
            java.time.DayOfWeek.SUNDAY -> 0
            java.time.DayOfWeek.MONDAY -> 1
            java.time.DayOfWeek.TUESDAY -> 2
            java.time.DayOfWeek.WEDNESDAY -> 3
            java.time.DayOfWeek.THURSDAY -> 4
            java.time.DayOfWeek.FRIDAY -> 5
            java.time.DayOfWeek.SATURDAY -> 6
        }
        
        val monthName = now.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + now.year

        val container = android.widget.LinearLayout(context)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(16, 16, 16, 16)
        container.setBackgroundColor(resolveThemeColor(context, com.google.android.material.R.attr.colorSurface))
        container.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Month name
        val monthText = android.widget.TextView(context)
        monthText.text = monthName
        monthText.textSize = 16f
        monthText.setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface))
        monthText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        monthText.setPadding(0, 0, 0, 16)
        monthText.gravity = android.view.Gravity.CENTER
        container.addView(monthText)

        // Days of week header (Sunday first)
        val daysOfWeek = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val weekHeaderRow = android.widget.LinearLayout(context)
        weekHeaderRow.orientation = android.widget.LinearLayout.HORIZONTAL
        weekHeaderRow.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        for (day in daysOfWeek) {
            val tv = android.widget.TextView(context)
            tv.text = day
            tv.gravity = android.view.Gravity.CENTER
            tv.textSize = 12f
            tv.setTextColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            tv.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.setPadding(0, 0, 0, 12)
            weekHeaderRow.addView(tv)
        }
        container.addView(weekHeaderRow)

        // Calendar grid
        val calendarGrid = android.widget.LinearLayout(context)
        calendarGrid.orientation = android.widget.LinearLayout.VERTICAL
        calendarGrid.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        var dayNum = 1
        val totalCells = firstDayOfWeek + daysInMonth
        val totalWeeks = (totalCells + 6) / 7 // Calculate needed weeks
        
        for (week in 0 until totalWeeks) {
            val weekRow = android.widget.LinearLayout(context)
            weekRow.orientation = android.widget.LinearLayout.HORIZONTAL
            weekRow.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            for (dayOfWeek in 0..6) {
                val cell = android.widget.FrameLayout(context)
                cell.layoutParams = android.widget.LinearLayout.LayoutParams(0, dpToPx(44), 1f)
                cell.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                
                val dayPosition = week * 7 + dayOfWeek
                val shouldShowDay = dayPosition >= firstDayOfWeek && dayNum <= daysInMonth
                
                if (shouldShowDay) {
                    val date = now.withDayOfMonth(dayNum)
                    val isCompleted = completions.contains(date)
                    val isToday = date == now
                    
                    val dayTv = android.widget.TextView(context)
                    dayTv.text = dayNum.toString()
                    dayTv.gravity = android.view.Gravity.CENTER
                    dayTv.textSize = 14f
                    dayTv.setTextColor(
                        when {
                            isCompleted -> resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
                            isToday -> resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary)
                            else -> resolveThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
                        }
                    )
                    dayTv.typeface = if (isToday) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    
                    val layoutParams = android.widget.FrameLayout.LayoutParams(
                        dpToPx(32), dpToPx(32)
                    )
                    layoutParams.gravity = android.view.Gravity.CENTER
                    dayTv.layoutParams = layoutParams
                    
                    when {
                        isCompleted -> {
                            val drawable = GradientDrawable()
                            drawable.shape = GradientDrawable.OVAL
                            drawable.setColor(resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary))
                            dayTv.background = drawable
                        }
                        isToday -> {
                            val drawable = GradientDrawable()
                            drawable.shape = GradientDrawable.OVAL
                            drawable.setColor(resolveThemeColor(context, com.google.android.material.R.attr.colorSurface)) // Light orange background
                            drawable.setStroke(dpToPx(2), resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary))
                            dayTv.background = drawable
                        }
                    }
                    
                    cell.addView(dayTv)
                    dayNum++
                }
                weekRow.addView(cell)
            }
            calendarGrid.addView(weekRow)
        }
        
        container.addView(calendarGrid)
        return container
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun updateReminderSummary(binding: FragmentStreakDetailsBinding) {
        binding.textReminderSummary.text = reminder?.toSummary() ?: "No reminder set"
        binding.buttonSetReminder.text = if (reminder != null) "Edit" else "Set Reminder"
    }

    private fun showReminderDialog(binding: FragmentStreakDetailsBinding) {
        var selectedTime = reminder?.let { java.time.LocalTime.parse(it.time) } ?: java.time.LocalTime.of(8, 0)
        val daysOfWeek = arrayOf("M", "T", "W", "T", "F", "S", "S")
        val checkedDays = BooleanArray(7) { false }
        
        // Pre-check days if reminder exists
        reminder?.days?.forEach { day ->
            checkedDays[day] = true
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.days_container)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker)
        val removeButton = dialogView.findViewById<TextView>(R.id.button_remove_reminder)
        
        // Set initial time
        timePicker.hour = selectedTime.hour
        timePicker.minute = selectedTime.minute
        
        // Create day circles
        daysOfWeek.forEachIndexed { index, day ->
            val dayButton = TextView(requireContext()).apply {
                text = day
                textSize = 14f
                setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.day_circle_background)
                isSelected = checkedDays[index]
                if (isSelected) {
                    setTextColor(resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary))
                }
                
                // Set fixed size for the circle
                val size = resources.getDimensionPixelSize(R.dimen.day_circle_size)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.margin_small)
                }
                
                gravity = android.view.Gravity.CENTER
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                
                setOnClickListener {
                    isSelected = !isSelected
                    setTextColor(if (isSelected) {
                        resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnPrimary)
                    } else {
                        resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface)
                    })
                    checkedDays[index] = isSelected
                }
            }
            daysContainer.addView(dayButton)
        }

        // Show/hide remove button based on whether reminder exists
        removeButton.visibility = if (reminder != null) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Set Reminder")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedDays = mutableListOf<Int>()
                checkedDays.forEachIndexed { index, checked ->
                    if (checked) selectedDays.add(index)
                }
                
                selectedTime = java.time.LocalTime.of(timePicker.hour, timePicker.minute)
                
                // If no days selected, treat as every day selected
                val reminder = if (selectedDays.isEmpty()) {
                    Reminder(selectedTime.toString(), (0..6).toList())
                } else {
                    Reminder(selectedTime.toString(), selectedDays)
                }
                
                val updatedStreak = homeViewModel.setStreakReminder(args.streak.id, reminder, requireContext())
                this.reminder = updatedStreak?.reminder
                updateReminderSummary(binding)
                updatedStreak?.reminder?.let { 
                    notificationScheduler.scheduleReminder(args.streak.id, args.streak.name, it)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Set up remove button
        removeButton.setOnClickListener {
            homeViewModel.removeStreakReminder(args.streak.id, requireContext())
            this.reminder = null
            updateReminderSummary(binding)
            notificationScheduler.cancelReminder(args.streak.id)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun Reminder.toSummary(): String {
        val timeStr = java.time.LocalTime.parse(time).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        
        // If all days are selected, show "every day"
        if (days.size == 7) {
            return "Every day at $timeStr"
        }
        
        // Sort days for consistent display
        val sortedDays = days.sorted()
        
        // Find consecutive days
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = sortedDays.first()
        var prev = start
        
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] != prev + 1) {
                ranges.add(Pair(start, prev))
                start = sortedDays[i]
            }
            prev = sortedDays[i]
        }
        ranges.add(Pair(start, prev))
        
        // Convert ranges to readable format
        val dayNames = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayRanges = ranges.map { (start, end) ->
            when {
                start == end -> dayNames[start]
                end == start + 1 -> "${dayNames[start]} and ${dayNames[end]}"
                else -> "${dayNames[start]} to ${dayNames[end]}"
            }
        }
        
        return "${dayRanges.joinToString(", ")} at $timeStr"
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

    // Helper function to resolve color from theme attribute
    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
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