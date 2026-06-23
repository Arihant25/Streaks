package com.arihant.streaks.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.arihant.streaks.MainActivity
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository

/**
 * Home-screen widget. Shows up to 8 streaks (one or two rows depending on the widget height).
 *
 * Tap zones: the streak block itself (emoji + numbers) toggles today's completion, while the
 * header (app icon / title), the gaps between streaks and the rest of the background open the
 * app — so opening the app no longer requires pixel-hunting around the toggle targets.
 */
class StreaksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val twoRows = minHeight >= TWO_ROW_THRESHOLD_DP

        val repository = StreakRepository.getInstance(context)
        repository.ensureLoaded()
        val streaks = repository.getStreaks()

        val views = RemoteViews(
            context.packageName,
            if (twoRows) R.layout.widget_streaks else R.layout.widget_streaks_1row
        )
        if (twoRows) {
            views.setViewVisibility(
                R.id.widget_row_2,
                if (streaks.size > 4) View.VISIBLE else View.GONE
            )
        }
        populateColumns(context, views, streaks)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_header, openApp)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun populateColumns(context: Context, views: RemoteViews, streaks: List<Streak>) {
        for (i in COLUMN_IDS.indices) {
            if (i < streaks.size) {
                val streak = streaks[i]
                val done = streak.isCompletedToday

                views.setViewVisibility(COLUMN_IDS[i], View.VISIBLE)
                views.setTextViewText(ICON_IDS[i], streak.emoji)
                views.setTextViewText(COUNT_IDS[i], streak.currentStreak.toString())
                val unit = unit(streak.frequency, streak.currentStreak)
                views.setTextViewText(UNIT_IDS[i], if (done) "✓ $unit" else unit)

                // Color handling is theme-aware. For NOT-done streaks we leave the layout's
                // adaptive @color/black untouched: when the launcher re-applies the cached
                // RemoteViews after a light/dark switch (without firing onUpdate), the XML
                // color re-resolves to the right shade. Baking a color here used to freeze it
                // to the old theme, so the numbers vanished into the new background until a
                // tap forced a rebuild. The accent (done) color is theme-independent, so it is
                // safe to set explicitly.
                if (done) {
                    val accent = try {
                        Color.parseColor(streak.color)
                    } catch (e: IllegalArgumentException) {
                        Color.parseColor(Streak.DEFAULT_COLOR)
                    }
                    views.setTextColor(COUNT_IDS[i], accent)
                    views.setTextColor(UNIT_IDS[i], accent)
                }
                views.setOnClickPendingIntent(
                    INNER_IDS[i],
                    com.arihant.streaks.notifications.StreakActionReceiver
                        .togglePendingIntent(context, streak.id)
                )
            } else {
                views.setViewVisibility(COLUMN_IDS[i], View.GONE)
            }
        }
    }

    private fun unit(frequency: FrequencyType, n: Int) = when (frequency) {
        FrequencyType.DAILY -> if (n == 1) "day" else "days"
        FrequencyType.WEEKLY -> if (n == 1) "week" else "weeks"
        FrequencyType.MONTHLY -> if (n == 1) "month" else "months"
        FrequencyType.YEARLY -> if (n == 1) "year" else "years"
    }

    companion object {
        private const val TWO_ROW_THRESHOLD_DP = 120

        private val COLUMN_IDS = intArrayOf(
            R.id.streak_column_0, R.id.streak_column_1, R.id.streak_column_2, R.id.streak_column_3,
            R.id.streak_column_4, R.id.streak_column_5, R.id.streak_column_6, R.id.streak_column_7
        )
        private val INNER_IDS = intArrayOf(
            R.id.streak_inner_0, R.id.streak_inner_1, R.id.streak_inner_2, R.id.streak_inner_3,
            R.id.streak_inner_4, R.id.streak_inner_5, R.id.streak_inner_6, R.id.streak_inner_7
        )
        private val ICON_IDS = intArrayOf(
            R.id.streak_icon_0, R.id.streak_icon_1, R.id.streak_icon_2, R.id.streak_icon_3,
            R.id.streak_icon_4, R.id.streak_icon_5, R.id.streak_icon_6, R.id.streak_icon_7
        )
        private val COUNT_IDS = intArrayOf(
            R.id.streak_count_0, R.id.streak_count_1, R.id.streak_count_2, R.id.streak_count_3,
            R.id.streak_count_4, R.id.streak_count_5, R.id.streak_count_6, R.id.streak_count_7
        )
        private val UNIT_IDS = intArrayOf(
            R.id.streak_unit_0, R.id.streak_unit_1, R.id.streak_unit_2, R.id.streak_unit_3,
            R.id.streak_unit_4, R.id.streak_unit_5, R.id.streak_unit_6, R.id.streak_unit_7
        )
    }
}
