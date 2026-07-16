package com.arihant.streaks.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakExportDto
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.data.settingsDataStore
import com.arihant.streaks.utils.WeekConfig
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StreakRepository.getInstance()
    private val context = getApplication<Application>().applicationContext

    private val THEME_KEY = stringPreferencesKey("theme")
    private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
    private val SHOW_WEEK_GRAPH_KEY = booleanPreferencesKey("show_week_graph")

    // null until the stored value loads, so observers never act on a fake default
    private val _theme = MutableStateFlow<String?>(null)
    val theme: StateFlow<String?> = _theme

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _showWeekGraph = MutableStateFlow<Boolean?>(null)
    val showWeekGraph: StateFlow<Boolean?> = _showWeekGraph

    // Expose streaks for export/import
    val streaksLiveData: LiveData<List<Streak>> = repository.streaks

    // An IO failure while reading would otherwise cancel viewModelScope and
    // crash the process; fall back to defaults instead
    private val settingsFlow =
            context.settingsDataStore.data.catch { emit(emptyPreferences()) }

    init {
        viewModelScope.launch {
            settingsFlow.map { prefs -> prefs[THEME_KEY] ?: "system" }.collect {
                _theme.value = it
            }
        }
        viewModelScope.launch {
            settingsFlow.map { prefs -> prefs[NOTIFICATIONS_KEY] ?: false }.collect {
                _notificationsEnabled.value = it
            }
        }
        viewModelScope.launch {
            settingsFlow.map { prefs -> prefs[SHOW_WEEK_GRAPH_KEY] ?: true }.collect {
                _showWeekGraph.value = it
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs -> prefs[THEME_KEY] = theme }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs -> prefs[NOTIFICATIONS_KEY] = enabled }
        }
    }

    fun setShowWeekGraph(show: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs -> prefs[SHOW_WEEK_GRAPH_KEY] = show }
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
            color: String = "#FF9900"
    ) {
        repository.addStreak(name, emoji, frequency, frequencyCount, context, color)
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

    fun getStreaksForExportDto(): List<StreakExportDto> {
        return (streaksLiveData.value ?: emptyList()).map { streak ->
            StreakExportDto(
                    id = streak.id,
                    name = streak.name,
                    emoji = streak.emoji,
                    frequency = streak.frequency,
                    frequencyCount = streak.frequencyCount,
                    createdDate = streak.createdDate,
                    lastCompletedDate = streak.lastCompletedDate,
                    currentStreak = streak.currentStreak,
                    bestStreak = streak.bestStreak,
                    completions = streak.completions,
                    reminder = streak.reminder,
                    color = streak.color
            )
        }
    }
}
