package com.example.streaks.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.streaks.databinding.FragmentSettingsBinding
import com.example.streaks.ui.dialogs.AddStreakDialog
import com.example.streaks.R
import com.example.streaks.data.Streak
import com.example.streaks.data.FrequencyType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import com.example.streaks.data.StreakExportDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(requireActivity().application) as T
            }
        }
    }

    private val themeOptions by lazy {
        listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportDataToUri(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importDataFromUri(it) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT).show()
            binding.switchEnableNotifications.isChecked = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        setupClickListeners()
        setupNotificationSwitch()
        setupThemeSpinner()
        setupExportImportButtons()
        observeSettings()

        return binding.root
    }

    private fun setupClickListeners() {
        binding.btnAddStreak.setOnClickListener {
            showAddStreakDialog()
        }
        binding.textPrivacyPolicy.setOnClickListener {
            val url = "https://Arihant25.github.io/streaks/privacy-policy.html"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun setupNotificationSwitch() {
        binding.switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            settingsViewModel.setNotificationEnabled(isChecked)
        }
    }

    private fun setupThemeSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter

        // Remove duplicate line
        binding.spinnerTheme.setSelection(0)
        
        // Fix spinner listener - use proper AdapterView.OnItemSelectedListener
        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val theme = when (position) {
                    1 -> "light"
                    2 -> "dark"
                    else -> "system"
                }
                settingsViewModel.setTheme(theme)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun setupExportImportButtons() {
        binding.btnExportData.setOnClickListener {
            exportLauncher.launch("streaks_export.json")
        }
        binding.btnImportData.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun exportDataToUri(uri: Uri) {
        val streaks = settingsViewModel.getStreaksForExportDto()
        val settings = mapOf(
            "theme" to settingsViewModel.theme.value,
            "notifications_enabled" to settingsViewModel.notificationsEnabled.value
        )
        val exportObj = mapOf(
            "settings" to settings,
            "streaks" to streaks
        )
        val json = Gson().toJson(exportObj)
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { writer ->
                    writer.write(json)
                }
            }
            Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importDataFromUri(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("Empty file")
            
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(json, type)
            
            // Handle settings with proper type casting
            val settingsMap = data["settings"] as? Map<String, Any>
            val streaksJson = Gson().toJson(data["streaks"])

            // Only support new format: StreakExportDto
            val streakExportListType = object : TypeToken<List<StreakExportDto>>() {}.type
            val streakExportList: List<StreakExportDto> = Gson().fromJson(streaksJson, streakExportListType)
            if (streakExportList.isNullOrEmpty()) throw Exception("No streaks found or wrong format")
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val streaks = streakExportList.map { dto ->
                Streak(
                    id = dto.id,
                    name = dto.name,
                    emoji = dto.emoji,
                    frequency = dto.frequency,
                    frequencyCount = dto.frequencyCount,
                    createdDate = LocalDate.parse(dto.createdDate, formatter),
                    lastCompletedDate = dto.lastCompletedDate?.let { LocalDate.parse(it, formatter) },
                    currentStreak = dto.currentStreak,
                    isCompletedToday = false // default
                )
            }
            
            // Restore settings
            settingsMap?.let { settings ->
                val theme = settings["theme"] as? String ?: "system"
                val notifications = settings["notifications_enabled"] as? Boolean ?: false
                settingsViewModel.setTheme(theme)
                settingsViewModel.setNotificationEnabled(notifications)
                binding.switchEnableNotifications.isChecked = notifications
                applyTheme(theme)
            }
            
            // Restore streaks
            settingsViewModel.setStreaksFromImport(streaks)
            Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.theme.collectLatest { theme ->
                val pos = when (theme) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                }
                if (binding.spinnerTheme.selectedItemPosition != pos) {
                    binding.spinnerTheme.setSelection(pos)
                }
                applyTheme(theme)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.notificationsEnabled.collectLatest { enabled ->
                if (binding.switchEnableNotifications.isChecked != enabled) {
                    binding.switchEnableNotifications.isChecked = enabled
                }
            }
        }
    }

    private fun showAddStreakDialog() {
        val dialog = AddStreakDialog { name, emoji, frequency, frequencyCount ->
            settingsViewModel.addStreak(name, emoji, frequency, frequencyCount)
        }
        dialog.show(parentFragmentManager, "AddStreakDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}