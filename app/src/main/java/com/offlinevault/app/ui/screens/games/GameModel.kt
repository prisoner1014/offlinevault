package com.offlinevault.app.ui.screens.games

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
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
            icon = Icons.AutoMirrored.Filled.ViewQuilt,
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
        ),
        GameDefinition(
            id = "slicer",
            title = "Cyber Blade Slicer",
            subtitle = "Fruit Ninja style swipe & slash",
            category = GameCategory.ARCADE,
            icon = Icons.Default.ContentCut,
            description = "Slice flying cyber-orbs and power gems with glowing neon blade trails. Avoid ticking bombs and score massive multi-combos.",
            badge = "Top Hit",
            instructions = "Swipe your finger across the screen to slice through flying targets. Never slice a red skull bomb! Slice 3 or more targets in a single stroke for bonus combo points."
        ),
        GameDefinition(
            id = "crossing",
            title = "Pixel Crossing",
            subtitle = "Crossy Road style highway runner",
            category = GameCategory.ARCADE,
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            description = "Hop across bustling neon highways, rivers with floating rafts, and high-speed train tracks in an infinite retro cyber world.",
            badge = "Endless Classic",
            instructions = "Tap anywhere to hop forward. Swipe Left, Right, or Down to dodge speeding traffic and floating logs. Don't linger too long or the recovery drone will catch you!"
        ),
        GameDefinition(
            id = "jetpack",
            title = "Jetpack Rush",
            subtitle = "Jetpack Joyride style thruster dash",
            category = GameCategory.ARCADE,
            icon = Icons.Default.RocketLaunch,
            description = "Thrust through a high-tech obstacle laboratory. Dodge electric zappers, laser grids, and homing missiles while collecting energy credits.",
            badge = "Reflex Hit",
            instructions = "Touch and hold anywhere to fire bullet thrusters and lift off. Release to drop with gravity. Dodge electric zapper fields and collect floating energy credits."
        ),
        GameDefinition(
            id = "hillclimb",
            title = "Hill Climb Racer",
            subtitle = "Physics 2D jeep hill climber",
            category = GameCategory.ARCADE,
            icon = Icons.Default.DirectionsCar,
            description = "Drive your physics jeep across rugged terrain. Balance throttle and brake, grab gas cans, and avoid flipping over!",
            badge = "Physics Hit",
            instructions = "Hold GAS (Right) to accelerate forward and tilt counter-clockwise in mid-air. Hold BRAKE (Left) to reverse and tilt clockwise. Collect coins and green gas cans to keep moving without running out of fuel!"
        ),
        GameDefinition(
            id = "altos",
            title = "Alto's Mountain Slide",
            subtitle = "Zen downhill snowboard tricks",
            category = GameCategory.ARCADE,
            icon = Icons.Default.Terrain,
            description = "Glide across endless alpine hills at dusk. Jump chasms, bounce off rocky crags, and pull off backflips under a serene pastel sky.",
            badge = "Endless Zen",
            instructions = "Tap anywhere to jump. Touch and hold while in mid-air to rotate backflips! Land cleanly on your board to gain massive speed boosts and lengthen your fluttering scarf."
        )
    )

    fun getGameById(id: String): GameDefinition? = games.firstOrNull { it.id == id }
}
