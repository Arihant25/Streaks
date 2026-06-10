package com.arihant.streaks.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.databinding.FragmentHomeBinding
import com.arihant.streaks.ui.adapters.StreaksAdapter
import com.arihant.streaks.ui.dialogs.AddStreakDialog
import com.google.android.material.transition.platform.MaterialSharedAxis

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding
        get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private lateinit var streaksAdapter: StreaksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupRecyclerView()
        observeStreaks()

        binding.fabAddStreak.setOnClickListener {
            AddStreakDialog.newForAdd().show(parentFragmentManager, "AddStreakDialog")
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
                AddStreakDialog.RESULT_KEY_ADD,
                viewLifecycleOwner
        ) { _, bundle ->
            val result = AddStreakDialog.parseResult(bundle)
            homeViewModel.addStreak(
                    result.name, result.emoji, result.frequency, result.frequencyCount, result.color
            )
        }
    }

    private fun setupRecyclerView() {
        streaksAdapter = StreaksAdapter(
                onStreakToggled = { streakId, shouldCheck ->
                    if (shouldCheck) homeViewModel.completeStreak(streakId)
                    else homeViewModel.uncompleteStreak(streakId)
                },
                onStreakClicked = { streak, view ->
                    val action = HomeFragmentDirections.actionHomeToStreakDetails(streak.id)
                    val extras = FragmentNavigatorExtras(view to "streak_card_${streak.id}")
                    findNavController().navigate(action, extras)
                }
        )

        binding.recyclerStreaks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = streaksAdapter
        }

        val itemTouchHelper = ItemTouchHelper(
                object : ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
                ) {
                    override fun onMove(
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder
                    ): Boolean {
                        val from = viewHolder.bindingAdapterPosition
                        val to = target.bindingAdapterPosition
                        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                            return false
                        }
                        streaksAdapter.moveItem(from, to)
                        return true
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                    override fun clearView(
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        // Persist the new order once the drag is finished
                        homeViewModel.reorderStreaks(streaksAdapter.currentIds)
                    }
                }
        )
        itemTouchHelper.attachToRecyclerView(binding.recyclerStreaks)
    }

    private fun observeStreaks() {
        homeViewModel.streaks.observe(viewLifecycleOwner) { streaks ->
            streaksAdapter.submitList(streaks)
            binding.emptyState.isVisible = streaks.isEmpty()
            binding.recyclerStreaks.isVisible = streaks.isNotEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
