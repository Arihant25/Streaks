package com.arihant.streaks

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { StreakRepository.getInstance(this) }
    private var loadErrorShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        binding.navView.setupWithNavController(navController)

        // Avoid reloading the destination when its tab is already selected
        binding.navView.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId)
            }
            true
        }

        openStreakFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openStreakFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Roll counters and "done today" flags over when the app survived midnight
        repository.refreshIfDayChanged()

        if (repository.lastLoadFailed && !loadErrorShown) {
            loadErrorShown = true
            Snackbar.make(
                binding.root,
                getString(R.string.load_data_failed),
                Snackbar.LENGTH_INDEFINITE
            ).setAnchorView(binding.navView).setAction(android.R.string.ok) {}.show()
        }
    }

    /** Notifications deep-link straight to the streak they remind about. */
    private fun openStreakFromIntent(intent: Intent?) {
        val streakId = intent?.getStringExtra(EXTRA_STREAK_ID) ?: return
        intent.removeExtra(EXTRA_STREAK_ID)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navController.navigate(
            R.id.streakDetailsFragment,
            bundleOf("streakId" to streakId)
        )
    }

    companion object {
        const val EXTRA_STREAK_ID = "extra_streak_id"
    }
}
