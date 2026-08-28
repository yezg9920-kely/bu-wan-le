package com.focusgate.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.ceil

class AnimatedNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.black)
        textAlign = Paint.Align.CENTER
    }
    private var animator: ValueAnimator? = null
    private var currentNumber = 0L
    private var targetNumber = 0L
    private var suffix = ""
    private var textSizeSp = 28f

    fun setTextSize(sp: Float) {
        textSizeSp = sp
        requestLayout()
        invalidate()
    }

    fun setTextColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setTargetNumber(number: Long, suffix: String = "") {
        this.targetNumber = number
        this.suffix = suffix
        this.currentNumber = 0
        contentDescription = "$number$suffix"
        requestLayout()
        invalidate()
    }

    fun startAnimation(durationMs: Long = 800) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                currentNumber = (targetNumber * fraction).toLong()
                contentDescription = "$currentNumber$suffix"
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = "$currentNumber$suffix"
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            resources.displayMetrics
        )
        val x = width / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            resources.displayMetrics
        )
        val widestText = "$targetNumber$suffix".ifEmpty { "0" }
        val desiredWidth = ceil(paint.measureText(widestText)).toInt() + paddingLeft + paddingRight
        val metrics = paint.fontMetrics
        val desiredHeight = ceil(metrics.bottom - metrics.top).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(desiredHeight.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
