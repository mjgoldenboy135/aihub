package com.bluekey.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onJoystickMoved: ((x: Int, y: Int) -> Unit)? = null

    private val outerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.FILL
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC")
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var thumbRadius = 0f
    private var maxDist = 0f

    private var thumbX = 0f
    private var thumbY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = min(w, h) / 2f - 8f
        thumbRadius = outerRadius * 0.30f
        maxDist = outerRadius - thumbRadius
        thumbX = centerX
        thumbY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centerX, centerY, outerRadius, outerFillPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Prevent parent from stealing MOVE events (fixes uncontrolled movement)
                parent?.requestDisallowInterceptTouchEvent(true)
                updateThumb(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateThumb(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                thumbX = centerX
                thumbY = centerY
                onJoystickMoved?.invoke(0, 0)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun updateThumb(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= maxDist) {
            thumbX = touchX
            thumbY = touchY
        } else {
            val ratio = maxDist / dist
            thumbX = centerX + dx * ratio
            thumbY = centerY + dy * ratio
        }

        val normX = ((thumbX - centerX) / maxDist * 127).toInt().coerceIn(-127, 127)
        val normY = ((thumbY - centerY) / maxDist * 127).toInt().coerceIn(-127, 127)
        onJoystickMoved?.invoke(normX, normY)
        invalidate()
    }
}
