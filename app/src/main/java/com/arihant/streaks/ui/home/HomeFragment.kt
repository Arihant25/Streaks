package com.arihant.streaks.ui.home

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.FragmentHomeBinding
import com.arihant.streaks.ui.adapters.StreaksAdapter
import com.arihant.streaks.ui.dialogs.AddStreakDialog
import com.arihant.streaks.ui.settings.SettingsViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private lateinit var streaksAdapter: StreaksAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var previousStreaks: List<Streak> = emptyList()

    // Where the next confetti burst should erupt from (the tapped circle or
    // swiped card); null falls back to the top of the screen
    private var pendingBurstOrigin: Position.Absolute? = null

    // Tutorial nudge on the first card hinting it can be swiped; plays at most
    // once per screen and stops appearing once the user has swiped for real
    private var swipeHintAnimator: ObjectAnimator? = null
    private var swipeHintPopup: PopupWindow? = null
    private var swipeHintPlayed = false

    companion object {
        const val TUTORIAL_PREFS = "streaks_tutorial"
        private const val PREF_SWIPE_HINT_DONE = "swipe_hint_done"
        private const val PREF_SWIPE_HINT_SHOWN = "swipe_hint_shown"
        private const val MAX_SWIPE_HINT_SHOWS = 3

        private val MILESTONES =
                mapOf(
                        7 to "🎉",
                        30 to "🏆",
                        50 to "🚀",
                        100 to "💯",
                        200 to "⚡",
                        365 to "🌟"
                )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Predictive back: Material motion for enter/return
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        // Scale gently out of the way when a card morphs into the details page
        exitTransition = MaterialElevationScale(false)
        reenterTransition = MaterialElevationScale(true)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // The hero bleeds behind the status bar (edge-to-edge); push its
        // content down by the status bar height so the title never overlaps
        val heroBasePadding = binding.heroHeader.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeader) {
                view,
                insets ->
            val statusBar =
                    insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.statusBars()
                            )
                            .top
            view.setPadding(
                    view.paddingLeft,
                    heroBasePadding + statusBar,
                    view.paddingRight,
                    view.paddingBottom
            )
            insets
        }

        // The list scrolls behind the nav pill; grow its bottom padding by the
        // navigation bar height so the last card clears the (raised) pill
        val listBasePadding = binding.recyclerStreaks.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerStreaks) {
                view,
                insets ->
            val navBar =
                    insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                            )
                            .bottom
            view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    listBasePadding + navBar
            )
            insets
        }

        setupRecyclerView()
        observeStreaks()

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }
        binding.emptyState.setOnClickListener { showAddStreakDialog() }

        // Wait for the list to be laid out so the shared-element return
        // transition can find the card it morphs back into
        postponeEnterTransition()
        binding.root.doOnPreDraw { startPostponedEnterTransition() }

        return binding.root
    }

    private fun showAddStreakDialog() {
        val existingCount = settingsViewModel.streaksLiveData.value?.size ?: 0
        AddStreakDialog(
                        onStreakAdded = { name, emoji, frequency, frequencyCount, color ->
                            settingsViewModel.addStreak(
                                    name,
                                    emoji,
                                    frequency,
                                    frequencyCount,
                                    color
                            )
                        },
                        isEditMode = false,
                        initialEmoji = AddStreakDialog.defaultEmojiFor(existingCount)
                )
                .show(parentFragmentManager, "AddStreakDialog")
    }

    private fun setupRecyclerView() {
        streaksAdapter =
                StreaksAdapter(
                        onStreakToggled = { streakId, shouldCheck, sourceView ->
                            if (shouldCheck) {
                                pendingBurstOrigin = burstOriginFor(sourceView)
                                homeViewModel.completeStreak(streakId, requireContext())
                            } else {
                                homeViewModel.uncompleteStreak(streakId, requireContext())
                            }
                        },
                        onStreakClicked = { streak, view ->
                            // A second tap mid-transition (or a tap after another card
                            // already navigated) would throw: the action no longer
                            // exists from the current destination
                            val navController = findNavController()
                            if (navController.currentDestination?.id == R.id.navigation_home) {
                                val action =
                                        com.arihant.streaks.ui.home.HomeFragmentDirections
                                                .actionHomeToStreakDetails(streak)
                                val extras =
                                        androidx.navigation.fragment.FragmentNavigatorExtras(
                                                view to "streak_card_${streak.id}"
                                        )
                                navController.navigate(action, extras)
                            }
                        }
                )

        binding.recyclerStreaks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = streaksAdapter
        }

        val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val checkDrawable =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_24)?.mutate()
        checkDrawable?.setTint(android.graphics.Color.WHITE)

        itemTouchHelper =
                ItemTouchHelper(
                        object :
                                ItemTouchHelper.SimpleCallback(
                                        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                                        ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
                                ) {
                            override fun onMove(
                                    recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    target: RecyclerView.ViewHolder
                            ): Boolean {
                                val fromPos = viewHolder.adapterPosition
                                val toPos = target.adapterPosition
                                if (fromPos == RecyclerView.NO_POSITION ||
                                                toPos == RecyclerView.NO_POSITION
                                ) {
                                        return false
                                }
                                streaksAdapter.moveItem(fromPos, toPos)
                                return true
                            }

                            override fun onSwiped(
                                    viewHolder: RecyclerView.ViewHolder,
                                    direction: Int
                            ) {
                                val pos = viewHolder.adapterPosition
                                if (pos == RecyclerView.NO_POSITION) return
                                // The user knows the gesture now, so retire the tutorial nudge
                                tutorialPrefs().edit().putBoolean(PREF_SWIPE_HINT_DONE, true).apply()
                                // submitList can shrink the list while a swipe settles,
                                // leaving pos past the end
                                val streak = streaksAdapter.currentList.getOrNull(pos) ?: return
                                viewHolder.itemView.performHapticFeedback(
                                        HapticFeedbackConstants.CONFIRM
                                )
                                if (streak.isCompletedToday) {
                                    homeViewModel.uncompleteStreak(streak.id, requireContext())
                                } else {
                                    pendingBurstOrigin = burstOriginFor(viewHolder.itemView)
                                    homeViewModel.completeStreak(streak.id, requireContext())
                                }
                                // ItemTouchHelper leaves the swiped view translated
                                // off-screen and keeps drawing the reveal; detaching and
                                // re-attaching clears that state so the card snaps back
                                itemTouchHelper?.attachToRecyclerView(null)
                                itemTouchHelper?.attachToRecyclerView(binding.recyclerStreaks)
                                streaksAdapter.notifyItemChanged(pos)
                            }

                            override fun getSwipeThreshold(
                                    viewHolder: RecyclerView.ViewHolder
                            ): Float = 0.35f

                            override fun onChildDraw(
                                    c: Canvas,
                                    recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder,
                                    dX: Float,
                                    dY: Float,
                                    actionState: Int,
                                    isCurrentlyActive: Boolean
                            ) {
                                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                                    val item = viewHolder.itemView
                                    val pos = viewHolder.adapterPosition
                                    val streak =
                                            if (pos != RecyclerView.NO_POSITION)
                                                    streaksAdapter.currentList.getOrNull(pos)
                                            else null
                                    swipePaint.color =
                                            streak?.let { parseStreakColor(it) }
                                                    ?: ContextCompat.getColor(
                                                            requireContext(),
                                                            R.color.orange
                                                    )
                                    val corner = 12f * resources.displayMetrics.density
                                    val reveal =
                                            if (dX > 0) {
                                                RectF(
                                                        item.left.toFloat(),
                                                        item.top.toFloat(),
                                                        item.left + dX,
                                                        item.bottom.toFloat()
                                                )
                                            } else {
                                                RectF(
                                                        item.right + dX,
                                                        item.top.toFloat(),
                                                        item.right.toFloat(),
                                                        item.bottom.toFloat()
                                                )
                                            }
                                    c.drawRoundRect(reveal, corner, corner, swipePaint)

                                    checkDrawable?.let { icon ->
                                        val iconSize =
                                                (24 * resources.displayMetrics.density).toInt()
                                        val cy = (item.top + item.bottom) / 2
                                        val absDx = Math.abs(dX)
                                        val left =
                                                if (dX > 0) item.left + iconSize
                                                else item.right - iconSize * 2
                                        icon.setBounds(
                                                left,
                                                cy - iconSize / 2,
                                                left + iconSize,
                                                cy + iconSize / 2
                                        )
                                        icon.alpha =
                                                (510 * (absDx / item.width))
                                                        .toInt()
                                                        .coerceIn(0, 255)
                                        icon.draw(c)
                                    }
                                }
                                super.onChildDraw(
                                        c,
                                        recyclerView,
                                        viewHolder,
                                        dX,
                                        dY,
                                        actionState,
                                        isCurrentlyActive
                                )
                            }

                            override fun onSelectedChanged(
                                    viewHolder: RecyclerView.ViewHolder?,
                                    actionState: Int
                            ) {
                                super.onSelectedChanged(viewHolder, actionState)
                                // Don't fight the user for translationX if the
                                // hint is mid-flight when they grab a card
                                if (viewHolder != null) cancelSwipeHint()
                                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                                    viewHolder?.itemView?.performHapticFeedback(
                                            HapticFeedbackConstants.GESTURE_START
                                    )
                                }
                            }

                            override fun clearView(
                                    recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder
                            ) {
                                super.clearView(recyclerView, viewHolder)
                                viewHolder.itemView.performHapticFeedback(
                                        HapticFeedbackConstants.GESTURE_END
                                )
                                // Persist the new order after drag is finished
                                val newOrder = streaksAdapter.currentList.map { it.id }
                                homeViewModel.reorderStreaks(newOrder, requireContext())
                            }
                        }
                )
        itemTouchHelper?.attachToRecyclerView(binding.recyclerStreaks)
    }

    private fun observeStreaks() {
        homeViewModel.streaks.observe(viewLifecycleOwner) { streaks ->
            celebrate(previousStreaks, streaks)
            previousStreaks = streaks

            streaksAdapter.submitList(streaks)

            // Show/hide empty state
            binding.emptyState.isVisible = streaks.isEmpty()
            binding.recyclerStreaks.isVisible = streaks.isNotEmpty()
            binding.fabAddStreak.isVisible = streaks.isEmpty()
            applyWeekPulseVisibility(streaks.isNotEmpty() && showWeekGraph)

            if (streaks.isNotEmpty()) {
                updateWeekPulse(streaks)
                maybeShowSwipeHint()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.showWeekGraph.filterNotNull().collect { show ->
                showWeekGraph = show
                applyWeekPulseVisibility(show && !homeViewModel.streaks.value.isNullOrEmpty())
            }
        }
    }

    private var showWeekGraph = true

    // Without the chart the hero keeps chart-sized bottom padding and looks
    // tall and empty, so tighten it into a normal app-bar height
    private fun applyWeekPulseVisibility(visible: Boolean) {
        binding.weekPulse.isVisible = visible
        val bottomPadding =
                ((if (visible) 20 else 12) * resources.displayMetrics.density).toInt()
        binding.heroHeader.setPadding(
                binding.heroHeader.paddingLeft,
                binding.heroHeader.paddingTop,
                binding.heroHeader.paddingRight,
                bottomPadding
        )
    }

    // Hero card: total completions this week + one colored pill per
    // completion per day, wave-animated only on first show
    private var weekPulseAnimated = false

    private fun updateWeekPulse(streaks: List<Streak>) {
        val today = java.time.LocalDate.now()
        var weekStart = today
        while (weekStart.dayOfWeek != com.arihant.streaks.utils.WeekConfig.firstDayOfWeek) {
            weekStart = weekStart.minusDays(1)
        }

        val completionsByStreak =
                streaks.map { parseStreakColor(it) to it.asLocalDateCompletions().toSet() }

        val days =
                (0L..6L).map { offset ->
                    val day = weekStart.plusDays(offset)
                    val colors =
                            completionsByStreak.mapNotNull { (color, dates) ->
                                color.takeIf { day in dates }
                            }
                    com.arihant.streaks.ui.views.WeekPulseView.Day(
                            label =
                                    day.dayOfWeek.getDisplayName(
                                            java.time.format.TextStyle.NARROW,
                                            java.util.Locale.getDefault()
                                    ),
                            colors = colors,
                            isToday = day == today,
                            isFuture = day.isAfter(today)
                    )
                }

        binding.weekPulse.setData(days, animate = !weekPulseAnimated)
        weekPulseAnimated = true
    }

    private fun tutorialPrefs() =
            requireContext().getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE)

    // Partially drag the first card right, bounce back, then left and back
    // again. A wordless hint that swiping marks a streak done or undone.
    private fun maybeShowSwipeHint() {
        if (swipeHintPlayed) return
        val prefs = tutorialPrefs()
        if (prefs.getBoolean(PREF_SWIPE_HINT_DONE, false)) return
        val shown = prefs.getInt(PREF_SWIPE_HINT_SHOWN, 0)
        if (shown >= MAX_SWIPE_HINT_SHOWS) return
        swipeHintPlayed = true

        binding.recyclerStreaks.post {
            val recycler = _binding?.recyclerStreaks ?: return@post
            val firstCard = recycler.getChildAt(0) ?: return@post
            prefs.edit().putInt(PREF_SWIPE_HINT_SHOWN, shown + 1).apply()

            showSwipeHintBubble(firstCard)

            val nudge = 56f * resources.displayMetrics.density
            swipeHintAnimator =
                    ObjectAnimator.ofFloat(
                                    firstCard,
                                    View.TRANSLATION_X,
                                    0f,
                                    nudge,
                                    -nudge * 0.12f, // Slight overshoot past centre = bounce
                                    0f,
                                    -nudge,
                                    nudge * 0.12f,
                                    0f
                            )
                            .apply {
                                startDelay = 600 // Let the enter transition settle first
                                duration = 1800
                                interpolator =
                                        android.view.animation.AccelerateDecelerateInterpolator()
                                addListener(
                                        object : android.animation.AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(
                                                    animation: android.animation.Animator
                                            ) {
                                                // Also runs after cancel(), so the card
                                                // never sticks part-way off its slot
                                                firstCard.translationX = 0f
                                            }
                                        }
                                )
                                start()
                            }
        }
    }

    // Text bubble shown under the first card while the nudge plays, spelling
    // out what the gesture does
    private fun showSwipeHintBubble(anchor: View) {
        val density = resources.displayMetrics.density
        val bubble =
                TextView(requireContext()).apply {
                    text = getString(R.string.tooltip_swipe_card)
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 13f
                    setPadding(
                            (14 * density).toInt(),
                            (10 * density).toInt(),
                            (14 * density).toInt(),
                            (10 * density).toInt()
                    )
                    maxWidth = (240 * density).toInt()
                    background =
                            GradientDrawable().apply {
                                cornerRadius = 10 * density
                                setColor(android.graphics.Color.parseColor("#E6202020"))
                            }
                }

        val popup =
                PopupWindow(
                        bubble,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        popup.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = 4 * density
        bubble.setOnClickListener { popup.dismiss() }

        // Centre the bubble just below the card so it doesn't cover the nudge
        bubble.measure(
                View.MeasureSpec.makeMeasureSpec((260 * density).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val xOff = anchor.width / 2 - bubble.measuredWidth / 2
        popup.showAsDropDown(anchor, xOff, (4 * density).toInt())
        swipeHintPopup = popup

        anchor.postDelayed({ if (popup.isShowing) popup.dismiss() }, 6000)
    }

    private fun cancelSwipeHint() {
        swipeHintAnimator?.cancel()
        swipeHintAnimator = null
        swipeHintPopup?.dismiss()
        swipeHintPopup = null
    }

    // Confetti on every completion, plus a snackbar when a milestone is hit
    private fun celebrate(oldStreaks: List<Streak>, newStreaks: List<Streak>) {
        val origin = pendingBurstOrigin
        pendingBurstOrigin = null
        if (oldStreaks.isEmpty()) return
        val oldById = oldStreaks.associateBy { it.id }
        for (streak in newStreaks) {
            val previous = oldById[streak.id] ?: continue
            if (!streak.isCompletedToday || previous.isCompletedToday) continue
            burstConfetti(listOf(parseStreakColor(streak)), origin)

            val emoji = MILESTONES[streak.currentStreak] ?: continue
            Snackbar.make(
                            binding.root,
                            "$emoji ${countLabel(streak)} of ${streak.name}! Keep it going!",
                            Snackbar.LENGTH_LONG
                    )
                    .setAction("Share") { shareMilestoneCard(streak) }
                    .show()
        }
    }

    private fun countLabel(streak: Streak): String {
        val plural =
                when (streak.frequency) {
                    FrequencyType.DAILY -> R.plurals.streak_days
                    FrequencyType.WEEKLY -> R.plurals.streak_weeks
                    FrequencyType.MONTHLY -> R.plurals.streak_months
                    FrequencyType.YEARLY -> R.plurals.streak_years
                }
        return resources.getQuantityString(plural, streak.currentStreak, streak.currentStreak)
    }

    private fun parseStreakColor(streak: Streak): Int {
        return try {
            android.graphics.Color.parseColor(streak.color)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#FF9900")
        }
    }

    private fun burstOriginFor(view: View): Position.Absolute {
        val viewLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        val konfettiLocation = IntArray(2)
        binding.konfettiView.getLocationInWindow(konfettiLocation)
        // Subtract any ItemTouchHelper-applied translation so the origin is the
        // card's original layout centre, not its off-screen swiped position.
        return Position.Absolute(
                viewLocation[0] - konfettiLocation[0] + view.width / 2f - view.translationX,
                viewLocation[1] - konfettiLocation[1] + view.height / 2f - view.translationY
        )
    }

    private fun burstConfetti(colors: List<Int>, origin: Position.Absolute? = null) {
        val party =
                Party(
                        angle = 270,
                        speed = 5f,
                        maxSpeed = 22f,
                        damping = 0.92f,
                        spread = 160,
                        colors = colors + listOf(0xFF6B35, 0xFFC107),
                        emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(100),
                        position = origin ?: Position.Relative(0.5, 0.5)
                )
        binding.konfettiView.start(party)
    }

    private fun shareMilestoneCard(streak: Streak) {
        try {
            val bitmap = createMilestoneBitmap(streak)
            val dir = File(requireContext().cacheDir, "shared_cards")
            dir.mkdirs()
            val file = File(dir, "milestone_${streak.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri =
                    FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                    )
            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
            startActivity(Intent.createChooser(intent, "Share milestone"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't share the milestone", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    private fun createMilestoneBitmap(streak: Streak): Bitmap {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(parseStreakColor(streak))
        // Soft dark scrim so white text stays readable on bright streak colors
        canvas.drawColor(android.graphics.Color.argb(60, 0, 0, 0))

        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    color = android.graphics.Color.WHITE
                }

        paint.textSize = 240f
        canvas.drawText(streak.emoji, size / 2f, 430f, paint)

        paint.textSize = 130f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(countLabel(streak), size / 2f, 640f, paint)

        paint.textSize = 64f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(streak.name, size / 2f, 740f, paint)

        paint.textSize = 44f
        paint.alpha = 200
        canvas.drawText("Tracked with Streaks 🔥", size / 2f, 990f, paint)

        return bitmap
    }

    override fun onResume() {
        super.onResume()
        // Recompute streaks so ones broken while the app sat in memory
        // (e.g. overnight) update without needing a completion to be marked
        homeViewModel.recalculateAllStreaks(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelSwipeHint()
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        _binding = null
    }
}
