package com.poplos

import android.graphics.Color
import android.graphics.RectF

// Game State Class to manage the city and RPG elements
class GameEngine {
    var year: Int = 2025
    var budget: Int = 10000
    var population: Int = 5000
    var happiness: Int = 50
    var difficulty: Difficulty = Difficulty.NORMAL
    var gameOver: Boolean = false
    var gameOverReason: String = ""

    // RPG Elements
    var playerX: Float = 500f
    var playerY: Float = 500f
    val playerSpeed: Float = 10f
    val playerSize: Float = 60f

    val buildings = listOf(
        Building("Мэрия", 100f, 100f, Color.GRAY, null), // Base
        Building("Парк", 800f, 200f, Color.GREEN, StoryTeller.getEvent("park")),
        Building("Завод", 200f, 800f, Color.DKGRAY, StoryTeller.getEvent("factory")),
        Building("Техно-Лаб", 800f, 800f, Color.CYAN, StoryTeller.getEvent("tech")),
        Building("Торговый Центр", 500f, 400f, Color.MAGENTA, StoryTeller.getEvent("shop"))
    )

    enum class Difficulty {
        EASY, NORMAL, HARD
    }

    fun initGame(difficulty: Difficulty) {
        this.difficulty = difficulty
        year = 2025
        playerX = 500f
        playerY = 500f

        when (difficulty) {
            Difficulty.EASY -> {
                budget = 20000
                population = 10000
                happiness = 70
            }
            Difficulty.NORMAL -> {
                budget = 10000
                population = 5000
                happiness = 50
            }
            Difficulty.HARD -> {
                budget = 5000
                population = 1000
                happiness = 30
            }
        }
        gameOver = false
    }

    fun applyEvent(choice: EventChoice) {
        budget += choice.budgetImpact
        population += choice.populationImpact
        happiness += choice.happinessImpact
        year++

        checkGameOver()
    }

    private fun checkGameOver() {
        if (budget <= 0) {
            gameOver = true
            gameOverReason = "Город обанкротился! Вы уволены!"
        } else if (happiness <= 0) {
            gameOver = true
            gameOverReason = "Жители взбунтовались и свергли вас!"
        } else if (population <= 0) {
            gameOver = true
            gameOverReason = "Все покинули город. Это город-призрак."
        }
    }
}

data class Building(
    val name: String,
    val x: Float,
    val y: Float,
    val color: Int,
    val eventTag: String? // Tag to fetch random event or specific event
) {
    val rect: RectF
        get() = RectF(x, y, x + 200f, y + 150f)
}

// Data classes for Events
data class GameEvent(
    val id: String,
    val title: String,
    val description: String,
    val choices: List<EventChoice>
)

data class EventChoice(
    val text: String,
    val budgetImpact: Int,
    val populationImpact: Int,
    val happinessImpact: Int,
    val consequenceText: String
)

// The Story Engine - Translated to Russian
object StoryTeller {
    // Pool of events
    private val events = listOf(
        GameEvent(
            "tech",
            "Регулирование Летающих Машин",
            "2025 год! Стартап хочет запустить летающее такси в Поплосе. Это рискованно, но круто.",
            listOf(
                EventChoice("Одобрить! (Круто)", -1000, 500, 10, "Такси популярны, но иногда падают на крыши."),
                EventChoice("Запретить (Безопасность)", 0, 0, -5, "Жителям скучно, но они живы.")
            )
        ),
        GameEvent(
            "factory",
            "Кибер-Крысы на заводе",
            "ГМО крысы сбежали из лаборатории и грызут интернет-кабели!",
            listOf(
                EventChoice("Нанять Кибер-Котов", -2000, 0, 5, "Коты уничтожили крыс лазерами. Мило, но дорого."),
                EventChoice("Игнорировать", 0, -100, -10, "Нет интернета? Люди в ярости!")
            )
        ),
        GameEvent(
            "park",
            "Туристы с Марса",
            "Пришельцы с Марса хотят построить курорт в центре парка.",
            listOf(
                EventChoice("Добро пожаловать!", 5000, 1000, -10, "Они платят золотом, но пахнут серой."),
                EventChoice("Построить стену", -5000, 0, 5, "Мы останемся людьми. Но бедными.")
            )
        ),
        GameEvent(
            "tech",
            "ИИ Помощник Мэра",
            "IT компания предлагает ИИ для управления городом. Обещают +200% эффективности.",
            listOf(
                EventChoice("Установить ИИ", -500, 0, 0, "ИИ помогает... стоп, почему он запер двери?"),
                EventChoice("Довериться чутью", 0, 0, 5, "Человеческая интуиция побеждает.")
            )
        ),
        GameEvent(
            "shop",
            "Неоновый Фестиваль",
            "Молодежь хочет устроить грандиозный фестиваль света.",
            listOf(
                EventChoice("Финансировать", -3000, 200, 20, "Город сияет! Туристы в восторге."),
                EventChoice("Слишком шумно", 0, -50, -10, "Вас прозвали 'Скучный Мэр'.")
            )
        ),
         GameEvent(
            "factory",
            "Крипто-Дороги",
            "Предложение замостить дороги старыми видеокартами для майнинга.",
            listOf(
                EventChoice("Сделать это", -1000, 0, 10, "Дороги теплые и майнят крипту зимой."),
                EventChoice("Вы спятили?", 0, 0, 0, "Мы оставим асфальт.")
            )
        ),
        GameEvent(
            "shop",
            "Битва Гигантских Роботов",
            "Два меха решили подраться в центре города.",
            listOf(
                EventChoice("Продавать билеты", 10000, -500, -20, "Прибыльное разрушение!"),
                EventChoice("Эвакуация и Ремонт", -5000, 0, 10, "Дорого, но люди ценят заботу.")
            )
        )
    )

    fun getEvent(tag: String?): String? {
        // Just returns the tag to identify type, real event logic fetches random per tag or completely random
        return tag
    }

    fun getRandomEvent(tag: String?): GameEvent {
        // If tag matches, try to find one, else random
        val filtered = if (tag != null) events.filter { it.id == tag } else events
        if (filtered.isNotEmpty()) return filtered.random()
        return events.random()
    }
}
