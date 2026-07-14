package com.tdpham.games.syobon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.R

class SyobonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "cat_meowio"
    override var onGameOver: ((Int) -> Unit)? = null

    companion object {
        private const val PREFS_NAME = "cat_meowio_settings"
        private const val ROWS = 15
        private const val COLS = 20
        private const val TOTAL_MAP_COLS = 100
        
        // Physics
        private const val GRAVITY = 0.018f
        private const val SPEED_ACCEL = 0.03f
        private const val FRICTION = 0.90f
        private const val MAX_SPEED = 0.22f
        private const val JUMP_IMPULSE = -0.35f
        private const val JUMP_HOLD_FORCE = -0.015f
        private const val MAX_JUMP_HOLD_TIME = 330L
        
        // Tile Types
        private const val TILE_EMPTY = 0
        private const val TILE_GROUND = 1
        private const val TILE_BRICK = 2
        private const val TILE_MYSTERY = 3
        private const val TILE_INVISIBLE = 4
        private const val TILE_SPIKE = 5
        private const val TILE_PIPE_BODY = 6
        private const val TILE_PIPE_TOP = 7
        private const val TILE_FLAGPOLE = 8
        private const val TILE_LAVA = 9
        private const val TILE_BRIDGE = 10
        private const val TILE_SPENT = 11
        private const val TILE_DEEP_GROUND = 12
        private const val TILE_HORIZ_PIPE_BODY = 13
        private const val TILE_HORIZ_PIPE_RIM = 14

        // Colors (Pre-parsed for performance)
        private val COLOR_SKY_DAY = Color.parseColor("#80D8FF")
        private val COLOR_SKY_CAVERN = Color.parseColor("#151B26")
        private val COLOR_SKY_CASTLE = Color.parseColor("#1A0A0A")
        private val COLOR_CLAY = Color.parseColor("#8D6E63")
        private val COLOR_GRASS = Color.parseColor("#4CAF50")
        private val COLOR_DAISY_YELLOW = Color.parseColor("#FFD54F")
        private val COLOR_BRICK_COPPER = Color.parseColor("#BF360C")
        private val COLOR_BRICK_HIGHLIGHT = Color.parseColor("#FF8A65")
        private val COLOR_BRICK_LINE = Color.parseColor("#3E2723")
        private val COLOR_NEON_ORANGE = Color.parseColor("#FF5722")
        private val COLOR_GLASS_CYAN = Color.parseColor("#B3E5FC")
        private val COLOR_GLASS_WHITE = Color.parseColor("#80FFFFFF")
        private val COLOR_IRON_BASE = Color.parseColor("#37474F")
        private val COLOR_PLASMA_AURA = Color.parseColor("#40FF1744")
        private val COLOR_METAL_SPIKE = Color.parseColor("#90A4AE")
        private val COLOR_PLASMA_GLOW = Color.parseColor("#FF1744")
        private val COLOR_PIPE_JADE = Color.parseColor("#2E7D32")
        private val COLOR_PIPE_DARK = Color.parseColor("#1B5E20")
        private val COLOR_FLAG_SEGMENT = Color.parseColor("#ECEFF1")
        private val COLOR_FLAG_GOLD = Color.parseColor("#FFD700")
        private val COLOR_FLAG_CYAN = Color.parseColor("#00E5FF")
        private val COLOR_LAVA_RED = Color.parseColor("#F44336")
        private val COLOR_BRIDGE_VIOLET = Color.parseColor("#6A1B9A")
        private val COLOR_BRIDGE_LIGHT = Color.parseColor("#BA68C8")
        private val COLOR_SPENT_BROWN = Color.parseColor("#5D4037")
        private val COLOR_SPENT_BORDER = Color.parseColor("#3E2723")
        private val COLOR_DEEP_SPOT = Color.parseColor("#795548")
        private val COLOR_NYAN_PURPLE = Color.parseColor("#E040FB")
        private val COLOR_NYAN_CYAN = Color.parseColor("#00E5FF")
        private val COLOR_NYAN_BLUE = Color.parseColor("#1976D2")
        private val COLOR_NYAN_GOLD = Color.parseColor("#FFD54F")
        private val COLOR_MUSHROOM_PURPLE = Color.parseColor("#7B1FA2")
        private val COLOR_SLIME_CYAN = Color.parseColor("#00E5FF")
        private val COLOR_SLIME_GREEN = Color.parseColor("#00E676")
        private val COLOR_SLIME_YELLOW = Color.parseColor("#FFD600")
        private val COLOR_SHELL_IRON = Color.parseColor("#455A64")
        private val COLOR_SHELL_LIGHT = Color.parseColor("#CFD8DC")
        private val COLOR_SHELL_ORANGE = Color.parseColor("#FF6F00")
        private val COLOR_HEAVY_SHELL = Color.parseColor("#212121")
        private val COLOR_CAT_GOLD = Color.parseColor("#FFD700")
        private val COLOR_CAT_SHADOW = Color.parseColor("#37474F")
        private val COLOR_EAR_PINK = Color.parseColor("#FFCDD2")
        private val COLOR_EAR_GOLD = Color.parseColor("#FFF59D")
        private val COLOR_EAR_SHADOW = Color.parseColor("#7E57C2")
        private val COLOR_COLLAR_BLUE = Color.parseColor("#00E5FF")
        private val COLOR_TIE_RED = Color.parseColor("#D50000")
        private val COLOR_EYE_CLASSIC = Color.parseColor("#29B6F6")
        private val COLOR_EYE_GOLD = Color.parseColor("#00C853")
        private val COLOR_OVERLAY_DIM = Color.parseColor("#B3000000")
        private val COLOR_DIALOG_BG = Color.parseColor("#212121")
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val celebrationManager = CelebrationManager()
    
    // Reusable objects to minimize allocations in draw/update
    private val tempRectF = RectF()
    private val tempPath = Path()
    private val random = java.util.Random()
    
    // Core game state
    private var gameOver = false
    private var gamePaused = false
    private var isLevelCleared = false
    private var currentLevel = 1
    private var isInitialized = false
    
    // Lives & death count
    private var currentLives = 3
    private var deaths = 0
    private var initialLivesOption = 3 // 3, 1, or -99
    private var catType = 0
    private var spikeLauncherPipeCol = 55
    private var hintShowFrames = 0
    private var best = 0
    private var levelResetCount = 0

    // Screen dimension and view scaling
    private var cellW = 0f
    private var cellH = 0f
    private var cameraX = 0f // camera scroll offset

    // Player (Cat) variables
    private var playerX = 2f // in cell coordinates
    private var playerY = 11f
    private val playerW = 0.8f
    private val playerH = 0.8f
    private var velX = 0f
    private var velY = 0f
    private var isOnGround = false
    private var isFacingRight = true
    private var jumpHoldTimer = 0L
    private var isDying = false
    private var deathTime = 0L
    private var dieSpinAngle = 0f

    // Keyboard Tracking
    private val pressedKeys = mutableSetOf<Int>()

    // Level elements & trap states
    private val map = Array(ROWS) { IntArray(TOTAL_MAP_COLS) }
    private val trapTriggered = BooleanArray(TOTAL_MAP_COLS)
    private val invisibleBlocks = Array(ROWS) { BooleanArray(TOTAL_MAP_COLS) } 
    private val fallingBlocks = Array(ROWS) { FloatArray(TOTAL_MAP_COLS) }
    
    // Pipe Launchers for periodic hazards
    private class PipeLauncher(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val isHorizontal: Boolean = false
    )
    private val pipeLaunchers = mutableListOf<PipeLauncher>()

    // Floating entity traps (Nyan cats / flying spikes)
    private class TrapEntity(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val type: Int, // 0: Nyan Cat, 1: Flying Spike, 2: Popup Ground Spike, 3: Companion Slime, 4: Golden Heart, 5: Lucky Bell
        val variant: Int = 0,
        val sourceX: Float = -1f, // If periodic, spawn from here
        val sourceY: Float = -1f,
        var respawnTime: Long = 0L
    )
    private val trapEntities = mutableListOf<TrapEntity>()

    // Land patrolling enemies (Goomba/Syobon style)
    private class LandEnemy(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var isDead: Boolean = false,
        val type: Int = 0, // 0: Cyber Slime, 1: Iron Shell, 2: Jump Slime, 3: Heavy Shell
        val variant: Int = 0,
        var huntTimer: Int = 0, // Frames to chase the cat
        var isOnGround: Boolean = false,
        var isChasing: Boolean = false
    )
    private val landEnemies = mutableListOf<LandEnemy>()

    // Brick breaking particles
    private class Debris(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Int = 40
    )
    private val debrisParticles = mutableListOf<Debris>()

    // Feedback Popups (+1 Life, -5 Ouchies, etc.)
    private class FeedbackPopup(
        var x: Float,
        var y: Float,
        val text: String,
        val color: Int,
        var life: Int = 120 // Increased from 60 to 120 for longer readability
    )
    private val feedbackPopups = mutableListOf<FeedbackPopup>()

    // Mystery Box Animation state
    private val activeBoxBumps = Array(ROWS) { FloatArray(TOTAL_MAP_COLS) }

    // Game loop
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUpdate = 0L
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver && !isLevelCleared) {
                update()
            }
            invalidate()
            mainHandler.postDelayed(this, 16)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun startGame() {
        mainHandler.removeCallbacks(gameLoop)
        gamePaused = false
        lastUpdate = System.currentTimeMillis()
        mainHandler.post(gameLoop)
    }

    override fun pause() {
        gamePaused = true
    }

    override fun resume() {
        gamePaused = false
        lastUpdate = System.currentTimeMillis()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    override fun resetGame() {
        currentLevel = 1
        // Load preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val livesOption = prefs.getInt(SyobonOptionsDialog.KEY_LIVES_TYPE, 0)
        initialLivesOption = when (livesOption) {
            1 -> 1
            2 -> -99
            else -> 3
        }
        currentLives = initialLivesOption
        catType = prefs.getInt("selected_cat_type", 0)
        if (initialLivesOption == -99) {
            // Keep deaths from resetting to preserve the joke
        } else {
            deaths = 0
        }
        
        // Load best (lowest ouchies is best, but ScoreManager tracks high scores. 
        // We'll treat completion as the record)
        best = ScoreManager.getHighScore(context, gameKey, 0)

        resetLevelState()
        gameOver = false
        gamePaused = false
        isLevelCleared = false
        hintShowFrames = 100
        
        startGame()
    }

    private fun resetLevelState() {
        levelResetCount++
        playerX = 2f
        playerY = 11f
        velX = 0f
        velY = 0f
        cameraX = 0f
        isOnGround = false
        isFacingRight = true
        isDying = false
        trapEntities.clear()
        landEnemies.clear()
        pipeLaunchers.clear()
        debrisParticles.clear()
        trapTriggered.fill(false)
        invisibleBlocks.forEach { it.fill(false) }
        fallingBlocks.forEach { it.fill(0f) }

        // 1. Randomize parameters
        val p1 = random.nextInt(4) - 3 
        val p2 = random.nextInt(7) - 3 
        val h1 = random.nextInt(2) + 2 
        val h2 = random.nextInt(2) + 3 
        val h3 = random.nextInt(2) + 2 
        // INCREASED RANDOMIZATION for layouts
        val b1 = random.nextInt(15) - 5 // wider horizontal range
        val bY = random.nextInt(6) // Much more vertical variation (up to Story 4)

        // 2. Build the map first so we can check for valid spawn points
        buildLevelMap(random, p1, p2, h1, h2, h3, b1, bY)

        // 3. Define enemy spawner with collision check
        fun addRandomizedEnemy(baseX: Float, y: Float, baseSpeed: Float) {
            var spawnX = (baseX + (random.nextFloat() * 12f - 6f)).coerceIn(4f, (TOTAL_MAP_COLS - 10).toFloat())
            
            // Validate spawn position
            var attempts = 0
            val floorY = if (currentLevel in 11..15) 12 else 13
            
            while (attempts < 30) {
                val gridX = spawnX.toInt()
                var columnIsClear = true
                for (checkY in 6..12) {
                    // In Sky levels, ground is at 12, so checkY should only go to 11
                    if (currentLevel in 11..15 && checkY == 12) continue
                    if (isSolid(checkY, gridX) || isSolid(checkY, gridX + 1)) {
                        columnIsClear = false
                        break
                    }
                }
                val groundOk = gridX in 0 until TOTAL_MAP_COLS - 1 && isSolid(floorY, gridX) && isSolid(floorY, gridX + 1)
                var tooCloseToOther = false
                for (other in landEnemies) {
                    if (Math.abs(other.x - spawnX) < 4.0f) {
                        tooCloseToOther = true
                        break
                    }
                }
                
                if (columnIsClear && groundOk && !tooCloseToOther) break
                spawnX += if (random.nextBoolean()) 4.0f else -4.0f
                spawnX = spawnX.coerceIn(4f, (TOTAL_MAP_COLS - 10).toFloat())
                attempts++
            }

            val speed = if (random.nextBoolean()) -Math.abs(baseSpeed) else Math.abs(baseSpeed)
            val enemyType = when {
                currentLevel >= 15 -> random.nextInt(4)
                currentLevel >= 10 -> random.nextInt(3)
                currentLevel >= 5 -> random.nextInt(2)
                else -> 0
            }
            landEnemies.add(LandEnemy(spawnX, y, speed, 0f, type = enemyType))
        }

        // 4. Randomize the active spike launcher pipe column
        spikeLauncherPipeCol = when {
            currentLevel <= 5 -> listOf(19 + p1, 35 + p2, 55).shuffled().first()
            currentLevel <= 10 -> 30 + p1
            else -> listOf(35 + p1, 63 + p2).shuffled().first()
        }

        // 5. Spawn enemies (density increases with level)
        val enemyCount = 5 + (currentLevel / 1.5).toInt().coerceAtMost(15)
        repeat(enemyCount) {
            addRandomizedEnemy(12f + it * (75f / enemyCount), 12f, 0.04f + (currentLevel * 0.002f))
        }
    }

    private fun buildLevelMap(rand: java.util.Random, p1: Int, p2: Int, h1: Int, h2: Int, h3: Int, b1: Int, bY: Int) {
        // 1. Clear map
        for (r in 0 until ROWS) {
            map[r].fill(TILE_EMPTY)
        }

        when {
            currentLevel <= 5 -> buildGrasslandLevel(rand, p1, p2, h1, h2, h3, b1, bY)
            currentLevel <= 10 -> buildCavernLevel(rand, p1, p2, h1, h2, b1, bY)
            currentLevel <= 15 -> buildSkyLevel(rand, p1, p2, h1, h2, h3, bY)
            else -> buildCastleLevel(rand, p1, p2, h1, h2, b1, bY)
        }
    }

    private fun buildGrasslandLevel(rand: java.util.Random, p1: Int, p2: Int, h1: Int, h2: Int, h3: Int, b1: Int, bY: Int) {
        val rewardChance = (20 + (currentLevel * 3)).coerceAtMost(80)
        
        // Randomize gap locations slightly
        val g1 = 20 + rand.nextInt(8)
        val g2 = 45 + rand.nextInt(10)
        val g3 = 70 + rand.nextInt(6)
        val gapWidth = 6 + rand.nextInt(3)

        // 1. Foundation: Wide craters that REQUIRE platforming
        for (c in 0 until TOTAL_MAP_COLS) {
            val isGap = (c in g1..g1+gapWidth) || (c in g2..g2+gapWidth) || (c in g3..g3+gapWidth)
            if (isGap && c > 15 && c != 88) continue
            map[13][c] = 1 
            map[14][c] = 12 
        }
        map[13][88] = 1; map[14][88] = 12

        // 2. Segmented Challenges
        val introX = 6 + b1.coerceIn(-2, 2)
        map[9][introX] = 2
        map[9][introX + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2 
        map[9][introX + 2] = 2
        
        // Bricks over the first gap (Mandatory Path)
        val rowY = 9 - (bY % 2)
        val startBrick = g1 - 1
        for (i in 0..gapWidth+2) {
            val col = startBrick + i
            if (col < TOTAL_MAP_COLS) {
                map[rowY][col] = if (i % 2 == 0) (if (rand.nextInt(100) < rewardChance) 3 else 2) else 2
            }
        }
        
        // Mid-Level: Vertical staircase using pipes and high platforms
        val pipeX = 35 + p2
        buildPipe(pipeX, h2)
        val platformX = pipeX + 2
        map[7][platformX] = if (rand.nextInt(100) < rewardChance) 3 else 2 
        map[7][platformX + 1] = 2
        map[7][platformX + 2] = if (rand.nextInt(100) < rewardChance) 3 else 2
        
        map[4][platformX + 3] = if (rand.nextInt(100) < rewardChance) 3 else 2
        map[4][platformX + 4] = 2
        map[4][platformX + 5] = if (rand.nextInt(100) < rewardChance) 3 else 2

        // Story 3: High reward over the second gap
        val s3Start = g2 - 2
        for (c in s3Start..s3Start+10 step 3) {
            if (c + 1 < TOTAL_MAP_COLS) {
                map[9][c] = 2
                map[9][c + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2
                map[5][c + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2
            }
        }

        buildPipe(g3 - 5, h3)
        buildHorizontalPipe(11, g3 + 2, 5, false, isLauncher = true)

        for (r in 3..12) map[r][88] = 8 
    }

    private fun buildCavernLevel(rand: java.util.Random, p1: Int, p2: Int, h1: Int, h2: Int, b1: Int, bY: Int) {
        val rewardChance = (25 + (currentLevel * 3)).coerceAtMost(85)
        val gapFreq = 15 + rand.nextInt(10)
        
        // 1. Structure: Enclosed space
        for (c in 0 until TOTAL_MAP_COLS) {
            map[2][c] = 12 // Ceiling
            if (c % gapFreq in 0..2 && c > 15 && c != 88) continue // Random Gaps
            map[13][c] = 1 
            map[14][c] = 12 
        }
        map[13][88] = 1

        // 2. Claustrophobic Platforming
        buildPipe(8 + p1.coerceIn(-2, 2), h1)

        val rowY = 9 - (bY % 2)
        for (c in 10..80 step 15) {
            val startX = c + b1.coerceIn(-3, 3)
            if (startX + 2 < TOTAL_MAP_COLS) {
                map[11][startX] = 2 
                map[11][startX + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2
                
                map[rowY][startX] = 2
                map[rowY][startX + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2
                map[rowY][startX + 2] = 4 // Hidden trap
                map[rowY - 3][startX + 1] = if (rand.nextInt(100) < rewardChance) 3 else 2
                invisibleBlocks[rowY][startX + 2] = false
            }
        }

        buildPipe(30 + p1, h1, isLauncher = true)
        buildPipe(55 + p2, h2)
        buildHorizontalPipe(10, 65, 4, true, isLauncher = true)

        for (r in 3..12) map[r][88] = 8
    }

    private fun buildSkyLevel(rand: java.util.Random, p1: Int, p2: Int, h1: Int, h2: Int, h3: Int, bY: Int) {
        val rewardChance = (30 + (currentLevel * 3)).coerceAtMost(90)
        val cloudPattern = 8 + rand.nextInt(5)
        
        // 1. Cloud-Based Foundations
        for (c in 0 until TOTAL_MAP_COLS) {
            if (c % cloudPattern < 6) {
                map[12][c] = 1 // Cloud surface
                map[13][c] = 12
            }
        }
        map[12][88] = 1; map[13][88] = 12

        // IMMEDIATE INTRO
        map[8][6] = 2
        map[8][7] = if (rand.nextInt(100) < rewardChance) 3 else 2
        map[8][8] = 2

        // 2. Vertical Cloud Story
        val cloudY = 8 - (bY % 2)
        for (c in 15..75 step 12) {
            val sx = c + p1.coerceIn(-2, 2)
            if (sx + 4 < TOTAL_MAP_COLS) {
                map[cloudY][sx] = 1
                map[cloudY][sx + 1] = 1
                map[cloudY][sx + 2] = if (rand.nextInt(100) < rewardChance) 3 else 2 
                
                if (c % 24 == 0) {
                    map[cloudY - 3][sx + 2] = if (rand.nextInt(100) < rewardChance) 3 else 2 
                    map[cloudY - 3][sx + 3] = 2
                    map[cloudY - 3][sx + 4] = if (rand.nextInt(100) < rewardChance) 3 else 2
                }
            }
        }

        buildPipe(35 + p1, h1, isLauncher = true)
        buildPipe(65 + p2, h2, isLauncher = true)

        for (r in 3..12) map[r][88] = 8
    }

    private fun buildCastleLevel(rand: java.util.Random, p1: Int, p2: Int, h1: Int, h2: Int, b1: Int, bY: Int) {
        val rewardChance = (35 + (currentLevel * 3)).coerceAtMost(95)
        
        // Randomize lava pit locations
        val lava1 = 10 + rand.nextInt(10)
        val lava2 = 40 + rand.nextInt(10)
        val lava3 = 65 + rand.nextInt(10)
        val lavaWidth = 10 + rand.nextInt(5)

        // 1. Hazardous Foundation
        for (c in 0 until TOTAL_MAP_COLS) {
            val isLava = (c in lava1..lava1+lavaWidth) || (c in lava2..lava2+lavaWidth) || (c in lava3..lava3+lavaWidth)
            if (isLava && c != 88) {
                map[13][c] = 9 
                map[14][c] = 9 
            } else {
                map[13][c] = 1 
                map[14][c] = 12 
            }
        }
        map[13][88] = 1

        // IMMEDIATE INTRO
        map[10][7] = 2
        map[10][8] = if (rand.nextInt(100) < rewardChance) 3 else 2
        map[10][9] = 2

        // 2. Structured "Floating Islands"
        val tierY = 10 - (bY % 2)
        val islands = listOf(lava1 + lavaWidth/2, lava2 + lavaWidth/2, lava3 + lavaWidth/2)
        for (baseX in islands) {
            val x = baseX + b1.coerceIn(-2, 2)
            if (x + 6 < TOTAL_MAP_COLS) {
                map[tierY][x] = 2
                map[tierY][x+1] = if (rand.nextInt(100) < rewardChance) 3 else 2
                map[tierY][x+2] = 2
                
                map[tierY - 3][x + 3] = 2
                map[tierY - 3][x + 4] = if (rand.nextInt(100) < rewardChance) 3 else 2
                map[tierY - 6][x + 5] = 2
                map[tierY - 6][x + 6] = if (rand.nextInt(100) < rewardChance) 3 else 2
            }
        }

        buildPipe(35 + p1, h1, isLauncher = true)
        buildPipe(63 + p2, h2)
        buildHorizontalPipe(9, 65, 5, false, isLauncher = true)

        for (r in 3..12) map[r][88] = 8
    }

    private fun buildPipe(col: Int, height: Int, isLauncher: Boolean = false) {
        val startRow = 13 - height
        for (r in startRow until 13) {
            map[r][col] = 6 // Pipe left body
            map[r][col + 1] = 6 // Pipe right body
        }
        map[startRow][col] = 7 // Pipe top left
        map[startRow][col + 1] = 7 // Pipe top right
        
        if (isLauncher) {
            pipeLaunchers.add(PipeLauncher(col + 0.5f, (startRow - 1).toFloat(), 0f, -0.15f))
        }
    }

    private fun buildHorizontalPipe(row: Int, col: Int, length: Int, facingRight: Boolean, isLauncher: Boolean = false) {
        val endCol = col + length
        for (c in col until endCol) {
            map[row][c] = 13 // Body
            map[row + 1][c] = 13 // Body
        }
        if (facingRight) {
            map[row][endCol - 1] = 14 // Rim
            map[row + 1][endCol - 1] = 14 // Rim
            if (isLauncher) {
                pipeLaunchers.add(PipeLauncher(endCol.toFloat(), row + 0.5f, 0.12f, 0f, true))
            }
        } else {
            map[row][col] = 14 // Rim
            map[row + 1][col] = 14 // Rim
            if (isLauncher) {
                pipeLaunchers.add(PipeLauncher(col - 1f, row + 0.5f, -0.12f, 0f, true))
            }
        }
    }

    private fun update() {
        if (gamePaused || gameOver || isLevelCleared) return

        if (isDying) {
            updateDeathAnimation()
            return
        }

        updatePlayerMovement()
        updateCamera()
        updateTrapsAndHazards()
        updateLandEnemies()
        updateParticles()
        updateFeedbackPopups()
    }

    private fun updateDeathAnimation() {
        dieSpinAngle += 15f
        velY += 0.015f
        playerY += velY
        
        if (System.currentTimeMillis() - deathTime > 2000) {
            deaths++
            currentLives--
            if (initialLivesOption != -99 && currentLives <= 0) {
                gameOver = true
                onGameOver?.invoke(deaths)
            } else {
                resetLevelState()
            }
        }
    }

    private fun updatePlayerMovement() {
        // Handle Horizontal Input (Inertia)
        var targetVelX = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) {
            targetVelX = -MAX_SPEED
            isFacingRight = false
        } else if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) {
            targetVelX = MAX_SPEED
            isFacingRight = true
        }

        // Smooth acceleration/deceleration
        velX += (targetVelX - velX) * SPEED_ACCEL * 10f
        velX *= FRICTION

        // Apply Gravity
        velY += GRAVITY

        // Handle variable height jumping
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_CENTER) || pressedKeys.contains(KeyEvent.KEYCODE_ENTER)) {
            val now = System.currentTimeMillis()
            if (isOnGround) {
                velY = JUMP_IMPULSE
                isOnGround = false
                jumpHoldTimer = now
                SoundManager.playClick()
            } else if (now - jumpHoldTimer < MAX_JUMP_HOLD_TIME) {
                velY += JUMP_HOLD_FORCE
            }
        }

        // Apply velocities & resolve grid collisions
        playerX += velX
        checkGridCollisionX()
        playerY += velY
        checkGridCollisionY()

        // Pit death check
        if (playerY > ROWS) die()

        // Trigger Checks (Spike, Lava, Flagpole)
        checkTileTriggers()
    }

    private fun checkTileTriggers() {
        val sCMin = Math.max(0, playerX.toInt())
        val sCMax = Math.min(TOTAL_MAP_COLS - 1, (playerX + playerW).toInt())
        val sRMin = Math.max(0, playerY.toInt())
        val sRMax = Math.min(ROWS - 1, (playerY + playerH).toInt())
        
        for (r in sRMin..sRMax) {
            for (c in sCMin..sCMax) {
                val tile = map[r][c]
                if (tile == TILE_SPIKE || tile == TILE_LAVA) {
                    if (rectIntersects(playerX, playerY, playerW, playerH, c.toFloat(), r.toFloat(), 1f, 1f)) {
                        die()
                    }
                } else if (tile == TILE_FLAGPOLE) {
                    if (rectIntersects(playerX, playerY, playerW, playerH, c.toFloat(), r.toFloat(), 1f, 1f)) {
                        triggerVictory()
                    }
                }
            }
        }
    }

    private fun rectIntersects(x1: Float, y1: Float, w1: Float, h1: Float, x2: Float, y2: Float, w2: Float, h2: Float): Boolean {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
    }

    private fun updateCamera() {
        val targetCam = playerX - COLS / 2f
        cameraX += (targetCam - cameraX) * 0.1f
        cameraX = cameraX.coerceIn(0f, (TOTAL_MAP_COLS - COLS).toFloat())
    }

    private fun updateTrapsAndHazards() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val difficulty = prefs.getInt(SyobonOptionsDialog.KEY_DIFFICULTY, 1)

        checkTrapTriggers(difficulty)

        // Periodic Spawner Check (Hazards from launchers)
        if (System.currentTimeMillis() % 3000 < 20) { 
             for (i in 0 until pipeLaunchers.size) {
                 val launcher = pipeLaunchers[i]
                 if (Math.abs(playerX - launcher.x) < 12f) {
                     val hazardType = if (random.nextBoolean()) 2 else 0
                     val speedVar = 1.0f + random.nextFloat() * 0.5f
                     trapEntities.add(TrapEntity(launcher.x, launcher.y, launcher.vx * speedVar, launcher.vy * speedVar, hazardType))
                     SoundManager.playError()
                 }
             }
             
             if (currentLevel in 11..15 && playerX > 10f) {
                 trapEntities.add(TrapEntity(cameraX + COLS + 1f, random.nextFloat() * 10f, -0.2f, 0f, 1))
                 SoundManager.playError()
             }
        }

        updateTrapEntities()
    }

    private fun updateTrapEntities() {
        for (i in trapEntities.size - 1 downTo 0) {
            val ent = trapEntities[i]
            
            if (ent.type in 3..5) {
                ent.vy = (Math.sin(System.currentTimeMillis() / 200.0) * 0.05f).toFloat()
                ent.y += ent.vy
                ent.x += ent.vx
                val gridX = if (ent.vx > 0) (ent.x + 0.8f).toInt() else ent.x.toInt()
                val gridY = ent.y.toInt()
                if (gridY in 0 until ROWS && gridX in 0 until TOTAL_MAP_COLS && isSolid(gridY, gridX)) {
                    ent.vx = -ent.vx
                }
            } else if (ent.type == 1) {
                ent.vy += 0.012f
                ent.y += ent.vy
                ent.x += ent.vx
                val mx = ent.x.toInt()
                val my = ent.y.toInt()
                if (my in 0 until ROWS && mx in 0 until TOTAL_MAP_COLS && isSolid(my + 1, mx)) {
                    ent.y = my.toFloat()
                    ent.vy = 0f
                }
            } else {
                ent.x += ent.vx
                ent.y += ent.vy
            }
            
            if (Math.abs(ent.x - playerX) < 0.7f && Math.abs(ent.y - playerY) < 0.7f) {
                when (ent.type) {
                    3 -> {
                        SoundManager.playScore()
                        deaths = Math.max(0, deaths - 1)
                        feedbackPopups.add(FeedbackPopup(playerX, playerY - 1f, "FRIEND! -1 OUCHIE", Color.CYAN))
                        trapEntities.removeAt(i)
                        continue
                    }
                    4 -> {
                        SoundManager.playSuccess()
                        if (initialLivesOption != -99) currentLives++
                        feedbackPopups.add(FeedbackPopup(playerX, playerY - 1f, "+1 LIFE", Color.YELLOW))
                        trapEntities.removeAt(i)
                        continue
                    }
                    5 -> {
                        SoundManager.playScore()
                        deaths = Math.max(0, deaths - 5)
                        feedbackPopups.add(FeedbackPopup(playerX, playerY - 1f, "LUCKY! -5 OUCHIES", COLOR_FLAG_GOLD))
                        trapEntities.removeAt(i)
                        continue
                    }
                    else -> die()
                }
            }
            
            if (ent.x < cameraX - 2 || ent.x > cameraX + COLS + 2 || ent.y > ROWS + 2) {
                trapEntities.removeAt(i)
            }
        }
    }

    private fun updateLandEnemies() {
        for (i in landEnemies.size - 1 downTo 0) {
            val enemy = landEnemies[i]
            if (enemy.isDead) {
                landEnemies.removeAt(i)
                continue
            }
            
            enemy.vy += GRAVITY
            enemy.y += enemy.vy
            
            val enemyW = 0.8f
            val enemyH = 0.8f
            val footBuffer = 0.1f
            val eLeft = enemy.x + footBuffer
            val eRight = enemy.x + enemyW - footBuffer
            val rBottom = (enemy.y + enemyH).toInt()
            enemy.isOnGround = false
            
            if (rBottom in 0 until ROWS) {
                val cMin = Math.max(0, eLeft.toInt())
                val cMax = Math.min(TOTAL_MAP_COLS - 1, eRight.toInt())
                for (c in cMin..cMax) {
                    if (isSolid(rBottom, c)) {
                        enemy.y = rBottom.toFloat() - enemyH
                        enemy.vy = 0f
                        enemy.isOnGround = true
                        break
                    }
                }
            }
            
            enemy.x += enemy.vx
            val wallBuffer = 0.1f
            val eTop = enemy.y + wallBuffer
            val eBottom = enemy.y + enemyH - wallBuffer
            val rMin = Math.max(0, eTop.toInt())
            val rMax = Math.min(ROWS - 1, eBottom.toInt())
            val checkCol = if (enemy.vx > 0) (enemy.x + enemyW).toInt() else enemy.x.toInt()
            
            var hitWall = false
            if (checkCol in 0 until TOTAL_MAP_COLS) {
                for (r in rMin..rMax) {
                    if (isSolid(r, checkCol)) {
                        hitWall = true
                        break
                    }
                }
            }
            
            if (hitWall) {
                enemy.vx = -enemy.vx
                if (enemy.vx > 0) enemy.x = checkCol + 1.01f
                else enemy.x = checkCol - enemyW - 0.01f
            }

            enemy.isChasing = false
            if (enemy.huntTimer > 0) {
                enemy.huntTimer--
                enemy.isChasing = true
                val huntSpeed = if (enemy.type == 2) 0.12f else 0.08f
                enemy.vx = if (playerX > enemy.x) Math.abs(huntSpeed) else -Math.abs(huntSpeed)
            } else {
                val distToPlayer = Math.abs(enemy.x - playerX)
                if (distToPlayer < 6f && Math.abs(enemy.y - playerY) < 2f) {
                    enemy.isChasing = true
                    val chaseSpeed = 0.08f
                    enemy.vx = if (playerX > enemy.x) Math.abs(chaseSpeed) else -Math.abs(chaseSpeed)
                }
            }
            
            if (enemy.type != 3) {
                val gridXFront = if (enemy.vx > 0) (enemy.x + 0.8f).toInt() else (enemy.x - 0.1f).toInt()
                val gridYBelow = (enemy.y + 1.2f).toInt()
                if (gridYBelow in 0 until ROWS && gridXFront in 0 until TOTAL_MAP_COLS) {
                    if (!isSolid(gridYBelow, gridXFront) && map[gridYBelow][gridXFront] != TILE_LAVA) {
                        val shouldFall = enemy.huntTimer > 0 && playerY > enemy.y + 1f
                        if (!shouldFall) enemy.vx = -enemy.vx
                    }
                }
            }

            if (enemy.type == 2 && enemy.isOnGround && random.nextFloat() < 0.02f) {
                enemy.vy = -0.2f
            }
            
            if (Math.abs(enemy.x - playerX) < 0.7f && Math.abs(enemy.y - playerY) < 0.7f) {
                if (velY > 0f && playerY + playerH - velY <= enemy.y + 0.3f) {
                    enemy.isDead = true
                    velY = -0.22f
                    SoundManager.playClick()
                } else {
                    die()
                }
            }
        }
    }

    private fun updateParticles() {
        for (i in debrisParticles.size - 1 downTo 0) {
            val d = debrisParticles[i]
            d.vx *= 0.98f
            d.vy += 0.015f
            d.x += d.vx
            d.y += d.vy
            d.life--
            if (d.life <= 0 || d.y > ROWS) debrisParticles.removeAt(i)
        }
    }

    private fun updateFeedbackPopups() {
        for (i in feedbackPopups.size - 1 downTo 0) {
            val p = feedbackPopups[i]
            p.y -= 0.05f
            p.life--
            if (p.life <= 0) feedbackPopups.removeAt(i)
        }
    }

    private fun checkTrapTriggers(difficulty: Int) {
        val px = playerX.toInt()
        
        // 1. The classic invisible block trap at col 11 (X ~ 10-12)
        if (px in 10..12 && !trapTriggered[11]) {
            if (difficulty > 0) { // Only in Brisk or Chaotic
                trapTriggered[11.coerceAtMost(TOTAL_MAP_COLS - 1)] = true
                // We don't spawn a nyan cat immediately, but change invisible block hints
            }
        }

        // 2. Spawn a flying spike (Nyan Cat style) at col 32 when passing near pipe
        if (playerX > 28f && !trapTriggered[32]) {
            trapTriggered[32.coerceAtMost(TOTAL_MAP_COLS - 1)] = true
            val rand = java.util.Random()
            // Spawn nyan cat flying from right side
            val speedFactor = if (difficulty == 2) 1.5f else 1.0f
            trapEntities.add(
                TrapEntity(
                    x = cameraX + COLS + 1f,
                    y = 8f,
                    vx = -0.18f * speedFactor,
                    vy = 0f,
                    type = 0, // Nyan Cat
                    variant = rand.nextInt(3)
                )
            )
            SoundManager.playError() // Spawn/Alert warning beep
        }

        // 3. Falling floor/bridge at col 45-47
        if (playerX >= 43.5f && playerX <= 49f && !trapTriggered[45]) {
            trapTriggered[45.coerceAtMost(TOTAL_MAP_COLS - 1)] = true
            // Begin bridge collapse!
            mainHandler.postDelayed(object : Runnable {
                var ticks = 0
                override fun run() {
                    if (gameOver || isLevelCleared) return
                    for (c in 45..47) {
                        fallingBlocks[13][c] = (ticks * ticks) * 0.02f
                    }
                    ticks++
                    if (ticks < 30) {
                        mainHandler.postDelayed(this, 30)
                    }
                }
            }, 100)
        }

        // 3b. Spike flying from randomized pipe
        if (playerX >= (spikeLauncherPipeCol - 2) && playerX <= (spikeLauncherPipeCol + 2) && !trapTriggered[spikeLauncherPipeCol.coerceAtMost(TOTAL_MAP_COLS - 1)]) {
            trapTriggered[spikeLauncherPipeCol.coerceAtMost(TOTAL_MAP_COLS - 1)] = true
            if (difficulty > 0) {
                trapEntities.add(
                    TrapEntity(
                        x = spikeLauncherPipeCol.toFloat() + 0.5f,
                        y = 10f,
                        vx = 0f,
                        vy = -0.12f,
                        type = 2, // Spike flying up
                        variant = 0
                    )
                )
                SoundManager.playError()
            }
        }

        // 4. Pole flag popup trap (when very close to the flagpole)
        if (playerX >= 83f && !trapTriggered[85.coerceAtMost(TOTAL_MAP_COLS - 1)]) {
            trapTriggered[85.coerceAtMost(TOTAL_MAP_COLS - 1)] = true
            // Spawn popup ground spikes right near flag base!
            if (difficulty > 0) {
                trapEntities.add(
                    TrapEntity(
                        x = 88f,
                        y = 12f,
                        vx = 0f,
                        vy = -0.05f,
                        type = 2, // Popup spike
                        variant = 0
                    )
                )
                SoundManager.playError()
            }
        }
    }

    private fun checkGridCollisionX() {
        val left = playerX
        val right = playerX + playerW
        val top = playerY + 0.01f
        val bottom = playerY + playerH - 0.01f

        val cMin = Math.max(0, left.toInt())
        val cMax = Math.min(TOTAL_MAP_COLS - 1, right.toInt())
        val rMin = Math.max(0, top.toInt())
        val rMax = Math.min(ROWS - 1, bottom.toInt())

        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                if (isSolid(r, c)) {
                    if (velX > 0) {
                        playerX = c - playerW
                        velX = 0f
                        return
                    } else if (velX < 0) {
                        playerX = c + 1f
                        velX = 0f
                        return
                    }
                }
            }
        }
    }

    private fun checkGridCollisionY() {
        // Precise buffer for feet: 0.25 of player width from each side
        // playerW is 0.8, so buffer is 0.2. Foot check range is [playerX + 0.2, playerX + 0.6]
        val buffer = 0.25f * playerW
        val left = playerX + buffer
        val right = playerX + playerW - buffer
        val top = playerY
        val bottom = playerY + playerH

        val cMin = Math.max(0, left.toInt())
        val cMax = Math.min(TOTAL_MAP_COLS - 1, right.toInt())
        
        isOnGround = false

        // 1. Check for ground collision (Falling)
        val rBottom = bottom.toInt()
        if (rBottom in 0 until ROWS) {
            var onAnySolid = false
            for (c in cMin..cMax) {
                if (isSolid(rBottom, c)) {
                    // Only snap if we were above the ground or moving into it
                    if (velY >= 0 && (bottom - velY) <= rBottom + 0.15f) {
                        playerY = rBottom - playerH
                        velY = 0f
                        isOnGround = true
                        onAnySolid = true
                        
                        // Trigger Mystery Box by jumping on top!
                        if (map[rBottom][c] == TILE_MYSTERY) {
                            onBlockHit(rBottom, c)
                        }
                        break
                    }
                }
            }
            if (onAnySolid) return
        }

        // 2. Check for ceiling collision (Jumping)
        val rTop = top.toInt()
        if (rTop in 0 until ROWS) {
            for (c in cMin..cMax) {
                val cell = map[rTop][c]
                // Invisible block check (from below)
                if (cell == TILE_INVISIBLE && invisibleBlocks[rTop][c] != true) {
                    if (velY < 0 && top <= rTop + 1) {
                        invisibleBlocks[rTop][c] = true
                        playerY = rTop + 1f
                        velY = 0f
                        onBlockHit(rTop, c)
                        return
                    }
                }
                
                if (isSolid(rTop, c)) {
                    if (velY < 0 && top <= rTop + 1) {
                        playerY = rTop + 1f
                        velY = 0f
                        onBlockHit(rTop, c)
                        return
                    }
                }
            }
        }
    }

    private fun onBlockHit(r: Int, c: Int) {
        val cellType = map[r][c]
        
        // Mystery Box Bump Animation
        if (cellType == TILE_MYSTERY || cellType == TILE_INVISIBLE || cellType == TILE_BRICK) {
            mainHandler.post(object : Runnable {
                var ticks = 0
                override fun run() {
                    if (ticks < 10) {
                        activeBoxBumps[r][c] = Math.sin(ticks * 0.314).toFloat() * -0.2f
                        ticks++
                        mainHandler.postDelayed(this, 16)
                    } else {
                        activeBoxBumps[r][c] = 0f
                    }
                }
            })
        }

        // 1. Invisible block bonk
        if (cellType == TILE_INVISIBLE) {
            if (invisibleBlocks[r][c] == true && r > 0 && map[r - 1][c] == TILE_SPIKE) return // Already active and trolled
            invisibleBlocks[r][c] = true
            SoundManager.playClick() // Block hit sound
            
            // Troll! Spawns a spike block directly above the invisible block!
            if (r > 0 && map[r - 1][c] == TILE_EMPTY) {
                map[r - 1][c] = TILE_SPIKE // Turn cell into a spike!
                SoundManager.playError()
            }
            return
        }

        // 2. Question block hit
        if (cellType == TILE_MYSTERY) {
            val currentReset = levelResetCount
            map[r][c] = TILE_SPENT // Turn into an indestructible "Spent" block
            SoundManager.playClick()
            
            // 0.2 second delay before opening
            mainHandler.postDelayed({
                if (gameOver || isLevelCleared || levelResetCount != currentReset || isDying) return@postDelayed
                
                val rand = java.util.Random()
                val randVal = rand.nextInt(100)
                when {
                    randVal < 30 -> { // 30% Coin
                        SoundManager.playScore()
                        deaths = Math.max(0, deaths - 1)
                    }
                    randVal < 45 -> { // 15% Lucky Bell (Pops out and floats)
                        trapEntities.add(TrapEntity(c.toFloat(), r - 1f, -0.05f, -0.15f, 5))
                    }
                    randVal < 60 -> { // 15% Golden Heart (Pops out and floats)
                        trapEntities.add(TrapEntity(c.toFloat(), r - 1f, 0.05f, -0.15f, 4))
                    }
                    randVal < 75 -> { // 15% Companion Slime
                        trapEntities.add(TrapEntity(c.toFloat(), r - 1f, -0.06f, -0.12f, 3))
                    }
                    randVal < 90 -> { // 15% Chasing Poison Mushroom (Trap)
                        val huntFrames = 180 + rand.nextInt(180) // 3-6 seconds
                        landEnemies.add(LandEnemy(c.toFloat(), r - 1f, -0.04f, 0f, type = 0, huntTimer = huntFrames))
                        SoundManager.playError()
                    }
                    else -> { // 10% Chasing Turbo Slime (Trap)
                        val huntFrames = 120 + rand.nextInt(60) // 2-3 seconds of high speed
                        landEnemies.add(LandEnemy(c.toFloat(), r - 1f, -0.1f, 0f, type = 2, huntTimer = huntFrames))
                        SoundManager.playError()
                    }
                }
                feedbackPopups.add(FeedbackPopup(c.toFloat(), r.toFloat() - 0.5f, "SURPRISE!", Color.WHITE))
            }, 200)
            return
        }

        // 3. Standard brick hit -> Break it!
        if (cellType == TILE_BRICK) {
            map[r][c] = TILE_EMPTY // Break and remove block
            SoundManager.playClick() // Shatter sound
            
            // Spawn debris particles
            val centerX = c + 0.5f
            val centerY = r + 0.5f
            val rand = java.util.Random()
            repeat(4) {
                debrisParticles.add(Debris(
                    centerX, centerY,
                    (rand.nextFloat() * 0.16f - 0.08f),
                    (rand.nextFloat() * -0.15f - 0.05f)
                ))
            }
            return
        }
    }

    private fun isSolid(r: Int, c: Int): Boolean {
        if (r !in 0 until ROWS || c !in 0 until TOTAL_MAP_COLS) return false
        val cell = map[r][c]
        
        return when (cell) {
            TILE_EMPTY -> false
            TILE_INVISIBLE -> invisibleBlocks[r][c]
            TILE_BRIDGE -> fallingBlocks[r][c] < 0.45f
            TILE_SPIKE, TILE_FLAGPOLE, TILE_LAVA -> false
            else -> true
        }
    }

    private fun die() {
        if (isDying) return
        isDying = true
        deathTime = System.currentTimeMillis()
        velY = -0.2f // pop up in the air
        pressedKeys.clear()
        SoundManager.playError() // Death sound
    }

    private fun triggerVictory() {
        isLevelCleared = true
        celebrationManager.start(width / 2f, height / 3f)
        SoundManager.playSuccess() // victory chime
        
        // Update best ouchies if current run was better (only on full completion)
        if (currentLevel == 20) {
            val oldBest = ScoreManager.getHighScore(context, gameKey, 0)
            if (oldBest == 0 || deaths < oldBest) {
                ScoreManager.updateHighScore(context, gameKey, deaths, 0)
                best = deaths
            }
        }
    }

    private fun handleNextLevelOrReset() {
        if (isLevelCleared) {
            isLevelCleared = false
            if (currentLevel < 20) {
                currentLevel++
                resetLevelState()
            } else {
                currentLevel = 1
                resetGame()
            }
        } else {
            resetGame()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver || isLevelCleared) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                handleNextLevelOrReset()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                pressedKeys.add(keyCode)
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_O -> {
                showOptions()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        return super.onKeyUp(keyCode, event)
    }

    private fun showOptions() {
        pause()
        SyobonOptionsDialog.show(context) {
            resume()
        }
    }

    override fun performClick(): Boolean {
        if (gamePaused && !gameOver && !isLevelCleared) {
            showOptions()
            return true
        }
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver || isLevelCleared) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
                handleNextLevelOrReset()
            }
            return true
        }

        if (gamePaused) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
            }
            return true
        }

        val px = event.x
        val py = event.y
        val screenW = width.toFloat()
        val screenH = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                pressedKeys.clear()
                if (py < screenH * 0.4f) {
                    // Tap top portion to JUMP
                    pressedKeys.add(KeyEvent.KEYCODE_DPAD_UP)
                } else {
                    if (px < screenW * 0.45f) {
                        pressedKeys.add(KeyEvent.KEYCODE_DPAD_LEFT)
                    } else if (px > screenW * 0.55f) {
                        pressedKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedKeys.clear()
            }
        }
        invalidate()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellW = w.toFloat() / COLS
        cellH = h.toFloat() / ROWS
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (hintShowFrames > 0) hintShowFrames--

        drawBackground(canvas)

        canvas.save()
        canvas.translate(-cameraX * cellW, 0f)

        drawLevelTiles(canvas)
        drawTrapEntities(canvas)
        drawEnemies(canvas)
        drawParticles(canvas)
        drawPopups(canvas)
        drawPlayer(canvas)

        canvas.restore()

        drawHUD(canvas)
        drawOverlays(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val skyColor = when (currentLevel) {
            1 -> COLOR_SKY_DAY
            2 -> COLOR_SKY_CAVERN
            else -> COLOR_SKY_CASTLE
        }
        canvas.drawColor(skyColor)
    }

    private fun drawLevelTiles(canvas: Canvas) {
        val startC = Math.max(0, (cameraX - 1).toInt())
        val endC = Math.min(TOTAL_MAP_COLS - 1, (cameraX + COLS + 1).toInt())

        for (r in 0 until ROWS) {
            for (c in startC..endC) {
                val tile = map[r][c]
                if (tile == TILE_EMPTY) continue

                val left = c * cellW
                val top = r * cellH + (fallingBlocks[r][c] + activeBoxBumps[r][c]) * cellH
                drawTile(canvas, tile, left, top, left + cellW, top + cellH, r, c)
            }
        }
    }

    private fun drawTrapEntities(canvas: Canvas) {
        for (ent in trapEntities) {
            val left = ent.x * cellW
            val top = ent.y * cellH
            drawTrapEntity(canvas, ent, left, top, left + cellW, top + cellH)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        for (enemy in landEnemies) {
            val el = enemy.x * cellW
            val et = enemy.y * cellH
            drawLandEnemy(canvas, el, et, el + cellW * 0.8f, et + cellH * 0.8f, enemy.type, enemy.isChasing)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        paint.color = COLOR_BRICK_COPPER
        for (d in debrisParticles) {
            val dl = d.x * cellW
            val dt = d.y * cellH
            canvas.drawRect(dl, dt, dl + cellW * 0.3f, dt + cellH * 0.3f, paint)
        }
    }

    private fun drawPopups(canvas: Canvas) {
        paint.textSize = 30f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        for (p in feedbackPopups) {
            paint.color = p.color
            paint.alpha = (p.life * 3).coerceAtMost(255)
            canvas.drawText(p.text, p.x * cellW, p.y * cellH, paint)
        }
        paint.alpha = 255
    }

    private fun drawPlayer(canvas: Canvas) {
        val pLeft = playerX * cellW
        val pTop = playerY * cellH
        val pRight = pLeft + playerW * cellW
        val pBottom = pTop + playerH * cellH
        
        if (isDying) {
            canvas.save()
            canvas.rotate(dieSpinAngle, (pLeft + pRight)/2f, (pTop + pBottom)/2f)
            drawCat(canvas, pLeft, pTop, pRight, pBottom)
            canvas.restore()
        } else {
            drawCat(canvas, pLeft, pTop, pRight, pBottom)
        }
    }

    private fun drawHUD(canvas: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = if (currentLevel == 1) Color.BLACK else Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        
        val hudY = height * 0.05f
        canvas.drawText("LEVEL: $currentLevel", 30f, hudY, paint)
        canvas.drawText("OUCHIES: $deaths", 30f, hudY + 45f, paint)
        canvas.drawText("LIVES: $currentLives", 30f, hudY + 90f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 30f, hudY, paint)

        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 30f, hudY + 135f, paint)
            paint.alpha = 255
        }
    }

    private fun drawOverlays(canvas: Canvas) {
        if (isLevelCleared) {
            celebrationManager.draw(canvas)
            if (currentLevel < 3) {
                drawOverlayScreen(canvas, "LEVEL $currentLevel CLEAR!", "Press OK/Center for Level ${currentLevel + 1}")
            } else {
                drawOverlayScreen(canvas, "YOU ARE A CAT HERO!", "All Levels Cleared! Press OK/Center to Replay")
            }
        } else if (gameOver) {
            drawOverlayScreen(canvas, "GAME OVER", "Press OK/Center to Retry")
        }
    }

    private fun drawTile(canvas: Canvas, tile: Int, l: Float, t: Float, r: Float, b: Float, row: Int, col: Int) {
        val w = r - l
        val h = b - t
        when (tile) {
            TILE_GROUND -> { // Premium Mossy Ground Block
                // Clay brown base
                paint.color = COLOR_CLAY
                canvas.drawRect(l, t, r, b, paint)
                // Grass cap
                paint.color = COLOR_GRASS
                canvas.drawRect(l, t, r, t + h * 0.20f, paint)
                // Decoration: A tiny clean white daisy flower in the center of the clay area
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                paint.color = Color.WHITE
                canvas.drawCircle(cx - 3f, cy + 2f, 2.5f, paint)
                canvas.drawCircle(cx + 3f, cy + 2f, 2.5f, paint)
                canvas.drawCircle(cx, cy - 2f, 2.5f, paint)
                paint.color = COLOR_DAISY_YELLOW // Yellow center
                canvas.drawCircle(cx, cy + 1f, 2.2f, paint)
            }
            TILE_BRICK -> { // Modern Metallic Copper Brick
                paint.color = COLOR_BRICK_COPPER // Deep Copper
                canvas.drawRect(l, t, r, b, paint)
                // Diagonal light reflection highlight
                paint.color = COLOR_BRICK_HIGHLIGHT
                tempPath.reset()
                tempPath.moveTo(l + w * 0.1f, b)
                tempPath.lineTo(l + w * 0.5f, t)
                tempPath.lineTo(l + w * 0.7f, t)
                tempPath.lineTo(l + w * 0.3f, b)
                tempPath.close()
                canvas.drawPath(tempPath, paint)
                // Brick lines
                paint.color = COLOR_BRICK_LINE
                paint.strokeWidth = 2f
                canvas.drawLine(l, t + h/2, r, t + h/2, paint)
                canvas.drawLine(l + w/2, t, l + w/2, t + h/2, paint)
                canvas.drawLine(l + w/4, t + h/2, l + w/4, b, paint)
                canvas.drawLine(l + 3 * w/4, t + h/2, l + 3 * w/4, b, paint)
            }
            TILE_MYSTERY -> { // Pulsing Neon Golden Question Block
                val pulse = (Math.sin(System.currentTimeMillis() / 120.0) * 15 + 15).toInt()
                paint.color = Color.rgb(255, 193 + pulse, 7) // Pulsing gold
                canvas.drawRect(l, t, r, b, paint)
                // Neon orange border
                paint.style = Paint.Style.STROKE
                paint.color = COLOR_NEON_ORANGE
                paint.strokeWidth = 4f
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
                paint.style = Paint.Style.FILL
                // Center '?' text
                paint.color = Color.BLACK
                paint.textSize = h * 0.7f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("?", l + w/2f, b - h*0.2f, paint)
            }
            TILE_INVISIBLE -> { // Translucent Glassmorphic Invisible Block (activated)
                if (invisibleBlocks[row][col]) {
                    paint.color = COLOR_GLASS_CYAN // Glass cyan tint
                    canvas.drawRect(l, t, r, b, paint)
                    // Translucent white inner glow
                    paint.color = COLOR_GLASS_WHITE
                    canvas.drawRect(l + 4, t + 4, r - 4, b - 4, paint)
                }
            }
            TILE_SPIKE -> { // Plasma Spikes with Translucent Energy Fields
                // Iron base
                paint.color = COLOR_IRON_BASE
                canvas.drawRect(l, b - 4, r, b, paint)
                // Translucent neon pink/red plasma energy aura (cool shield shape)
                paint.color = COLOR_PLASMA_AURA
                val cx = (l + r) / 2
                canvas.drawCircle(cx, t + h/2f, w * 0.45f, paint)
                // Sleek metallic spike cone
                paint.color = COLOR_METAL_SPIKE
                tempPath.reset()
                tempPath.moveTo(l + w * 0.1f, b)
                tempPath.lineTo(cx, t + h * 0.1f)
                tempPath.lineTo(r - w * 0.1f, b)
                tempPath.close()
                canvas.drawPath(tempPath, paint)
                // Glowing plasma tip
                paint.color = COLOR_PLASMA_GLOW
                canvas.drawCircle(cx, t + h * 0.1f, 4.5f, paint)
            }
            TILE_PIPE_BODY -> { // Premium Cyber Pipe Body
                paint.color = COLOR_PIPE_JADE // Clean jade green
                canvas.drawRect(l, t, r, b, paint)
                // Single elegant vertical reflection stripe on the left side
                paint.color = COLOR_GRASS
                canvas.drawRect(l + w * 0.12f, t, l + w * 0.22f, b, paint)
                // Emblem decoration: A clean jade green core to give volume without confusing it for an enemy
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                paint.color = COLOR_PIPE_DARK
                canvas.drawCircle(cx, cy, w * 0.10f, paint)
            }
            TILE_PIPE_TOP -> { // Premium Cyber Pipe Top
                paint.color = COLOR_PIPE_DARK // Dark green
                canvas.drawRect(l - 4, t, r + 4, b, paint)
                // Light green rim highlight
                paint.color = COLOR_GRASS
                canvas.drawRect(l - 4, t, r + 4, t + 4, paint)
            }
            TILE_FLAGPOLE -> { // Flagpole
                paint.color = COLOR_FLAG_SEGMENT
                canvas.drawRect(l + w * 0.42f, t, l + w * 0.58f, b, paint)
                if (row == 3) {
                    // Gold flagpole cap
                    paint.color = COLOR_FLAG_GOLD
                    canvas.drawCircle(l + w/2, t, w * 0.25f, paint)
                    // Custom cyan flag
                    tempPath.reset()
                    tempPath.moveTo(l + w/2, t + 10)
                    tempPath.lineTo(l - w * 1.6f, t + h * 0.35f)
                    tempPath.lineTo(l + w/2, t + h * 0.7f)
                    tempPath.close()
                    paint.color = COLOR_FLAG_CYAN
                    canvas.drawPath(tempPath, paint)
                }
            }
            TILE_LAVA -> { // Animated Lava Molten Flow
                val pulse = (Math.sin(System.currentTimeMillis() / 150.0) * 18).toInt()
                paint.color = Color.rgb(244, 67 + pulse, 54) // Vibrant lava red
                canvas.drawRect(l, t, r, b, paint)
                // Heat waves overlay
                paint.color = Color.YELLOW
                canvas.drawCircle(l + w * 0.25f, t + h * 0.3f, 3.5f, paint)
                canvas.drawCircle(l + w * 0.75f, t + h * 0.6f, 4.5f, paint)
            }
            TILE_BRIDGE -> { // Premium Collapsible Bridge Block
                paint.color = COLOR_BRIDGE_VIOLET // Violet
                canvas.drawRect(l, t, r, b, paint)
                paint.color = COLOR_BRIDGE_LIGHT
                canvas.drawRect(l + 3, t + 3, r - 3, b - 3, paint)
            }
            TILE_SPENT -> { // Spent/Used Block (from Question Block)
                paint.color = COLOR_SPENT_BROWN // Dull Brown
                canvas.drawRect(l, t, r, b, paint)
                // Draw a simple border to show it's spent
                paint.style = Paint.Style.STROKE
                paint.color = COLOR_SPENT_BORDER
                paint.strokeWidth = 3f
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
                paint.style = Paint.Style.FILL
            }
            TILE_DEEP_GROUND -> { // Deep Ground / Ceiling (No grass cap)
                paint.color = COLOR_CLAY
                canvas.drawRect(l, t, r, b, paint)
                // Decoration: Subtle dark spots
                paint.color = COLOR_DEEP_SPOT
                canvas.drawCircle(l + w * 0.3f, t + h * 0.4f, 3f, paint)
                canvas.drawCircle(l + w * 0.7f, t + h * 0.7f, 2.5f, paint)
            }
            TILE_HORIZ_PIPE_BODY -> { // Horizontal Pipe Body
                paint.color = COLOR_PIPE_JADE // Clean jade green
                canvas.drawRect(l, t, r, b, paint)
                // Single elegant horizontal reflection stripe on the top side
                paint.color = COLOR_GRASS
                canvas.drawRect(l, t + h * 0.12f, r, t + h * 0.22f, paint)
                // Emblem decoration
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                paint.color = COLOR_PIPE_DARK
                canvas.drawCircle(cx, cy, h * 0.10f, paint)
            }
            TILE_HORIZ_PIPE_RIM -> { // Horizontal Pipe Rim
                paint.color = COLOR_PIPE_DARK // Dark green
                canvas.drawRect(l, t - 6, r, b + 6, paint)
                // Light green rim highlight
                paint.color = COLOR_GRASS
                canvas.drawRect(l, t - 6, l + 4, b + 6, paint)
            }
        }
    }

    private fun drawTrapEntity(canvas: Canvas, ent: TrapEntity, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val w = r - l
        val h = b - t
        
        when (ent.type) {
            0 -> { // Nyan Cat variants
                paint.color = Color.WHITE
                tempRectF.set(l, t + 4, r, b - 4)
                canvas.drawRoundRect(tempRectF, 8f, 8f, paint)
                
                // Eyes
                paint.color = Color.BLACK
                canvas.drawCircle(l + (r - l) * 0.7f, t + (b - t) * 0.4f, 4f, paint)
                canvas.drawCircle(l + (r - l) * 0.4f, t + (b - t) * 0.4f, 4f, paint)

                when (ent.variant) {
                    0 -> { // Classic Rainbow Tail
                        paint.color = COLOR_NYAN_PURPLE
                        canvas.drawRect(r, t + 6, r + 15, t + 10, paint)
                        paint.color = COLOR_NYAN_CYAN
                        canvas.drawRect(r, t + 10, r + 15, t + 14, paint)
                    }
                    1 -> { // Blue Bow Tie
                        paint.color = COLOR_NYAN_BLUE
                        tempPath.reset()
                        tempPath.moveTo(cx - 8, cy + 4)
                        tempPath.lineTo(cx + 8, cy + 12)
                        tempPath.lineTo(cx + 8, cy + 4)
                        tempPath.lineTo(cx - 8, cy + 12)
                        tempPath.close()
                        canvas.drawPath(tempPath, paint)
                        paint.color = COLOR_NYAN_GOLD
                        canvas.drawCircle(cx, cy + 8, 3f, paint)
                    }
                    2 -> { // Cool Shades
                        paint.color = Color.BLACK
                        canvas.drawRect(l + (r - l) * 0.3f, t + (b - t) * 0.35f, l + (r - l) * 0.8f, t + (b - t) * 0.45f, paint)
                        paint.strokeWidth = 2f
                        canvas.drawLine(l + (r - l) * 0.55f, t + (b - t) * 0.4f, l + (r - l) * 0.65f, t + (b - t) * 0.4f, paint)
                    }
                }
            }
            1 -> { // Poison Mushroom (Purple body, white spots)
                paint.color = COLOR_MUSHROOM_PURPLE
                tempRectF.set(l, t, r, b - 4)
                canvas.drawOval(tempRectF, paint)
                // Spots
                paint.color = Color.WHITE
                canvas.drawCircle(l + (r - l)*0.3f, t + (b - t)*0.3f, 4f, paint)
                canvas.drawCircle(l + (r - l)*0.7f, t + (b - t)*0.3f, 4f, paint)
                canvas.drawCircle(l + (r - l)*0.5f, t + (b - t)*0.6f, 3f, paint)
            }
            2 -> { // Popup ground spikes (Futuristic Plasma Spikes)
                // Translucent neon pink/red plasma energy aura
                paint.color = COLOR_PLASMA_AURA
                canvas.drawCircle(cx, t + (b - t)/2f, (r - l) * 0.45f, paint)
                // Sleek metallic spike cone
                paint.color = COLOR_METAL_SPIKE
                tempPath.reset()
                tempPath.moveTo(l + (r - l) * 0.1f, b)
                tempPath.lineTo(cx, t + (b - t) * 0.1f)
                tempPath.lineTo(r - (r - l) * 0.1f, b)
                tempPath.close()
                canvas.drawPath(tempPath, paint)
                // Glowing plasma tip
                paint.color = COLOR_PLASMA_GLOW
                canvas.drawCircle(cx, t + (b - t) * 0.1f, 4.5f, paint)
            }
            3 -> { // Companion Slime (Cyan, happy face)
                val bounce = (Math.sin(System.currentTimeMillis() / 100.0) * 3f).toFloat()
                
                paint.color = COLOR_SLIME_CYAN // Electric cyan
                tempRectF.set(l, t + h * 0.1f + bounce, r, b)
                canvas.drawOval(tempRectF, paint)
                
                // Cute happy smiley eyes
                paint.color = Color.BLACK
                canvas.drawCircle(cx - w * 0.18f, cy + bounce / 2f, 2.5f, paint)
                canvas.drawCircle(cx + w * 0.18f, cy + bounce / 2f, 2.5f, paint)
                
                // Happy smile
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                tempPath.reset()
                tempPath.moveTo(cx - 3f, cy + 4f + bounce/2f)
                tempPath.quadTo(cx, cy + 7f + bounce/2f, cx + 3f, cy + 4f + bounce/2f)
                canvas.drawPath(tempPath, paint)
                paint.style = Paint.Style.FILL
            }
            4 -> { // Golden Heart (1-UP)
                val pulse = (Math.sin(System.currentTimeMillis() / 150.0) * 4f).toFloat()
                paint.color = COLOR_FLAG_GOLD // Gold
                tempPath.reset()
                val top = t + pulse
                val bot = b - pulse
                tempPath.moveTo(cx, top + (bot - top) * 0.3f)
                tempPath.cubicTo(l, top, l, top + (bot - top) * 0.6f, cx, bot)
                tempPath.cubicTo(r, top + (bot - top) * 0.6f, r, top, cx, top + (bot - top) * 0.3f)
                canvas.drawPath(tempPath, paint)
            }
            5 -> { // Lucky Bell (Deaths down)
                paint.color = COLOR_NYAN_GOLD // Amber/Bell Gold
                canvas.drawCircle(cx, t + h * 0.4f, w * 0.35f, paint)
                canvas.drawRect(l + w * 0.15f, t + h * 0.5f, r - w * 0.15f, b - h * 0.1f, paint)
                // Ribbon
                paint.color = Color.RED
                canvas.drawRect(l + w * 0.1f, t + h * 0.45f, r - w * 0.1f, t + h * 0.55f, paint)
            }
        }
    }

    private fun drawLandEnemy(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, type: Int, isChasing: Boolean) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t
        
        // Draw chasing indicator (Exclamation mark)
        if (isChasing) {
            paint.color = Color.RED
            paint.style = Paint.Style.FILL
            val exW = w * 0.15f
            val exH = h * 0.4f
            canvas.drawRect(cx - exW/2, t - exH - 10f, cx + exW/2, t - 10f, paint)
            canvas.drawCircle(cx, t - 5f, exW/2, paint)
        }
        paint.style = Paint.Style.FILL

        when (type) {
            0 -> { // 1. Cyber Slime
                val bounce = (Math.sin(System.currentTimeMillis() / 100.0) * 3f).toFloat()
                paint.color = COLOR_SLIME_GREEN // Neon green
                tempRectF.set(l, t + h * 0.2f + bounce, r, b)
                canvas.drawOval(tempRectF, paint)
                paint.color = Color.YELLOW
                canvas.drawCircle(cx, cy + bounce / 2f + 3f, w * 0.15f, paint)
                paint.color = if (isChasing) Color.RED else Color.BLACK
                canvas.drawCircle(cx - w * 0.18f, cy + bounce / 2f, 3f, paint)
                canvas.drawCircle(cx + w * 0.18f, cy + bounce / 2f, 3f, paint)
            }
            1 -> { // 2. Iron Shell
                paint.color = COLOR_SHELL_IRON
                tempRectF.set(l, t + h * 0.2f, r, b)
                canvas.drawArc(tempRectF, 180f, 180f, true, paint)
                paint.color = COLOR_SHELL_LIGHT
                tempPath.reset()
                tempPath.moveTo(l + w * 0.2f, t + h * 0.3f)
                tempPath.lineTo(l + w * 0.25f, t + h * 0.1f)
                tempPath.lineTo(l + w * 0.35f, t + h * 0.3f)
                canvas.drawPath(tempPath, paint)
                paint.color = COLOR_SHELL_ORANGE
                tempRectF.set(cx - 10f, b - h * 0.3f, cx + 10f, b - h * 0.1f)
                canvas.drawRoundRect(tempRectF, 2f, 2f, paint)
            }
            2 -> { // 3. Jump Slime
                val bounce = (Math.sin(System.currentTimeMillis() / 80.0) * 5f).toFloat()
                paint.color = COLOR_SLIME_YELLOW // Yellow
                tempRectF.set(l, t + h * 0.1f + bounce, r, b)
                canvas.drawOval(tempRectF, paint)
                paint.color = if (isChasing) Color.RED else Color.BLACK
                canvas.drawCircle(cx - w * 0.2f, cy + bounce, 4f, paint)
                canvas.drawCircle(cx + w * 0.2f, cy + bounce, 4f, paint)
                // Crown for jump slime
                paint.color = COLOR_NEON_ORANGE
                canvas.drawRect(cx - 5, t + bounce - 5, cx + 5, t + bounce, paint)
            }
            3 -> { // 4. Heavy Shell
                paint.color = COLOR_HEAVY_SHELL
                tempRectF.set(l - 10, t, r + 10, b)
                canvas.drawRoundRect(tempRectF, 12f, 12f, paint)
                paint.color = Color.RED
                canvas.drawCircle(cx - 15, cy, 5f, paint)
                canvas.drawCircle(cx + 15, cy, 5f, paint)
            }
        }
    }

    private fun drawCat(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t

        val catColor = when (catType) {
            1 -> COLOR_CAT_GOLD
            2 -> COLOR_CAT_SHADOW
            else -> Color.WHITE
        }
        val innerEarColor = when (catType) {
            1 -> COLOR_EAR_GOLD
            2 -> COLOR_EAR_SHADOW
            else -> COLOR_EAR_PINK
        }

        paint.color = catColor
        
        // 1. Draw Tail
        tempPath.reset()
        if (isFacingRight) {
            tempPath.moveTo(l + w * 0.2f, b - h * 0.4f)
            tempPath.quadTo(l - w * 0.2f, b - h * 0.7f, l - w * 0.1f, b - h * 0.8f)
            tempPath.quadTo(l - w * 0.3f, b - h * 0.7f, l + w * 0.1f, b - h * 0.3f)
        } else {
            tempPath.moveTo(r - w * 0.2f, b - h * 0.4f)
            tempPath.quadTo(r + w * 0.2f, b - h * 0.7f, r + w * 0.1f, b - h * 0.8f)
            tempPath.quadTo(r + w * 0.3f, b - h * 0.7f, r - w * 0.1f, b - h * 0.3f)
        }
        canvas.drawPath(tempPath, paint)

        // 2. Draw Body
        tempRectF.set(l + w * 0.15f, t + h * 0.45f, r - w * 0.15f, b - h * 0.1f)
        canvas.drawRoundRect(tempRectF, 8f, 8f, paint)

        // 2b. Character specific accessory (on body)
        when (catType) {
            0 -> { // Classic Syobon: Blue collar / scarf
                paint.color = COLOR_COLLAR_BLUE
                canvas.drawRect(l + w * 0.2f, t + h * 0.42f, r - w * 0.2f, t + h * 0.48f, paint)
                // Draw a small yellow bell
                paint.color = Color.YELLOW
                canvas.drawCircle(cx, t + h * 0.48f, 4f, paint)
            }
            1 -> { // Golden Neko: Red tie
                paint.color = COLOR_TIE_RED
                tempPath.reset()
                tempPath.moveTo(cx, t + h * 0.45f)
                tempPath.lineTo(cx - 5f, t + h * 0.58f)
                tempPath.lineTo(cx, t + h * 0.65f)
                tempPath.lineTo(cx + 5f, t + h * 0.58f)
                tempPath.close()
                canvas.drawPath(tempPath, paint)
            }
            2 -> { // Shadow Nya: Red belt/sash
                paint.color = COLOR_TIE_RED
                canvas.drawRect(l + w * 0.15f, t + h * 0.55f, r - w * 0.15f, t + h * 0.62f, paint)
            }
        }

        // 3. Draw Legs (animated walking offsets)
        val walkOffset = if (!isOnGround) 0f else Math.sin(System.currentTimeMillis() / 80.0).toFloat() * 4f
        paint.color = catColor
        canvas.drawRoundRect(l + w * 0.25f, b - h * 0.15f, l + w * 0.4f, b + walkOffset, 3f, 3f, paint)
        canvas.drawRoundRect(r - w * 0.4f, b - h * 0.15f, r - w * 0.25f, b - walkOffset, 3f, 3f, paint)

        // 4. Draw Head
        paint.color = catColor
        tempRectF.set(l + w * 0.05f, t + h * 0.05f, r - w * 0.05f, t + h * 0.65f)
        canvas.drawOval(tempRectF, paint)

        // 5. Draw Ears
        tempPath.reset()
        tempPath.moveTo(l + w * 0.15f, t + h * 0.25f)
        tempPath.lineTo(l + w * 0.05f, t)
        tempPath.lineTo(l + w * 0.35f, t + h * 0.18f)
        tempPath.moveTo(r - w * 0.15f, t + h * 0.25f)
        tempPath.lineTo(r - w * 0.05f, t)
        tempPath.lineTo(r - w * 0.35f, t + h * 0.18f)
        canvas.drawPath(tempPath, paint)

        paint.color = innerEarColor
        tempPath.reset()
        tempPath.moveTo(l + w * 0.18f, t + h * 0.22f)
        tempPath.lineTo(l + w * 0.1f, t + h * 0.06f)
        tempPath.lineTo(l + w * 0.3f, t + h * 0.18f)
        tempPath.moveTo(r - w * 0.18f, t + h * 0.22f)
        tempPath.lineTo(r - w * 0.1f, t + h * 0.06f)
        tempPath.lineTo(r - w * 0.3f, t + h * 0.18f)
        canvas.drawPath(tempPath, paint)

        // 5b. Head accessories
        when (catType) {
            1 -> { // Golden Neko: Royal Crown
                paint.color = COLOR_NYAN_GOLD // Gold crown base
                tempPath.reset()
                tempPath.moveTo(cx - 10f, t + h * 0.08f)
                tempPath.lineTo(cx - 15f, t - 8f)
                tempPath.lineTo(cx - 5f, t + h * 0.02f)
                tempPath.lineTo(cx, t - 12f)
                tempPath.lineTo(cx + 5f, t + h * 0.02f)
                tempPath.lineTo(cx + 15f, t - 8f)
                tempPath.lineTo(cx + 10f, t + h * 0.08f)
                tempPath.close()
                canvas.drawPath(tempPath, paint)
                // Red crown gem
                paint.color = Color.RED
                canvas.drawCircle(cx, t - 2f, 2.5f, paint)
            }
            2 -> { // Shadow Nya: Red Ninja Headband tail
                paint.color = COLOR_TIE_RED
                // Draw headband knot tails waving back
                tempPath.reset()
                if (isFacingRight) {
                    tempPath.moveTo(l + w * 0.15f, t + h * 0.25f)
                    tempPath.lineTo(l - w * 0.2f, t + h * 0.28f)
                    tempPath.lineTo(l - w * 0.15f, t + h * 0.4f)
                    tempPath.close()
                } else {
                    tempPath.moveTo(r - w * 0.15f, t + h * 0.25f)
                    tempPath.lineTo(r + w * 0.2f, t + h * 0.28f)
                    tempPath.lineTo(r + w * 0.15f, t + h * 0.4f)
                    tempPath.close()
                }
                canvas.drawPath(tempPath, paint)
            }
        }

        // 6. Draw Eyes (･ω･)
        val eyeColor = when (catType) {
            1 -> COLOR_EYE_GOLD
            2 -> Color.RED
            else -> COLOR_EYE_CLASSIC
        }
        paint.color = eyeColor
        val headCy = (tempRectF.top + tempRectF.bottom) / 2f
        if (isFacingRight) {
            canvas.drawCircle(cx + w * 0.15f, headCy - h * 0.05f, 3.5f, paint)
            canvas.drawCircle(cx - w * 0.15f, headCy - h * 0.05f, 3.5f, paint)
        } else {
            canvas.drawCircle(cx - w * 0.15f, headCy - h * 0.05f, 3.5f, paint)
            canvas.drawCircle(cx + w * 0.15f, headCy - h * 0.05f, 3.5f, paint)
        }

        // 7. Draw Mouth (･ω･)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = if (catType == 2) Color.WHITE else Color.BLACK // white mouth for dark shadow cat
        tempPath.reset()
        tempPath.moveTo(cx - 5f, headCy + 6f)
        tempPath.quadTo(cx - 2.5f, headCy + 10f, cx, headCy + 6f)
        tempPath.quadTo(cx + 2.5f, headCy + 10f, cx + 5f, headCy + 6f)
        canvas.drawPath(tempPath, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawOverlayScreen(canvas: Canvas, title: String, subtitle: String) {
        // Draw dimming background
        paint.color = COLOR_OVERLAY_DIM
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw dialog box
        val dialogW = width * 0.6f
        val dialogH = height * 0.4f
        val dl = (width - dialogW)/2f
        val dt = (height - dialogH)/2f
        
        paint.color = COLOR_DIALOG_BG
        tempRectF.set(dl, dt, dl + dialogW, dt + dialogH)
        canvas.drawRoundRect(tempRectF, 16f, 16f, paint)
        
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(tempRectF, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // Text
        paint.color = COLOR_FLAG_GOLD
        paint.textSize = 54f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width/2f, dt + dialogH * 0.4f, paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(subtitle, width/2f, dt + dialogH * 0.7f, paint)
    }

    override fun onDetachedFromWindow() {
        mainHandler.removeCallbacks(gameLoop)
        super.onDetachedFromWindow()
    }
}
