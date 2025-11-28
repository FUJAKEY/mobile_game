package com.poplos

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer: GameRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = GameRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // Pass Joystick Inputs to Renderer
    fun setMoveInput(x: Float, y: Float) {
        queueEvent {
            renderer.joyMoveX = x
            renderer.joyMoveY = y
        }
    }

    fun setLookInput(x: Float, y: Float) {
        queueEvent {
            renderer.joyLookX = x
            renderer.joyLookY = y
        }
    }

    fun jump() {
        queueEvent {
            renderer.jump()
        }
    }
}
