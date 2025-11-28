package com.poplos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var gameEngine: GameEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameEngine = GameEngine()

        // Hide status bar for full immersion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        setupStartMenu()
    }

    private fun setupStartMenu() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E")) // Dark Background
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val titleText = TextView(this).apply {
            text = "Poplos 2025"
            textSize = 50f
            setTextColor(Color.parseColor("#00F0FF")) // Neon Blue
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 100)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Play Button
        val playButton = createStyledButton("PLAY") {
            showDifficultySelection()
        }

        // Settings Button
        val settingsButton = createStyledButton("SETTINGS") {
            Toast.makeText(this@MainActivity, "Music: ON\nSound: ON", Toast.LENGTH_SHORT).show()
        }

        rootLayout.addView(titleText)
        rootLayout.addView(playButton)
        rootLayout.addView(settingsButton)

        setContentView(rootLayout)
    }

    private fun showDifficultySelection() {
        val difficultyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Select Difficulty"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        difficultyLayout.addView(title)

        GameEngine.Difficulty.values().forEach { diff ->
            val btn = createStyledButton(diff.name) {
                startGame(diff)
            }
            difficultyLayout.addView(btn)
        }

        setContentView(difficultyLayout)
    }

    private fun startGame(difficulty: GameEngine.Difficulty) {
        gameEngine.initGame(difficulty)
        setupGameUI()
        showNextTurn()
    }

    private lateinit var statsText: TextView
    private lateinit var eventTitle: TextView
    private lateinit var eventDesc: TextView
    private lateinit var choicesLayout: LinearLayout

    private fun setupGameUI() {
        val mainGameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        // Stats Bar
        statsText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#F8E71C")) // Accent Yellow
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 20)
        }

        // Card-like container for event
        val eventCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#16213E")) // Panel Background
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#FF0099")) // Neon Pink Border
            }
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // Weight 1
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }

        eventTitle = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        eventDesc = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }

        eventCard.addView(eventTitle)
        eventCard.addView(eventDesc)

        // Choices Area
        choicesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }

        mainGameLayout.addView(statsText)
        mainGameLayout.addView(eventCard)
        mainGameLayout.addView(choicesLayout)

        setContentView(mainGameLayout)
    }

    private fun updateStatsDisplay() {
        statsText.text = "Year: ${gameEngine.year} | 💰 ${gameEngine.budget} | 👥 ${gameEngine.population} | 😊 ${gameEngine.happiness}%"
    }

    private fun showNextTurn() {
        if (gameEngine.gameOver) {
            showGameOver()
            return
        }

        updateStatsDisplay()

        // Get Event
        val event = StoryTeller.getRandomEvent()

        eventTitle.text = event.title
        eventDesc.text = event.description

        choicesLayout.removeAllViews()

        event.choices.forEach { choice ->
            val btn = createStyledButton(choice.text) {
                handleChoice(choice)
            }
            choicesLayout.addView(btn)
        }
    }

    private fun handleChoice(choice: EventChoice) {
        gameEngine.applyEvent(choice)

        // Show result feedback
        val dialog = AlertDialog.Builder(this)
            .setTitle("Result")
            .setMessage(choice.consequenceText)
            .setPositiveButton("Next") { dialog, which ->
                showNextTurn()
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun showGameOver() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("GAME OVER")
            .setMessage("${gameEngine.gameOverReason}\n\nFinal Year: ${gameEngine.year}")
            .setPositiveButton("Return to Menu") { dialog, which ->
                setupStartMenu()
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    // Helper to create "2025 Style" Buttons
    private fun createStyledButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 18f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF0099")) // Neon Pink
                cornerRadius = 50f
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            setOnClickListener { onClick() }
        }
    }
}
