package com.poplos

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer: GameRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = GameRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private var previousX = 0f
    private var previousY = 0f
    private val SCALE_FACTOR = 0.15f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y

        when (e.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY

                // Inverse Y for natural look
                queueEvent {
                    renderer.rotateCamera(dx * SCALE_FACTOR, -dy * SCALE_FACTOR)
                }
            }
        }

        previousX = x
        previousY = y
        return true
    }

    // Pass Joystick Inputs to Renderer
    fun setMoveInput(x: Float, y: Float) {
        queueEvent {
            renderer.joyMoveX = x
            renderer.joyMoveY = y
        }
    }

    fun jump() {
        queueEvent {
            renderer.jump()
        }
    }
}
