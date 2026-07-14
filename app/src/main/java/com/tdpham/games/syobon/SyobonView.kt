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

class SyobonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "syobon_action"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val celebrationManager = CelebrationManager()
    
    // Core game state
    private var gameOver = false
    private var gamePaused = false
    private var isLevelCleared = false
    
    // Lives & death count
    private var currentLives = 3
    private var deaths = 0
    private var initialLivesOption = 3 // 3, 1, or -99

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
    private val maxJumpHoldTime = 350 // milliseconds
    private var isDying = false
    private var deathTime = 0L
    private var dieSpinAngle = 0f

    // Physics parameters
    private val gravity = 0.018f
    private val speedAccel = 0.008f
    private val friction = 0.82f
    private val maxSpeed = 0.16f
    private val jumpImpulse = -0.35f
    private val jumpHoldForce = -0.016f

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
        var isDead: Boolean = false
    )
    private val landEnemies = mutableListOf<LandEnemy>()

    // Game loop
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUpdate = 0L
    private val gameLoop = object : Runnable {
        override fun run() {
            update()
            invalidate()
            mainHandler.postDelayed(this, 16)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
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
        // Load preferences
        val prefs = context.getSharedPreferences("syobon_settings", Context.MODE_PRIVATE)
        val livesOption = prefs.getInt(SyobonOptionsDialog.KEY_LIVES_TYPE, 0)
        initialLivesOption = when (livesOption) {
            1 -> 1
            2 -> -99
            else -> 3
        }
        currentLives = initialLivesOption
        if (initialLivesOption == -99) {
            // Keep deaths from resetting to preserve the joke
        } else {
            deaths = 0
        }

        resetLevelState()
        gameOver = false
        gamePaused = false
        isLevelCleared = false
        
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
        landEnemies.add(LandEnemy(14f, 12f, -0.04f, 0f))
        landEnemies.add(LandEnemy(28f, 12f, -0.04f, 0f))
        landEnemies.add(LandEnemy(38f, 12f, 0.04f, 0f))
        landEnemies.add(LandEnemy(60f, 12f, -0.04f, 0f))
        trapTriggered.fill(false)
        invisibleBlocks.clear()
        fallingBlocks.clear()

        buildLevelMap()
    }

    private fun buildLevelMap() {
        // Clear map
        for (r in 0 until rows) {
            map[r].fill(0)
        }

        // 1. Ground bricks
        for (c in 0 until totalMapCols) {
            // Classic Mario gap at col 22-24 and col 45-47
            if (c in 22..24 || c in 45..47 || c in 72..75) {
                continue
            }
            map[13][c] = 1 // Ground top
            map[14][c] = 1 // Ground deep
        }

        // 2. Standard structures
        // First group of question / brick blocks
        map[9][8] = 2 // Brick
        map[9][9] = 3 // Question block
        map[9][10] = 2 // Brick
        map[9][11] = 4 // INVISIBLE block (trap!)
        map[9][12] = 2 // Brick

        // Let's seed invisible block state
        invisibleBlocks[Pair(9, 11)] = false // initially invisible

        // Pipes
        buildPipe(16, 3) // Normal pipe
        buildPipe(32, 4) // Trolled pipe (nyan cat spawns when near)
        buildPipe(55, 3) // Pipe with hidden spikes inside

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
        val prefs = context.getSharedPreferences("syobon_settings", Context.MODE_PRIVATE)
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

        // Spike collision check
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
                if (map[r][c] == 5) {
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
        val iterator = trapEntities.iterator()
        while (iterator.hasNext()) {
            val ent = iterator.next()
            
            if (ent.type == 1) {
                // Apply gravity to poison mushroom
                ent.vy += 0.012f
                ent.y += ent.vy
                ent.x += ent.vx
                
                // Simple collision check for mushroom
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
                die()
            }
            
            // Remove off-screen/fallen entities
            if (ent.x < cameraX - 2 || ent.x > cameraX + cols + 2 || ent.y > rows + 2) {
                iterator.remove()
            }
        }

        // Update Land Enemies
        val enemyIterator = landEnemies.iterator()
        while (enemyIterator.hasNext()) {
            val enemy = enemyIterator.next()
            if (enemy.isDead) {
                enemyIterator.remove()
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
            
            // Reverse direction if hitting solid wall blocks
            val nextX = (enemy.x + (if (enemy.vx > 0) 0.8f else -0.1f)).toInt()
            if (ey in 0 until rows && nextX in 0 until totalMapCols) {
                if (isSolid(ey, nextX)) {
                    enemy.vx = -enemy.vx
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
                trapTriggered[11] = true
                // We don't spawn a nyan cat immediately, but change invisible block hints
            }
        }

        // 2. Spawn a flying spike (Nyan Cat style) at col 32 when passing near pipe
        if (playerX > 28f && !trapTriggered[32]) {
            trapTriggered[32] = true
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
            trapTriggered[45] = true
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

        // 4. Pole flag popup trap (when very close to the flagpole)
        if (playerX >= 83f && !trapTriggered[85]) {
            trapTriggered[85] = true
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

            // Troll! Spawns a toxic poison mushroom sliding towards player!
            trapEntities.add(
                TrapEntity(
                    x = c.toFloat(),
                    y = r - 1f,
                    vx = -0.06f,
                    vy = 0f,
                    type = 1 // Poison Mushroom
                )
            )
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver || isLevelCleared) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                pressedKeys.add(keyCode)
            }
            KeyEvent.KEYCODE_MENU -> {
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
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver || isLevelCleared) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
                resetGame()
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#80D8FF")) // Retro Sky Blue

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
            drawLandEnemy(canvas, el, et, er, eb)
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

        // 4. Draw HUD (Ouchies, Lives)
        paint.color = Color.BLACK
        paint.textSize = 36f
        paint.typeface = Typeface.MONOSPACE
        paint.textAlign = Paint.Align.LEFT
        
        val livesStr = if (currentLives < 0) "$currentLives" else "$currentLives"
        canvas.drawText("OUCHIES: $deaths", 30f, 60f, paint)
        canvas.drawText("LIVES: $livesStr", 30f, 110f, paint)

        // 5. Success overlay
        if (isLevelCleared) {
            celebrationManager.draw(canvas)
            drawOverlayScreen(canvas, "LEVEL CLEARED!", "Press OK/Center to Play Again")
        } else if (gameOver) {
            drawOverlayScreen(canvas, "GAME OVER", "Press OK/Center to Retry")
        }
    }

    private fun drawTile(canvas: Canvas, tile: Int, l: Float, t: Float, r: Float, b: Float, row: Int, col: Int) {
        when (tile) {
            1 -> { // Ground block (Brown/Yellow retro textured)
                paint.color = Color.parseColor("#8B5A2B")
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#CD853F")
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
                paint.color = Color.parseColor("#8B5A2B")
                paint.strokeWidth = 3f
                canvas.drawLine(l, t, r, t, paint)
            }
            2 -> { // Brick (Orange brown)
                paint.color = Color.parseColor("#B22222") // Firebrick red
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#CD5C5C")
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
                paint.color = Color.BLACK
                paint.strokeWidth = 2f
                canvas.drawLine(l, t + (b - t)/2, r, t + (b - t)/2, paint)
                canvas.drawLine(l + (r - l)/2, t, l + (r - l)/2, t + (b - t)/2, paint)
                canvas.drawLine(l + (r - l)/4, t + (b - t)/2, l + (r - l)/4, b, paint)
                canvas.drawLine(l + 3 * (r - l)/4, t + (b - t)/2, l + 3 * (r - l)/4, b, paint)
            }
            3 -> { // Question block (Yellow)
                paint.color = Color.parseColor("#DAA520") // Goldenrod
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#FFD700") // Gold
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
                // Draw '?'
                paint.color = Color.BLACK
                paint.textSize = (b - t) * 0.7f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("?", l + (r - l)/2f, b - (b - t)*0.2f, paint)
            }
            4 -> { // Invisible block (only draw outline if it is hit and active)
                if (invisibleBlocks[Pair(row, col)] == true) {
                    // Turn it into a flat metal block
                    paint.color = Color.parseColor("#78909C")
                    canvas.drawRect(l, t, r, b, paint)
                    paint.color = Color.parseColor("#B0BEC5")
                    canvas.drawRect(l + 4, t + 4, r - 4, b - 4, paint)
                }
            }
            5 -> { // Spike (Triangle shape)
                paint.color = Color.parseColor("#90A4AE") // Steel grey
                val path = Path()
                path.moveTo(l, b)
                path.lineTo(l + (r - l)/2, t)
                path.lineTo(r, b)
                path.close()
                canvas.drawPath(path, paint)
            }
            6 -> { // Pipe body (Green side lines)
                paint.color = Color.parseColor("#2E7D32")
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(l + 4, t, r - 4, b, paint)
            }
            7 -> { // Pipe top
                paint.color = Color.parseColor("#1B5E20")
                canvas.drawRect(l - 4, t, r + 4, b, paint)
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(l, t + 2, r, b - 2, paint)
            }
            8 -> { // Flagpole
                paint.color = Color.parseColor("#B0BEC5")
                canvas.drawRect(l + (r - l)*0.4f, t, l + (r - l)*0.6f, b, paint)
                // If it is top, draw a green circle/knob
                if (row == 3) {
                    paint.color = Color.parseColor("#FF1744")
                    canvas.drawCircle(l + (r - l)/2, t, (r - l)*0.3f, paint)
                    
                    // Draw red flag waving
                    val flagPath = Path()
                    flagPath.moveTo(l + (r - l)/2, t + 10)
                    flagPath.lineTo(l - (r - l)*1.5f, t + (b - t)*0.3f)
                    flagPath.lineTo(l + (r - l)/2, t + (b - t)*0.6f)
                    flagPath.close()
                    canvas.drawPath(flagPath, paint)
                }
            }
            10 -> { // Collapsible bridge/floor (same texture as ground but reddish)
                paint.color = Color.parseColor("#A349A4")
                canvas.drawRect(l, t, r, b, paint)
                paint.color = Color.parseColor("#C8BFE7")
                canvas.drawRect(l + 2, t + 2, r - 2, b - 2, paint)
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
            2 -> { // Popup ground spikes
                paint.color = Color.parseColor("#D50000") // Lethal Red spikes
                val path = Path()
                path.moveTo(l, b)
                path.lineTo(l + (r - l)/2, t)
                path.lineTo(r, b)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawLandEnemy(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t
        
        paint.color = Color.WHITE
        canvas.drawOval(l, t + h * 0.2f, r, b, paint)
        
        // Draw two cute little ears
        val earPath = Path()
        earPath.moveTo(l + w * 0.2f, t + h * 0.3f)
        earPath.lineTo(l + w * 0.1f, t + h * 0.1f)
        earPath.lineTo(l + w * 0.35f, t + h * 0.25f)
        earPath.moveTo(r - w * 0.2f, t + h * 0.3f)
        earPath.lineTo(r - w * 0.1f, t + h * 0.1f)
        earPath.lineTo(r - w * 0.35f, t + h * 0.25f)
        canvas.drawPath(earPath, paint)
        
        // Draw eyes
        paint.color = Color.BLACK
        canvas.drawCircle(cx - w * 0.2f, cy, 3.5f, paint)
        canvas.drawCircle(cx + w * 0.2f, cy, 3.5f, paint)
    }

    private fun drawCat(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        val w = r - l
        val h = b - t

        paint.color = Color.WHITE
        
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

        // 3. Draw Legs (animated walking offsets)
        val walkOffset = if (!isOnGround) 0f else Math.sin(System.currentTimeMillis() / 80.0).toFloat() * 4f
        paint.color = Color.WHITE
        canvas.drawRoundRect(l + w * 0.25f, b - h * 0.15f, l + w * 0.4f, b + walkOffset, 3f, 3f, paint)
        canvas.drawRoundRect(r - w * 0.4f, b - h * 0.15f, r - w * 0.25f, b - walkOffset, 3f, 3f, paint)

        // 4. Draw Head
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

        paint.color = Color.parseColor("#FFCDD2")
        val innerEarsPath = Path()
        innerEarsPath.moveTo(l + w * 0.18f, t + h * 0.22f)
        innerEarsPath.lineTo(l + w * 0.1f, t + h * 0.06f)
        innerEarsPath.lineTo(l + w * 0.3f, t + h * 0.18f)
        innerEarsPath.moveTo(r - w * 0.18f, t + h * 0.22f)
        innerEarsPath.lineTo(r - w * 0.1f, t + h * 0.06f)
        innerEarsPath.lineTo(r - w * 0.3f, t + h * 0.18f)
        canvas.drawPath(innerEarsPath, paint)

        // 6. Draw Eyes (･ω･)
        paint.color = Color.BLACK
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
