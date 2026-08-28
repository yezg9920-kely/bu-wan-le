package com.focusgate.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.focusgate.app.R.color.chart_text)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.focusgate.app.R.color.chart_grid)
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6000000.toInt()
    }
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var barColor: Int = context.getColor(com.focusgate.app.R.color.chart_purple)
    private var animatedFraction = 0f
    private var animator: ValueAnimator? = null
    private var touchedIndex = -1

    private val paddingBottom = 28f * resources.displayMetrics.density
    private val paddingTop = 16f * resources.displayMetrics.density
    private val paddingLeft = 8f * resources.displayMetrics.density
    private val paddingRight = 8f * resources.displayMetrics.density
    private val barGapRatio = 0.3f

    fun setData(values: List<Float>, labels: List<String>, barColor: Int = this.barColor) {
        this.values = values
        this.labels = labels
        this.barColor = barColor
        this.animatedFraction = 0f
        invalidate()
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                this@BarChartView.animatedFraction = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && values.isNotEmpty()) {
            val chartWidth = width - paddingLeft - paddingRight
            val barCount = values.size
            val barSlotWidth = chartWidth / barCount
            val index = ((event.x - paddingLeft) / barSlotWidth).toInt().coerceIn(0, barCount - 1)
            touchedIndex = index
            invalidate()
            val valStr = values.getOrNull(index)?.toInt()?.toString() ?: "0"
            Toast.makeText(context, "${labels.getOrNull(index) ?: ""}: $valStr", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val chartHeight = height - paddingTop - paddingBottom
        val chartWidth = width - paddingLeft - paddingRight
        val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val barCount = values.size
        val barSlotWidth = chartWidth / barCount
        val barWidth = barSlotWidth * (1f - barGapRatio)
        val barOffset = (barSlotWidth - barWidth) / 2f

        // Draw grid line at bottom
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, gridPaint)

        values.forEachIndexed { index, value ->
            val barHeight = (value / maxValue) * chartHeight * animatedFraction
            val left = paddingLeft + index * barSlotWidth + barOffset
            val top = height - paddingBottom - barHeight
            val right = left + barWidth
            val bottom = height - paddingBottom

            val isTouched = index == touchedIndex
            val alpha = if (isTouched) 255 else 220
            barPaint.alpha = alpha

            val gradient = LinearGradient(
                left, top, left, bottom,
                barColor,
                adjustAlpha(barColor, 0.6f),
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient

            val rect = RectF(left, top.coerceAtMost(bottom - 4f), right, bottom)
            val radius = 6f * resources.displayMetrics.density
            canvas.drawRoundRect(rect, radius, radius, barPaint)
            barPaint.shader = null

            // Label
            if (labels.size > index) {
                val label = labels[index]
                canvas.drawText(label, left + barWidth / 2f, height - 8f, labelPaint)
            }
        }

        // Tooltip for touched index
        if (touchedIndex >= 0 && touchedIndex < values.size) {
            val value = values[touchedIndex]
            val label = labels.getOrNull(touchedIndex) ?: ""
            val text = "$label\n${value.toInt()}"
            val x = paddingLeft + touchedIndex * barSlotWidth + barSlotWidth / 2f
            val barHeight = (value / maxValue) * chartHeight * animatedFraction
            val y = height - paddingBottom - barHeight - 16f

            val textWidth = tooltipTextPaint.measureText(text.lines().maxByOrNull { it.length } ?: "")
            val tooltipRect = RectF(
                x - textWidth / 2f - 12f,
                y - 36f,
                x + textWidth / 2f + 12f,
                y - 4f
            )
            canvas.drawRoundRect(tooltipRect, 8f, 8f, tooltipPaint)
            canvas.drawText("${value.toInt()}", x, y - 14f, tooltipTextPaint)
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (color shr 24 and 0xFF)
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        return ((a * factor).toInt() shl 24) or (r shl 16) or (g shl 8) or b
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
