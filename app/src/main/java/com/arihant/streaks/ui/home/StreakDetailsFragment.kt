package com.arihant.streaks.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arihant.streaks.R
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.FragmentStreakDetailsBinding
import com.arihant.streaks.ui.dialogs.AddStreakDialog
import com.arihant.streaks.utils.NotificationScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialContainerTransform

class StreakDetailsFragment : Fragment() {
        private val args: StreakDetailsFragmentArgs by navArgs()
        private val homeViewModel: HomeViewModel by activityViewModels()
        private var reminder: Reminder? = null // In-memory for now
        private lateinit var notificationScheduler: NotificationScheduler
        
        // Track the currently displayed month for calendar navigation
        private var currentDisplayMonth: java.time.LocalDate = java.time.LocalDate.now().withDayOfMonth(1)

        // First-run tooltip pointing at today's cell in the monthly calendar
        private var todayCellView: View? = null
        private var calendarTooltip: android.widget.PopupWindow? = null
        private var calendarTooltipQueued = false

        // References to the dynamically built stat views so they can be updated in place
        private var currentStreakNumberView: TextView? = null
        private var currentStreakUnitView: TextView? = null
        private var bestStreakNumberView: TextView? = null
        private var bestStreakUnitView: TextView? = null
        private var quickStatsRow: LinearLayout? = null

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // The tapped card morphs into this page (and back on return)
                sharedElementEnterTransition =
                        MaterialContainerTransform().apply {
                                drawingViewId = R.id.nav_host_fragment_activity_main
                                duration = 400L
                                scrimColor = Color.TRANSPARENT
                                setAllContainerColors(
                                        ContextCompat.getColor(requireContext(), R.color.white)
                                )
                        }
        }

        override fun onCreateView(
                inflater: LayoutInflater,
                container: ViewGroup?,
                savedInstanceState: Bundle?
        ): View? {
                val streak = args.streak
                val binding = FragmentStreakDetailsBinding.inflate(inflater, container, false)
                notificationScheduler = NotificationScheduler(requireContext())

                // Edge-to-edge: keep content below the status bar and above the nav bar
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) {
                        view,
                        insets ->
                        val bars =
                                insets.getInsets(
                                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                )
                        view.setPadding(
                                view.paddingLeft,
                                bars.top,
                                view.paddingRight,
                                bars.bottom
                        )
                        insets
                }

                binding.textEmoji.text = streak.emoji
                binding.textName.text = streak.name
                binding.textFrequency.text =
                        formatFrequency(streak.frequency, streak.frequencyCount)

                setupStreakStatsLayout(binding, streak)

                // --- GitHub-style year graph ---
                binding.yearGraphContainer.removeAllViews()
                binding.yearGraphContainer.addView(
                        createYearGraphView(streak, binding, animate = true)
                )

                // Adjust the height of the yearGraphContainer to wrap its content
                val yearGraphContainerLayoutParams = binding.yearGraphContainer.layoutParams
                yearGraphContainerLayoutParams.height =
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                binding.yearGraphContainer.layoutParams = yearGraphContainerLayoutParams

                // --- Quick stats (total / this month / this year) ---
                renderQuickStats(binding, streak)

                // --- Monthly view ---
                setupMonthlyViewWithNavigation(binding, streak)
                maybeShowCalendarTooltip(binding.root)

                // --- Manage cards ---
                this.reminder = streak.reminder
                updateReminderSummary(binding)
                addPressAnimation(binding.cardReminder)
                addPressAnimation(binding.cardEdit)
                addPressAnimation(binding.cardDelete)
                binding.cardReminder.setOnClickListener { showReminderDialog(binding) }

                // --- Edit card ---
                // Re-registered on every view creation so the sheet can deliver its
                // result even after rotation or process death recreates this fragment
                parentFragmentManager.setFragmentResultListener(
                        REQUEST_EDIT_STREAK,
                        viewLifecycleOwner
                ) { _, result ->
                        val name = result.getString(AddStreakDialog.RESULT_NAME)!!
                        val emoji = result.getString(AddStreakDialog.RESULT_EMOJI)!!
                        val color = result.getString(AddStreakDialog.RESULT_COLOR)!!
                        val frequency =
                                com.arihant.streaks.data.FrequencyType.valueOf(
                                        result.getString(AddStreakDialog.RESULT_FREQUENCY)!!
                                )
                        val frequencyCount =
                                result.getInt(AddStreakDialog.RESULT_FREQUENCY_COUNT)
                        homeViewModel.updateStreakDetails(
                                streak.id,
                                name,
                                emoji,
                                color,
                                frequency,
                                frequencyCount,
                                requireContext()
                        )
                        Toast.makeText(requireContext(), "Streak updated", Toast.LENGTH_SHORT)
                                .show()
                        binding.textName.text = name
                        binding.textEmoji.text = emoji
                        binding.textFrequency.text = formatFrequency(frequency, frequencyCount)
                }
                binding.cardEdit.setOnClickListener {
                        // Pull the latest values so reopening the sheet after an edit
                        // doesn't show stale data
                        val current =
                                homeViewModel.streaks.value?.find { it.id == streak.id } ?: streak
                        AddStreakDialog.newInstance(
                                        requestKey = REQUEST_EDIT_STREAK,
                                        isEditMode = true,
                                        initialFrequency = current.frequency,
                                        initialFrequencyCount = current.frequencyCount,
                                        initialName = current.name,
                                        initialEmoji = current.emoji,
                                        initialColor = current.color
                                )
                                .show(parentFragmentManager, "EditStreakDialog")
                }

                // --- Delete card ---
                binding.cardDelete.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete Streak")
                                .setMessage("Are you sure you want to delete this streak?")
                                .setPositiveButton("Delete") { _, _ ->
                                        homeViewModel.deleteStreak(streak.id, requireContext())
                                        Toast.makeText(
                                                        requireContext(),
                                                        "Streak deleted",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        requireActivity().onBackPressedDispatcher.onBackPressed()
                                        notificationScheduler.cancelReminder(streak.id)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                }

                // Set transitionName for shared element
                binding.root.transitionName = "streak_card_${streak.id}"

                // Keep this page in sync with the repository so marking a date
                // updates the stats, year graph and calendar immediately
                homeViewModel.streaks.observe(viewLifecycleOwner) { streaks ->
                        val updated = streaks.find { it.id == streak.id } ?: return@observe
                        updateStreakStats(updated)
                        binding.yearGraphContainer.removeAllViews()
                        binding.yearGraphContainer.addView(
                                createYearGraphView(updated, binding, animate = false)
                        )
                        refreshMonthlyView(binding, updated)
                        renderQuickStats(binding, updated)
                }

                return binding.root
        }

        override fun onDestroyView() {
                super.onDestroyView()
                calendarTooltip?.dismiss()
                calendarTooltip = null
                todayCellView = null
                currentStreakNumberView = null
                currentStreakUnitView = null
                bestStreakNumberView = null
                bestStreakUnitView = null
                quickStatsRow = null
        }

        private fun renderQuickStats(binding: FragmentStreakDetailsBinding, streak: Streak) {
                val parent = binding.yearGraphContainer.parent as ViewGroup
                quickStatsRow?.let { parent.removeView(it) }

                val row =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams
                                                        .MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT
                                        )
                                setPadding(0, dpToPx(4), 0, dpToPx(12))
                        }

                fun tile(value: String, label: String): android.widget.LinearLayout {
                        return android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                gravity = android.view.Gravity.CENTER
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                0,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT,
                                                1f
                                        )
                                addView(
                                        android.widget.TextView(requireContext()).apply {
                                                text = value
                                                textSize = 22f
                                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                setTextColor(
                                                        resolveThemeColor(
                                                                requireContext(),
                                                                com.google
                                                                        .android
                                                                        .material
                                                                        .R
                                                                        .attr
                                                                        .colorOnSurface
                                                        )
                                                )
                                                gravity = android.view.Gravity.CENTER
                                        }
                                )
                                addView(
                                        android.widget.TextView(requireContext()).apply {
                                                text = label
                                                textSize = 12f
                                                setTextColor(
                                                        resolveThemeColor(
                                                                requireContext(),
                                                                com.google
                                                                        .android
                                                                        .material
                                                                        .R
                                                                        .attr
                                                                        .colorOnSurfaceVariant
                                                        )
                                                )
                                                gravity = android.view.Gravity.CENTER
                                        }
                                )
                        }
                }

                val completions = streak.asLocalDateCompletions()
                val now = java.time.LocalDate.now()
                row.addView(tile("${completions.size}", "Total"))
                row.addView(
                        tile(
                                "${completions.count { it.year == now.year && it.month == now.month }}",
                                "This Month"
                        )
                )
                row.addView(tile("${completions.count { it.year == now.year }}", "This Year"))

                parent.addView(row, parent.indexOfChild(binding.yearGraphContainer) + 1)
                quickStatsRow = row
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)

                // Handle predictive back gesture
                requireActivity()
                        .onBackPressedDispatcher
                        .addCallback(
                                viewLifecycleOwner,
                                object : androidx.activity.OnBackPressedCallback(true) {
                                        override fun handleOnBackPressed() {
                                                findNavController().popBackStack()
                                        }
                                }
                        )
        }

        private fun setupStreakStatsLayout(binding: FragmentStreakDetailsBinding, streak: Streak) {
                // Create a new horizontal container for the streak stats
                val streakStatsContainer =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams
                                                        .MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT
                                        )
                        }

                val streakUnit = getStreakUnit(streak.frequency, streak.currentStreak)

                // Current streak (left side)
                val currentStreakLayout =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                gravity = android.view.Gravity.START
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                0,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT,
                                                1f
                                        )
                        }

                val currentStreakNumberLayout =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                gravity =
                                        android.view.Gravity.START or
                                                android.view.Gravity
                                                        .BOTTOM // Align to bottom for different
                                // text sizes
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT
                                        )
                        }

                val currentStreakNumber =
                        android.widget.TextView(requireContext()).apply {
                                text = "${streak.currentStreak}" // Number only
                                textSize = 54f
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google.android.material.R.attr.colorOnSurface
                                        )
                                )
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                // gravity = android.view.Gravity.START // Gravity handled by parent
                        }

                val currentStreakUnitText =
                        android.widget.TextView(requireContext()).apply {
                                text = streakUnit
                                textSize = 14f // Smaller font size for unit
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                                setPadding(
                                        dpToPx(4),
                                        0,
                                        0,
                                        dpToPx(4)
                                ) // Add some padding and adjust bottom padding for alignment
                        }
                currentStreakNumberLayout.addView(currentStreakNumber)
                currentStreakNumberLayout.addView(currentStreakUnitText)
                currentStreakNumberView = currentStreakNumber
                currentStreakUnitView = currentStreakUnitText

                val currentStreakLabel =
                        android.widget.TextView(requireContext()).apply {
                                text = "Current Streak"
                                textSize = 14f
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                                gravity = android.view.Gravity.START
                                setPadding(0, 8, 0, 0)
                        }

                currentStreakLayout.addView(currentStreakNumberLayout) // Add the new layout
                currentStreakLayout.addView(currentStreakLabel)

                // Best streak (right side)
                val bestStreakLayout =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                gravity = android.view.Gravity.END
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                0,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT,
                                                1f
                                        )
                        }

                val bestStreakNumberLayout =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                gravity =
                                        android.view.Gravity.END or
                                                android.view.Gravity.BOTTOM // Align to bottom
                                layoutParams =
                                        android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT,
                                                android.widget.LinearLayout.LayoutParams
                                                        .WRAP_CONTENT
                                        )
                        }

                val bestStreakNumber =
                        android.widget.TextView(requireContext()).apply {
                                text = "${streak.bestStreak}" // Number only
                                textSize = 54f
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                // gravity = android.view.Gravity.END // Gravity handled by parent
                        }

                val bestStreakUnitText =
                        android.widget.TextView(requireContext()).apply {
                                text = streakUnit
                                textSize = 14f // Smaller font size for unit
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                                setPadding(
                                        dpToPx(4),
                                        0,
                                        0,
                                        dpToPx(4)
                                ) // Add some padding and adjust bottom padding
                        }
                bestStreakNumberLayout.addView(bestStreakNumber)
                bestStreakNumberLayout.addView(bestStreakUnitText)
                bestStreakNumberView = bestStreakNumber
                bestStreakUnitView = bestStreakUnitText

                val bestStreakLabel =
                        android.widget.TextView(requireContext()).apply {
                                text = "Best Streak"
                                textSize = 14f
                                setTextColor(
                                        resolveThemeColor(
                                                requireContext(),
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                                gravity = android.view.Gravity.END
                                setPadding(0, 8, 0, 0)
                        }

                bestStreakLayout.addView(bestStreakNumberLayout) // Add the new layout
                bestStreakLayout.addView(bestStreakLabel) // Then add label

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

                // Count the big numbers up from zero as the page opens
                animateCountUp(currentStreakNumber, streak.currentStreak)
                animateCountUp(bestStreakNumber, streak.bestStreak)
        }

        private fun animateCountUp(view: TextView, target: Int) {
                if (target <= 0) {
                        view.text = "$target"
                        return
                }
                android.animation.ValueAnimator.ofInt(0, target).apply {
                        duration = 700
                        startDelay = 250 // Let the container transform land first
                        interpolator = android.view.animation.DecelerateInterpolator()
                        addUpdateListener { view.text = "${it.animatedValue}" }
                        start()
                }
        }

        private fun updateStreakStats(streak: Streak) {
                currentStreakNumberView?.text = "${streak.currentStreak}"
                currentStreakUnitView?.text = getStreakUnit(streak.frequency, streak.currentStreak)
                bestStreakNumberView?.text = "${streak.bestStreak}"
                bestStreakUnitView?.text = getStreakUnit(streak.frequency, streak.bestStreak)
        }

        private fun getStreakUnit(
                frequency: com.arihant.streaks.data.FrequencyType,
                count: Int
        ): String {
                return when (frequency) {
                        com.arihant.streaks.data.FrequencyType.DAILY ->
                                if (count == 1) "Day" else "Days"
                        com.arihant.streaks.data.FrequencyType.WEEKLY ->
                                if (count == 1) "Week" else "Weeks"
                        com.arihant.streaks.data.FrequencyType.MONTHLY ->
                                if (count == 1) "Month" else "Months"
                        com.arihant.streaks.data.FrequencyType.YEARLY ->
                                if (count == 1) "Year" else "Years"
                }
        }

        private fun formatFrequency(
                frequency: com.arihant.streaks.data.FrequencyType,
                count: Int
        ): String {
                return when (frequency) {
                        com.arihant.streaks.data.FrequencyType.DAILY -> "Every day"
                        com.arihant.streaks.data.FrequencyType.WEEKLY ->
                                if (count == 1) "$count Day a Week" else "$count Days a Week"
                        com.arihant.streaks.data.FrequencyType.MONTHLY ->
                                if (count == 1) "$count Day a Month" else "$count Days a Month"
                        com.arihant.streaks.data.FrequencyType.YEARLY ->
                                if (count == 1) "$count Day a Year" else "$count Days a Year"
                }
        }

        private fun createYearGraphView(
                streak: com.arihant.streaks.data.Streak,
                binding: FragmentStreakDetailsBinding,
                animate: Boolean
        ): View {
                val context = requireContext()
                val completions = streak.asLocalDateCompletions().toSet()
                val year = java.time.LocalDate.now().year
                val streakColor =
                        try {
                                android.graphics.Color.parseColor(streak.color)
                        } catch (e: Exception) {
                                android.graphics.Color.parseColor("#FF9900")
                        }

                val card =
                        com.google.android.material.card.MaterialCardView(context).apply {
                                radius = dpToPx(20).toFloat()
                                cardElevation = 0f
                                strokeWidth = dpToPx(1)
                                strokeColor = ContextCompat.getColor(context, R.color.gray_medium)
                                setCardBackgroundColor(
                                        ContextCompat.getColor(context, R.color.white)
                                )
                                layoutParams =
                                        ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                        }

                val column = android.widget.LinearLayout(context)
                column.orientation = android.widget.LinearLayout.VERTICAL
                column.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(14))

                val header = android.widget.LinearLayout(context)
                header.orientation = android.widget.LinearLayout.HORIZONTAL
                header.gravity = android.view.Gravity.CENTER_VERTICAL

                val title = android.widget.TextView(context)
                title.text = "$year Activity"
                title.textSize = 16f
                title.typeface = android.graphics.Typeface.DEFAULT_BOLD
                title.setTextColor(
                        resolveThemeColor(
                                context,
                                com.google.android.material.R.attr.colorOnSurface
                        )
                )
                title.layoutParams =
                        android.widget.LinearLayout.LayoutParams(
                                0,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                        )
                header.addView(title)

                column.addView(header)

                val graph = com.arihant.streaks.ui.views.YearGraphView(context)
                graph.onDayTapped = { date, isCompleted ->
                        showDateToggleConfirmation(date, isCompleted, streak, binding)
                }
                graph.setData(completions, streakColor, streak.frequency, animate)

                val scroll = android.widget.HorizontalScrollView(context)
                scroll.isHorizontalScrollBarEnabled = false
                scroll.setPadding(0, dpToPx(12), 0, dpToPx(10))
                scroll.addView(graph)
                column.addView(scroll)
                // Land with today's week centred instead of January
                scroll.post {
                        scroll.scrollTo(
                                (graph.todayScrollX() - scroll.width / 2).coerceAtLeast(0),
                                0
                        )
                }

                // Less -> More legend for the streak-depth ramp
                val legend = android.widget.LinearLayout(context)
                legend.orientation = android.widget.LinearLayout.HORIZONTAL
                legend.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END

                fun legendLabel(text: String): TextView {
                        return TextView(context).apply {
                                this.text = text
                                textSize = 11f
                                setTextColor(
                                        resolveThemeColor(
                                                context,
                                                com.google
                                                        .android
                                                        .material
                                                        .R
                                                        .attr
                                                        .colorOnSurfaceVariant
                                        )
                                )
                        }
                }

                legend.addView(legendLabel("Less"))
                val swatchSize = dpToPx(11)
                for (level in 0..4) {
                        val swatch = View(context)
                        val lp = android.widget.LinearLayout.LayoutParams(swatchSize, swatchSize)
                        lp.marginStart = if (level == 0) dpToPx(6) else dpToPx(3)
                        if (level == 4) lp.marginEnd = dpToPx(6)
                        swatch.layoutParams = lp
                        swatch.background =
                                GradientDrawable().apply {
                                        cornerRadius = dpToPx(3).toFloat()
                                        setColor(
                                                if (level == 0) graph.colorForEmpty()
                                                else graph.rampColor(level)
                                        )
                                }
                        legend.addView(swatch)
                }
                legend.addView(legendLabel("More"))
                column.addView(legend)

                card.addView(column)
                return card
        }

        // Cards shrink slightly on press and spring back on release
        private fun addPressAnimation(view: View) {
                view.setOnTouchListener { v, event ->
                        when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN ->
                                        v.animate()
                                                .scaleX(0.97f)
                                                .scaleY(0.97f)
                                                .setDuration(90)
                                                .start()
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL ->
                                        v.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(200)
                                                .setInterpolator(
                                                        android.view.animation
                                                                .OvershootInterpolator(2f)
                                                )
                                                .start()
                        }
                        false
                }
        }

        private fun setupMonthlyViewWithNavigation(binding: FragmentStreakDetailsBinding, streak: com.arihant.streaks.data.Streak) {
                refreshMonthlyView(binding, streak)
        }

        private fun refreshMonthlyView(binding: FragmentStreakDetailsBinding, streak: com.arihant.streaks.data.Streak) {
                // The calendar is rebuilt from scratch, so drop any tooltip
                // anchored to a cell that is about to be detached
                calendarTooltip?.dismiss()
                todayCellView = null
                val monthlyView = createMonthlyViewWithNavigation(binding, streak, currentDisplayMonth)
                binding.monthlyViewContainer.removeAllViews()
                binding.monthlyViewContainer.addView(monthlyView)
        }

        private fun createMonthlyViewWithNavigation(binding: FragmentStreakDetailsBinding, streak: com.arihant.streaks.data.Streak, displayDate: java.time.LocalDate = java.time.LocalDate.now()): View {
                val context = requireContext()
                val now = java.time.LocalDate.now()
                val completions = streak.asLocalDateCompletions().toSet()
                val daysInMonth = displayDate.lengthOfMonth()
                val streakColor =
                        try {
                                android.graphics.Color.parseColor(streak.color)
                        } catch (e: Exception) {
                                android.graphics.Color.parseColor("#FF9900")
                        }

                val firstOfMonth = displayDate.withDayOfMonth(1)
                // Offset of the 1st of the month within the configured week
                val weekStart = com.arihant.streaks.utils.WeekConfig.firstDayOfWeek
                val firstDayOfWeek =
                        (firstOfMonth.dayOfWeek.value - weekStart.value + 7) % 7

                val monthName =
                        displayDate.month.name.lowercase().replaceFirstChar { it.uppercase() } +
                                " " +
                                displayDate.year
                val card = com.google.android.material.card.MaterialCardView(context).apply {
                        radius = dpToPx(20).toFloat()
                        cardElevation = 0f
                        strokeWidth = dpToPx(1)
                        strokeColor = ContextCompat.getColor(context, R.color.gray_medium)
                        setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
                        layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                }
                val container = android.widget.LinearLayout(context)
                container.orientation = android.widget.LinearLayout.VERTICAL
                container.setPadding(16, 16, 16, 16)
                container.layoutParams =
                        android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )

                // Navigation header with arrows and month name
                val headerLayout = android.widget.LinearLayout(context)
                headerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
                headerLayout.gravity = android.view.Gravity.CENTER_VERTICAL
                headerLayout.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )

                // Left arrow
                val leftArrow = android.widget.ImageView(context)
                leftArrow.setImageResource(R.drawable.ic_arrow_left)
                leftArrow.contentDescription = getString(R.string.previous_month)
                leftArrow.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                
                // Create ripple effect background
                val typedValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                leftArrow.setBackgroundResource(typedValue.resourceId)
                
                leftArrow.isClickable = true
                leftArrow.isFocusable = true
                leftArrow.layoutParams = android.widget.LinearLayout.LayoutParams(
                        dpToPx(40),
                        dpToPx(40)
                )
                
                // Month name
                val monthText = android.widget.TextView(context)
                monthText.text = monthName
                monthText.textSize = 16f
                monthText.setTextColor(
                        resolveThemeColor(
                                context,
                                com.google.android.material.R.attr.colorOnSurface
                        )
                )
                monthText.typeface = android.graphics.Typeface.DEFAULT_BOLD
                monthText.gravity = android.view.Gravity.CENTER
                monthText.layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )

                // Right arrow
                val rightArrow = android.widget.ImageView(context)
                rightArrow.setImageResource(R.drawable.ic_arrow_right)
                rightArrow.contentDescription = getString(R.string.next_month)
                rightArrow.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                
                // Create ripple effect background
                val typedValueRight = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValueRight, true)
                rightArrow.setBackgroundResource(typedValueRight.resourceId)
                
                rightArrow.isClickable = true
                rightArrow.isFocusable = true
                rightArrow.layoutParams = android.widget.LinearLayout.LayoutParams(
                        dpToPx(40),
                        dpToPx(40)
                )

                // Set click listeners for navigation
                leftArrow.setOnClickListener {
                    currentDisplayMonth = currentDisplayMonth.minusMonths(1)
                    refreshMonthlyView(binding, streak)
                }
                
                rightArrow.setOnClickListener {
                    currentDisplayMonth = currentDisplayMonth.plusMonths(1)
                    refreshMonthlyView(binding, streak)
                }

                headerLayout.addView(leftArrow)
                headerLayout.addView(monthText)
                headerLayout.addView(rightArrow)
                
                // Hide right arrow if we're at or past the current month
                val currentMonth = java.time.LocalDate.now().withDayOfMonth(1)
                rightArrow.visibility = if (displayDate.withDayOfMonth(1) >= currentMonth) {
                    android.view.View.INVISIBLE
                } else {
                    android.view.View.VISIBLE
                }
                
                container.addView(headerLayout)

                // Add some space after header
                val spacer = android.view.View(context)
                spacer.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(16)
                )
                container.addView(spacer)

                // Days of week header, starting on the configured first day
                val daysOfWeek =
                        (0L..6L).map { offset ->
                                com.arihant.streaks.utils.WeekConfig.firstDayOfWeek
                                        .plus(offset)
                                        .getDisplayName(
                                                java.time.format.TextStyle.SHORT,
                                                java.util.Locale.getDefault()
                                        )
                        }
                val weekHeaderRow = android.widget.LinearLayout(context)
                weekHeaderRow.orientation = android.widget.LinearLayout.HORIZONTAL
                weekHeaderRow.layoutParams =
                        android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )

                for (day in daysOfWeek) {
                        val tv = android.widget.TextView(context)
                        tv.text = day
                        tv.gravity = android.view.Gravity.CENTER
                        tv.textSize = 12f
                        tv.setTextColor(
                                resolveThemeColor(
                                        context,
                                        com.google.android.material.R.attr.colorOnSurfaceVariant
                                )
                        )
                        tv.layoutParams =
                                android.widget.LinearLayout.LayoutParams(
                                        0,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                        1f
                                )
                        tv.setPadding(0, 0, 0, 12)
                        weekHeaderRow.addView(tv)
                }
                container.addView(weekHeaderRow)

                // Calendar grid
                val calendarGrid = android.widget.LinearLayout(context)
                calendarGrid.orientation = android.widget.LinearLayout.VERTICAL
                calendarGrid.layoutParams =
                        android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )

                var dayNum = 1
                val totalCells = firstDayOfWeek + daysInMonth
                val totalWeeks = (totalCells + 6) / 7 // Calculate needed weeks

                for (week in 0 until totalWeeks) {
                        val weekRow = android.widget.LinearLayout(context)
                        weekRow.orientation = android.widget.LinearLayout.HORIZONTAL
                        weekRow.layoutParams =
                                android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                )

                        for (dayOfWeek in 0..6) {
                                val cell = android.widget.FrameLayout(context)
                                cell.layoutParams =
                                        android.widget.LinearLayout.LayoutParams(0, dpToPx(44), 1f)
                                cell.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))

                                val dayPosition = week * 7 + dayOfWeek
                                val shouldShowDay =
                                        dayPosition >= firstDayOfWeek && dayNum <= daysInMonth

                                if (shouldShowDay) {
                                        val date = displayDate.withDayOfMonth(dayNum)
                                        val isCompleted = completions.contains(date)
                                        val isToday = date == now

                                        val dayTv = android.widget.TextView(context)
                                        dayTv.text = dayNum.toString()
                                        dayTv.gravity = android.view.Gravity.CENTER
                                        dayTv.textSize = 14f
                                        dayTv.setTextColor(
                                                when {
                                                        isCompleted ->
                                                                resolveThemeColor(
                                                                        context,
                                                                        com.google
                                                                                .android
                                                                                .material
                                                                                .R
                                                                                .attr
                                                                                .colorOnSurface
                                                                )
                                                        isToday ->
                                                                resolveThemeColor(
                                                                        context,
                                                                        androidx.appcompat.R.attr.colorPrimary
                                                                )
                                                        else ->
                                                                resolveThemeColor(
                                                                        context,
                                                                        com.google
                                                                                .android
                                                                                .material
                                                                                .R
                                                                                .attr
                                                                                .colorOnSurfaceVariant
                                                                )
                                                }
                                        )
                                        dayTv.typeface =
                                                if (isToday) android.graphics.Typeface.DEFAULT_BOLD
                                                else android.graphics.Typeface.DEFAULT

                                        val layoutParams =
                                                android.widget.FrameLayout.LayoutParams(
                                                        dpToPx(32),
                                                        dpToPx(32)
                                                )
                                        layoutParams.gravity = android.view.Gravity.CENTER
                                        dayTv.layoutParams = layoutParams

                                        when {
                                                isCompleted -> {
                                                        val drawable = GradientDrawable()
                                                        drawable.shape = GradientDrawable.OVAL
                                                        drawable.setColor(streakColor)
                                                        dayTv.background = drawable
                                                }
                                                isToday -> {
                                                        val drawable = GradientDrawable()
                                                        drawable.shape = GradientDrawable.OVAL
                                                        drawable.setColor(
                                                                resolveThemeColor(
                                                                        context,
                                                                        com.google
                                                                                .android
                                                                                .material
                                                                                .R
                                                                                .attr
                                                                                .colorSurface
                                                                )
                                                        ) // Light orange background
                                                        drawable.setStroke(dpToPx(2), streakColor)
                                                        dayTv.background = drawable
                                                }
                                        }

                                        // Add click listener for date toggling
                                        dayTv.setOnClickListener {
                                                showDateToggleConfirmation(date, isCompleted, streak, binding)
                                        }
                                        
                                        // Make the day clickable
                                        dayTv.isClickable = true
                                        dayTv.isFocusable = true

                                        if (isToday) todayCellView = dayTv

                                        cell.addView(dayTv)
                                        dayNum++
                                }
                                weekRow.addView(cell)
                        }
                        calendarGrid.addView(weekRow)
                }

                container.addView(calendarGrid)
                card.addView(container)
                return card
        }

        // Show a "tap any date" tooltip over today's cell on the first two
        // visits to this page after install, once the enter transition lands
        private fun maybeShowCalendarTooltip(root: View) {
                if (calendarTooltipQueued) return
                val prefs =
                        requireContext()
                                .getSharedPreferences(
                                        HomeFragment.TUTORIAL_PREFS,
                                        Context.MODE_PRIVATE
                                )
                val shown = prefs.getInt(PREF_CALENDAR_TOOLTIP_SHOWN, 0)
                if (shown >= 2) return
                calendarTooltipQueued = true

                root.postDelayed(
                        {
                                val anchor = todayCellView ?: return@postDelayed
                                if (!isAdded || !anchor.isAttachedToWindow) return@postDelayed
                                prefs.edit().putInt(PREF_CALENDAR_TOOLTIP_SHOWN, shown + 1).apply()
                                showCalendarTooltip(anchor)
                        },
                        700 // Container transform runs 400ms; land after it settles
                )
        }

        private fun showCalendarTooltip(anchor: View) {
                val bubble =
                        TextView(requireContext()).apply {
                                text = getString(R.string.tooltip_tap_date)
                                setTextColor(Color.WHITE)
                                textSize = 13f
                                setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
                                maxWidth = dpToPx(220)
                                background =
                                        GradientDrawable().apply {
                                                cornerRadius = dpToPx(10).toFloat()
                                                setColor(Color.parseColor("#E6202020"))
                                        }
                        }

                val popup =
                        android.widget.PopupWindow(
                                bubble,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                popup.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                )
                popup.isOutsideTouchable = true
                popup.elevation = dpToPx(4).toFloat()
                bubble.setOnClickListener { popup.dismiss() }

                // Measure so the bubble can sit centred above today's cell
                bubble.measure(
                        View.MeasureSpec.makeMeasureSpec(dpToPx(240), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val xOff = anchor.width / 2 - bubble.measuredWidth / 2
                val yOff = -(anchor.height + bubble.measuredHeight + dpToPx(8))
                popup.showAsDropDown(anchor, xOff, yOff)
                calendarTooltip = popup

                anchor.postDelayed({ if (popup.isShowing) popup.dismiss() }, 6000)
        }

        private fun showDateToggleConfirmation(
            date: java.time.LocalDate,
            isCurrentlyCompleted: Boolean,
            streak: com.arihant.streaks.data.Streak,
            binding: FragmentStreakDetailsBinding
        ) {
            if (date.isAfter(java.time.LocalDate.now())) {
                Toast.makeText(requireContext(), "Can't mark future dates", Toast.LENGTH_SHORT)
                        .show()
                return
            }
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            val title = if (isCurrentlyCompleted) getString(R.string.mark_uncompleted) else getString(R.string.mark_completed)
            val message = if (isCurrentlyCompleted) {
                getString(R.string.confirm_mark_uncompleted)
            } else {
                getString(R.string.confirm_mark_completed)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage("$message\n$dateStr")
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    toggleDateCompletion(date, isCurrentlyCompleted, streak, binding)
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }

        private fun toggleDateCompletion(
            date: java.time.LocalDate,
            isCurrentlyCompleted: Boolean,
            streak: com.arihant.streaks.data.Streak,
            binding: FragmentStreakDetailsBinding
        ) {
            // Toggle the completion status; the streaks observer refreshes the UI
            homeViewModel.toggleStreakCompletion(streak.id, date, requireContext())
        }

        private fun dpToPx(dp: Int): Int {
                return TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                dp.toFloat(),
                                resources.displayMetrics
                        )
                        .toInt()
        }

        private fun updateReminderSummary(binding: FragmentStreakDetailsBinding) {
                binding.textReminderSummary.text =
                        reminder?.toSummary() ?: "None set — tap to add one"
        }

        private fun showReminderDialog(binding: FragmentStreakDetailsBinding) {
                var selectedTime =
                        reminder?.let { java.time.LocalTime.parse(it.time) }
                                ?: java.time.LocalTime.of(8, 0)
                val daysOfWeek = arrayOf("M", "T", "W", "T", "F", "S", "S")
                // A new reminder starts with every day on — clearer than the old
                // hidden "no days selected means every day" rule
                val checkedDays = BooleanArray(7) { reminder == null }
                reminder?.days?.forEach { day -> checkedDays[day] = true }

                val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
                val daysContainer = dialogView.findViewById<LinearLayout>(R.id.days_container)
                val timeText = dialogView.findViewById<TextView>(R.id.text_time)
                val liveSummary = dialogView.findViewById<TextView>(R.id.text_live_summary)
                val timeCard = dialogView.findViewById<View>(R.id.card_time)
                val saveButton = dialogView.findViewById<View>(R.id.btn_save_reminder)
                val removeButton = dialogView.findViewById<TextView>(R.id.button_remove_reminder)

                val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                fun refreshSummary() {
                        timeText.text = selectedTime.format(timeFormatter)
                        val days = (0..6).filter { checkedDays[it] }.ifEmpty { (0..6).toList() }
                        liveSummary.text = Reminder(selectedTime.toString(), days).toSummary()
                }

                // Day circles with a springy toggle
                daysOfWeek.forEachIndexed { index, day ->
                        val dayButton =
                                TextView(requireContext()).apply {
                                        text = day
                                        textSize = 14f
                                        background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.day_circle_background
                                                )

                                        fun applyTextColor() {
                                                setTextColor(
                                                        resolveThemeColor(
                                                                requireContext(),
                                                                if (isSelected) {
                                                                        com.google.android.material
                                                                                .R.attr
                                                                                .colorOnPrimary
                                                                } else {
                                                                        com.google.android.material
                                                                                .R.attr
                                                                                .colorOnSurface
                                                                }
                                                        )
                                                )
                                        }
                                        isSelected = checkedDays[index]
                                        applyTextColor()

                                        // Set fixed size for the circle
                                        val size =
                                                resources.getDimensionPixelSize(
                                                        R.dimen.day_circle_size
                                                )
                                        layoutParams =
                                                LinearLayout.LayoutParams(size, size).apply {
                                                        marginEnd =
                                                                resources.getDimensionPixelSize(
                                                                        R.dimen.margin_small
                                                                )
                                                }

                                        gravity = android.view.Gravity.CENTER
                                        textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER

                                        setOnClickListener {
                                                isSelected = !isSelected
                                                checkedDays[index] = isSelected
                                                applyTextColor()
                                                performHapticFeedback(
                                                        android.view.HapticFeedbackConstants
                                                                .CLOCK_TICK
                                                )
                                                scaleX = 0.7f
                                                scaleY = 0.7f
                                                animate().scaleX(1f)
                                                        .scaleY(1f)
                                                        .setDuration(220)
                                                        .setInterpolator(
                                                                android.view.animation
                                                                        .OvershootInterpolator(2.5f)
                                                        )
                                                        .start()
                                                refreshSummary()
                                        }
                                }
                        daysContainer.addView(dayButton)
                }

                refreshSummary()

                val dialog =
                        com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
                dialog.setContentView(dialogView)

                // Big time opens Material's clock picker
                timeCard.setOnClickListener {
                        val is24Hour =
                                android.text.format.DateFormat.is24HourFormat(requireContext())
                        val picker =
                                com.google.android.material.timepicker.MaterialTimePicker.Builder()
                                        .setTimeFormat(
                                                if (is24Hour) {
                                                        com.google.android.material.timepicker
                                                                .TimeFormat.CLOCK_24H
                                                } else {
                                                        com.google.android.material.timepicker
                                                                .TimeFormat.CLOCK_12H
                                                }
                                        )
                                        .setHour(selectedTime.hour)
                                        .setMinute(selectedTime.minute)
                                        .setTitleText(getString(R.string.remind_me))
                                        .build()
                        picker.addOnPositiveButtonClickListener {
                                selectedTime = java.time.LocalTime.of(picker.hour, picker.minute)
                                refreshSummary()
                        }
                        picker.show(childFragmentManager, "reminder_time_picker")
                }

                saveButton.setOnClickListener {
                        val selectedDays = (0..6).filter { checkedDays[it] }
                        // If no days selected, treat as every day selected
                        val newReminder =
                                Reminder(
                                        selectedTime.toString(),
                                        selectedDays.ifEmpty { (0..6).toList() }
                                )

                        val updatedStreak =
                                homeViewModel.setStreakReminder(
                                        args.streak.id,
                                        newReminder,
                                        requireContext()
                                )
                        this.reminder = updatedStreak?.reminder
                        updateReminderSummary(binding)
                        updatedStreak?.reminder?.let {
                                notificationScheduler.scheduleReminder(
                                        args.streak.id,
                                        args.streak.name,
                                        it
                                )
                        }
                        dialog.dismiss()
                }

                // Show/hide remove button based on whether reminder exists
                removeButton.visibility = if (reminder != null) View.VISIBLE else View.GONE
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
                val timeStr =
                        java.time.LocalTime.parse(time)
                                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

                // All days selected shows "every day"; old app versions stored
                // every-day reminders as an EMPTY days list, so imported data
                // can legitimately carry one — treat it the same, don't crash
                if (days.isEmpty() || days.size == 7) {
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
                val dayNames =
                        arrayOf(
                                "Monday",
                                "Tuesday",
                                "Wednesday",
                                "Thursday",
                                "Friday",
                                "Saturday",
                                "Sunday"
                        )
                val dayRanges =
                        ranges.map { (start, end) ->
                                when {
                                        start == end -> dayNames[start]
                                        end == start + 1 ->
                                                "${dayNames[start]} and ${dayNames[end]}"
                                        else -> "${dayNames[start]} to ${dayNames[end]}"
                                }
                        }

                return "${dayRanges.joinToString(", ")} at $timeStr"
        }

        companion object {
                const val ARG_STREAK = "streak"
                private const val REQUEST_EDIT_STREAK = "details_edit_streak"
                private const val PREF_CALENDAR_TOOLTIP_SHOWN = "calendar_tooltip_shown"

                fun scheduleReminderAlarm(
                        context: Context,
                        streakId: String,
                        streakName: String,
                        reminder: Reminder?
                ) {
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

