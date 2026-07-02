package com.arihant.streaks.widgets

import android.content.Context
import com.arihant.streaks.data.Streak

/**
 * Per-widget configuration, keyed by appWidgetId. A null [Config.selectedIds] means
 * "all streaks, including ones created later"; a non-null set is an explicit filter.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_MODE = "mode_"
    private const val KEY_STREAKS = "streaks_"

    enum class Mode {
        AUTO,
        GRID,
        LIST
    }

    data class Config(val mode: Mode = Mode.AUTO, val selectedIds: Set<String>? = null) {
        fun filter(streaks: List<Streak>): List<Streak> =
                selectedIds?.let { ids -> streaks.filter { it.id in ids } } ?: streaks
    }

    fun load(context: Context, appWidgetId: Int): Config {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode =
                prefs.getString(KEY_MODE + appWidgetId, null)?.let {
                    runCatching { Mode.valueOf(it) }.getOrNull()
                }
                        ?: Mode.AUTO
        val ids = prefs.getStringSet(KEY_STREAKS + appWidgetId, null)
        return Config(mode, ids)
    }

    fun save(context: Context, appWidgetId: Int, config: Config) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_MODE + appWidgetId, config.mode.name)
        if (config.selectedIds != null) {
            editor.putStringSet(KEY_STREAKS + appWidgetId, config.selectedIds)
        } else {
            editor.remove(KEY_STREAKS + appWidgetId)
        }
        editor.apply()
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            editor.remove(KEY_MODE + id)
            editor.remove(KEY_STREAKS + id)
        }
        editor.apply()
    }
}
