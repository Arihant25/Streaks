package com.arihant.streaks.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakExportDto
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.utils.WeekConfig
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StreakRepository.getInstance()
    private val context = getApplication<Application>().applicationContext

    // null until the stored value loads, so observers never act on a fake default
    private val _theme = MutableStateFlow<String?>(null)
    val theme: StateFlow<String?> = _theme

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _showWeekGraph = MutableStateFlow<Boolean?>(null)
    val showWeekGraph: StateFlow<Boolean?> = _showWeekGraph

    // Expose streaks for export/import
    val streaksLiveData: LiveData<List<Streak>> = repository.streaks

    init {
        viewModelScope.launch {
            SettingsStore.data(context)
                    .map { prefs -> prefs[SettingsStore.THEME_KEY] ?: "system" }
                    .collect { _theme.value = it }
        }
        viewModelScope.launch {
            SettingsStore.data(context)
                    .map { prefs -> prefs[SettingsStore.NOTIFICATIONS_KEY] ?: false }
                    .collect { _notificationsEnabled.value = it }
        }
        viewModelScope.launch {
            SettingsStore.data(context)
                    .map { prefs -> prefs[SettingsStore.SHOW_WEEK_GRAPH_KEY] ?: true }
                    .collect { _showWeekGraph.value = it }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            SettingsStore.edit(context) { prefs -> prefs[SettingsStore.THEME_KEY] = theme }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            SettingsStore.edit(context) { prefs -> prefs[SettingsStore.NOTIFICATIONS_KEY] = enabled }
        }
    }

    fun setShowWeekGraph(show: Boolean) {
        viewModelScope.launch {
            SettingsStore.edit(context) { prefs -> prefs[SettingsStore.SHOW_WEEK_GRAPH_KEY] = show }
        }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        WeekConfig.setFirstDayOfWeek(context, day)
        // Weekly periods shift with the week boundary, so re-derive streak counts
        repository.recalculateAllStreaks(context)
    }

    fun addStreak(
            name: String,
            emoji: String,
            frequency: FrequencyType,
            frequencyCount: Int,
            color: String = Streak.DEFAULT_COLOR,
            isNegative: Boolean = false
    ) {
        repository.addStreak(name, emoji, frequency, frequencyCount, context, color, isNegative)
    }

    fun getStreaksForExport(): List<Streak> {
        return streaksLiveData.value ?: emptyList()
    }

    fun setStreaksFromImport(streaks: List<Streak>) {
        repository.setStreaksFromImport(streaks, context)
    }

    fun loadStreaksFromFile() {
        repository.loadStreaksFromFile(context)
    }

    fun refreshIfDayChanged() {
        repository.refreshIfDayChanged(context)
    }

    /** toDto() keeps every field — including position, frequencyHistory and isNegative. */
    fun getStreaksForExportDto(): List<StreakExportDto> =
            (streaksLiveData.value ?: emptyList()).map { it.toDto() }
}
