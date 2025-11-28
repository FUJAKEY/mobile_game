package com.poplos

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 3D Game View
        gameView = GameSurfaceView(this)

        // 2. UI Overlay
        val overlayLayout = FrameLayout(this)

        // Left Joystick (Move)
        val leftJoystick = JoystickView(this).apply {
            layoutParams = FrameLayout.LayoutParams(400, 400).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(50, 0, 0, 50)
            }
        }
        leftJoystick.setOnMoveListener { x, y ->
            gameView.setMoveInput(x, y)
        }

        // Right Joystick (Look)
        val rightJoystick = JoystickView(this).apply {
            layoutParams = FrameLayout.LayoutParams(400, 400).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 50, 50)
            }
        }
        rightJoystick.setOnMoveListener { x, y ->
            gameView.setLookInput(x, y)
        }

        // Jump Button (Center Bottom or Near Right Joystick)
        // Let's put it above Right Joystick
        val jumpButton = Button(this).apply {
            text = "JUMP"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80FF0099")) // Semi-transparent pink
            layoutParams = FrameLayout.LayoutParams(300, 150).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 100, 500) // Above joystick
            }
            setOnClickListener {
                gameView.jump()
            }
        }

        overlayLayout.addView(leftJoystick)
        overlayLayout.addView(rightJoystick)
        overlayLayout.addView(jumpButton)

        // Combine
        val rootLayout = FrameLayout(this)
        rootLayout.addView(gameView)
        rootLayout.addView(overlayLayout)

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }
}
