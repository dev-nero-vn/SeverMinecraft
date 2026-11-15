package vn.nero.gameturbo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import java.util.Locale

/**
 * Circular meter: numeric text and progress ring animate from the same sampled value.
 * Unknown metrics use a moving indicator and never invent a numeric reading.
 */
class CircularStatView(
    context: Context,
    private val title: String,
    initialValue: String,
    initialPercent: Int?,
    private val accentColor: Int
) : View(context) {

    private val density = resources.displayMetrics.density
    private var valueText = initialValue
    private var animatedPercent = (initialPercent ?: 0).toFloat().coerceIn(0f, 100f)
    private var animatedNumber = initialPercent?.toFloat()
    private var targetPercent = initialPercent
    private var sweepAngle = 0f
    private var progressAnimator: ValueAnimator? = null
    private var indeterminateAnimator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF30394B.toInt()
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7F8FF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8C2D6.toInt()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = accentColor
    }

    fun update(value: String, progress: Int?) {
        valueText = value
        val next = progress?.coerceIn(0, 100)
        targetPercent = next
        progressAnimator?.cancel()
        if (next != null) {
            val fromPercent = animatedPercent
            val fromNumber = animatedNumber ?: next.toFloat()
            progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 480L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    animatedPercent = fromPercent + (next - fromPercent) * fraction
                    animatedNumber = fromNumber + (next - fromNumber) * fraction
                    invalidate()
                }
                start()
            }
            stopIndeterminate()
        } else {
            animatedNumber = null
            startIndeterminate()
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (targetPercent == null) startIndeterminate()
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        stopIndeterminate()
        super.onDetachedFromWindow()
    }

    private fun startIndeterminate() {
        if (indeterminateAnimator?.isRunning == true) return
        indeterminateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1250L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopIndeterminate() {
        indeterminateAnimator?.cancel()
        indeterminateAnimator = null
        sweepAngle = 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(82), widthMeasureSpec),
            resolveSize(dp(84), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = dp(29).toFloat()
        val radius = min(dp(23).toFloat(), min(width * 0.34f, dp(23).toFloat()))
        val stroke = dp(5).toFloat()
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(bounds, -90f, 360f, false, trackPaint)

        if (targetPercent == null) {
            canvas.drawArc(bounds, sweepAngle - 90f, 105f, false, progressPaint)
        } else {
            canvas.drawArc(bounds, -90f, (animatedPercent / 100f) * 360f, false, progressPaint)
        }

        valuePaint.textSize = when {
            valueText.length >= 5 -> dp(10).toFloat()
            valueText.length >= 4 -> dp(11).toFloat()
            else -> dp(13).toFloat()
        }
        val number = animatedNumber
        val display = if (number != null) {
            String.format(Locale.getDefault(), "%.0f%%", number)
        } else {
            valueText
        }
        val baseline = cy - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(display, cx, baseline, valuePaint)
        titlePaint.textSize = dp(9).toFloat()
        canvas.drawText(title, cx, dp(72).toFloat(), titlePaint)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
