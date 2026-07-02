package com.arihant.streaks.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.arihant.streaks.MainActivity
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.StreakRepository

class StreaksWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.arihant.streaks.widget.TOGGLE_STREAK"
        const val EXTRA_STREAK_ID = "streak_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val streakId = intent.getStringExtra(EXTRA_STREAK_ID)
            if (streakId != null) {
                // The app process may be dead, so hydrate from disk first
                val repository = StreakRepository.getInstance()
                repository.loadStreaksFromFile(context)
                val streak = repository.streaks.value?.find { it.id == streakId }
                if (streak != null) {
                    // completeStreak/uncompleteStreak save and re-trigger a widget update
                    if (streak.isCompletedToday) {
                        repository.uncompleteStreak(streakId, context)
                    } else {
                        repository.completeStreak(streakId, context)
                    }
                }
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_streaks)

            // Load streak data from repository
            val repository = StreakRepository.getInstance()
            repository.loadStreaksFromFile(context)
            val streaks = repository.streaks.value?.sortedBy { it.position } ?: emptyList()

            for (i in 0 until 4) {
                val columnId =
                        context.resources.getIdentifier(
                                "streak_column_$i",
                                "id",
                                context.packageName
                        )
                val iconId =
                        context.resources.getIdentifier("streak_icon_$i", "id", context.packageName)
                val countId =
                        context.resources.getIdentifier(
                                "streak_count_$i",
                                "id",
                                context.packageName
                        )
                val unitId =
                        context.resources.getIdentifier("streak_unit_$i", "id", context.packageName)
                if (i < streaks.size) {
                    val streak = streaks[i]
                    views.setViewVisibility(columnId, android.view.View.VISIBLE)
                    views.setTextViewText(iconId, streak.emoji)
                    views.setTextViewText(countId, streak.currentStreak.toString())
                    views.setTextViewText(
                            unitId,
                            getUnitLabel(streak.frequency, streak.currentStreak)
                    )

                    // Completed today: tint the count in the streak's color
                    val streakColor =
                            try {
                                android.graphics.Color.parseColor(streak.color)
                            } catch (e: Exception) {
                                android.graphics.Color.parseColor("#FF9900")
                            }
                    views.setTextColor(
                            countId,
                            if (streak.isCompletedToday) streakColor
                            else context.getColor(R.color.black)
                    )

                    // Tap a column to toggle today's completion without opening the app
                    val toggleIntent =
                            Intent(context, StreaksWidgetProvider::class.java).apply {
                                action = ACTION_TOGGLE
                                putExtra(EXTRA_STREAK_ID, streak.id)
                                data = android.net.Uri.parse("streaks://widget/toggle/${streak.id}")
                            }
                    val togglePendingIntent =
                            PendingIntent.getBroadcast(
                                    context,
                                    i,
                                    toggleIntent,
                                    PendingIntent.FLAG_IMMUTABLE or
                                            PendingIntent.FLAG_UPDATE_CURRENT
                            )
                    views.setOnClickPendingIntent(columnId, togglePendingIntent)
                } else {
                    views.setViewVisibility(columnId, android.view.View.GONE)
                }
            }

            // Set up click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent =
                    PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    PendingIntent.FLAG_IMMUTABLE
                            else 0
                    )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getUnitLabel(frequency: FrequencyType, count: Int): String {
        return when (frequency) {
            FrequencyType.DAILY -> if (count == 1) "day" else "days"
            FrequencyType.WEEKLY -> if (count == 1) "week" else "weeks"
            FrequencyType.MONTHLY -> if (count == 1) "month" else "months"
            FrequencyType.YEARLY -> if (count == 1) "year" else "years"
        }
    }
}
