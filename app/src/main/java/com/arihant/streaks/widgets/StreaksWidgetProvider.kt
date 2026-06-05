package com.arihant.streaks.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.arihant.streaks.MainActivity
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.StreakRepository

class StreaksWidgetProvider : AppWidgetProvider() {

    private val TWO_ROW_THRESHOLD_DP = 120

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val options   = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val twoRows   = minHeight >= TWO_ROW_THRESHOLD_DP

        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(context)
        val streaks = repository.streaks.value?.sortedBy { it.position } ?: emptyList()

        val views = if (twoRows) buildTwoRowViews(context, streaks)
                    else         buildOneRowViews(context, streaks)

        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun buildOneRowViews(context: Context, streaks: List<com.arihant.streaks.data.Streak>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_streaks_1row)
        populateColumns(context, views, streaks)
        return views
    }

    private fun buildTwoRowViews(context: Context, streaks: List<com.arihant.streaks.data.Streak>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_streaks)
        views.setViewVisibility(R.id.widget_row_2, if (streaks.size > 4) View.VISIBLE else View.GONE)
        populateColumns(context, views, streaks)
        return views
    }

    private fun populateColumns(context: Context, views: RemoteViews, streaks: List<com.arihant.streaks.data.Streak>) {
        for (i in 0 until 8) {
            val colId   = context.resources.getIdentifier("streak_column_$i", "id", context.packageName)
            val iconId  = context.resources.getIdentifier("streak_icon_$i",   "id", context.packageName)
            val countId = context.resources.getIdentifier("streak_count_$i",  "id", context.packageName)
            val unitId  = context.resources.getIdentifier("streak_unit_$i",   "id", context.packageName)
            if (i < streaks.size) {
                val s = streaks[i]
                views.setViewVisibility(colId, View.VISIBLE)
                views.setTextViewText(iconId,  s.emoji)
                views.setTextViewText(countId, s.currentStreak.toString())
                views.setTextViewText(unitId,  unit(s.frequency, s.currentStreak))
            } else {
                views.setViewVisibility(colId, View.GONE)
            }
        }
    }

    private fun unit(f: FrequencyType, n: Int) = when (f) {
        FrequencyType.DAILY   -> if (n == 1) "day"   else "days"
        FrequencyType.WEEKLY  -> if (n == 1) "week"  else "weeks"
        FrequencyType.MONTHLY -> if (n == 1) "month" else "months"
        FrequencyType.YEARLY  -> if (n == 1) "year"  else "years"
    }
}
