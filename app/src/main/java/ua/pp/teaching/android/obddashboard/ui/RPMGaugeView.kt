/*
MIT License

Copyright (c) 2025 Ronan Le Meillat - TEAChing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ua.pp.teaching.android.obddashboard.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import ua.pp.teaching.android.obddashboard.R

/**
 * Custom view for displaying RPM gauge with color zones Displays a 3/8 circle arc with white,
 * orange, and red zones
 */
class RPMGaugeView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var strokeWidth = 20f

    // RPM ranges and colors
    private val maxRpm = 6000f // Changed from 8000f
    private val normalZoneEnd = 4000f // Changed from 5000f
    private val warningZoneEnd = 5000f // Changed from 6500f

    // Current RPM value
    private var currentRpm = 0f

    // Arc angle (3/8 of circle = 135 degrees)
    private val sweepAngle = 135f
    private val startAngle = 202.5f // Start from bottom left

    // Colors
    private val normalColor = context.getColor(R.color.gauge_normal)
    private val warningColor = context.getColor(R.color.gauge_warning)
    private val dangerColor = context.getColor(R.color.gauge_danger)
    private val textColor = context.getColor(R.color.text_primary)
    private val secondaryTextColor = context.getColor(R.color.text_secondary)

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = strokeWidth

        textPaint.color = textColor
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = strokeWidth
        backgroundPaint.color = Color.GRAY
        backgroundPaint.alpha = 50 // Use a less prominent background
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = 2f * h / 3f
        radius = (min(w, h) / 2f) - strokeWidth // Adjusted for potentially smaller gauge
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // Draw background arc
        paint.color = backgroundPaint.color
        paint.alpha = backgroundPaint.alpha
        canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
        paint.alpha = 255 // Reset alpha for other paint operations

        // Calculate zones in degrees
        val normalAngle = (normalZoneEnd / maxRpm) * sweepAngle
        val warningAngle = ((warningZoneEnd - normalZoneEnd) / maxRpm) * sweepAngle
        val dangerAngle = sweepAngle - normalAngle - warningAngle

        // Draw normal zone (white)
        paint.color = normalColor
        canvas.drawArc(rect, startAngle, normalAngle, false, paint)

        // Draw warning zone (orange)
        paint.color = warningColor
        canvas.drawArc(rect, startAngle + normalAngle, warningAngle, false, paint)

        // Draw danger zone (red)
        paint.color = dangerColor
        canvas.drawArc(rect, startAngle + normalAngle + warningAngle, dangerAngle, false, paint)

        // Draw needle
        drawNeedle(canvas)

        // Draw center circle (no longer needed based on screenshot)
        // paint.style = Paint.Style.FILL
        // paint.color = textColor
        // canvas.drawCircle(centerX, centerY, strokeWidth / 2, paint)
        // paint.style = Paint.Style.STROKE

        // Draw RPM value in center
        textPaint.textSize = radius * 0.2f // Slightly larger text
        textPaint.color = textColor // Ensure text color is set
        canvas.drawText(
                context.getString(R.string.rpm_label).uppercase(), // "RPM"
                centerX,
                centerY - textPaint.textSize * 0.5f, // Adjusted position
                textPaint
        )

        // Draw RPM unit label ("x 1000")
        textPaint.textSize = radius * 0.1f // Slightly larger unit text
        textPaint.color = secondaryTextColor // Ensure text color is set for unit
        canvas.drawText(
                context.getString(R.string.rpm_unit_suffix), // "x 1000"
                centerX,
                centerY + textPaint.textSize * 2.8f, // Adjusted position to be lower
                textPaint
        )

        // Draw tick marks and numbers
        drawTickMarks(canvas)
    }

    private fun drawNeedle(canvas: Canvas) {
        val rpmRatio = (currentRpm / maxRpm).coerceIn(0f, 1f)
        val needleAngle = startAngle + (rpmRatio * sweepAngle)
        val needleRadians = Math.toRadians(needleAngle.toDouble())

        val needleLength = radius * 0.85f // Needle a bit longer
        val needleEndX = centerX + cos(needleRadians).toFloat() * needleLength
        val needleEndY = centerY + sin(needleRadians).toFloat() * needleLength

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth / 2.5f // Thicker needle
        paint.color = textColor // Needle color matching text
        paint.strokeCap = Paint.Cap.BUTT // Butt cap for needle
        canvas.drawLine(centerX, centerY, needleEndX, needleEndY, paint)

        // Draw needle base/pivot
        paint.style = Paint.Style.FILL
        paint.color = textColor
        canvas.drawCircle(centerX, centerY, strokeWidth / 1.5f, paint) // Larger pivot

        // Reset paint properties
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND // Reset cap for arc
    }

    private fun drawTickMarks(canvas: Canvas) {
        val tickCount = 6 // 0, 1, 2, 3, 4, 5, 6 (for 0-6000 RPM)
        textPaint.textSize = radius * 0.1f // Larger tick numbers
        textPaint.color = secondaryTextColor

        for (i in 0..tickCount) {
            val rpm = (i * 1000).toFloat()
            val rpmRatio = rpm / maxRpm
            val angle = startAngle + (rpmRatio * sweepAngle)
            val radians = Math.toRadians(angle.toDouble())

            val innerRadius = radius * 0.9f
            val outerRadius = radius // Ticks go to the edge of the gauge background

            val innerX = centerX + cos(radians).toFloat() * innerRadius
            val innerY = centerY + sin(radians).toFloat() * innerRadius
            val outerX = centerX + cos(radians).toFloat() * outerRadius
            val outerY = centerY + sin(radians).toFloat() * outerRadius

            paint.strokeWidth = 3f // Thicker ticks
            paint.color = secondaryTextColor
            canvas.drawLine(innerX, innerY, outerX, outerY, paint)

            // Draw numbers (all numbers, not just even)
            val textRadius = radius * 0.78f // Numbers closer to center
            val textX = centerX + cos(radians).toFloat() * textRadius
            val textY =
                    centerY +
                            sin(radians).toFloat() * textRadius +
                            textPaint.textSize * 0.4f // Adjust Y for alignment
            canvas.drawText(i.toString(), textX, textY, textPaint)
        }
        paint.strokeWidth = strokeWidth // Reset stroke width
    }

    /**
     * Updates the RPM value and redraws the gauge
     * @param rpm RPM value (0-6000)
     */
    fun setRpm(rpm: Float) {
        currentRpm = rpm.coerceIn(0f, maxRpm)
        invalidate()
    }

    /**
     * Gets the current RPM value
     * @return current RPM value
     */
    fun getRpm(): Float = currentRpm
}
