package com.arihant.streaks.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arihant.streaks.data.SettingsStore
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakExportDto
import com.arihant.streaks.data.StreakRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StreakRepository.getInstance(application)
    private val context = application.applicationContext

    private val _theme = MutableStateFlow(SettingsStore.THEME_SYSTEM)
    val theme: StateFlow<String> = _theme

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _weekStartsMonday = MutableStateFlow(SettingsStore.weekStartsMonday(context))
    val weekStartsMonday: StateFlow<Boolean> = _weekStartsMonday

    init {
        viewModelScope.launch {
            SettingsStore.themeFlow(context).collect { _theme.value = it }
        }
        viewModelScope.launch {
            SettingsStore.notificationsEnabledFlow(context).collect {
                _notificationsEnabled.value = it
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { SettingsStore.setTheme(context, theme) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { SettingsStore.setNotificationsEnabled(context, enabled) }
    }

    fun setWeekStartsMonday(enabled: Boolean) {
        if (_weekStartsMonday.value == enabled) return
        _weekStartsMonday.value = enabled
        SettingsStore.setWeekStartsMonday(context, enabled)
        // Weekly periods are defined by the week start, so the numbers must be recomputed
        repository.recalculateAll()
    }

    fun getStreaksForExport(): List<StreakExportDto> = repository.getStreaks().map { it.toDto() }

    fun getStreaks(): List<Streak> = repository.getStreaks()

    fun importStreaks(streaks: List<Streak>) = repository.replaceAll(streaks)
}
