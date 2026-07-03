package com.arihant.streaks.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.utils.WeekConfig
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * GitHub-style contribution heatmap for one calendar year, drawn on canvas.
 *
 * Completed days are shaded on a single-hue sequential ramp: the deeper a day
 * sits inside a consecutive run, the closer its color is to the full accent.
 * The run is counted in the streak's own cadence — consecutive days for daily
 * streaks, consecutive active weeks/months/years otherwise.
 * Cells reveal themselves with a staggered left-to-right wave on first show.
 */
class YearGraphView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

        /** Called when the user taps a day cell (never a future date). */
        var onDayTapped: ((date: LocalDate, isCompleted: Boolean) -> Unit)? = null

        private val cellSize = dp(13f)
        private val cellGap = dp(3f)
        private val cellRadius = dp(3.5f)
        private val monthLabelHeight = dp(18f)

        private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val todayPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = dp(1.5f)
                }
        private val labelPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = dp(10f)
                        color = Color.parseColor("#FF9E9E9E")
                }

        // Theme-aware: gray_medium / white flip to dark variants in values-night
        private val emptyColor = ContextCompat.getColor(context, R.color.gray_medium)
        private val screenBackground = ContextCompat.getColor(context, R.color.screen_background)

        private var year = LocalDate.now().year
        private var gridStart: LocalDate = LocalDate.now() // first cell (top of column 0)
        private var numWeeks = 0
        private var completions: Set<LocalDate> = emptySet()

        /** Color per day-of-year (index 0 = Jan 1), 0 = not completed. */
        private var dayColors: IntArray = IntArray(0)

        private var accent = Color.parseColor("#FF9900")
        private var frequency = FrequencyType.DAILY
        private var revealFraction = 1f
        private var revealAnimator: ValueAnimator? = null

        private val cellRect = RectF()

        fun setData(
                completions: Set<LocalDate>,
                accentColor: Int,
                frequency: FrequencyType,
                animate: Boolean
        ) {
                this.completions = completions
                this.accent = accentColor
                this.frequency = frequency
                this.year = LocalDate.now().year
                todayPaint.color = accentColor

                val start = LocalDate.of(year, 1, 1)
                var gs = start
                while (gs.dayOfWeek != WeekConfig.firstDayOfWeek) gs = gs.minusDays(1)
                gridStart = gs

                val end = LocalDate.of(year, 12, 31)
                numWeeks = ((java.time.temporal.ChronoUnit.DAYS.between(gs, end)) / 7 + 1).toInt()

                computeDayColors()
                requestLayout()

                if (animate) startReveal() else revealFraction = 1f
                invalidate()
        }

        /** Sequential ramp: levels 1..4 -> faint to full accent. */
        fun rampColor(level: Int): Int {
                val t =
                        when (level.coerceIn(1, 4)) {
                                1 -> 0.35f
                                2 -> 0.55f
                                3 -> 0.78f
                                else -> 1f
                        }
                return blendWithBackground(accent, t)
        }

        fun colorForEmpty(): Int = emptyColor

        /** X offset of the current week's column, for auto-scrolling to today. */
        fun todayScrollX(): Int {
                val weekIndex =
                        (java.time.temporal.ChronoUnit.DAYS.between(gridStart, LocalDate.now()) / 7)
                                .toInt()
                return ((cellSize + cellGap) * weekIndex).toInt()
        }

        /** First day of the period (day/week/month/year) a date belongs to. */
        private fun periodKey(date: LocalDate): LocalDate =
                when (frequency) {
                        FrequencyType.DAILY -> date
                        FrequencyType.WEEKLY -> {
                                var d = date
                                while (d.dayOfWeek != WeekConfig.firstDayOfWeek) d = d.minusDays(1)
                                d
                        }
                        FrequencyType.MONTHLY -> date.withDayOfMonth(1)
                        FrequencyType.YEARLY -> date.withDayOfYear(1)
                }

        private fun previousPeriod(key: LocalDate): LocalDate =
                when (frequency) {
                        FrequencyType.DAILY -> key.minusDays(1)
                        FrequencyType.WEEKLY -> key.minusWeeks(1)
                        FrequencyType.MONTHLY -> key.minusMonths(1)
                        FrequencyType.YEARLY -> key.minusYears(1)
                }

        private fun computeDayColors() {
                val start = LocalDate.of(year, 1, 1)
                val days = if (start.isLeapYear) 366 else 365
                dayColors = IntArray(days)

                // A period is "active" if it has at least one completion; the run for a
                // day is how many consecutive active periods end at its own period
                val activePeriods = completions.mapTo(HashSet()) { periodKey(it) }
                val runByPeriod = HashMap<LocalDate, Int>()
                fun runFor(key: LocalDate): Int =
                        runByPeriod.getOrPut(key) {
                                if (key !in activePeriods) 0 else 1 + runFor(previousPeriod(key))
                        }

                var date = start
                for (i in 0 until days) {
                        if (completions.contains(date)) {
                                val run = runFor(periodKey(date))
                                val level =
                                        if (frequency == FrequencyType.DAILY) {
                                                when {
                                                        run >= 7 -> 4
                                                        run >= 3 -> 3
                                                        else -> run
                                                }
                                        } else {
                                                run.coerceAtMost(4)
                                        }
                                dayColors[i] = rampColor(level)
                        }
                        date = date.plusDays(1)
                }
        }

        private fun startReveal() {
                revealAnimator?.cancel()
                revealFraction = 0f
                revealAnimator =
                        ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 1100
                                startDelay = 150
                                interpolator = DecelerateInterpolator()
                                addUpdateListener {
                                        revealFraction = it.animatedValue as Float
                                        invalidate()
                                }
                                start()
                        }
        }

        override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                revealAnimator?.cancel()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = (numWeeks * (cellSize + cellGap) - cellGap).coerceAtLeast(0f)
                val h = monthLabelHeight + 7 * (cellSize + cellGap) - cellGap
                setMeasuredDimension(w.toInt() + paddingLeft + paddingRight, h.toInt())
        }

        override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val today = LocalDate.now()
                val step = cellSize + cellGap
                // Stagger constant: how much of the timeline each column's pop-in occupies
                val k = 0.6f

                var date = gridStart
                for (week in 0 until numWeeks) {
                        val colX = paddingLeft + week * step

                        // Month label above the column containing the 1st
                        for (d in 0..6) {
                                val cellDate = date.plusDays(d.toLong())
                                if (cellDate.year == year && cellDate.dayOfMonth == 1) {
                                        canvas.drawText(
                                                cellDate.month.getDisplayName(
                                                        TextStyle.SHORT,
                                                        Locale.getDefault()
                                                ),
                                                colX,
                                                labelPaint.textSize,
                                                labelPaint
                                        )
                                }
                        }

                        // Per-column reveal progress for the wave animation
                        val colStart = (week.toFloat() / numWeeks) * k
                        val p = ((revealFraction - colStart) / (1f - k)).coerceIn(0f, 1f)
                        if (p == 0f) {
                                date = date.plusWeeks(1)
                                continue
                        }

                        for (d in 0..6) {
                                val cellDate = date.plusDays(d.toLong())
                                if (cellDate.year != year) continue

                                val color =
                                        dayColors.getOrNull(cellDate.dayOfYear - 1)
                                                ?.takeIf { it != 0 }
                                                ?: emptyColor
                                cellPaint.color = color
                                cellPaint.alpha = (Color.alpha(color) * p).toInt()

                                val inset = (1f - (0.5f + 0.5f * p)) * cellSize / 2f
                                val top = monthLabelHeight + d * step
                                cellRect.set(
                                        colX + inset,
                                        top + inset,
                                        colX + cellSize - inset,
                                        top + cellSize - inset
                                )
                                canvas.drawRoundRect(cellRect, cellRadius, cellRadius, cellPaint)

                                if (cellDate == today && p == 1f) {
                                        cellRect.inset(-dp(1.5f), -dp(1.5f))
                                        canvas.drawRoundRect(
                                                cellRect,
                                                cellRadius + dp(1f),
                                                cellRadius + dp(1f),
                                                todayPaint
                                        )
                                }
                        }
                        date = date.plusWeeks(1)
                }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) return true
                if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)

                val step = cellSize + cellGap
                val week = ((event.x - paddingLeft) / step).toInt()
                val day = ((event.y - monthLabelHeight) / step).toInt()
                if (week !in 0 until numWeeks || day !in 0..6) return true

                val date = gridStart.plusWeeks(week.toLong()).plusDays(day.toLong())
                if (date.year != year || date.isAfter(LocalDate.now())) return true

                performClick()
                onDayTapped?.invoke(date, completions.contains(date))
                return true
        }

        override fun performClick(): Boolean {
                super.performClick()
                return true
        }

        private fun blendWithBackground(color: Int, t: Float): Int {
                val br = Color.red(screenBackground)
                val bg = Color.green(screenBackground)
                val bb = Color.blue(screenBackground)
                val r = (br + (Color.red(color) - br) * t).toInt()
                val g = (bg + (Color.green(color) - bg) * t).toInt()
                val b = (bb + (Color.blue(color) - bb) * t).toInt()
                return Color.rgb(r, g, b)
        }

        private fun dp(v: Float): Float =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        v,
                        resources.displayMetrics
                )
}
