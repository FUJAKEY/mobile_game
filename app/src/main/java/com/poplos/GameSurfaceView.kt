package com.poplos

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer: GameRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = GameRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // Touch handling for virtual joystick/camera
    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private val TOUCH_SCALE_FACTOR = 0.005f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y

        when (e.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY

                // Left side of screen = Move, Right side = Look
                if (x > width / 2) {
                    // Look
                    renderer.cameraYaw += dx * 0.2f
                    renderer.cameraPitch -= dy * 0.2f
                    // Clamp pitch
                    if (renderer.cameraPitch > 89f) renderer.cameraPitch = 89f
                    if (renderer.cameraPitch < -89f) renderer.cameraPitch = -89f
                } else {
                    // Move
                    // For simplicity in this step, simple forward/back based on Y drag
                    // A proper joystick is harder to implement in raw GL view without UI overlay
                    // We will handle movement in GameRenderer based on a "moving" flag or similar?
                    // actually let's just pass the deltas to renderer to interpret as velocity

                    renderer.inputMoveX = dx * 0.05f
                    renderer.inputMoveZ = dy * 0.05f
                }
            }
            MotionEvent.ACTION_DOWN -> {
                // If tap on bottom center, Jump?
                 if (x < width / 2 && y > height - 200) {
                     // Virtual jump zone?
                 }
            }
            MotionEvent.ACTION_UP -> {
                 renderer.inputMoveX = 0f
                 renderer.inputMoveZ = 0f
            }
        }

        previousX = x
        previousY = y
        return true
    }

    fun jump() {
        renderer.jump()
    }
}
