package com.arihant.streaks

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.arihant.streaks.databinding.ActivityMainBinding
import com.arihant.streaks.ui.settings.SettingsViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load streaks from persistent storage
        settingsViewModel.loadStreaksFromFile()

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        // Add custom navigation listener to prevent reloading when already on the same page
        navView.setOnItemSelectedListener { item ->
            val currentDestination = navController.currentDestination?.id

            when (item.itemId) {
                R.id.navigation_home -> {
                    if (currentDestination != R.id.navigation_home) {
                        navController.navigate(R.id.navigation_home)
                    }
                    true
                }
                R.id.navigation_settings -> {
                    if (currentDestination != R.id.navigation_settings) {
                        navController.navigate(R.id.navigation_settings)
                    }
                    true
                }
                else -> {
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
    }

    fun getBottomNavigationView(): BottomNavigationView = binding.navView
}
