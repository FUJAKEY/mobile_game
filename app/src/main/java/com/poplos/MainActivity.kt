package com.poplos

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var gameEngine: GameEngine
    private var gameView: GameView? = null
    private lateinit var statsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameEngine = GameEngine()

        // Hide status bar for full immersion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        setupStartMenu()
    }

    private fun setupStartMenu() {
        val menuLayout = LinearLayout(this).apply {
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
            text = "Поплос 2025"
            textSize = 50f
            setTextColor(Color.parseColor("#00F0FF")) // Neon Blue
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 100)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Play Button
        val playButton = createStyledButton("ИГРАТЬ") {
            showDifficultySelection()
        }

        // Settings Button
        val settingsButton = createStyledButton("НАСТРОЙКИ") {
            Toast.makeText(this@MainActivity, "Музыка: ВКЛ\nЗвук: ВКЛ", Toast.LENGTH_SHORT).show()
        }

        menuLayout.addView(titleText)
        menuLayout.addView(playButton)
        menuLayout.addView(settingsButton)

        setContentView(menuLayout)
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
            text = "Выберите сложность"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        difficultyLayout.addView(title)

        // Translated Difficulty buttons
        val difficulties = mapOf(
            GameEngine.Difficulty.EASY to "Легко",
            GameEngine.Difficulty.NORMAL to "Нормально",
            GameEngine.Difficulty.HARD to "Сложно"
        )

        difficulties.forEach { (diff, name) ->
            val btn = createStyledButton(name) {
                startGame(diff)
            }
            difficultyLayout.addView(btn)
        }

        setContentView(difficultyLayout)
    }

    private fun startGame(difficulty: GameEngine.Difficulty) {
        gameEngine.initGame(difficulty)
        setupGameUI()
    }

    private fun setupGameUI() {
        rootLayout = FrameLayout(this)

        // 1. Game View (The Map)
        gameView = GameView(this, gameEngine) { building ->
            handleBuildingInteraction(building)
        }
        rootLayout.addView(gameView)

        // 2. HUD (Stats)
        val statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent
            setPadding(20, 20, 20, 20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        statsText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#F8E71C"))
        }
        updateStatsDisplay()

        statsContainer.addView(statsText)
        rootLayout.addView(statsContainer)

        setContentView(rootLayout)

        Toast.makeText(this, "Исследуйте город! Идите к зданиям!", Toast.LENGTH_LONG).show()
    }

    private fun updateStatsDisplay() {
        statsText.text = "Год: ${gameEngine.year} | 💰 ${gameEngine.budget} | 👥 ${gameEngine.population} | 😊 ${gameEngine.happiness}%"
    }

    private fun handleBuildingInteraction(building: Building) {
        if (gameEngine.gameOver) return

        if (building.eventTag == null) {
            Toast.makeText(this, "Это ${building.name}. Здесь тихо.", Toast.LENGTH_SHORT).show()
            return
        }

        // Trigger Event
        val event = StoryTeller.getRandomEvent(building.eventTag)
        showEventDialog(event)
    }

    private fun showEventDialog(event: GameEvent) {
        // Custom Dialog Layout for Undertale style
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(6, Color.WHITE) // White border
                cornerRadius = 10f
            }
        }

        // Title
        val titleView = TextView(this).apply {
            text = event.title
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        dialogView.addView(titleView)

        // Description
        val descView = TextView(this).apply {
            text = event.description
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 40)
        }
        dialogView.addView(descView)

        // Create the dialog now so we can dismiss it in buttons
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        builder.setView(dialogView)
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Transparent background for custom shape

        // Choices
        event.choices.forEach { choice ->
            val btn = Button(this).apply {
                text = "* ${choice.text}"
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.TRANSPARENT) // Transparent button background
                setOnClickListener {
                    dialog.dismiss()
                    handleChoice(choice)
                }
            }
            dialogView.addView(btn)
        }

        dialog.show()
    }

    private fun handleChoice(choice: EventChoice) {
        gameEngine.applyEvent(choice)
        updateStatsDisplay()

        // Show result feedback
        val dialog = AlertDialog.Builder(this)
            .setTitle("Результат")
            .setMessage(choice.consequenceText)
            .setPositiveButton("ОК") { dialog, which ->
                if (gameEngine.gameOver) {
                    showGameOver()
                }
            }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun showGameOver() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("ИГРА ОКОНЧЕНА")
            .setMessage("${gameEngine.gameOverReason}\n\nФинальный Год: ${gameEngine.year}")
            .setPositiveButton("В Меню") { dialog, which ->
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
