package com.arihant.streaks.utils

import android.content.Context
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * App-wide first-day-of-week setting. The value is cached in memory so week
 * math in the repository and adapters (which have no Context) stays
 * synchronous; call [init] from any entry point that loads streak data.
 */
object WeekConfig {
    private const val PREFS_NAME = "week_prefs"
    private const val KEY_FIRST_DAY = "first_day_of_week" // ISO value, 1=Monday .. 7=Sunday

    @Volatile private var cached: DayOfWeek? = null

    fun init(context: Context) {
        if (cached != null) return
        val stored =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(KEY_FIRST_DAY, 0)
        cached =
                if (stored in 1..7) DayOfWeek.of(stored)
                else WeekFields.of(Locale.getDefault()).firstDayOfWeek
    }

    val firstDayOfWeek: DayOfWeek
        get() = cached ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek

    fun weekFields(): WeekFields = WeekFields.of(firstDayOfWeek, 1)

    fun setFirstDayOfWeek(context: Context, day: DayOfWeek) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_FIRST_DAY, day.value)
                .apply()
        cached = day
    }
}
