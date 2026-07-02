package com.arihant.streaks.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A hue ring color picker: drag the thumb around the wheel to choose any hue.
 * Saturation and value are fixed so every pick stays vivid enough to tint
 * completion circles and calendar cells.
 */
class HueWheelView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var onColorChanged: ((Int) -> Unit)? = null

    private var hue = 30f
    private val ringPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStrokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
            }
    private var ringRadius = 0f
    private var ringThickness = 0f
    private var lastHapticHue = -1000f

    val selectedColor: Int
        get() = colorForHue(hue)

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val half = min(w, h) / 2f
        ringThickness = half * 0.24f
        ringRadius = half - ringThickness / 2f - half * 0.1f
        val hues =
                IntArray(361) { Color.HSVToColor(floatArrayOf(it.toFloat(), SATURATION, VALUE)) }
        ringPaint.shader = SweepGradient(w / 2f, h / 2f, hues, null)
        ringPaint.strokeWidth = ringThickness
        thumbStrokePaint.strokeWidth = half * 0.045f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        val angleRad = Math.toRadians(hue.toDouble())
        val tx = cx + ringRadius * cos(angleRad).toFloat()
        val ty = cy + ringRadius * sin(angleRad).toFloat()
        val thumbRadius = ringThickness * 0.72f
        thumbPaint.color = selectedColor
        canvas.drawCircle(tx, ty, thumbRadius, thumbPaint)
        canvas.drawCircle(tx, ty, thumbRadius, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dx = event.x - width / 2f
                val dy = event.y - height / 2f
                var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (degrees < 0) degrees += 360f
                hue = degrees
                if (abs(hue - lastHapticHue) > 12f) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastHapticHue = hue
                }
                invalidate()
                onColorChanged?.invoke(selectedColor)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val SATURATION = 0.85f
        private const val VALUE = 1f

        private fun colorForHue(hue: Float): Int =
                Color.HSVToColor(floatArrayOf(hue, SATURATION, VALUE))
    }
}
