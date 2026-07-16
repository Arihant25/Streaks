package com.arihant.streaks

import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.arihant.streaks.databinding.ActivityMainBinding
import com.arihant.streaks.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ forces edge-to-edge for targetSdk 35; opt in explicitly so
        // older versions behave the same and the system bars get proper scrims
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the floating nav pill above the system navigation bar
        val pillBaseMargin =
                (binding.navPill.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.navPill) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = pillBaseMargin + navBar
            }
            insets
        }

        // Load streaks from persistent storage
        settingsViewModel.loadStreaksFromFile()

        // Apply the saved theme on launch (previously only applied when opening Settings)
        lifecycleScope.launch {
            settingsViewModel.theme.filterNotNull().collect { theme ->
                val mode =
                        when (theme) {
                            "light" -> AppCompatDelegate.MODE_NIGHT_NO
                            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }

        // FragmentContainerView: the NavController must be fetched from the
        // NavHostFragment, findNavController() fails during onCreate
        val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
                        as NavHostFragment
        val navController = navHostFragment.navController

        binding.tabHome.setOnClickListener {
            if (navController.currentDestination?.id != R.id.navigation_home) {
                navController.navigate(R.id.navigation_home)
            }
        }
        binding.tabSettings.setOnClickListener {
            if (navController.currentDestination?.id != R.id.navigation_settings) {
                navController.navigate(R.id.navigation_settings)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home -> setSelectedTab(0)
                R.id.navigation_settings -> setSelectedTab(1)
                // Other destinations (e.g. streak details) keep the last selection
            }
        }
    }

    private fun setSelectedTab(index: Int) {
        val tabWidth = 100f * resources.displayMetrics.density
        binding.navIndicator
                .animate()
                .translationX(index * tabWidth)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()

        val orange = getColor(R.color.orange)
        val gray = getColor(R.color.gray_dark)
        binding.iconHome.setColorFilter(if (index == 0) orange else gray)
        binding.iconSettings.setColorFilter(if (index == 1) orange else gray)
    }
}
