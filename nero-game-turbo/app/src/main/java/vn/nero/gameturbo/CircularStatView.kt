package vn.nero.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Compact circular meter for live device statistics.
 * A null percentage deliberately leaves the ring empty instead of inventing a value.
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
    private var percent = initialPercent?.coerceIn(0, 100)

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
        percent = progress?.coerceIn(0, 100)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(68)
        val desiredHeight = dp(82)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
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
        percent?.let { p ->
            canvas.drawArc(bounds, -90f, (p / 100f) * 360f, false, progressPaint)
        }

        val valueSize = when {
            valueText.length >= 5 -> dp(10).toFloat()
            valueText.length >= 4 -> dp(11).toFloat()
            else -> dp(13).toFloat()
        }
        valuePaint.textSize = valueSize
        val baseline = cy - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(valueText, cx, baseline, valuePaint)

        titlePaint.textSize = dp(9).toFloat()
        canvas.drawText(title, cx, dp(72).toFloat(), titlePaint)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
