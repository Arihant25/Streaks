package com.example.streaks.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streaks.databinding.FragmentHomeBinding
import com.example.streaks.ui.adapters.StreaksAdapter
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.google.android.material.transition.platform.MaterialContainerTransform

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
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
        streaksAdapter = StreaksAdapter(
            onStreakToggled = { streakId, shouldCheck ->
                if (shouldCheck) {
                    homeViewModel.completeStreak(streakId, requireContext())
                } else {
                    homeViewModel.uncompleteStreak(streakId, requireContext())
                }
            },
            onStreakClicked = { streak, view ->
                val action = com.example.streaks.ui.home.HomeFragmentDirections.actionHomeToStreakDetails(streak)
                val extras = androidx.navigation.fragment.FragmentNavigatorExtras(
                    view to "streak_card_${streak.id}"
                )
                findNavController().navigate(action, extras)
            }
        )
        
        binding.recyclerStreaks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = streaksAdapter
        }
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