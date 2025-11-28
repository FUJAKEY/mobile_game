package com.poplos

// Game State Class to manage the city
class GameEngine {
    var year: Int = 2025
    var budget: Int = 10000
    var population: Int = 5000
    var happiness: Int = 50
    var difficulty: Difficulty = Difficulty.NORMAL
    var gameOver: Boolean = false
    var gameOverReason: String = ""

    enum class Difficulty {
        EASY, NORMAL, HARD
    }

    fun initGame(difficulty: Difficulty) {
        this.difficulty = difficulty
        year = 2025
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
            gameOverReason = "City went bankrupt! You are fired!"
        } else if (happiness <= 0) {
            gameOver = true
            gameOverReason = "Citizens rioted and overthrew you!"
        } else if (population <= 0) {
            gameOver = true
            gameOverReason = "Everyone left the city. It's a ghost town."
        }
    }
}

// Data classes for Events
data class GameEvent(
    val title: String,
    val description: String,
    val choices: List<EventChoice>
)

data class EventChoice(
    val text: String,
    val budgetImpact: Int,
    val populationImpact: Int,
    val happinessImpact: Int,
    val consequenceText: String // Text shown after selection
)

// The Story Engine
object StoryTeller {
    private val events = listOf(
        GameEvent(
            "Flying Car Regulations",
            "It's 2025! A startup wants to launch a flying taxi service in Poplos. It's risky but cool.",
            listOf(
                EventChoice("Approve it! (Cool factor)", -1000, 500, 10, "The taxis are a hit, but crash occasionally."),
                EventChoice("Ban it (Safety first)", 0, 0, -5, "Citizens are bored but safe.")
            )
        ),
        GameEvent(
            "Cyber-Rat Infestation",
            "Genetically modified rats escaped from the lab. They are eating the internet cables!",
            listOf(
                EventChoice("Hire Cyber-Cats", -2000, 0, 5, "The cats laser-beamed the rats. Cute but expensive."),
                EventChoice("Ignore them", 0, -100, -10, "No internet? People are furious!")
            )
        ),
        GameEvent(
            "Alien Tourism",
            "Aliens from Mars want to build a resort in the city center.",
            listOf(
                EventChoice("Welcome them!", 5000, 1000, -10, "They pay in gold, but smell like sulfur."),
                EventChoice("Build a wall", -5000, 0, 5, "We stay human. But poor.")
            )
        ),
        GameEvent(
            "AI Mayor Assistant",
            "A tech company offers an AI to help you run the city. It promises +200% efficiency.",
            listOf(
                EventChoice("Install AI", -500, 0, 0, "The AI is helpful... wait, why is it locking the doors?"),
                EventChoice("Trust my gut", 0, 0, 5, "Human intuition wins this round.")
            )
        ),
        GameEvent(
            "The Neon Festival",
            "Youth wants to organize a massive neon light festival.",
            listOf(
                EventChoice("Fund it", -3000, 200, 20, "The city glows! Tourists flock in."),
                EventChoice("Too loud", 0, -50, -10, "You become known as the 'Boring Mayor'.")
            )
        ),
         GameEvent(
            "Crypto-Roads",
            "A proposal to pave roads with discarded crypto-mining GPUs.",
            listOf(
                EventChoice("Do it", -1000, 0, 10, "The roads are warm and compute hash in winter."),
                EventChoice("Are you crazy?", 0, 0, 0, "We stick to asphalt.")
            )
        ),
        GameEvent(
            "Giant Robot Fight",
            "Two giant mechs decided to fight in downtown.",
            listOf(
                EventChoice("Sell tickets", 10000, -500, -20, "Profitable destruction!"),
                EventChoice("Evacuate & Repair", -5000, 0, 10, "Expensive, but people appreciate the safety.")
            )
        )
    )

    fun getRandomEvent(): GameEvent {
        return events.random()
    }
}
