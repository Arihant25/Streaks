package com.arihant.streaks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Single process-wide DataStore delegate. Declaring a second delegate for the same file
// crashes at runtime, so every reader must go through this object.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsStore {

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private val THEME_KEY = stringPreferencesKey("theme")
    private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

    private const val PREFS_NAME = "streaks_settings"
    private const val KEY_WEEK_MONDAY = "week_starts_monday"

    private fun dataStore(context: Context): DataStore<Preferences> =
        context.applicationContext.settingsDataStore

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun themeFlow(context: Context): Flow<String> =
        dataStore(context).data.map { it[THEME_KEY] ?: THEME_SYSTEM }

    /** Used once at process start, before any UI exists. */
    fun themeBlocking(context: Context): String =
        runBlocking { themeFlow(context).first() }

    suspend fun setTheme(context: Context, theme: String) {
        dataStore(context).edit { it[THEME_KEY] = theme }
    }

    // ── Notifications master switch ───────────────────────────────────────────

    fun notificationsEnabledFlow(context: Context): Flow<Boolean> =
        dataStore(context).data.map { it[NOTIFICATIONS_KEY] ?: false }

    /** Used from BroadcastReceivers where suspending is not an option. */
    fun notificationsEnabledBlocking(context: Context): Boolean =
        runBlocking { notificationsEnabledFlow(context).first() }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        dataStore(context).edit { it[NOTIFICATIONS_KEY] = enabled }
    }

    // ── Week start ────────────────────────────────────────────────────────────

    fun weekStartsMonday(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WEEK_MONDAY, false)

    fun setWeekStartsMonday(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WEEK_MONDAY, enabled).apply()
    }
}
