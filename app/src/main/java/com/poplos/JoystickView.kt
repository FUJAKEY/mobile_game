package com.poplos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint().apply {
        color = Color.parseColor("#40FFFFFF") // Semi-transparent white
        style = Paint.Style.FILL
    }
    private val stickPaint = Paint().apply {
        color = Color.parseColor("#FF0099") // Neon Pink
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var stickRadius = 0f

    private var stickX = 0f
    private var stickY = 0f

    private var onJoystickMoveListener: ((Float, Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 3f
        stickRadius = baseRadius / 2f

        stickX = centerX
        stickY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw Base
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        // Draw Stick
        canvas.drawCircle(stickX, stickY, stickRadius, stickPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance < baseRadius) {
                    stickX = event.x
                    stickY = event.y
                } else {
                    val ratio = baseRadius / distance
                    stickX = centerX + dx * ratio
                    stickY = centerY + dy * ratio
                }

                // Normalize (-1 to 1)
                val normX = (stickX - centerX) / baseRadius
                val normY = (stickY - centerY) / baseRadius

                onJoystickMoveListener?.invoke(normX, normY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stickX = centerX
                stickY = centerY
                onJoystickMoveListener?.invoke(0f, 0f)
            }
        }
        invalidate()
        return true
    }

    fun setOnMoveListener(listener: (Float, Float) -> Unit) {
        onJoystickMoveListener = listener
    }
}
