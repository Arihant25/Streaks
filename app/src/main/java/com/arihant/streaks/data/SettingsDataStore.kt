package com.arihant.streaks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

// The one process-wide DataStore for the "settings" file. DataStore throws
// IllegalStateException if two delegates for the same file are ever read in
// the same process (e.g. ReminderReceiver firing while the app is open), so
// every reader must go through this property.
val Context.settingsDataStore: DataStore<Preferences> by
        preferencesDataStore(
                name = "settings",
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
        )
