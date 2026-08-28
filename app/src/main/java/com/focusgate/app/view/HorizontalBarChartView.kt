package com.focusgate.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class HorizontalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class BarItem(val label: String, val percent: Float, val color: Int)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.black)
        textSize = 13f * resources.displayMetrics.scaledDensity
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.darker_gray)
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5F5F5.toInt()
    }

    private var items: List<BarItem> = emptyList()
    private var animatedFraction = 0f
    private var animator: ValueAnimator? = null

    private val barHeight = 24f * resources.displayMetrics.density
    private val barSpacing = 16f * resources.displayMetrics.density
    private val labelWidth = 80f * resources.displayMetrics.density
    private val percentWidth = 40f * resources.displayMetrics.density
    private val cornerRadius = 8f * resources.displayMetrics.density

    fun setData(data: List<BarItem>) {
        items = data.filter { it.percent > 0 }
        animatedFraction = 0f
        invalidate()
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                this@HorizontalBarChartView.animatedFraction = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = ((items.size.coerceAtLeast(1)) * (barHeight + barSpacing) + barSpacing).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val maxBarWidth = width - labelWidth - percentWidth - 32f * resources.displayMetrics.density
        val maxPercent = items.maxOfOrNull { it.percent } ?: 100f

        var y = barSpacing
        items.forEach { item ->
            // Label
            canvas.drawText(item.label, labelWidth - 8f, y + barHeight / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)

            // Background bar
            val bgRect = RectF(labelWidth, y, labelWidth + maxBarWidth, y + barHeight)
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

            // Filled bar
            val targetWidth = if (maxPercent > 0) (item.percent / maxPercent) * maxBarWidth else 0f
            val currentWidth = targetWidth * animatedFraction
            if (currentWidth > 0) {
                barPaint.color = item.color
                val fillRect = RectF(labelWidth, y, labelWidth + currentWidth, y + barHeight)
                canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, barPaint)
            }

            // Percent text
            canvas.drawText("${item.percent.toInt()}%", labelWidth + maxBarWidth + 8f, y + barHeight / 2f - (percentPaint.descent() + percentPaint.ascent()) / 2f, percentPaint)

            y += barHeight + barSpacing
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
