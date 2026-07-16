package com.arihant.streaks.ui.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.arihant.streaks.R
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakExportDto
import com.arihant.streaks.databinding.FragmentSettingsBinding
import com.arihant.streaks.ui.dialogs.AddStreakDialog
import com.arihant.streaks.utils.PermissionHelper
import com.arihant.streaks.utils.WeekConfig
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    companion object {
        private const val REQUEST_ADD_STREAK = "settings_add_streak"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding!!

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return modelClass.cast(SettingsViewModel(requireActivity().application))!!
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

    private val exportLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
                    uri: Uri? ->
                uri?.let { exportDataToUri(it) }
            }

    private val importLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let { importDataFromUri(it) }
            }

    private val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(
                                    requireContext(),
                                    "Notification permission denied",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    binding.switchEnableNotifications.isChecked = false
                }
            }

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Edge-to-edge: keep content below the status bar and above the nav bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars =
                    insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    )
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        // Re-registered on every view creation so the sheet can deliver its
        // result even after rotation or process death recreates this fragment
        parentFragmentManager.setFragmentResultListener(
                REQUEST_ADD_STREAK,
                viewLifecycleOwner
        ) { _, result ->
            settingsViewModel.addStreak(
                    result.getString(AddStreakDialog.RESULT_NAME)!!,
                    result.getString(AddStreakDialog.RESULT_EMOJI)!!,
                    com.arihant.streaks.data.FrequencyType.valueOf(
                            result.getString(AddStreakDialog.RESULT_FREQUENCY)!!
                    ),
                    result.getInt(AddStreakDialog.RESULT_FREQUENCY_COUNT),
                    result.getString(AddStreakDialog.RESULT_COLOR)!!
            )
        }

        setupClickListeners()
        setupNotificationSwitch()
        setupNotificationChannelButton()
        setupTestNotificationButton()
        setupThemeSelector()
        setupFirstDaySelector()
        setupExportImportButtons()
        
    
        observeSettings()

        // Request notification permission if enabled but not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsEnabled = binding.switchEnableNotifications.isChecked
            val permissionGranted =
                    requireContext()
                            .checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
            if (notificationsEnabled && !permissionGranted) {
                notificationPermissionLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        return binding.root
    }

    private fun setupClickListeners() {
        binding.btnAddStreak.setOnClickListener { showAddStreakDialog() }
        binding.textPrivacyPolicy.setOnClickListener {
            openUrl("https://Arihant25.github.io/streaks/privacy-policy.html")
        }
        binding.rowAuthor.setOnClickListener { openUrl("https://arihant25.github.io") }
        binding.rowSourceCode.setOnClickListener {
            openUrl("https://github.com/Arihant25/streaks")
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    private fun setupNotificationSwitch() {
        binding.switchEnableNotifications.setOnCheckedChangeListener { _, isChecked ->
            
        if (isChecked) {
                // Check notification permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionGranted =
                            requireContext()
                                    .checkSelfPermission(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!permissionGranted) {
                        notificationPermissionLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS
                        )
                        return@setOnCheckedChangeListener
                    }
                }

                // Check exact alarm permission
                if (!PermissionHelper.checkExactAlarmPermission(requireContext())) {
                    PermissionHelper.requestExactAlarmPermission(this)
                }
            }
            settingsViewModel.setNotificationEnabled(isChecked)
        }
    }

    private fun setupThemeSelector() {
        binding.rowTheme.setOnClickListener {
            val current =
                    when (settingsViewModel.theme.value) {
                        "light" -> 1
                        "dark" -> 2
                        else -> 0
                    }
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.theme))
                    .setSingleChoiceItems(themeOptions.toTypedArray(), current) { dialog, which ->
                        val theme =
                                when (which) {
                                    1 -> "light"
                                    2 -> "dark"
                                    else -> "system"
                                }
                        // Persist only; MainActivity observes the store and applies night mode
                        settingsViewModel.setTheme(theme)
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
        }
    }

    private fun setupFirstDaySelector() {
        WeekConfig.init(requireContext())
        val days = DayOfWeek.values() // MONDAY..SUNDAY
        val names = days.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
        binding.textFirstDayValue.text = names[WeekConfig.firstDayOfWeek.value - 1]

        binding.rowFirstDay.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.start_week_on))
                    .setSingleChoiceItems(
                            names.toTypedArray(),
                            WeekConfig.firstDayOfWeek.value - 1
                    ) { dialog, which ->
                        val day = days[which]
                        if (day != WeekConfig.firstDayOfWeek) {
                            settingsViewModel.setFirstDayOfWeek(day)
                        }
                        binding.textFirstDayValue.text = names[which]
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
        }
    }

    private fun setupExportImportButtons() {
        binding.btnExportData.setOnClickListener { exportLauncher.launch("streaks_export.json") }
        binding.btnImportData.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun exportDataToUri(uri: Uri) {
        val streaks = settingsViewModel.getStreaksForExportDto()
        val settings =
                mapOf(
                        "theme" to (settingsViewModel.theme.value ?: "system"),
                        "notifications_enabled" to settingsViewModel.notificationsEnabled.value,
                        "first_day_of_week" to WeekConfig.firstDayOfWeek.value
                )
        val exportObj = mapOf("settings" to settings, "streaks" to streaks)
        val json = Gson().toJson(exportObj)
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out).use { writer -> writer.write(json) }
            }
            Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun importDataFromUri(uri: Uri) {
        try {
            val json =
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input)).use { reader -> reader.readText() }
                    }
                            ?: throw Exception("Empty file")

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(json, type)

            // Handle settings with proper type casting
            val settingsMap = data["settings"] as? Map<String, Any>
            val streaksJson = Gson().toJson(data["streaks"])

            // Only support new format: StreakExportDto
            val streakExportListType = object : TypeToken<List<StreakExportDto>>() {}.type
            val streakExportList: List<StreakExportDto> =
                    Gson().fromJson(streaksJson, streakExportListType)
            if (streakExportList.isNullOrEmpty())
                    throw Exception("No streaks found or wrong format")
            val streaks =
                    streakExportList.map { dto ->
                        val todayStr = java.time.LocalDate.now().toString()
                        val isCompletedToday = dto.lastCompletedDate == todayStr
                        Streak(
                                id = dto.id,
                                name = dto.name,
                                emoji = dto.emoji,
                                frequency = dto.frequency,
                                frequencyCount = dto.frequencyCount,
                                createdDate = dto.createdDate,
                                lastCompletedDate = dto.lastCompletedDate,
                                currentStreak = dto.currentStreak,
                                bestStreak = dto.bestStreak,
                                isCompletedToday = isCompletedToday,
                                completions = dto.completions ?: emptyList(),
                                reminder = dto.reminder,
                                color = dto.color ?: "#FF9900"
                        )
                    }

            // Restore settings
            settingsMap?.let { settings ->
                val theme = settings["theme"] as? String ?: "system"
                val notifications = settings["notifications_enabled"] as? Boolean ?: false
                settingsViewModel.setTheme(theme)
                settingsViewModel.setNotificationEnabled(notifications)
                binding.switchEnableNotifications.isChecked = notifications
                // Gson parses JSON numbers as Double
                val firstDay = (settings["first_day_of_week"] as? Double)?.toInt()
                if (firstDay != null && firstDay in 1..7) {
                    settingsViewModel.setFirstDayOfWeek(DayOfWeek.of(firstDay))
                    binding.textFirstDayValue.text =
                            DayOfWeek.of(firstDay)
                                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
            }
            // Restore streaks
            settingsViewModel.setStreaksFromImport(streaks)
            // Schedule reminders for imported streaks using new NotificationScheduler
            val scheduler = com.arihant.streaks.utils.NotificationScheduler(requireContext())
            streaks.forEach { streak ->
                streak.reminder?.let { reminder ->
                    scheduler.scheduleReminder(streak.id, streak.name, reminder)
                }
            }
            Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.theme.filterNotNull().collectLatest { theme ->
                val pos =
                        when (theme) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                binding.textThemeValue.text = themeOptions[pos]
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.notificationsEnabled.collectLatest { enabled ->
                if (binding.switchEnableNotifications.isChecked != enabled) {
                    binding.switchEnableNotifications.isChecked = enabled
                }
            }
        }
        // Only user toggles write to the store; programmatic syncs detach the
        // listener first so the stored value can't echo back and flip the switch
        val weekGraphListener =
                android.widget.CompoundButton.OnCheckedChangeListener { _, isChecked ->
                    settingsViewModel.setShowWeekGraph(isChecked)
                }
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.showWeekGraph.filterNotNull().collectLatest { show ->
                binding.switchShowWeekGraph.setOnCheckedChangeListener(null)
                binding.switchShowWeekGraph.isChecked = show
                binding.switchShowWeekGraph.setOnCheckedChangeListener(weekGraphListener)
            }
        }
    }

    private fun showAddStreakDialog() {
        val existingCount = settingsViewModel.streaksLiveData.value?.size ?: 0
        AddStreakDialog.newInstance(
                        requestKey = REQUEST_ADD_STREAK,
                        initialEmoji = AddStreakDialog.defaultEmojiFor(existingCount)
                )
                .show(parentFragmentManager, "AddStreakDialog")
    }

    private fun setupNotificationChannelButton() {
        binding.btnNotificationChannelSettings.setOnClickListener {
            PermissionHelper.showNotificationChannelSettings(this)
        }
    }

    private fun setupTestNotificationButton() {
        binding.btnTestNotification.setOnClickListener { sendTestNotification() }
    }
    private fun sendTestNotification() {
        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    "streak_reminder_channel",
                                    "Streak Reminders",
                                    NotificationManager.IMPORTANCE_HIGH
                            )
                            .apply {
                                description = "Notifications for streak reminders"
                                enableVibration(true)
                                enableLights(true)
                            }
            val notificationManager =
                    requireContext().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted =
                    requireContext()
                            .checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                Toast.makeText(
                                requireContext(),
                                "Notification permission required",
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return
            }
        }

        val notification =
                NotificationCompat.Builder(requireContext(), "streak_reminder_channel")
                        .setSmallIcon(com.arihant.streaks.R.drawable.ic_notification_24)
                        .setContentTitle("Test Notification")
                        .setContentText(
                                "This is a test notification to verify that notifications are working!"
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setVibrate(longArrayOf(0, 250, 250, 250))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .build()

        try {
            val notificationManager = NotificationManagerCompat.from(requireContext())
            notificationManager.notify(12345, notification)
            Toast.makeText(requireContext(), "Test notification sent!", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
