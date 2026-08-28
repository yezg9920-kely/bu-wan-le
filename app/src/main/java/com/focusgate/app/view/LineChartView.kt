package com.focusgate.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.focusgate.app.R.color.chart_text)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(com.focusgate.app.R.color.chart_grid)
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var lineColor: Int = context.getColor(com.focusgate.app.R.color.chart_teal)
    private var animatedFraction = 0f
    private var animator: ValueAnimator? = null

    private val paddingBottom = 28f * resources.displayMetrics.density
    private val paddingTop = 16f * resources.displayMetrics.density
    private val paddingLeft = 8f * resources.displayMetrics.density
    private val paddingRight = 8f * resources.displayMetrics.density

    fun setData(values: List<Float>, labels: List<String>, lineColor: Int = this.lineColor) {
        this.values = values
        this.labels = labels
        this.lineColor = lineColor
        this.animatedFraction = 0f
        invalidate()
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                this@LineChartView.animatedFraction = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val chartHeight = height - paddingTop - paddingBottom
        val chartWidth = width - paddingLeft - paddingRight
        val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val count = values.size
        val stepX = chartWidth / (count - 1)

        // Grid line bottom
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, gridPaint)

        // Build path
        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<Pair<Float, Float>>()

        values.forEachIndexed { index, value ->
            val x = paddingLeft + index * stepX
            val y = height - paddingBottom - (value / maxValue) * chartHeight * animatedFraction
            points.add(x to y)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - paddingBottom)
                fillPath.lineTo(x, y)
            } else {
                // Simple smoothing
                val prev = points[index - 1]
                val midX = (prev.first + x) / 2f
                path.quadTo(prev.first, prev.second, midX, (prev.second + y) / 2f)
                path.quadTo(midX, (prev.second + y) / 2f, x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Fill area
        if (points.isNotEmpty()) {
            val last = points.last()
            fillPath.lineTo(last.first, height - paddingBottom)
            fillPath.close()
            val gradient = LinearGradient(
                0f, paddingTop, 0f, height - paddingBottom,
                adjustAlpha(lineColor, 0.25f),
                adjustAlpha(lineColor, 0.02f),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = gradient
            canvas.drawPath(fillPath, fillPaint)
        }

        // Line
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

        // Dots
        dotPaint.color = lineColor
        val dotRadius = 4f * resources.displayMetrics.density
        points.forEach { (x, y) ->
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, dotRadius + 2f, dotPaint)
            dotPaint.color = lineColor
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }

        // Labels
        labels.forEachIndexed { index, label ->
            val x = paddingLeft + index * stepX
            canvas.drawText(label, x, height - 8f, labelPaint)
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
