package com.focusgate.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Slice(val label: String, val percent: Float, val color: Int)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.black)
        textSize = 13f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.darker_gray)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private var slices: List<Slice> = emptyList()
    private var animatedFraction = 0f
    private var animator: ValueAnimator? = null

    private val chartColors = listOf(
        context.getColor(com.focusgate.app.R.color.chart_purple),
        context.getColor(com.focusgate.app.R.color.chart_teal),
        context.getColor(com.focusgate.app.R.color.chart_orange),
        context.getColor(com.focusgate.app.R.color.chart_pink),
        context.getColor(com.focusgate.app.R.color.chart_blue),
        context.getColor(com.focusgate.app.R.color.chart_gray)
    )

    fun setData(data: Map<String, Float>) {
        val total = data.values.sum().coerceAtLeast(1f)
        slices = data.toList().sortedByDescending { it.second }.mapIndexed { index, entry ->
            Slice(
                label = entry.first,
                percent = (entry.second / total) * 100f,
                color = chartColors.getOrElse(index) { chartColors.last() }
            )
        }
        animatedFraction = 0f
        invalidate()
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                this@DonutChartView.animatedFraction = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(400)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val size = width.coerceAtMost(height).toFloat()
        val strokeWidth = size * 0.18f
        val radius = (size - strokeWidth) / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        val rect = RectF(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius
        )

        arcPaint.strokeWidth = strokeWidth
        arcPaint.strokeCap = Paint.Cap.ROUND

        val totalSweep = 360f * animatedFraction
        var currentAngle = -90f

        slices.forEach { slice ->
            val sweep = (slice.percent / 100f) * totalSweep
            if (sweep > 1f) {
                arcPaint.color = slice.color
                canvas.drawArc(rect, currentAngle, sweep, false, arcPaint)
            }
            currentAngle += sweep
        }

        // Center text: top app name + percent
        if (animatedFraction > 0.5f) {
            val top = slices.firstOrNull()
            if (top != null) {
                val alpha = ((animatedFraction - 0.5f) * 2 * 255).toInt().coerceIn(0, 255)
                labelPaint.alpha = alpha
                percentPaint.alpha = alpha
                canvas.drawText(top.label, centerX, centerY - 6f, labelPaint)
                canvas.drawText("${top.percent.toInt()}%", centerX, centerY + 26f, percentPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
