package com.example.streaks

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.activity.viewModels
import com.example.streaks.ui.settings.SettingsViewModel

import androidx.navigation.ui.setupWithNavController
import com.example.streaks.databinding.ActivityMainBinding

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

        // Add custom navigation listener for home icon
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Pop back to home and clear the back stack
                    navController.popBackStack(R.id.navigation_home, true)
                    navController.navigate(R.id.navigation_home)
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