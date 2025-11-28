package com.poplos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class GameView(context: Context, private val gameEngine: GameEngine, private val onEventTriggered: (Building) -> Unit) : View(context) {

    private val paint = Paint()
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }

    // Generate a simple pixel art bitmap for the player
    private val playerBitmap: Bitmap by lazy {
        createPixelCharacter()
    }

    private var targetX: Float? = null
    private var targetY: Float? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Background (Black for retro feel)
        canvas.drawColor(Color.BLACK)

        // Draw Grid Lines (optional, for retro feel)
        paint.color = Color.DKGRAY
        paint.strokeWidth = 2f
        for (i in 0 until width step 100) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), paint)
        }
        for (i in 0 until height step 100) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
        }

        // Draw Buildings
        gameEngine.buildings.forEach { building ->
            paint.color = building.color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawRect(building.rect, paint) // Outline

            paint.style = Paint.Style.FILL
            paint.alpha = 100
            canvas.drawRect(building.rect, paint) // Transparent fill
            paint.alpha = 255

            // Pixelated Text
            canvas.drawText(building.name, building.rect.centerX(), building.rect.centerY(), textPaint)
        }

        // Draw Player (Pixel Art Bitmap)
        // Center the bitmap on playerX, playerY
        val destRect = Rect(
            (gameEngine.playerX - gameEngine.playerSize).toInt(),
            (gameEngine.playerY - gameEngine.playerSize).toInt(),
            (gameEngine.playerX + gameEngine.playerSize).toInt(),
            (gameEngine.playerY + gameEngine.playerSize).toInt()
        )
        canvas.drawBitmap(playerBitmap, null, destRect, null)

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
                gameEngine.playerX -= 15
                gameEngine.playerY -= 15

                // Trigger Event
                onEventTriggered(building)
            }
        }
    }

    private fun createPixelCharacter(): Bitmap {
        val width = 16
        val height = 16
        val pixels = IntArray(width * height)

        // Simple "Smile" or Character pattern
        // 0 = Transparent, 1 = White
        val pattern = listOf(
            0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,
            0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,
            0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,
            0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,
            0,1,1,0,0,1,1,1,1,1,1,0,0,1,1,0, // Eyes
            1,1,1,0,0,1,1,1,1,1,1,0,0,1,1,1,
            1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1, // Mouth
            1,1,1,1,1,0,1,1,1,1,0,1,1,1,1,1,
            0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,
            0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,
            0,0,0,1,1,1,1,1,1,1,1,1,1,0,0,0,
            0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,
            0,0,0,0,0,0,1,0,0,1,0,0,0,0,0,0, // Legs
            0,0,0,0,0,1,1,0,0,1,1,0,0,0,0,0
        )

        for (i in pixels.indices) {
            pixels[i] = if (pattern[i] == 1) Color.WHITE else Color.TRANSPARENT
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
