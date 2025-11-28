package com.poplos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GameView(context: Context, private val gameEngine: GameEngine, private val onEventTriggered: (Building) -> Unit) : View(context) {

    private val paint = Paint()
    private val playerPaint = Paint().apply { color = Color.RED }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private var targetX: Float? = null
    private var targetY: Float? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Background
        canvas.drawColor(Color.parseColor("#1A1A2E")) // Dark Blue ground

        // Draw Buildings
        gameEngine.buildings.forEach { building ->
            paint.color = building.color
            canvas.drawRect(building.rect, paint)
            canvas.drawText(building.name, building.rect.centerX(), building.rect.centerY(), textPaint)
        }

        // Draw Player (Mayor)
        canvas.drawCircle(gameEngine.playerX, gameEngine.playerY, gameEngine.playerSize / 2, playerPaint)

        // Move Player Logic (Simple interpolation)
        if (targetX != null && targetY != null) {
            val dx = targetX!! - gameEngine.playerX
            val dy = targetY!! - gameEngine.playerY
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            if (distance > 10f) {
                val moveX = (dx / distance) * gameEngine.playerSpeed
                val moveY = (dy / distance) * gameEngine.playerSpeed
                gameEngine.playerX += moveX
                gameEngine.playerY += moveY
                invalidate() // Redraw next frame
                checkCollisions()
            } else {
                targetX = null
                targetY = null
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            targetX = event.x
            targetY = event.y
            invalidate() // Start movement loop
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun checkCollisions() {
        val playerRect = RectF(
            gameEngine.playerX - gameEngine.playerSize/2,
            gameEngine.playerY - gameEngine.playerSize/2,
            gameEngine.playerX + gameEngine.playerSize/2,
            gameEngine.playerY + gameEngine.playerSize/2
        )

        gameEngine.buildings.forEach { building ->
            if (RectF.intersects(playerRect, building.rect)) {
                // Stop movement
                targetX = null
                targetY = null
                // Move player back slightly to avoid infinite loop trigger
                gameEngine.playerX -= 10
                gameEngine.playerY -= 10

                // Trigger Event
                onEventTriggered(building)
            }
        }
    }
}
