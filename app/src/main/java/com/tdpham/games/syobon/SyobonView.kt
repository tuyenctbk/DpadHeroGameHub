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
import com.tdpham.games.R

class SyobonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "cat_meowio"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val celebrationManager = CelebrationManager()
    
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
    private val PREFS_NAME = "cat_meowio_settings"
    private var hintShowFrames = 0
    private var best = 0

    // Screen dimension and view scaling
    private var cellW = 0f
    private var cellH = 0f
    private val rows = 15
    private val cols = 20 // visible columns in viewport
    private val totalMapCols = 100 // total level size
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
    private val maxJumpHoldTime = 330 // milliseconds
    private var isDying = false
    private var deathTime = 0L
    private var dieSpinAngle = 0f

    // Physics parameters
    private val gravity = 0.018f
    private val speedAccel = 0.03f
    private val friction = 0.90f
    private val maxSpeed = 0.22f
    private val jumpImpulse = -0.33f
    private val jumpHoldForce = -0.015f

    // Keyboard Tracking
    private val pressedKeys = mutableSetOf<Int>()

    // Level elements & trap states
    private val map = Array(rows) { IntArray(totalMapCols) }
    private val trapTriggered = BooleanArray(totalMapCols)
    private val invisibleBlocks = mutableMapOf<Pair<Int, Int>, Boolean>() // coordinate to visibility state
    private val fallingBlocks = mutableMapOf<Pair<Int, Int>, Float>() // coordinate to yOffset
    
    // Floating entity traps (Nyan cats / flying spikes)
    private class TrapEntity(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val type: Int // 0: Nyan Cat, 1: Flying Spike, 2: Popup Ground Spike
    )
    private val trapEntities = mutableListOf<TrapEntity>()

    // Land patrolling enemies (Goomba/Syobon style)
    private class LandEnemy(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var isDead: Boolean = false,
        val type: Int = 0 // 0: Cyber Slime, 1: Iron Shell
    )
    private val landEnemies = mutableListOf<LandEnemy>()

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

        resetLevelState()
        gameOver = false
        gamePaused = false
        isLevelCleared = false
        hintShowFrames = 100
        
        startGame()
    }

    private fun resetLevelState() {
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
        val rand = java.util.Random()

        // 1. Randomize parameters
        val p1 = rand.nextInt(7) - 3 // -3 to +3
        val p2 = rand.nextInt(9) - 4 // -4 to +4
        val h1 = rand.nextInt(2) + 2 // 2 to 3
        val h2 = rand.nextInt(2) + 3 // 3 to 4
        val h3 = rand.nextInt(2) + 2 // 2 to 3
        val b1 = rand.nextInt(3) - 2 // -2 to 0 (shifted further left)

        // 2. Build the map first so we can check for valid spawn points
        buildLevelMap(p1, p2, h1, h2, h3, b1)

        // 3. Define enemy spawner with collision check
        fun addRandomizedEnemy(baseX: Float, y: Float, baseSpeed: Float) {
            var spawnX = (baseX + (rand.nextFloat() * 8f - 4f)).coerceIn(4f, (totalMapCols - 10).toFloat())
            
            // Validate spawn position
            var attempts = 0
            while (attempts < 25) {
                val gridX = spawnX.toInt()
                
                // 1. Column is clear of obstacles (pipes/blocks)
                var columnIsClear = true
                for (checkY in 6..12) {
                    if (isSolid(checkY, gridX) || isSolid(checkY, gridX + 1)) {
                        columnIsClear = false
                        break
                    }
                }
                
                // 2. Column is over solid ground (not a gap)
                val groundOk = gridX in 0 until totalMapCols - 1 && map[13][gridX] == 1 && map[13][gridX + 1] == 1
                
                // 3. Distance check from other enemies to prevent overlapping
                var tooCloseToOther = false
                for (other in landEnemies) {
                    if (Math.abs(other.x - spawnX) < 3.0f) {
                        tooCloseToOther = true
                        break
                    }
                }
                
                if (columnIsClear && groundOk && !tooCloseToOther) break
                
                // Shift and try again
                spawnX += if (rand.nextBoolean()) 3.0f else -3.0f
                spawnX = spawnX.coerceIn(4f, (totalMapCols - 10).toFloat())
                attempts++
            }

            val speed = if (rand.nextBoolean()) -Math.abs(baseSpeed) else Math.abs(baseSpeed)
            val enemyType = if (rand.nextBoolean()) 0 else 1
            landEnemies.add(LandEnemy(spawnX, y, speed, 0f, type = enemyType))
        }

        // 4. Randomize the active spike launcher pipe column
        spikeLauncherPipeCol = when (currentLevel) {
            1 -> listOf(20 + p1, 35 + p2, 55).shuffled().first()
            2 -> 30 + p1
            else -> listOf(35 + p1, 63 + p2).shuffled().first()
        }

        // 5. Spawn enemies
        when (currentLevel) {
            1 -> {
                repeat(rand.nextInt(3) + 4) { // 4 to 6 enemies
                    addRandomizedEnemy(15f + it * 12f, 12f, 0.04f)
                }
            }
            2 -> {
                repeat(rand.nextInt(4) + 5) { // 5 to 8 enemies
                    addRandomizedEnemy(10f + it * 12f, 12f, 0.045f)
                }
            }
            else -> {
                repeat(rand.nextInt(5) + 6) { // 6 to 10 enemies
                    addRandomizedEnemy(10f + it * 8f, 12f, 0.05f)
                }
            }
        }
        trapTriggered.fill(false)
        invisibleBlocks.clear()
        fallingBlocks.clear()
        
        // Re-build level map to reset trap states that might have been cleared by internal fills
        // (Though buildLevelMap already does this, we keep the call sequence clean)
        buildLevelMap(p1, p2, h1, h2, h3, b1)
    }

    private fun buildLevelMap(p1: Int, p2: Int, h1: Int, h2: Int, h3: Int, b1: Int) {
        // Clear map
        for (r in 0 until rows) {
            map[r].fill(0)
        }

        when (currentLevel) {
            1 -> buildLevel1(p1, p2, h1, h2, h3, b1)
            2 -> buildLevel2(p1, h1)
            else -> buildLevel3(p1, p2, h1, h2)
        }
    }

    private fun buildLevel1(p1: Int, p2: Int, h1: Int, h2: Int, h3: Int, b1: Int) {
        // 1. Ground bricks
        for (c in 0 until totalMapCols) {
            // Classic Mario gap at col 21-23 and col 45-47
            if (c in 21..23 || c in 45..47 || c in 72..75) {
                continue
            }
            map[13][c] = 1 // Ground top
            map[14][c] = 1 // Ground deep
        }

        // 2. Standard structures
        // First group of blocks shifted further left (offset by b1)
        val group1X = 5 + b1 
        map[9][group1X] = 2 // Brick
        map[9][group1X + 1] = 3 // Question block
        map[9][group1X + 2] = 2 // Brick
        map[9][group1X + 3] = 4 // INVISIBLE block (trap!)
        map[9][group1X + 4] = 2 // Brick
        invisibleBlocks[Pair(9, group1X + 3)] = false 

        // First pit invisible block troll at col 20
        map[9][20] = 4
        invisibleBlocks[Pair(9, 20)] = false

        // Pipes with randomized offsets and heights
        // Shifted Pipe 1 further right to ensure no overlap with block group (group ends at max 11, pipe starts at min 18)
        buildPipe(20 + p1, h1) 
        buildPipe(35 + p2, h2)
        buildPipe(55, h3) 

        // Bridge over the second gap (collapsible!)
        for (c in 45..47) {
            map[13][c] = 10 // Collapsible bridge block
            fallingBlocks[Pair(13, c)] = 0f
        }

        // Flagpole at col 88
        map[12][88] = 8 // Flagpole base
        for (r in 3..11) {
            map[r][88] = 8 // flagpole shaft
        }
    }

    private fun buildLevel2(p1: Int, h1: Int) {
        // Underground Cavern
        // 1. Ground bricks
        for (c in 0 until totalMapCols) {
            if (c in 18..20 || c in 42..45 || c in 70..73) {
                continue
            }
            map[13][c] = 1 // Ground top
            map[14][c] = 1 // Ground deep
        }

        // 2. Ceiling blocks
        for (c in 0 until totalMapCols) {
            map[2][c] = 1 // Solid ceiling
        }

        // Floating blocks group
        map[9][10] = 2
        map[9][11] = 3
        map[9][12] = 2
        map[9][13] = 4 // Invisible block
        map[9][14] = 2
        invisibleBlocks[Pair(9, 13)] = false

        // Pipe
        buildPipe(30 + p1, h1)

        // Collapsible floating bridge tiles
        map[9][41] = 2
        map[9][42] = 10
        map[9][43] = 10
        map[9][44] = 10
        map[9][45] = 2
        fallingBlocks[Pair(9, 42)] = 0f
        fallingBlocks[Pair(9, 43)] = 0f
        fallingBlocks[Pair(9, 44)] = 0f

        // Floating bricks
        map[8][60] = 2
        map[8][61] = 3
        map[8][62] = 2
        map[8][63] = 3
        map[8][64] = 2

        // Flagpole at col 88
        map[12][88] = 8
        for (r in 3..11) {
            map[r][88] = 8
        }
    }

    private fun buildLevel3(p1: Int, p2: Int, h1: Int, h2: Int) {
        // Castle / Lava Style
        for (c in 0 until totalMapCols) {
            if (c in 15..28 || c in 42..58 || c in 70..83) {
                map[13][c] = 9 // Lava Top
                map[14][c] = 9 // Lava Deep
            } else {
                map[13][c] = 1 // Ground top
                map[14][c] = 1 // Ground deep
            }
        }

        // Stepping stone bricks over lava
        map[9][18] = 2
        map[9][20] = 2
        map[9][22] = 3
        map[9][24] = 2
        map[9][26] = 2

        // Pipes
        buildPipe(35 + p1, h1)
        buildPipe(63 + p2, h2)

        // Stepping stone platforms
        map[10][45] = 2
        map[9][50] = 2
        map[10][55] = 2

        // Castle wall blocks
        for (r in 5..12) {
            map[r][68] = 1
        }

        // Flagpole at col 88
        map[12][88] = 8
        for (r in 3..11) {
            map[r][88] = 8
        }
    }

    private fun buildPipe(col: Int, height: Int) {
        val startRow = 13 - height
        for (r in startRow until 13) {
            map[r][col] = 6 // Pipe left body
            map[r][col + 1] = 6 // Pipe right body
        }
        map[startRow][col] = 7 // Pipe top left
        map[startRow][col + 1] = 7 // Pipe top right
    }

    private fun update() {
        if (gamePaused || gameOver || isLevelCleared) return

        if (isDying) {
            val now = System.currentTimeMillis()
            dieSpinAngle += 15f
            velY += 0.015f
            playerY += velY
            
            if (now - deathTime > 2000) {
                // Respawn
                deaths++
                currentLives--
                if (initialLivesOption != -99 && currentLives <= 0) {
                    gameOver = true
                    onGameOver?.invoke(deaths)
                } else {
                    resetLevelState()
                }
            }
            return
        }

        // Read options difficulty
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val difficulty = prefs.getInt(SyobonOptionsDialog.KEY_DIFFICULTY, 1) // 0: Calm, 1: Brisk, 2: Chaotic

        // Handle Horizontal Input (Inertia)
        var targetVelX = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) {
            targetVelX = -maxSpeed
            isFacingRight = false
        } else if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) {
            targetVelX = maxSpeed
            isFacingRight = true
        }

        // Smooth acceleration/deceleration
        velX += (targetVelX - velX) * speedAccel * 10f
        velX *= friction

        // Apply Gravity
        velY += gravity

        // Handle variable height jumping
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_CENTER) || pressedKeys.contains(KeyEvent.KEYCODE_ENTER)) {
            val now = System.currentTimeMillis()
            if (isOnGround) {
                velY = jumpImpulse
                isOnGround = false
                jumpHoldTimer = now
                SoundManager.playClick() // Jump sound
            } else if (now - jumpHoldTimer < maxJumpHoldTime) {
                // Apply subtle upwards lift while holding jump
                velY += jumpHoldForce
            }
        }

        // Apply velocities & resolve grid collisions (AABB)
        // 1. Resolve X movement
        playerX += velX
        checkGridCollisionX()

        // 2. Resolve Y movement
        playerY += velY
        checkGridCollisionY()

        // Pit death check
        if (playerY > rows) {
            die()
        }

        // Spike & Lava collision check
        val pLeft = playerX
        val pRight = playerX + playerW
        val pTop = playerY
        val pBottom = playerY + playerH
        val sCMin = Math.max(0, pLeft.toInt())
        val sCMax = Math.min(totalMapCols - 1, pRight.toInt())
        val sRMin = Math.max(0, pTop.toInt())
        val sRMax = Math.min(rows - 1, pBottom.toInt())
        for (r in sRMin..sRMax) {
            for (c in sCMin..sCMax) {
                val tile = map[r][c]
                if (tile == 5 || tile == 9) {
                    if (pRight > c && pLeft < c + 1 && pBottom > r && pTop < r + 1) {
                        die()
                    }
                }
            }
        }

        // Trigger Camera Scroll
        val targetCam = playerX - cols / 2f
        cameraX += (targetCam - cameraX) * 0.1f
        cameraX = cameraX.coerceIn(0f, (totalMapCols - cols).toFloat())

        // Troll Trigger Checks
        checkTrapTriggers(difficulty)

        // Update Trap Entities
        val trapIter = trapEntities.toMutableList().iterator()
        while (trapIter.hasNext()) {
            val ent = trapIter.next()
            
            if (ent.type == 1 || ent.type == 3) {
                // Apply gravity to poison mushroom and companion slime
                ent.vy += 0.012f
                ent.y += ent.vy
                ent.x += ent.vx
                
                // Simple collision check for sliding entities
                val mx = ent.x.toInt()
                val my = ent.y.toInt()
                if (my in 0 until rows && mx in 0 until totalMapCols) {
                    if (isSolid(my + 1, mx)) {
                        ent.y = my.toFloat()
                        ent.vy = 0f
                    }
                }
            } else {
                ent.x += ent.vx
                ent.y += ent.vy
            }
            
            // Check collision with player
            if (Math.abs(ent.x - playerX) < 0.7f && Math.abs(ent.y - playerY) < 0.7f) {
                if (ent.type == 3) {
                    // Companion slime gives extra protection or points
                    SoundManager.playScore()
                    trapEntities.remove(ent)
                    continue
                } else {
                    die()
                }
            }
            
            // Remove off-screen/fallen entities
            if (ent.x < cameraX - 2 || ent.x > cameraX + cols + 2 || ent.y > rows + 2) {
                trapEntities.remove(ent)
            }
        }

        // Update Land Enemies
        val enemyList = landEnemies.toMutableList()
        for (enemy in enemyList) {
            if (enemy.isDead) {
                landEnemies.remove(enemy)
                continue
            }
            
            // Apply gravity
            enemy.vy += gravity
            enemy.y += enemy.vy
            
            // Vertical collision
            val ex = enemy.x.toInt()
            val ey = enemy.y.toInt()
            if (ey in 0 until rows && ex in 0 until totalMapCols) {
                if (isSolid(ey + 1, ex)) {
                    enemy.y = ey.toFloat()
                    enemy.vy = 0f
                }
            }
            
            // Move horizontally
            enemy.x += enemy.vx
            
            // Re-resolve horizontal collision to prevent sticking to pipes
            val eyCheck = enemy.y.toInt()
            
            // Check for wall at front
            var wallAtFront = false
            var hitX = 0
            val checkX = if (enemy.vx > 0) (enemy.x + 0.85f).toInt() else enemy.x.toInt()
            
            if (eyCheck in 0 until rows && checkX in 0 until totalMapCols && isSolid(eyCheck, checkX)) {
                wallAtFront = true
                hitX = checkX
            }

            if (wallAtFront) {
                enemy.vx = -enemy.vx
                // Snap out of the wall to prevent sticking/shaking
                if (enemy.vx > 0) {
                    // Was moving left, hit wall at hitX, now move right
                    enemy.x = (hitX + 1.05f)
                } else {
                    // Was moving right, hit wall at hitX, now move left
                    enemy.x = (hitX - 0.90f)
                }
            }
            
            // Bounding box collision check with player
            if (Math.abs(enemy.x - playerX) < 0.7f && Math.abs(enemy.y - playerY) < 0.7f) {
                // If player is falling onto the enemy's head
                if (velY > 0f && playerY + playerH - velY <= enemy.y + 0.3f) {
                    enemy.isDead = true
                    velY = -0.22f // bounce player high up!
                    SoundManager.playClick()
                } else {
                    die()
                }
            }
        }
    }

    private fun checkTrapTriggers(difficulty: Int) {
        val px = playerX.toInt()
        
        // 1. The classic invisible block trap at col 11 (X ~ 10-12)
        if (px in 10..12 && !trapTriggered[11]) {
            if (difficulty > 0) { // Only in Brisk or Chaotic
                trapTriggered[11.coerceAtMost(totalMapCols-1)] = true
                // We don't spawn a nyan cat immediately, but change invisible block hints
            }
        }

        // 2. Spawn a flying spike (Nyan Cat style) at col 32 when passing near pipe
        if (playerX > 28f && !trapTriggered[32]) {
            trapTriggered[32.coerceAtMost(totalMapCols-1)] = true
            // Spawn nyan cat flying from right side
            val speedFactor = if (difficulty == 2) 1.5f else 1.0f
            trapEntities.add(
                TrapEntity(
                    x = cameraX + cols + 1f,
                    y = 8f,
                    vx = -0.18f * speedFactor,
                    vy = 0f,
                    type = 0 // Nyan Cat
                )
            )
            SoundManager.playError() // Spawn/Alert warning beep
        }

        // 3. Falling floor/bridge at col 45-47
        if (playerX >= 43.5f && playerX <= 49f && !trapTriggered[45]) {
            trapTriggered[45.coerceAtMost(totalMapCols-1)] = true
            // Begin bridge collapse!
            mainHandler.postDelayed(object : Runnable {
                var ticks = 0
                override fun run() {
                    if (gameOver || isLevelCleared) return
                    for (c in 45..47) {
                        fallingBlocks[Pair(13, c)] = (ticks * ticks) * 0.02f
                    }
                    ticks++
                    if (ticks < 30) {
                        mainHandler.postDelayed(this, 30)
                    }
                }
            }, 100)
        }

        // 3b. Spike flying from randomized pipe
        if (playerX >= (spikeLauncherPipeCol - 2) && playerX <= (spikeLauncherPipeCol + 2) && !trapTriggered[spikeLauncherPipeCol.coerceAtMost(totalMapCols-1)]) {
            trapTriggered[spikeLauncherPipeCol.coerceAtMost(totalMapCols-1)] = true
            if (difficulty > 0) {
                trapEntities.add(
                    TrapEntity(
                        x = spikeLauncherPipeCol.toFloat() + 0.5f,
                        y = 10f,
                        vx = 0f,
                        vy = -0.12f,
                        type = 2 // Spike flying up
                    )
                )
                SoundManager.playError()
            }
        }

        // 4. Pole flag popup trap (when very close to the flagpole)
        if (playerX >= 83f && !trapTriggered[85.coerceAtMost(totalMapCols-1)]) {
            trapTriggered[85.coerceAtMost(totalMapCols-1)] = true
            // Spawn popup ground spikes right near flag base!
            if (difficulty > 0) {
                trapEntities.add(
                    TrapEntity(
                        x = 88f,
                        y = 12f,
                        vx = 0f,
                        vy = -0.05f,
                        type = 2 // Popup spike
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
        val cMax = Math.min(totalMapCols - 1, right.toInt())
        val rMin = Math.max(0, top.toInt())
        val rMax = Math.min(rows - 1, bottom.toInt())

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
        val left = playerX + 0.01f
        val right = playerX + playerW - 0.01f
        val top = playerY
        val bottom = playerY + playerH

        val cMin = Math.max(0, left.toInt())
        val cMax = Math.min(totalMapCols - 1, right.toInt())
        val rMin = Math.max(0, top.toInt())
        val rMax = Math.min(rows - 1, bottom.toInt())

        isOnGround = false

        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                val cell = if (r in 0 until rows && c in 0 until totalMapCols) map[r][c] else 0
                if (cell == 4 && invisibleBlocks[Pair(r, c)] != true) {
                    if (velY < 0 && top <= r + 1 && top - velY > r) {
                        invisibleBlocks[Pair(r, c)] = true
                        playerY = r + 1f
                        velY = 0f
                        onBlockHit(r, c)
                        return
                    }
                }

                if (isSolid(r, c)) {
                    if (velY > 0) {
                        playerY = r - playerH
                        velY = 0f
                        isOnGround = true
                        return
                    } else if (velY < 0) {
                        playerY = r + 1f
                        velY = 0f
                        onBlockHit(r, c)
                        return
                    }
                }
            }
        }
    }

    private fun onBlockHit(r: Int, c: Int) {
        val cellType = map[r][c]
        
        // 1. Invisible block bonk
        if (cellType == 4) {
            invisibleBlocks[Pair(r, c)] = true
            SoundManager.playClick() // Block hit sound
            
            // Troll! Spawns a spike block directly above the invisible block!
            if (r > 0 && map[r - 1][c] == 0) {
                map[r - 1][c] = 5 // Turn cell into a spike!
                SoundManager.playError()
            }
            return
        }

        // 2. Question block hit
        if (cellType == 3) {
            map[r][c] = 2 // turn into hit brick
            SoundManager.playClick()

            val randVal = (Math.random() * 100).toInt()
            if (randVal < 40) {
                // Reward: Glowing Coin! Reduces deaths/ouchies count as a reward!
                SoundManager.playScore()
                deaths = Math.max(0, deaths - 1)
            } else if (randVal < 70) {
                // Reward: Companion Slime (harmless, happy companion)
                trapEntities.add(
                    TrapEntity(
                        x = c.toFloat(),
                        y = r - 1f,
                        vx = -0.06f,
                        vy = 0f,
                        type = 3 // Companion Slime
                    )
                )
            } else {
                // Troll: Spawns toxic poison mushroom
                trapEntities.add(
                    TrapEntity(
                        x = c.toFloat(),
                        y = r - 1f,
                        vx = -0.06f,
                        vy = 0f,
                        type = 1 // Poison Mushroom
                    )
                )
                SoundManager.playError()
            }
            return
        }

        // 3. Standard brick hit -> Break it!
        if (cellType == 2) {
            map[r][c] = 0 // Break and remove block
            SoundManager.playClick() // Shatter sound
            return
        }
    }

    private fun isSolid(r: Int, c: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until totalMapCols) return false
        val cell = map[r][c]
        
        // Invisible block: only solid if active (visible)
        if (cell == 4) {
            return invisibleBlocks[Pair(r, c)] == true
        }

        // Collapsible bridge block: only solid if it hasn't fallen
        if (cell == 10) {
            val yOffset = fallingBlocks[Pair(r, c)] ?: 0f
            return yOffset < 0.2f
        }
        
        // Spikes and flagpoles are triggers, not solid blocks!
        if (cell == 5 || cell == 8) {
            // Flagpole collision -> Victory check!
            if (cell == 8 && !isDying && !isLevelCleared) {
                triggerVictory()
            }
            return false
        }
        
        return cell != 0
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
    }

    private fun handleNextLevelOrReset() {
        if (isLevelCleared) {
            if (currentLevel < 3) {
                currentLevel++
                resetLevelState()
                isLevelCleared = false
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
        cellW = w.toFloat() / cols
        cellH = h.toFloat() / rows
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (hintShowFrames > 0) {
            hintShowFrames--
        }

        val skyColor = when (currentLevel) {
            1 -> "#80D8FF" // Sky Blue
            2 -> "#151B26" // Cavern Dark
            else -> "#1A0A0A" // Castle Dark Red
        }
        canvas.drawColor(Color.parseColor(skyColor))

        // Apply camera scroll translation
        canvas.save()
        canvas.translate(-cameraX * cellW, 0f)

        // 1. Draw Grid Tiles
        val startC = Math.max(0, (cameraX - 1).toInt())
        val endC = Math.min(totalMapCols - 1, (cameraX + cols + 1).toInt())

        for (r in 0 until rows) {
            for (c in startC..endC) {
                val tile = map[r][c]
                if (tile == 0) continue

                val yOffset = fallingBlocks[Pair(r, c)] ?: 0f
                val left = c * cellW
                val top = r * cellH + yOffset * cellH
                val right = left + cellW
                val bottom = top + cellH
                
                drawTile(canvas, tile, left, top, right, bottom, r, c)
            }
        }

        // 2. Draw Trap Entities (Nyan cats, spikes)
        for (ent in trapEntities) {
            val left = ent.x * cellW
            val top = ent.y * cellH
            val right = left + cellW
            val bottom = top + cellH
            
            drawTrapEntity(canvas, ent, left, top, right, bottom)
        }

        // 2b. Draw Land Enemies
        for (enemy in landEnemies) {
            val el = enemy.x * cellW
            val et = enemy.y * cellH
            val er = el + cellW * 0.8f
            val eb = et + cellH * 0.8f
            drawLandEnemy(canvas, el, et, er, eb, enemy.type)
        }

        // 3. Draw Player (Cat)
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

        canvas.restore()

        // 4. Draw HUD (Level, Ouchies, Lives)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = if (currentLevel == 1) Color.BLACK else Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        
        val hudY = height * 0.05f
        val livesStr = if (currentLives < 0) "$currentLives" else "$currentLives"
        canvas.drawText("LEVEL: $currentLevel", 30f, hudY, paint)
        canvas.drawText("OUCHIES: $deaths", 30f, hudY + 45f, paint)
        canvas.drawText("LIVES: $livesStr", 30f, hudY + 90f, paint)
        
        // Best Score (Deaths in this game context)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 30f, hudY, paint)

        // Quick Hint
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = if (currentLevel == 1) Color.BLACK else Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 30f, hudY + 135f, paint)
            paint.alpha = 255
        }

        // 5. Success overlay
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
            1 -> { // Premium Mossy Ground Block
                // Clay brown base
                paint.color = Color.parseColor("#8D6E63")
                canvas.drawRect(l, t, r, b, paint)
                // Grass cap
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(l, t, r, t + h * 0.20f, paint)
                // Decoration: A tiny clean white daisy flower in the center of the clay area
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                paint.color = Color.WHITE
                canvas.drawCircle(cx - 3f, cy + 2f, 2.5f, paint)
                canvas.drawCircle(cx + 3f, cy + 2f, 2.5f, paint)
                canvas.drawCircle(cx, cy - 2f, 2.5f, paint)
                paint.color = Color.parseColor("#FFD54F") // Yellow center
                canvas.drawCircle(cx, cy + 1f, 2.2f, paint)
            }
            2 -> { // Modern Metallic Copper Brick
                paint.color = Color.parseColor("#BF360C") // Deep Copper
                canvas.drawRect(l, t, r, b, paint)
                // Diagonal light reflection highlight
                paint.color = Color.parseColor("#FF8A65")
                val path = Path()
                path.moveTo(l + w * 0.1f, b)
                path.lineTo(l + w * 0.5f, t)
                path.lineTo(l + w * 0.7f, t)
                path.lineTo(l + w * 0.3f, b)
                path.close()
                canvas.drawPath(path, paint)
                // Brick lines
                paint.color = Color.parseColor("#3E2723")
                paint.strokeWidth = 2f
                canvas.drawLine(l, t + h/2, r, t + h/2, paint)
                canvas.drawLine(l + w/2, t, l + w/2, t + h/2, paint)
                canvas.drawLine(l + w/4, t + h/2, l + w/4, b, paint)
                canvas.drawLine(l + 3 * w/4, t + h/2, l + 3 * w/4, b, paint)
            }
            3 -> { // Pulsing Neon Golden Question Block
                val pulse = (Math.sin(System.currentTimeMillis() / 120.0) * 15 + 15).toInt()
                paint.color = Color.rgb(255, 193 + pulse, 7) // Pulsing gold
                canvas.drawRect(l, t, r, b, paint)
                // Neon orange border
                paint.style = Paint.Style.STROKE
                paint.color = Color.parseColor("#FF5722")
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
            4 -> { // Translucent Glassmorphic Invisible Block (activated)
                if (invisibleBlocks[Pair(row, col)] == true) {
                    paint.color = Color.parseColor("#B3E5FC") // Glass cyan tint
                    canvas.drawRect(l, t, r, b, paint)
                    // Translucent white inner glow
                    paint.color = Color.parseColor("#80FFFFFF")
                    canvas.drawRect(l + 4, t + 4, r - 4, b - 4, paint)
                }
            }
            5 -> { // Plasma Spikes with Translucent Energy Fields
                // Iron base
                paint.color = Color.parseColor("#37474F")
                canvas.drawRect(l, b - 4, r, b, paint)
                // Translucent neon pink/red plasma energy aura (cool shield shape)
                paint.color = Color.parseColor("#40FF1744")
                val cx = (l + r) / 2
                canvas.drawCircle(cx, t + h/2f, w * 0.45f, paint)
                // Sleek metallic spike cone
                paint.color = Color.parseColor("#90A4AE")
                val path = Path()
                path.moveTo(l + w * 0.1f, b)
                path.lineTo(cx, t + h * 0.1f)
                path.lineTo(r - w * 0.1f, b)
                path.close()
                canvas.drawPath(path, paint)
                // Glowing plasma tip
                paint.color = Color.parseColor("#FF1744")
                canvas.drawCircle(cx, t + h * 0.1f, 4.5f, paint)
            }
            6 -> { // Premium Cyber Pipe Body
                paint.color = Color.parseColor("#2E7D32") // Clean jade green
                canvas.drawRect(l, t, r, b, paint)
                // Single elegant vertical reflection stripe on the left side
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(l + w * 0.12f, t, l + w * 0.22f, b, paint)
                // Emblem decoration: A clean jade green core to give volume without confusing it for an enemy
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                paint.color = Color.parseColor("#1B5E20")
                canvas.drawCircle(cx, cy, w * 0.10f, paint)
            }
            7 -> { // Premium Cyber Pipe Top
                paint.color = Color.parseColor("#1B5E20") // Dark green
                canvas.drawRect(l - 4, t, r + 4, b, paint)
                // Light green rim highlight
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(l - 4, t, r + 4, t + 4, paint)
            }
            8 -> { // Flagpole
                paint.color = Color.parseColor("#ECEFF1")
                canvas.drawRect(l + w * 0.42f, t, l + w * 0.58f, b, paint)
                if (row <= 11) {
                    // Draw pole segments
                }
                if (row == 3) {
                    // Gold flagpole cap
                    paint.color = Color.parseColor("#FFD700")
                    canvas.drawCircle(l + w/2, t, w * 0.25f, paint)
                    // Custom cyan flag
                    val flagPath = Path()
                    flagPath.moveTo(l + w/2, t + 10)
                    flagPath.lineTo(l - w * 1.6f, t + h * 0.35f)
                    flagPath.lineTo(l + w/2, t + h * 0.7f)
                    flagPath.close()
                    paint.color = Color.parseColor("#00E5FF")
                    canvas.drawPath(flagPath, paint)
                }
            }
            9 -> { // Animated Lava Molten Flow
                val pulse = (Math.sin(System.currentTimeMillis() / 150.0) * 18).toInt()
                paint.color = Color.rgb(244, 67 + pulse, 54) // Vibrant lava red
                canvas.drawRect(l, t, r, b, paint)
                // Heat waves overlay
                paint.color = Color.YELLOW
                canvas.drawCircle(l + w * 0.25f, t + h * 0.3f, 3.5f, paint)
                canvas.drawCircle(l + w * 0.75f, t + h * 0.6f, 4.5f, paint)
            }
            10 -> { // Premium Collapsible Bridge Block
                paint.color = Color.parseColor("#6A1B9A") // Violet
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#BA68C8")
                canvas.drawRect(l + 3, t + 3, r - 3, b - 3, paint)
            }
        }
    }

    private fun drawTrapEntity(canvas: Canvas, ent: TrapEntity, l: Float, t: Float, r: Float, b: Float) {
        when (ent.type) {
            0 -> { // Nyan Cat (white rectangular body, yellow tail lines, cute face)
                paint.color = Color.WHITE
                canvas.drawRoundRect(l, t + 4, r, b - 4, 8f, 8f, paint)
                // Eyes
                paint.color = Color.BLACK
                canvas.drawCircle(l + (r - l)*0.7f, t + (b - t)*0.4f, 4f, paint)
                canvas.drawCircle(l + (r - l)*0.4f, t + (b - t)*0.4f, 4f, paint)
                // Rainbow tail trailing to the right
                paint.color = Color.parseColor("#E040FB")
                canvas.drawRect(r, t + 6, r + 15, t + 10, paint)
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawRect(r, t + 10, r + 15, t + 14, paint)
            }
            1 -> { // Poison Mushroom (Purple body, white spots)
                paint.color = Color.parseColor("#7B1FA2")
                canvas.drawOval(l, t, r, b - 4, paint)
                // Spots
                paint.color = Color.WHITE
                canvas.drawCircle(l + (r - l)*0.3f, t + (b - t)*0.3f, 4f, paint)
                canvas.drawCircle(l + (r - l)*0.7f, t + (b - t)*0.3f, 4f, paint)
                canvas.drawCircle(l + (r - l)*0.5f, t + (b - t)*0.6f, 3f, paint)
            }
            2 -> { // Popup ground spikes (Futuristic Plasma Spikes)
                val cx = (l + r) / 2
                // Translucent neon pink/red plasma energy aura
                paint.color = Color.parseColor("#40FF1744")
                canvas.drawCircle(cx, t + (b - t)/2f, (r - l) * 0.45f, paint)
                // Sleek metallic spike cone
                paint.color = Color.parseColor("#90A4AE")
                val path = Path()
                path.moveTo(l + (r - l) * 0.1f, b)
                path.lineTo(cx, t + (b - t) * 0.1f)
                path.lineTo(r - (r - l) * 0.1f, b)
                path.close()
                canvas.drawPath(path, paint)
                // Glowing plasma tip
                paint.color = Color.parseColor("#FF1744")
                canvas.drawCircle(cx, t + (b - t) * 0.1f, 4.5f, paint)
            }
            3 -> { // Companion Slime (Cyan, happy face)
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                val w = r - l
                val h = b - t
                val bounce = (Math.sin(System.currentTimeMillis() / 100.0) * 3f).toFloat()
                
                paint.color = Color.parseColor("#00E5FF") // Electric cyan
                canvas.drawOval(l, t + h * 0.1f + bounce, r, b, paint)
                
                // Cute happy smiley eyes
                paint.color = Color.BLACK
                canvas.drawCircle(cx - w * 0.18f, cy + bounce / 2f, 2.5f, paint)
                canvas.drawCircle(cx + w * 0.18f, cy + bounce / 2f, 2.5f, paint)
                
                // Happy smile
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                val mouthPath = Path()
                mouthPath.moveTo(cx - 3f, cy + 4f + bounce/2f)
                mouthPath.quadTo(cx, cy + 7f + bounce/2f, cx + 3f, cy + 4f + bounce/2f)
                canvas.drawPath(mouthPath, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawLandEnemy(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, type: Int) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t
        
        if (type == 0) { // 1. Cyber Slime
            // Animated vertical bouncing/squishing slime
            val bounce = (Math.sin(System.currentTimeMillis() / 100.0) * 3f).toFloat()
            paint.color = Color.parseColor("#00E676") // Neon green
            val slimeRect = RectF(l, t + h * 0.2f + bounce, r, b)
            canvas.drawOval(slimeRect, paint)
            
            // Neon core
            paint.color = Color.parseColor("#FFFF00")
            canvas.drawCircle(cx, cy + bounce / 2f + 3f, w * 0.15f, paint)
            
            // Small cyber eyes
            paint.color = Color.BLACK
            canvas.drawCircle(cx - w * 0.18f, cy + bounce / 2f, 3f, paint)
            canvas.drawCircle(cx + w * 0.18f, cy + bounce / 2f, 3f, paint)
        } else { // 2. Iron Shell
            // Steel blue shell base dome
            paint.color = Color.parseColor("#455A64")
            val shellRect = RectF(l, t + h * 0.2f, r, b)
            canvas.drawArc(shellRect, 180f, 180f, true, paint)
            
            // Draw spikes on the shell dome
            paint.color = Color.parseColor("#CFD8DC")
            val spikePath = Path()
            spikePath.moveTo(l + w * 0.2f, t + h * 0.3f)
            spikePath.lineTo(l + w * 0.25f, t + h * 0.1f)
            spikePath.lineTo(l + w * 0.35f, t + h * 0.3f)
            
            spikePath.moveTo(cx, t + h * 0.2f)
            spikePath.lineTo(cx, t - 4f)
            spikePath.lineTo(cx + w * 0.1f, t + h * 0.2f)
            
            spikePath.moveTo(r - w * 0.35f, t + h * 0.3f)
            spikePath.lineTo(r - w * 0.25f, t + h * 0.1f)
            spikePath.lineTo(r - w * 0.2f, t + h * 0.3f)
            canvas.drawPath(spikePath, paint)
            
            // Glowing orange visor eyes peeking out
            paint.color = Color.parseColor("#FF6F00")
            canvas.drawRoundRect(cx - 10f, b - h * 0.3f, cx + 10f, b - h * 0.1f, 2f, 2f, paint)
        }
    }

    private fun drawCat(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t

        val catColor = when (catType) {
            1 -> Color.parseColor("#FFD700") // Golden Neko
            2 -> Color.parseColor("#37474F") // Shadow Nya
            else -> Color.WHITE // Classic Syobon
        }
        val innerEarColor = when (catType) {
            1 -> Color.parseColor("#FFF59D") // Light yellow
            2 -> Color.parseColor("#7E57C2") // Light purple
            else -> Color.parseColor("#FFCDD2") // Pink
        }

        paint.color = catColor
        
        // 1. Draw Tail
        val tailPath = Path()
        if (isFacingRight) {
            tailPath.moveTo(l + w * 0.2f, b - h * 0.4f)
            tailPath.quadTo(l - w * 0.2f, b - h * 0.7f, l - w * 0.1f, b - h * 0.8f)
            tailPath.quadTo(l - w * 0.3f, b - h * 0.7f, l + w * 0.1f, b - h * 0.3f)
        } else {
            tailPath.moveTo(r - w * 0.2f, b - h * 0.4f)
            tailPath.quadTo(r + w * 0.2f, b - h * 0.7f, r + w * 0.1f, b - h * 0.8f)
            tailPath.quadTo(r + w * 0.3f, b - h * 0.7f, r - w * 0.1f, b - h * 0.3f)
        }
        canvas.drawPath(tailPath, paint)

        // 2. Draw Body
        val bodyRect = RectF(l + w * 0.15f, t + h * 0.45f, r - w * 0.15f, b - h * 0.1f)
        canvas.drawRoundRect(bodyRect, 8f, 8f, paint)

        // 2b. Character specific accessory (on body)
        when (catType) {
            0 -> { // Classic Syobon: Blue collar / scarf
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawRect(l + w * 0.2f, t + h * 0.42f, r - w * 0.2f, t + h * 0.48f, paint)
                // Draw a small yellow bell
                paint.color = Color.YELLOW
                canvas.drawCircle(cx, t + h * 0.48f, 4f, paint)
            }
            1 -> { // Golden Neko: Red tie
                paint.color = Color.parseColor("#D50000")
                val tiePath = Path()
                tiePath.moveTo(cx, t + h * 0.45f)
                tiePath.lineTo(cx - 5f, t + h * 0.58f)
                tiePath.lineTo(cx, t + h * 0.65f)
                tiePath.lineTo(cx + 5f, t + h * 0.58f)
                tiePath.close()
                canvas.drawPath(tiePath, paint)
            }
            2 -> { // Shadow Nya: Red belt/sash
                paint.color = Color.parseColor("#D50000")
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
        val headRect = RectF(l + w * 0.05f, t + h * 0.05f, r - w * 0.05f, t + h * 0.65f)
        canvas.drawOval(headRect, paint)

        // 5. Draw Ears
        val earsPath = Path()
        earsPath.moveTo(l + w * 0.15f, t + h * 0.25f)
        earsPath.lineTo(l + w * 0.05f, t)
        earsPath.lineTo(l + w * 0.35f, t + h * 0.18f)
        earsPath.moveTo(r - w * 0.15f, t + h * 0.25f)
        earsPath.lineTo(r - w * 0.05f, t)
        earsPath.lineTo(r - w * 0.35f, t + h * 0.18f)
        canvas.drawPath(earsPath, paint)

        paint.color = innerEarColor
        val innerEarsPath = Path()
        innerEarsPath.moveTo(l + w * 0.18f, t + h * 0.22f)
        innerEarsPath.lineTo(l + w * 0.1f, t + h * 0.06f)
        innerEarsPath.lineTo(l + w * 0.3f, t + h * 0.18f)
        innerEarsPath.moveTo(r - w * 0.18f, t + h * 0.22f)
        innerEarsPath.lineTo(r - w * 0.1f, t + h * 0.06f)
        innerEarsPath.lineTo(r - w * 0.3f, t + h * 0.18f)
        canvas.drawPath(innerEarsPath, paint)

        // 5b. Head accessories
        when (catType) {
            1 -> { // Golden Neko: Royal Crown
                paint.color = Color.parseColor("#FFD54F") // Gold crown base
                val crownPath = Path()
                crownPath.moveTo(cx - 10f, t + h * 0.08f)
                crownPath.lineTo(cx - 15f, t - 8f)
                crownPath.lineTo(cx - 5f, t + h * 0.02f)
                crownPath.lineTo(cx, t - 12f)
                crownPath.lineTo(cx + 5f, t + h * 0.02f)
                crownPath.lineTo(cx + 15f, t - 8f)
                crownPath.lineTo(cx + 10f, t + h * 0.08f)
                crownPath.close()
                canvas.drawPath(crownPath, paint)
                // Red crown gem
                paint.color = Color.RED
                canvas.drawCircle(cx, t - 2f, 2.5f, paint)
            }
            2 -> { // Shadow Nya: Red Ninja Headband tail
                paint.color = Color.parseColor("#D50000")
                // Draw headband knot tails waving back
                val bandPath = Path()
                if (isFacingRight) {
                    bandPath.moveTo(l + w * 0.15f, t + h * 0.25f)
                    bandPath.lineTo(l - w * 0.2f, t + h * 0.28f)
                    bandPath.lineTo(l - w * 0.15f, t + h * 0.4f)
                    bandPath.close()
                } else {
                    bandPath.moveTo(r - w * 0.15f, t + h * 0.25f)
                    bandPath.lineTo(r + w * 0.2f, t + h * 0.28f)
                    bandPath.lineTo(r + w * 0.15f, t + h * 0.4f)
                    bandPath.close()
                }
                canvas.drawPath(bandPath, paint)
            }
        }

        // 6. Draw Eyes (･ω･)
        val eyeColor = when (catType) {
            1 -> Color.parseColor("#00C853") // Golden Neko: Emerald green eyes
            2 -> Color.RED // Shadow Nya: Glowing red eyes
            else -> Color.parseColor("#29B6F6") // Classic Syobon: Cute sky-blue eyes
        }
        paint.color = eyeColor
        val headCy = (headRect.top + headRect.bottom) / 2f
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
        val mouthPath = Path()
        mouthPath.moveTo(cx - 5f, headCy + 6f)
        mouthPath.quadTo(cx - 2.5f, headCy + 10f, cx, headCy + 6f)
        mouthPath.quadTo(cx + 2.5f, headCy + 10f, cx + 5f, headCy + 6f)
        canvas.drawPath(mouthPath, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawOverlayScreen(canvas: Canvas, title: String, subtitle: String) {
        // Draw dimming background
        paint.color = Color.parseColor("#B3000000")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw dialog box
        val dialogW = width * 0.6f
        val dialogH = height * 0.4f
        val dl = (width - dialogW)/2f
        val dt = (height - dialogH)/2f
        
        paint.color = Color.parseColor("#212121")
        canvas.drawRoundRect(dl, dt, dl + dialogW, dt + dialogH, 16f, 16f, paint)
        
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(dl, dt, dl + dialogW, dt + dialogH, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // Text
        paint.color = Color.parseColor("#FFD700")
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
