package com.arihant.streaks.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
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

    companion object {
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
                        isEditMode = false
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
                            val action =
                                    com.arihant.streaks.ui.home.HomeFragmentDirections
                                            .actionHomeToStreakDetails(streak)
                            val extras =
                                    androidx.navigation.fragment.FragmentNavigatorExtras(
                                            view to "streak_card_${streak.id}"
                                    )
                            findNavController().navigate(action, extras)
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
                                        ItemTouchHelper.RIGHT
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
                                val streak = streaksAdapter.currentList[pos]
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
                                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
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
                                            RectF(
                                                    item.left.toFloat(),
                                                    item.top.toFloat(),
                                                    item.left + dX,
                                                    item.bottom.toFloat()
                                            )
                                    c.drawRoundRect(reveal, corner, corner, swipePaint)

                                    checkDrawable?.let { icon ->
                                        val iconSize =
                                                (24 * resources.displayMetrics.density).toInt()
                                        val cy = (item.top + item.bottom) / 2
                                        val left = item.left + iconSize
                                        icon.setBounds(
                                                left,
                                                cy - iconSize / 2,
                                                left + iconSize,
                                                cy + iconSize / 2
                                        )
                                        icon.alpha =
                                                (510 * (dX / item.width))
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
        }
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
                            "$emoji ${countLabel(streak)} of ${streak.name} — keep it going!",
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
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        _binding = null
    }
}
