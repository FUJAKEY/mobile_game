package com.poplos

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 3D Game View
        gameView = GameSurfaceView(this)

        // 2. UI Overlay (Jump Button)
        val overlayLayout = FrameLayout(this)

        // Jump Button
        val jumpButton = Button(this).apply {
            text = "JUMP"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80FF0099")) // Semi-transparent pink
            layoutParams = FrameLayout.LayoutParams(300, 200).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(50, 50, 50, 50)
            }
            setOnClickListener {
                gameView.jump()
            }
        }

        // Instructions Text
        val instructions = TextView(this).apply {
            text = "Left: Move | Right: Look"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(50, 50, 0, 0)
        }

        overlayLayout.addView(jumpButton)
        overlayLayout.addView(instructions)

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
