package com.offlinevault.app.ui.screens.games

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class GameCategory(val label: String) {
    ALL("All Games"),
    ARCADE("Arcade"),
    PUZZLE("Puzzle"),
    BRAIN("Brain & Logic"),
    CARDS("Cards"),
    STRATEGY("Strategy")
}

data class GameDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: GameCategory,
    val icon: ImageVector,
    val description: String,
    val badge: String,
    val instructions: String
)

object OfflineGameCatalog {

    val games: List<GameDefinition> = listOf(
        GameDefinition(
            id = "2048",
            title = "2048 Cyber",
            subtitle = "Slide & merge numbers",
            category = GameCategory.PUZZLE,
            icon = Icons.Default.GridOn,
            description = "Swipe and merge numbered tiles to reach 2048 and set new high scores.",
            badge = "Puzzle",
            instructions = "Swipe in any direction (or use on-screen arrows / swipe gestures) to slide tiles. When two tiles with the same number touch, they merge into one!"
        ),
        GameDefinition(
            id = "snake",
            title = "Retro Snake",
            subtitle = "Classic arcade crawler",
            category = GameCategory.ARCADE,
            icon = Icons.Default.LinearScale,
            description = "Classic arcade snake with responsive D-pad & swipe controls, speed levels, and retro synth sounds.",
            badge = "Classic Arcade",
            instructions = "Guide the snake to eat energy pellets. Avoid hitting the borders and your own tail! Use swipe gestures or the on-screen D-pad buttons."
        ),
        GameDefinition(
            id = "tetris",
            title = "Block Drop",
            subtitle = "Falling tetromino puzzle",
            category = GameCategory.ARCADE,
            icon = Icons.Default.ViewQuilt,
            description = "Classic falling block arcade puzzle. Rotate, drop, clear lines, and beat high score records.",
            badge = "Arcade Favorite",
            instructions = "Use on-screen controls: Rotate (↻), Move Left/Right, Soft Drop, Hard Drop (⚡), and Hold (📥). Clear horizontal lines to score points and level up."
        ),
        GameDefinition(
            id = "minesweeper",
            title = "Minesweeper",
            subtitle = "Clear the minefield safely",
            category = GameCategory.BRAIN,
            icon = Icons.Default.Dangerous,
            description = "Uncover safe cells without detonating hidden mines. Features Beginner, Intermediate, and Expert grids.",
            badge = "Brain / Logic",
            instructions = "Tap cells to reveal them. Numbers tell you how many mines touch that cell. Toggle between Dig (⛏️) and Flag (🚩) modes, or long-press to flag mines."
        ),
        GameDefinition(
            id = "sudoku",
            title = "Sudoku Master",
            subtitle = "9x9 number logic puzzle",
            category = GameCategory.BRAIN,
            icon = Icons.Default.Numbers,
            description = "Logic-based number puzzle with Easy, Medium, and Hard difficulties, pencil notes, and conflict detection.",
            badge = "Logic Master",
            instructions = "Fill the 9x9 grid so every row, column, and 3x3 box contains digits 1 to 9. Use the Notes mode to jot down candidates and Erase to clear mistakes."
        ),
        GameDefinition(
            id = "glider",
            title = "Cyber Glider",
            subtitle = "Tap-to-fly obstacle dodger",
            category = GameCategory.ARCADE,
            icon = Icons.Default.Flight,
            description = "Navigate your cyber glider through hazardous neon pylons. Smooth 60 FPS physics and high scores.",
            badge = "Reflex Arcade",
            instructions = "Tap anywhere on the screen to boost upwards. Gravity pulls you down. Time your taps to navigate safely through the gaps!"
        ),
        GameDefinition(
            id = "solitaire",
            title = "Klondike Solitaire",
            subtitle = "Timeless single-player cards",
            category = GameCategory.CARDS,
            icon = Icons.Default.Style,
            description = "Classic offline Solitaire. 7 tableau columns, 4 foundations (Ace to King), tap-to-move, and auto-finish.",
            badge = "Cards",
            instructions = "Build 4 foundation piles from Ace to King by suit. In the tableau, stack cards in descending order with alternating colors (Red/Black). Tap cards for instant moves."
        ),
        GameDefinition(
            id = "connect4",
            title = "Connect 4 & Tic-Tac-Toe",
            subtitle = "Offline AI & 2-Player Pass-and-Play",
            category = GameCategory.STRATEGY,
            icon = Icons.Default.Casino,
            description = "Two classic board games in one! Play against the built-in offline AI (Easy/Hard) or challenge a friend locally.",
            badge = "Strategy & AI",
            instructions = "Switch between Connect 4 (drop chips to connect 4 in a row) and Tic-Tac-Toe (3 in a row). Choose 1 Player vs offline AI or 2 Players pass-and-play."
        ),
        GameDefinition(
            id = "memory",
            title = "Memory Matrix",
            subtitle = "Card flip pair matching",
            category = GameCategory.BRAIN,
            icon = Icons.Default.Psychology,
            description = "Test and boost your memory by flipping and matching pairs. 3 grid sizes and multiple vibrant themes.",
            badge = "Memory",
            instructions = "Tap two cards to flip them. If they match, they stay open. If not, they flip back. Find all pairs in the fewest moves and fastest time!"
        ),
        GameDefinition(
            id = "breakout",
            title = "Neon Breakout",
            subtitle = "Brick smashing paddle arcade",
            category = GameCategory.ARCADE,
            icon = Icons.Default.SportsTennis,
            description = "Smash neon bricks with your paddle and bouncing ball. Collect power-ups across 5 challenging levels.",
            badge = "Retro Action",
            instructions = "Drag the paddle or use on-screen buttons to bounce the ball into bricks. Catch falling power-up capsules (Wide Paddle, Multi-Ball, Lasers, Slow Ball) to maximize your score."
        )
    )

    fun getGameById(id: String): GameDefinition? = games.firstOrNull { it.id == id }
}
