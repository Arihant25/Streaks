package com.arihant.streaks.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.arihant.streaks.MainActivity
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository

class StreaksWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.arihant.streaks.widget.TOGGLE_STREAK"
        const val EXTRA_STREAK_ID = "streak_id"

        private const val MAX_COLUMNS = 8
        // Approximate dp per grid column. Wide enough for "months" under a
        // 3-digit count; anything tighter clips the unit label.
        private const val COLUMN_WIDTH_DP = 48
        // Tall enough to fit the streak name under each column
        private const val NAMED_GRID_HEIGHT_DP = 100
        // From this height on, AUTO mode switches to the scrollable list
        private const val LIST_HEIGHT_DP = 160

        /**
         * Density steps for the grid. The column stacks emoji + count + unit (+ name), which
         * needs ~80dp; squeezed below that the content used to spill over the rounded
         * background, so text scales down and the unit row drops out on short widgets.
         */
        private enum class GridDensity(
                val minHeightDp: Int,
                val emojiSp: Float,
                val countSp: Float,
                val showUnit: Boolean
        ) {
            TINY(0, 18f, 13f, showUnit = false),
            COMPACT(64, 24f, 14f, showUnit = false),
            REGULAR(80, 30f, 16f, showUnit = true),
            NAMED(NAMED_GRID_HEIGHT_DP, 30f, 18f, showUnit = true);

            companion object {
                fun forHeight(heightDp: Int): GridDensity =
                        if (heightDp <= 0) REGULAR // launcher didn't report a size
                        else entries.last { heightDp >= it.minHeightDp }
            }
        }

        private val COLUMN_IDS =
                intArrayOf(
                        R.id.streak_column_0,
                        R.id.streak_column_1,
                        R.id.streak_column_2,
                        R.id.streak_column_3,
                        R.id.streak_column_4,
                        R.id.streak_column_5,
                        R.id.streak_column_6,
                        R.id.streak_column_7
                )
        private val ICON_IDS =
                intArrayOf(
                        R.id.streak_icon_0,
                        R.id.streak_icon_1,
                        R.id.streak_icon_2,
                        R.id.streak_icon_3,
                        R.id.streak_icon_4,
                        R.id.streak_icon_5,
                        R.id.streak_icon_6,
                        R.id.streak_icon_7
                )
        private val COUNT_IDS =
                intArrayOf(
                        R.id.streak_count_0,
                        R.id.streak_count_1,
                        R.id.streak_count_2,
                        R.id.streak_count_3,
                        R.id.streak_count_4,
                        R.id.streak_count_5,
                        R.id.streak_count_6,
                        R.id.streak_count_7
                )
        private val UNIT_IDS =
                intArrayOf(
                        R.id.streak_unit_0,
                        R.id.streak_unit_1,
                        R.id.streak_unit_2,
                        R.id.streak_unit_3,
                        R.id.streak_unit_4,
                        R.id.streak_unit_5,
                        R.id.streak_unit_6,
                        R.id.streak_unit_7
                )
        private val NAME_IDS =
                intArrayOf(
                        R.id.streak_name_0,
                        R.id.streak_name_1,
                        R.id.streak_name_2,
                        R.id.streak_name_3,
                        R.id.streak_name_4,
                        R.id.streak_name_5,
                        R.id.streak_name_6,
                        R.id.streak_name_7
                )
        private val CHECK_IDS =
                intArrayOf(
                        R.id.streak_check_0,
                        R.id.streak_check_1,
                        R.id.streak_check_2,
                        R.id.streak_check_3,
                        R.id.streak_check_4,
                        R.id.streak_check_5,
                        R.id.streak_check_6,
                        R.id.streak_check_7
                )

        fun parseStreakColor(color: String): Int =
                try {
                    android.graphics.Color.parseColor(color)
                } catch (e: Exception) {
                    android.graphics.Color.parseColor("#FF9900")
                }

        fun getUnitLabel(frequency: FrequencyType, count: Int): String {
            return when (frequency) {
                FrequencyType.DAILY -> if (count == 1) "day" else "days"
                FrequencyType.WEEKLY -> if (count == 1) "week" else "weeks"
                FrequencyType.MONTHLY -> if (count == 1) "month" else "months"
                FrequencyType.YEARLY -> if (count == 1) "year" else "years"
            }
        }

        fun updateAppWidget(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetId: Int
        ) {
            val config = WidgetPrefs.load(context, appWidgetId)
            val repository = StreakRepository.getInstance()
            repository.loadStreaksFromFile(context)
            val streaks =
                    config.filter(repository.streaks.value ?: emptyList()).sortedBy { it.position }

            // A single RemoteViews sized from the widget options re-applies
            // reliably on every launcher; the multi-size RemoteViews map made
            // some launchers keep showing the cached view after tap updates.
            // onAppWidgetOptionsChanged re-runs this whenever the user resizes.
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val portrait =
                    context.resources.configuration.orientation !=
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val widthDp =
                    options.getInt(
                            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                            else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    )
            val heightDp =
                    options.getInt(
                            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                            else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    )

            val columns =
                    if (widthDp <= 0) 4
                    else (widthDp / COLUMN_WIDTH_DP).coerceIn(1, MAX_COLUMNS)
            val density = GridDensity.forHeight(heightDp)
            val views =
                    when (config.mode) {
                        WidgetPrefs.Mode.LIST -> buildListViews(context, appWidgetId, streaks)
                        WidgetPrefs.Mode.GRID ->
                                buildGridViews(context, streaks, columns, density)
                        WidgetPrefs.Mode.AUTO ->
                                if (heightDp >= LIST_HEIGHT_DP)
                                        buildListViews(context, appWidgetId, streaks)
                                else buildGridViews(context, streaks, columns, density)
                    }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildGridViews(
                context: Context,
                streaks: List<Streak>,
                columns: Int,
                density: GridDensity
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_streaks)
            val visibleCount = minOf(columns, streaks.size, MAX_COLUMNS)

            views.setViewVisibility(
                    R.id.widget_empty,
                    if (streaks.isEmpty()) View.VISIBLE else View.GONE
            )

            for (i in 0 until MAX_COLUMNS) {
                if (i < visibleCount) {
                    val streak = streaks[i]
                    views.setViewVisibility(COLUMN_IDS[i], View.VISIBLE)
                    views.setTextViewText(ICON_IDS[i], streak.emoji)
                    views.setTextViewTextSize(
                            ICON_IDS[i], TypedValue.COMPLEX_UNIT_SP, density.emojiSp
                    )
                    views.setTextViewText(COUNT_IDS[i], streak.currentStreak.toString())
                    views.setTextViewTextSize(
                            COUNT_IDS[i], TypedValue.COMPLEX_UNIT_SP, density.countSp
                    )
                    if (density.showUnit) {
                        views.setViewVisibility(UNIT_IDS[i], View.VISIBLE)
                        views.setTextViewText(
                                UNIT_IDS[i],
                                getUnitLabel(streak.frequency, streak.currentStreak)
                        )
                    } else {
                        views.setViewVisibility(UNIT_IDS[i], View.GONE)
                    }

                    // Negative habits invert the highlight: the streak is "on" while the
                    // day is still clean, and a slip-up today mutes the column instead.
                    val streakColor = parseStreakColor(streak.color)
                    val highlighted =
                            if (streak.isNegative) !streak.isCompletedToday
                            else streak.isCompletedToday
                    if (highlighted) {
                        views.setTextColor(COUNT_IDS[i], streakColor)
                    } else {
                        // Resolved in the launcher process so it follows the system
                        // theme like the widget background; context.getColor here
                        // would apply the in-app theme override instead
                        views.setColor(COUNT_IDS[i], "setTextColor", R.color.black)
                    }

                    // Status badge. Positive: an accent check once done today. Negative:
                    // an accent check while clean, a gray cross after a slip-up.
                    // TINY columns have no room for it — the emoji itself is barely bigger.
                    when {
                        density == GridDensity.TINY ->
                                views.setViewVisibility(CHECK_IDS[i], View.GONE)
                        streak.isNegative && streak.isCompletedToday -> {
                            views.setViewVisibility(CHECK_IDS[i], View.VISIBLE)
                            views.setImageViewResource(CHECK_IDS[i], R.drawable.ic_slip_24)
                            views.setColor(CHECK_IDS[i], "setColorFilter", R.color.gray_dark)
                        }
                        streak.isNegative -> {
                            views.setViewVisibility(CHECK_IDS[i], View.VISIBLE)
                            views.setImageViewResource(CHECK_IDS[i], R.drawable.ic_widget_check)
                            views.setInt(CHECK_IDS[i], "setColorFilter", streakColor)
                        }
                        streak.isCompletedToday -> {
                            views.setViewVisibility(CHECK_IDS[i], View.VISIBLE)
                            views.setImageViewResource(CHECK_IDS[i], R.drawable.ic_widget_check)
                            views.setInt(CHECK_IDS[i], "setColorFilter", streakColor)
                        }
                        else -> views.setViewVisibility(CHECK_IDS[i], View.GONE)
                    }

                    if (density == GridDensity.NAMED) {
                        views.setViewVisibility(NAME_IDS[i], View.VISIBLE)
                        views.setTextViewText(NAME_IDS[i], streak.name)
                    } else {
                        views.setViewVisibility(NAME_IDS[i], View.GONE)
                    }
                    views.setOnClickPendingIntent(
                            COLUMN_IDS[i],
                            toggleIntent(context, streak.id, i)
                    )
                } else {
                    views.setViewVisibility(COLUMN_IDS[i], View.GONE)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun buildListViews(
                context: Context,
                appWidgetId: Int,
                streaks: List<Streak>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_streaks_list)

            // Items are built inline (RemoteCollectionItems) instead of going through
            // the deprecated RemoteViewsService round trip
            val items =
                    RemoteViews.RemoteCollectionItems.Builder()
                            .setHasStableIds(true)
                            .apply {
                                streaks.forEach { streak ->
                                    addItem(
                                            streak.id.hashCode().toLong(),
                                            buildListItem(context, streak)
                                    )
                                }
                            }
                            .build()
            views.setRemoteAdapter(R.id.widget_list, items)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Rows fill in the streak id; the template must be mutable to accept it
            val toggleTemplate =
                    Intent(context, StreaksWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE
                    }
            views.setPendingIntentTemplate(
                    R.id.widget_list,
                    PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            toggleTemplate,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
            )

            views.setOnClickPendingIntent(R.id.widget_empty, openAppIntent(context))
            return views
        }

        private fun buildListItem(context: Context, streak: Streak): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_streak_list_item)

            views.setTextViewText(R.id.item_emoji, streak.emoji)
            views.setTextViewText(R.id.item_name, streak.name)
            val unit = getUnitLabel(streak.frequency, streak.currentStreak)
            views.setTextViewText(
                    R.id.item_streak,
                    context.getString(
                            if (streak.isNegative) R.string.widget_streak_summary_negative
                            else R.string.widget_streak_summary,
                            streak.currentStreak,
                            unit,
                            streak.bestStreak
                    )
            )

            // Negative habits invert the highlight: accent while clean, muted after a slip
            val color = parseStreakColor(streak.color)
            when {
                streak.isNegative && streak.isCompletedToday -> {
                    views.setImageViewResource(R.id.item_check, R.drawable.ic_slip_24)
                    views.setColor(R.id.item_check, "setColorFilter", R.color.gray_dark)
                }
                streak.isNegative -> {
                    views.setImageViewResource(R.id.item_check, R.drawable.ic_widget_check)
                    views.setInt(R.id.item_check, "setColorFilter", color)
                }
                streak.isCompletedToday -> {
                    views.setImageViewResource(R.id.item_check, R.drawable.ic_widget_check)
                    views.setInt(R.id.item_check, "setColorFilter", color)
                }
                else -> {
                    views.setImageViewResource(R.id.item_check, R.drawable.ic_widget_circle)
                    views.setColor(R.id.item_check, "setColorFilter", R.color.gray_dark)
                }
            }
            val highlighted =
                    if (streak.isNegative) !streak.isCompletedToday else streak.isCompletedToday
            if (highlighted) {
                views.setTextColor(R.id.item_name, color)
            } else {
                // Launcher-side resource resolution keeps text readable when the
                // in-app theme disagrees with the system theme
                views.setColor(R.id.item_name, "setTextColor", R.color.black)
            }

            // Fills in the pending intent template set on the ListView by the provider
            val fillIn =
                    Intent().apply {
                        putExtra(EXTRA_STREAK_ID, streak.id)
                        data = Uri.parse("streaks://widget/toggle/${streak.id}")
                    }
            views.setOnClickFillInIntent(R.id.widget_item_root, fillIn)
            return views
        }

        private fun toggleIntent(context: Context, streakId: String, requestCode: Int): PendingIntent {
            val intent =
                    Intent(context, StreaksWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE
                        putExtra(EXTRA_STREAK_ID, streakId)
                        data = Uri.parse("streaks://widget/toggle/$streakId")
                    }
            return PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun openAppIntent(context: Context): PendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                )
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
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            newOptions: android.os.Bundle?
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
    }
}
