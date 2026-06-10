package com.arihant.streaks.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.arihant.streaks.R
import com.arihant.streaks.StreaksApp
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.StreakExportDto
import com.arihant.streaks.databinding.FragmentSettingsBinding
import com.arihant.streaks.notifications.Notifications
import com.arihant.streaks.notifications.ReminderScheduler
import com.arihant.streaks.utils.PermissionHelper
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding!!

    private val settingsViewModel: SettingsViewModel by viewModels()

    private val themeValues = listOf(
        SettingsStore.THEME_SYSTEM, SettingsStore.THEME_LIGHT, SettingsStore.THEME_DARK
    )

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportDataToUri(it) }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importDataFromUri(it) }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    requireContext(), R.string.notification_permission_denied, Toast.LENGTH_SHORT
                ).show()
                binding.switchEnableNotifications.isChecked = false
                settingsViewModel.setNotificationsEnabled(false)
            }
        }

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        setupNotificationSwitch()
        setupThemeSpinner()
        setupWeekStartSwitch()
        setupExportImportButtons()
        setupMiscButtons()
        observeSettings()

        return binding.root
    }

    private fun setupNotificationSwitch() {
        binding.switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            // Programmatic sync from the observer — don't re-run permission prompts
            if (isChecked == settingsViewModel.notificationsEnabled.value) {
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!PermissionHelper.canScheduleExactAlarms(requireContext())) {
                    PermissionHelper.requestExactAlarmPermission(this)
                }
            }
            settingsViewModel.setNotificationsEnabled(isChecked)
        }
    }

    private fun setupThemeSpinner() {
        val themeLabels = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, themeLabels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val theme = themeValues[position]
                // The spinner fires once on layout and on programmatic setSelection();
                // writing then would clobber the saved theme before DataStore emits it
                if (theme == settingsViewModel.theme.value) return
                settingsViewModel.setTheme(theme)
                StreaksApp.applyTheme(theme)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupWeekStartSwitch() {
        binding.switchWeekStartsMonday.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.setWeekStartsMonday(isChecked)
        }
    }

    private fun setupExportImportButtons() {
        binding.btnExportData.setOnClickListener { exportLauncher.launch("streaks_export.json") }
        binding.btnImportData.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun setupMiscButtons() {
        binding.btnNotificationChannelSettings.setOnClickListener {
            PermissionHelper.showNotificationChannelSettings(this)
        }
        binding.btnTestNotification.setOnClickListener {
            if (Notifications.canPost(requireContext())) {
                Notifications.showTest(requireContext())
                Toast.makeText(requireContext(), R.string.test_notification_sent, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    requireContext(), R.string.notification_permission_required, Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.textPrivacyPolicy.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://Arihant25.github.io/streaks/privacy-policy.html")
                )
            )
        }
    }

    // ── Export / import ───────────────────────────────────────────────────────

    private fun exportDataToUri(uri: Uri) {
        val exportObj = mapOf(
            "settings" to mapOf(
                "theme" to settingsViewModel.theme.value,
                "notifications_enabled" to settingsViewModel.notificationsEnabled.value,
                "week_starts_monday" to settingsViewModel.weekStartsMonday.value
            ),
            "streaks" to settingsViewModel.getStreaksForExport()
        )
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { it.write(Gson().toJson(exportObj)) }
            }
            Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importDataFromUri(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { it.readText() }
            } ?: throw IllegalArgumentException("Empty file")

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(json, type)

            val streaksJson = Gson().toJson(data["streaks"])
            val dtoListType = object : TypeToken<List<StreakExportDto>>() {}.type
            val dtoList: List<StreakExportDto>? = Gson().fromJson(streaksJson, dtoListType)
            if (dtoList.isNullOrEmpty()) {
                throw IllegalArgumentException("No streaks found or wrong format")
            }

            // toStreak() keeps frequencyHistory and position; replaceAll() recalculates
            val streaks = dtoList.map { it.toStreak() }

            @Suppress("UNCHECKED_CAST")
            val settingsMap = data["settings"] as? Map<String, Any>
            settingsMap?.let { settings ->
                val theme = settings["theme"] as? String ?: SettingsStore.THEME_SYSTEM
                val notifications = settings["notifications_enabled"] as? Boolean ?: false
                val weekMonday = settings["week_starts_monday"] as? Boolean
                settingsViewModel.setTheme(theme)
                settingsViewModel.setNotificationsEnabled(notifications)
                weekMonday?.let { settingsViewModel.setWeekStartsMonday(it) }
                StreaksApp.applyTheme(theme)
            }

            settingsViewModel.importStreaks(streaks)
            ReminderScheduler(requireContext()).rescheduleAll(settingsViewModel.getStreaks())

            Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ── State observers ───────────────────────────────────────────────────────

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.theme.collectLatest { theme ->
                val position = themeValues.indexOf(theme).coerceAtLeast(0)
                if (binding.spinnerTheme.selectedItemPosition != position) {
                    binding.spinnerTheme.setSelection(position)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.notificationsEnabled.collectLatest { enabled ->
                if (binding.switchEnableNotifications.isChecked != enabled) {
                    binding.switchEnableNotifications.isChecked = enabled
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.weekStartsMonday.collectLatest { monday ->
                if (binding.switchWeekStartsMonday.isChecked != monday) {
                    binding.switchWeekStartsMonday.isChecked = monday
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
