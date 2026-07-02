package com.arihant.streaks.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.FragmentHomeBinding
import com.arihant.streaks.ui.adapters.StreaksAdapter
import com.arihant.streaks.ui.settings.SettingsViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
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
    private var previousStreaks: List<Streak> = emptyList()

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

        // Respect the "show 🔥 on dashboard" setting
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.showFlame.collect { show -> streaksAdapter.showFlame = show }
        }

        // Wait for the list to be laid out so the shared-element return
        // transition can find the card it morphs back into
        postponeEnterTransition()
        binding.root.doOnPreDraw { startPostponedEnterTransition() }

        return binding.root
    }

    private fun setupRecyclerView() {
        streaksAdapter =
                StreaksAdapter(
                        onStreakToggled = { streakId, shouldCheck ->
                            if (shouldCheck) {
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

        val itemTouchHelper =
                ItemTouchHelper(
                        object :
                                ItemTouchHelper.SimpleCallback(
                                        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                                        0
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
                                // No swipe actions
                            }

                            override fun clearView(
                                    recyclerView: RecyclerView,
                                    viewHolder: RecyclerView.ViewHolder
                            ) {
                                super.clearView(recyclerView, viewHolder)
                                // Persist the new order after drag is finished
                                val newOrder = streaksAdapter.currentList.map { it.id }
                                homeViewModel.reorderStreaks(newOrder, requireContext())
                            }
                        }
                )
        itemTouchHelper.attachToRecyclerView(binding.recyclerStreaks)
    }

    private fun observeStreaks() {
        homeViewModel.streaks.observe(viewLifecycleOwner) { streaks ->
            celebrate(previousStreaks, streaks)
            previousStreaks = streaks

            streaksAdapter.submitList(streaks)

            // Show/hide empty state
            binding.emptyState.isVisible = streaks.isEmpty()
            binding.recyclerStreaks.isVisible = streaks.isNotEmpty()
        }
    }

    // Confetti on every completion, plus a snackbar when a milestone is hit
    private fun celebrate(oldStreaks: List<Streak>, newStreaks: List<Streak>) {
        if (oldStreaks.isEmpty()) return
        val oldById = oldStreaks.associateBy { it.id }
        for (streak in newStreaks) {
            val previous = oldById[streak.id] ?: continue
            if (!streak.isCompletedToday || previous.isCompletedToday) continue
            burstConfetti(listOf(parseStreakColor(streak)))

            val emoji = MILESTONES[streak.currentStreak] ?: continue
            val plural =
                    when (streak.frequency) {
                        FrequencyType.DAILY -> R.plurals.streak_days
                        FrequencyType.WEEKLY -> R.plurals.streak_weeks
                        FrequencyType.MONTHLY -> R.plurals.streak_months
                        FrequencyType.YEARLY -> R.plurals.streak_years
                    }
            val count =
                    resources.getQuantityString(
                            plural,
                            streak.currentStreak,
                            streak.currentStreak
                    )
            Snackbar.make(
                            binding.root,
                            "$emoji $count of ${streak.name} — keep it going!",
                            Snackbar.LENGTH_LONG
                    )
                    .show()
        }
    }

    private fun parseStreakColor(streak: Streak): Int {
        return try {
            android.graphics.Color.parseColor(streak.color)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#FF9900")
        }
    }

    private fun burstConfetti(colors: List<Int>) {
        val party =
                Party(
                        speed = 0f,
                        maxSpeed = 30f,
                        damping = 0.9f,
                        spread = 360,
                        colors = colors + listOf(0xFF6B35, 0xFFC107),
                        emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(120),
                        position = Position.Relative(0.5, 0.22)
                )
        binding.konfettiView.start(party)
    }

    override fun onResume() {
        super.onResume()
        // Recompute streaks so ones broken while the app sat in memory
        // (e.g. overnight) update without needing a completion to be marked
        homeViewModel.recalculateAllStreaks(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
