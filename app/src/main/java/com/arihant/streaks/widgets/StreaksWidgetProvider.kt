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

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
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
        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(context)
        val streaks = repository.streaks.value?.sortedBy { it.position } ?: emptyList()

        // Determine widget height in dp
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        // Use max height in portrait, min height in landscape (standard Android practice)
        val heightDp = if (maxHeight > 0) maxHeight else minHeight

        // One row ≈ 80dp, two rows ≈ 160dp+
        val twoRows = heightDp >= 120

        val layoutRes = if (twoRows) R.layout.widget_streaks else R.layout.widget_streaks_1row
        val views = RemoteViews(context.packageName, layoutRes)

        if (twoRows) {
            // Show or hide second row depending on streak count
            views.setViewVisibility(
                R.id.widget_row_2,
                if (streaks.size > 4) View.VISIBLE else View.GONE
            )
        }

        for (i in 0 until 8) {
            val columnId = context.resources.getIdentifier("streak_column_$i", "id", context.packageName)
            val iconId   = context.resources.getIdentifier("streak_icon_$i",   "id", context.packageName)
            val countId  = context.resources.getIdentifier("streak_count_$i",  "id", context.packageName)
            val unitId   = context.resources.getIdentifier("streak_unit_$i",   "id", context.packageName)

            if (columnId == 0) continue  // ID not in this layout

            if (i < streaks.size) {
                val streak = streaks[i]
                views.setViewVisibility(columnId, View.VISIBLE)
                views.setTextViewText(iconId, streak.emoji)
                views.setTextViewText(countId, streak.currentStreak.toString())
                views.setTextViewText(unitId, getUnitLabel(streak.frequency, streak.currentStreak))
            } else {
                views.setViewVisibility(columnId, View.GONE)
            }
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getUnitLabel(frequency: FrequencyType, count: Int): String {
        return when (frequency) {
            FrequencyType.DAILY   -> if (count == 1) "day"   else "days"
            FrequencyType.WEEKLY  -> if (count == 1) "week"  else "weeks"
            FrequencyType.MONTHLY -> if (count == 1) "month" else "months"
            FrequencyType.YEARLY  -> if (count == 1) "year"  else "years"
        }
    }
}
