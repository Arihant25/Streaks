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

// Single process-wide DataStore delegate. Declaring a second delegate for the same file name
// crashes at runtime ("There are multiple DataStores active for the same file"), which is
// exactly what the old SettingsViewModel + ReminderWorker pair could do — so every reader
// must go through this object.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsStore {

    val THEME_KEY = stringPreferencesKey("theme")
    val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
    val SHOW_WEEK_GRAPH_KEY = booleanPreferencesKey("show_week_graph")

    fun data(context: Context): Flow<Preferences> =
            context.applicationContext.settingsDataStore.data

    suspend fun edit(context: Context, transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.applicationContext.settingsDataStore.edit(transform)
    }

    /** For BroadcastReceivers where suspending is not an option; the read is a few ms. */
    fun notificationsEnabledBlocking(context: Context): Boolean = runBlocking {
        data(context).map { it[NOTIFICATIONS_KEY] ?: false }.first()
    }
}
