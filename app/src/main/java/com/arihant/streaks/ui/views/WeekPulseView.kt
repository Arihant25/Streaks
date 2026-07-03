package com.arihant.streaks.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.arihant.streaks.R

/**
 * Minimal "week pulse" chart for the home hero card.
 *
 * One column per day of the current week; each completion that day is a
 * rounded pill in its streak's own color, stacked bottom-up. Days without
 * completions show a small resting dot (fainter for future days). Columns
 * grow in with a staggered left-to-right wave on first show.
 */
class WeekPulseView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : View(context, attrs) {

        data class Day(
                val label: String,
                val colors: List<Int>,
                val isToday: Boolean,
                val isFuture: Boolean
        )

        private val maxPillWidth = dp(22f)
        private val pillGap = dp(3f)
        private val labelHeight = dp(18f)
        private val maxPillUnit = dp(28f)

        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dotPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = ContextCompat.getColor(context, R.color.gray_medium)
                }
        private val labelPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = dp(10f)
                        textAlign = Paint.Align.CENTER
                        color = ContextCompat.getColor(context, R.color.gray_dark)
                }
        private val todayLabelPaint =
                Paint(labelPaint).apply {
                        typeface = Typeface.DEFAULT_BOLD
                        color = ContextCompat.getColor(context, R.color.black)
                }

        private var days: List<Day> = emptyList()
        private var onDark = false
        private var revealFraction = 1f
        private var revealAnimator: ValueAnimator? = null
        private val pillRect = RectF()

        /**
         * [onDark] draws everything in white for colored backgrounds — streak
         * colors like orange/yellow would disappear against the hero gradient.
         */
        fun setData(days: List<Day>, animate: Boolean, onDark: Boolean = false) {
                this.onDark = onDark
                if (onDark) {
                        labelPaint.color = 0xB3FFFFFF.toInt()
                        todayLabelPaint.color = 0xFFFFFFFF.toInt()
                        dotPaint.color = 0xFFFFFFFF.toInt()
                }
                this.days = days
                if (animate) startReveal() else revealFraction = 1f
                invalidate()
        }

        private fun startReveal() {
                revealAnimator?.cancel()
                revealFraction = 0f
                revealAnimator =
                        ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 900
                                startDelay = 250
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

        override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (days.isEmpty()) return

                val chartBottom = height - labelHeight
                val chartHeight = chartBottom - dp(4f)
                val maxCount = days.maxOf { it.colors.size }.coerceAtLeast(1)
                val unit =
                        ((chartHeight - (maxCount - 1) * pillGap) / maxCount)
                                .coerceAtMost(maxPillUnit)
                val slot = width.toFloat() / days.size
                // Pills scale with the column slot so a full-width chart
                // doesn't render skinny sticks
                val pillWidth = (slot * 0.45f).coerceAtMost(maxPillWidth)
                // Rounded square, not a capsule
                val radius = dp(4f).coerceAtMost(minOf(unit, pillWidth) / 2f)
                // Stagger constant: how much of the timeline the wave spans
                val k = 0.5f

                for ((i, day) in days.withIndex()) {
                        val cx = slot * (i + 0.5f)

                        canvas.drawText(
                                day.label,
                                cx,
                                height - dp(4f),
                                if (day.isToday) todayLabelPaint else labelPaint
                        )

                        val colStart = (i.toFloat() / days.size) * k
                        val p = ((revealFraction - colStart) / (1f - k)).coerceIn(0f, 1f)
                        if (p == 0f) continue

                        if (day.colors.isEmpty()) {
                                // Small rounded square matching the pill shape
                                val dotSize = pillWidth * 0.55f
                                dotPaint.alpha = ((if (day.isFuture) 90 else 255) * p).toInt()
                                pillRect.set(
                                        cx - dotSize / 2f,
                                        chartBottom - dotSize,
                                        cx + dotSize / 2f,
                                        chartBottom.toFloat()
                                )
                                canvas.drawRoundRect(
                                        pillRect,
                                        dp(2.5f),
                                        dp(2.5f),
                                        dotPaint
                                )
                                continue
                        }

                        // Grow the whole stack up from the baseline
                        canvas.save()
                        canvas.scale(1f, p, cx, chartBottom.toFloat())
                        var bottom = chartBottom.toFloat()
                        for (color in day.colors) {
                                pillPaint.color = if (onDark) 0xFFFFFFFF.toInt() else color
                                pillPaint.alpha = (255 * p).toInt()
                                pillRect.set(
                                        cx - pillWidth / 2f,
                                        bottom - unit,
                                        cx + pillWidth / 2f,
                                        bottom
                                )
                                canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
                                bottom -= unit + pillGap
                        }
                        canvas.restore()
                }
        }

        private fun dp(v: Float): Float =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        v,
                        resources.displayMetrics
                )
}
