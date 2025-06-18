package com.example.streaks.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.streaks.databinding.FragmentHomeBinding
import com.example.streaks.ui.adapters.StreaksAdapter
import com.google.android.material.transition.platform.MaterialSharedAxis

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var streaksAdapter: StreaksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Predictive back: Material motion for enter/return
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
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
                                    com.example.streaks.ui.home.HomeFragmentDirections
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
            streaksAdapter.submitList(streaks)

            // Show/hide empty state
            binding.emptyState.isVisible = streaks.isEmpty()
            binding.recyclerStreaks.isVisible = streaks.isNotEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
