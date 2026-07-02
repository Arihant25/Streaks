package com.arihant.streaks.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.arihant.streaks.R
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository

class StreaksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId =
                intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                )
        return StreaksWidgetFactory(applicationContext, appWidgetId)
    }
}

private class StreaksWidgetFactory(
        private val context: Context,
        private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var streaks: List<Streak> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val config = WidgetPrefs.load(context, appWidgetId)
        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(context)
        streaks = config.filter(repository.streaks.value ?: emptyList()).sortedBy { it.position }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = streaks.size

    override fun getViewAt(position: Int): RemoteViews? {
        val streak = streaks.getOrNull(position) ?: return null
        val views = RemoteViews(context.packageName, R.layout.widget_streak_list_item)

        views.setTextViewText(R.id.item_emoji, streak.emoji)
        views.setTextViewText(R.id.item_name, streak.name)
        val unit = StreaksWidgetProvider.getUnitLabel(streak.frequency, streak.currentStreak)
        views.setTextViewText(
                R.id.item_streak,
                context.getString(
                        R.string.widget_streak_summary,
                        streak.currentStreak,
                        unit,
                        streak.bestStreak
                )
        )

        val color = StreaksWidgetProvider.parseStreakColor(streak.color)
        if (streak.isCompletedToday) {
            views.setImageViewResource(R.id.item_check, R.drawable.ic_widget_check)
            views.setInt(R.id.item_check, "setColorFilter", color)
        } else {
            views.setImageViewResource(R.id.item_check, R.drawable.ic_widget_circle)
            views.setInt(R.id.item_check, "setColorFilter", context.getColor(R.color.gray_dark))
        }
        views.setTextColor(
                R.id.item_name,
                if (streak.isCompletedToday) color else context.getColor(R.color.black)
        )

        // Fills in the pending intent template set on the ListView by the provider
        val fillIn =
                Intent().apply {
                    putExtra(StreaksWidgetProvider.EXTRA_STREAK_ID, streak.id)
                    data = Uri.parse("streaks://widget/toggle/${streak.id}")
                }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
            streaks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
