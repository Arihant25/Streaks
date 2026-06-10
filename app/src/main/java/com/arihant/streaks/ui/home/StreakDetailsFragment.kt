package com.arihant.streaks.ui.home

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.databinding.FragmentStreakDetailsBinding
import com.arihant.streaks.notifications.Notifications
import com.arihant.streaks.notifications.ReminderScheduler
import com.arihant.streaks.ui.dialogs.AddStreakDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialSharedAxis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class StreakDetailsFragment : Fragment() {

    private val args: StreakDetailsFragmentArgs by navArgs()
    private val homeViewModel: HomeViewModel by activityViewModels()

    private var _binding: FragmentStreakDetailsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var scheduler: ReminderScheduler

    /** Latest snapshot from the repository; every UI section binds from this. */
    private var streak: Streak? = null

    /** Set while we delete with undo, so the observer doesn't double-pop. */
    private var leavingAfterDelete = false

    private var displayMonth: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var displayYear: Int = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        savedInstanceState?.getString(STATE_DISPLAY_MONTH)?.let {
            displayMonth = LocalDate.parse(it)
        }
        displayYear = savedInstanceState?.getInt(STATE_DISPLAY_YEAR, displayYear) ?: displayYear
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreakDetailsBinding.inflate(inflater, container, false)
        binding.root.transitionName = "streak_card_${args.streakId}"
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scheduler = ReminderScheduler(requireContext())

        binding.buttonSetReminder.setOnClickListener { showReminderDialog() }
        binding.buttonEdit.setOnClickListener {
            streak?.let { AddStreakDialog.newForEdit(it).show(parentFragmentManager, "EditStreakDialog") }
        }
        binding.buttonDelete.setOnClickListener { deleteWithUndo() }

        parentFragmentManager.setFragmentResultListener(
            AddStreakDialog.RESULT_KEY_EDIT, viewLifecycleOwner
        ) { _, bundle -> onEdited(AddStreakDialog.parseResult(bundle)) }

        homeViewModel.streaks.observe(viewLifecycleOwner) { streaks ->
            val current = streaks.find { it.id == args.streakId }
            if (current == null) {
                if (!leavingAfterDelete) findNavController().popBackStack()
                return@observe
            }
            streak = current
            bind(current)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DISPLAY_MONTH, displayMonth.toString())
        outState.putInt(STATE_DISPLAY_YEAR, displayYear)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bind(streak: Streak) {
        binding.textEmoji.text = streak.emoji
        binding.textName.text = streak.name
        binding.textFrequency.text = formatFrequency(streak.frequency, streak.frequencyCount)

        binding.textCurrentStreak.text = streak.currentStreak.toString()
        binding.textCurrentStreakUnit.text = streakUnit(streak.frequency, streak.currentStreak)
        binding.textBestStreak.text = streak.bestStreak.toString()
        binding.textBestStreakUnit.text = streakUnit(streak.frequency, streak.bestStreak)

        binding.textReminderSummary.text =
            streak.reminder?.toSummary() ?: getString(R.string.no_reminder_set)
        binding.buttonSetReminder.text =
            if (streak.reminder != null) getString(R.string.edit) else getString(R.string.set_reminder)

        binding.monthlyViewContainer.removeAllViews()
        binding.monthlyViewContainer.addView(createMonthView(streak))

        binding.yearGraphContainer.removeAllViews()
        binding.yearGraphContainer.addView(createYearGraph(streak))
    }

    private fun onEdited(result: AddStreakDialog.Result) {
        val current = streak ?: return
        homeViewModel.updateStreakNameEmojiColor(
            current.id, result.name, result.emoji, result.color
        )
        if (result.frequency != current.frequency ||
            result.frequencyCount != current.frequencyCount
        ) {
            homeViewModel.updateStreakFrequency(current.id, result.frequency, result.frequencyCount)
        }
        Toast.makeText(requireContext(), R.string.streak_updated, Toast.LENGTH_SHORT).show()
    }

    // ── Delete with undo ──────────────────────────────────────────────────────

    private fun deleteWithUndo() {
        val current = streak ?: return
        val appContext = requireContext().applicationContext
        val activityRoot = requireActivity().findViewById<View>(android.R.id.content)
        val anchor = requireActivity().findViewById<View>(R.id.nav_view)

        leavingAfterDelete = true
        homeViewModel.deleteStreak(current.id)
        scheduler.cancel(current.id)
        Notifications.cancelReminder(appContext, current.id)
        findNavController().popBackStack()

        Snackbar.make(activityRoot, R.string.streak_deleted, Snackbar.LENGTH_LONG)
            .setAnchorView(anchor)
            .setAction(R.string.undo) {
                StreakRepository.getInstance(appContext).restoreStreak(current)
                current.reminder?.let {
                    ReminderScheduler(appContext).scheduleNext(current.id, it)
                }
            }
            .show()
    }

    // ── Year graph (GitHub style) ─────────────────────────────────────────────

    private fun createYearGraph(streak: Streak): View {
        val context = requireContext()
        val completions = streak.asLocalDateCompletions().toSet()
        val today = LocalDate.now()
        val year = displayYear
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        val streakColor = parseStreakColor(streak.color)
        val emptyCellColor =
            resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24))
        }

        // Header: ‹ 2026 Activity ›
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val minYear = minOf(
            LocalDate.parse(streak.createdDate).year,
            completions.minOfOrNull { it.year } ?: year
        )
        val leftArrow = navArrow(R.drawable.ic_arrow_left, getString(R.string.previous_year)) {
            displayYear--
            streakSnapshot()?.let { bindYearGraphOnly(it) }
        }
        leftArrow.visibility = if (year > minYear) View.VISIBLE else View.INVISIBLE
        val title = TextView(context).apply {
            text = getString(R.string.year_activity, year)
            textSize = 16f
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rightArrow = navArrow(R.drawable.ic_arrow_right, getString(R.string.next_year)) {
            displayYear++
            streakSnapshot()?.let { bindYearGraphOnly(it) }
        }
        rightArrow.visibility = if (year < today.year) View.VISIBLE else View.INVISIBLE
        header.addView(leftArrow)
        header.addView(title)
        header.addView(rightArrow)
        container.addView(header)

        val cellSizePx = dpToPx(12)
        val cellMarginPx = dpToPx(1)
        val columnWidthPx = cellSizePx + 2 * cellMarginPx

        val firstWeekDay =
            if (SettingsStore.weekStartsMonday(context)) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        val gridStart = yearStart.with(TemporalAdjusters.previousOrSame(firstWeekDay))

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Month labels are positioned absolutely above the column where each month begins
        val monthLabels = FrameLayout(context)
        var columnCount = 0
        var todayColumnIndex = -1
        var weekStart = gridStart
        while (weekStart <= yearEnd) {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(cellMarginPx, 0, cellMarginPx, 0) }
            }
            for (dayOfWeek in 0..6) {
                val cellDate = weekStart.plusDays(dayOfWeek.toLong())
                val cell = View(context)
                cell.layoutParams = LinearLayout.LayoutParams(cellSizePx, cellSizePx).apply {
                    setMargins(0, cellMarginPx, 0, cellMarginPx)
                }
                if (cellDate.year == year) {
                    cell.background = GradientDrawable().apply {
                        cornerRadius = dpToPx(2).toFloat()
                        setColor(if (cellDate in completions) streakColor else emptyCellColor)
                        if (cellDate == today) setStroke(dpToPx(1), streakColor)
                    }
                    if (cellDate.dayOfMonth == 1) {
                        monthLabels.addView(TextView(context).apply {
                            text = cellDate.month.getDisplayName(
                                java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH
                            )
                            textSize = 10f
                            setTextColor(
                                resolveThemeColor(
                                    com.google.android.material.R.attr.colorOnSurfaceVariant
                                )
                            )
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            ).apply { leftMargin = columnCount * columnWidthPx }
                        })
                    }
                }
                if (cellDate == today) todayColumnIndex = columnCount
                column.addView(cell)
            }
            grid.addView(column)
            columnCount++
            weekStart = weekStart.plusWeeks(1)
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                monthLabels,
                LinearLayout.LayoutParams(columnCount * columnWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
            addView(grid)
        }
        val scroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(8), 0, dpToPx(8))
            isHorizontalScrollBarEnabled = false
            addView(scrollContent)
        }
        container.addView(scroll)

        // Bring the current week into view when showing the current year
        if (todayColumnIndex >= 0) {
            scroll.post {
                val target = (todayColumnIndex * columnWidthPx) - scroll.width / 2
                scroll.scrollTo(target.coerceAtLeast(0), 0)
            }
        }

        return container
    }

    private fun streakSnapshot(): Streak? = streak

    private fun bindYearGraphOnly(streak: Streak) {
        binding.yearGraphContainer.removeAllViews()
        binding.yearGraphContainer.addView(createYearGraph(streak))
    }

    // ── Month calendar ────────────────────────────────────────────────────────

    private fun createMonthView(streak: Streak): View {
        val context = requireContext()
        val today = LocalDate.now()
        val completions = streak.asLocalDateCompletions().toSet()
        val firstOfMonth = displayMonth.withDayOfMonth(1)
        val daysInMonth = displayMonth.lengthOfMonth()
        val streakColor = parseStreakColor(streak.color)
        val weekStartsMonday = SettingsStore.weekStartsMonday(context)

        // Mon=1→0 … Sun=7→6 (Monday-first) | Sun=7→0 … Sat=6→6 (Sunday-first)
        val firstDayOffset =
            if (weekStartsMonday) (firstOfMonth.dayOfWeek.value - 1) % 7
            else firstOfMonth.dayOfWeek.value % 7

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header: ‹ June 2026 ›
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val leftArrow = navArrow(R.drawable.ic_arrow_left, getString(R.string.previous_month)) {
            displayMonth = displayMonth.minusMonths(1)
            streakSnapshot()?.let { bindMonthViewOnly(it) }
        }
        val monthTitle = TextView(context).apply {
            text = getString(
                R.string.month_title,
                displayMonth.month.getDisplayName(
                    java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH
                ),
                displayMonth.year
            )
            textSize = 16f
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rightArrow = navArrow(R.drawable.ic_arrow_right, getString(R.string.next_month)) {
            displayMonth = displayMonth.plusMonths(1)
            streakSnapshot()?.let { bindMonthViewOnly(it) }
        }
        rightArrow.visibility =
            if (displayMonth >= today.withDayOfMonth(1)) View.INVISIBLE else View.VISIBLE
        header.addView(leftArrow)
        header.addView(monthTitle)
        header.addView(rightArrow)
        container.addView(header)

        container.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12)
            )
        })

        // Weekday header
        val dayNames =
            if (weekStartsMonday) arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            else arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val weekHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        dayNames.forEach { day ->
            weekHeader.addView(TextView(context).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(
                    resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, 0, 0, dpToPx(8))
            })
        }
        container.addView(weekHeader)

        // Day grid
        var dayNumber = 1
        val totalCells = firstDayOffset + daysInMonth
        val totalWeeks = (totalCells + 6) / 7
        val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

        for (week in 0 until totalWeeks) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (dayOfWeek in 0..6) {
                val cell = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(48), 1f)
                }
                val position = week * 7 + dayOfWeek
                if (position >= firstDayOffset && dayNumber <= daysInMonth) {
                    val date = displayMonth.withDayOfMonth(dayNumber)
                    val isCompleted = date in completions
                    val isToday = date == today
                    val isFuture = date.isAfter(today)

                    val dayText = TextView(context).apply {
                        text = dayNumber.toString()
                        gravity = Gravity.CENTER
                        textSize = 14f
                        typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        setTextColor(
                            when {
                                isCompleted -> resolveThemeColor(
                                    com.google.android.material.R.attr.colorOnSurface
                                )
                                isToday -> resolveThemeColor(
                                    com.google.android.material.R.attr.colorPrimary
                                )
                                else -> resolveThemeColor(
                                    com.google.android.material.R.attr.colorOnSurfaceVariant
                                )
                            }
                        )
                        alpha = if (isFuture) 0.4f else 1f
                        layoutParams = FrameLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply {
                            gravity = Gravity.CENTER
                        }
                        background = when {
                            isCompleted -> GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(streakColor)
                            }
                            isToday -> GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(
                                    resolveThemeColor(
                                        com.google.android.material.R.attr.colorSurface
                                    )
                                )
                                setStroke(dpToPx(2), streakColor)
                            }
                            else -> null
                        }
                    }
                    cell.addView(dayText)

                    if (!isFuture) {
                        // The whole 48dp cell is the touch target, not just the 34dp circle
                        cell.isClickable = true
                        cell.isFocusable = true
                        cell.foreground = rippleDrawable(context)
                        cell.contentDescription = getString(
                            if (isCompleted) R.string.cd_day_completed
                            else R.string.cd_day_not_completed,
                            date.format(dateFormatter)
                        )
                        cell.setOnClickListener { confirmToggleDate(date, isCompleted) }
                    }
                    dayNumber++
                }
                row.addView(cell)
            }
            container.addView(row)
        }

        return container
    }

    private fun bindMonthViewOnly(streak: Streak) {
        binding.monthlyViewContainer.removeAllViews()
        binding.monthlyViewContainer.addView(createMonthView(streak))
    }

    private fun confirmToggleDate(date: LocalDate, isCurrentlyCompleted: Boolean) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        val title =
            if (isCurrentlyCompleted) getString(R.string.mark_uncompleted)
            else getString(R.string.mark_completed)
        val message =
            if (isCurrentlyCompleted) getString(R.string.confirm_mark_uncompleted)
            else getString(R.string.confirm_mark_completed)

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("$message\n$dateStr")
            .setPositiveButton(R.string.yes) { _, _ ->
                // The LiveData observer rebinds stats, calendar and year graph
                homeViewModel.toggleStreakCompletion(args.streakId, date)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // ── Reminder ──────────────────────────────────────────────────────────────

    private fun showReminderDialog() {
        val current = streak ?: return
        val existing = current.reminder
        val initialTime = existing?.let { runCatching { LocalTime.parse(it.time) }.getOrNull() }
            ?: LocalTime.of(8, 0)
        val dayLetters = arrayOf("M", "T", "W", "T", "F", "S", "S")
        val checkedDays = BooleanArray(7)
        existing?.days?.forEach { if (it in 0..6) checkedDays[it] = true }

        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.days_container)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker)
        val removeButton = dialogView.findViewById<TextView>(R.id.button_remove_reminder)

        timePicker.hour = initialTime.hour
        timePicker.minute = initialTime.minute

        dayLetters.forEachIndexed { index, letter ->
            val dayButton = TextView(requireContext()).apply {
                text = letter
                textSize = 14f
                background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.day_circle_background)
                isSelected = checkedDays[index]
                applyDayColor(this)
                val size = resources.getDimensionPixelSize(R.dimen.day_circle_size)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.margin_small)
                }
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setOnClickListener {
                    isSelected = !isSelected
                    checkedDays[index] = isSelected
                    applyDayColor(this)
                }
            }
            daysContainer.addView(dayButton)
        }

        removeButton.visibility = if (existing != null) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.set_reminder)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val selectedDays = (0..6).filter { checkedDays[it] }
                val time = LocalTime.of(timePicker.hour, timePicker.minute)
                // No days selected means every day
                val reminder = Reminder(
                    time.toString(),
                    selectedDays.ifEmpty { (0..6).toList() }
                )
                val updated = homeViewModel.setStreakReminder(args.streakId, reminder)
                updated?.reminder?.let { scheduler.scheduleNext(args.streakId, it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        removeButton.setOnClickListener {
            homeViewModel.removeStreakReminder(args.streakId)
            scheduler.cancel(args.streakId)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyDayColor(view: TextView) {
        view.setTextColor(
            resolveThemeColor(
                if (view.isSelected) com.google.android.material.R.attr.colorOnPrimary
                else com.google.android.material.R.attr.colorOnSurface
            )
        )
    }

    private fun Reminder.toSummary(): String {
        val timeStr = LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a"))
        if (days.size == 7 || days.isEmpty()) return "Every day at $timeStr"

        val sortedDays = days.sorted()
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = sortedDays.first()
        var prev = start
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] != prev + 1) {
                ranges.add(start to prev)
                start = sortedDays[i]
            }
            prev = sortedDays[i]
        }
        ranges.add(start to prev)

        val dayNames = arrayOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )
        val parts = ranges.map { (first, last) ->
            when {
                first == last -> dayNames[first]
                last == first + 1 -> "${dayNames[first]} and ${dayNames[last]}"
                else -> "${dayNames[first]} to ${dayNames[last]}"
            }
        }
        return "${parts.joinToString(", ")} at $timeStr"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun navArrow(iconRes: Int, description: String, onClick: () -> Unit): ImageView =
        ImageView(requireContext()).apply {
            setImageResource(iconRes)
            contentDescription = description
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = rippleDrawable(requireContext())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener { onClick() }
        }

    private fun rippleDrawable(context: Context): android.graphics.drawable.Drawable? {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return ContextCompat.getDrawable(context, outValue.resourceId)
    }

    private fun parseStreakColor(color: String): Int = try {
        Color.parseColor(color)
    } catch (e: IllegalArgumentException) {
        Color.parseColor(Streak.DEFAULT_COLOR)
    }

    private fun streakUnit(frequency: FrequencyType, count: Int): String = when (frequency) {
        FrequencyType.DAILY -> if (count == 1) "Day" else "Days"
        FrequencyType.WEEKLY -> if (count == 1) "Week" else "Weeks"
        FrequencyType.MONTHLY -> if (count == 1) "Month" else "Months"
        FrequencyType.YEARLY -> if (count == 1) "Year" else "Years"
    }

    private fun formatFrequency(frequency: FrequencyType, count: Int): String = when (frequency) {
        FrequencyType.DAILY -> "Every day"
        FrequencyType.WEEKLY -> if (count == 1) "Once a week" else "$count times a week"
        FrequencyType.MONTHLY -> if (count == 1) "Once a month" else "$count times a month"
        FrequencyType.YEARLY -> if (count == 1) "Once a year" else "$count times a year"
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    companion object {
        private const val STATE_DISPLAY_MONTH = "state_display_month"
        private const val STATE_DISPLAY_YEAR = "state_display_year"
    }
}
