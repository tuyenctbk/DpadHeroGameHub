package com.tdpham.games.tanks

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class BattleTanksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "battle_tanks"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rows = 13
    private val cols = 13
    private var cellW = 0f
    private var cellH = 0f

    private val grid = Array(rows) { IntArray(cols) } // 0:Empty, 1:Brick, 2:Steel, 3:Base, 4:Trees
    private var player = Tank(6, 12, 0, true)
    private val enemies = mutableListOf<Tank>()
    private val bullets = mutableListOf<Bullet>()
    private val particles = mutableListOf<GameEnvironment.Particle>()
    
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var level = 1
    private var startingLevel = 1
    private var isLevelLoading = false
    private var isLevelCleared = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "battle_tanks_settings"
    private val KEY_START_LEVEL = "start_level"
    private val KEY_TANK_TYPE = "selected_tank_type"
    private var selectedTankType = 0
    private var playerLives = 3
    private val hints = listOf(
        "Press MENU for Options",
        "Press BACK for Help / Pause",
        "Use D-PAD to Move & Aim",
        "Tanks shoot automatically"
    )
    private var currentHintIndex = 0
    private var hintFrameCounter = 0
    private var isInitialized = false
    
    private var bgType = GameEnvironment.BackgroundType.SOLID
    private var isNight = false

    private val handler = Handler(Looper.getMainLooper())
    private val animHandler = Handler(Looper.getMainLooper())
    private val gridRect = RectF()
    private val tempPath = Path()
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || isLevelCleared) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 30) // Tanks update a bit slower for retro feel
            }
        }
    }

    data class Tank(var x: Int, var y: Int, var dir: Int, val isPlayer: Boolean, var lastFire: Long = 0, var hp: Int = 1) {
        var blinkUntil: Long = 0L
    }
    data class Bullet(var fx: Float, var fy: Float, var dir: Int, val isPlayer: Boolean)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
        SoundManager.playSuccess()
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
        invalidate()
    }

    override fun pause() {
        gamePaused = true
        handler.removeCallbacks(gameLoop)
    }
    override fun resume() {
        gamePaused = false
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        animHandler.removeCallbacks(animRunnable)
    }

    override fun resetGame() {
        // Load start level from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        startingLevel = prefs.getInt(KEY_START_LEVEL, 1).coerceIn(1, 3)
        selectedTankType = prefs.getInt(KEY_TANK_TYPE, 0).coerceIn(0, 2)
        level = startingLevel

        score = 0
        best = ScoreManager.getHighScore(context, gameKey, startingLevel)
        gameOver = false
        gamePaused = true
        isLevelCleared = false

        playerLives = when(selectedTankType) {
            1 -> 1
            2 -> 5
            else -> 3
        }
        animHandler.removeCallbacks(animRunnable)
        setupLevel()
        hintFrameCounter = 0
        invalidate()
    }

    private fun setupLevel() {
        isLevelLoading = true
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = 0
        
        bgType = GameEnvironment.BackgroundType.SOLID
        isNight = false
        
        // Random map generation
        for (r in 1 until rows - 1) {
            for (c in 0 until cols) {
                if (Random.nextFloat() < 0.25) grid[r][c] = 1 // Brick
                else if (Random.nextFloat() < 0.08) grid[r][c] = 2 // Steel
                else if (Random.nextFloat() < 0.05) grid[r][c] = 4 // Trees
            }
        }
        
        grid[rows - 1][cols / 2] = 3 // Base
        // Ensure path to base is clear
        grid[rows - 1][cols / 2 - 1] = 1
        grid[rows - 1][cols / 2 + 1] = 1
        grid[rows - 2][cols / 2] = 1
        
        player = Tank(cols / 2 - 2, rows - 1, 0, true)
        enemies.clear()
        bullets.clear()
        particles.clear()
        spawnEnemy()
        isLevelLoading = false
    }

    private fun onGameOverTriggered() {
        gameOver = true
        gamePaused = true
        SoundManager.playError()
        val oldBest = best
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, startingLevel)
        if (isNewHigh) best = score
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = false,
            isNewHigh = isNewHigh,
            score = score,
            highScore = oldBest
        )
        onGameOver?.invoke(score)
        animHandler.removeCallbacks(animRunnable)
        animHandler.post(animRunnable)
    }

    private fun spawnEnemy() {
        if (enemies.size < (level + 2).coerceAtMost(6)) {
            val ex = listOf(0, cols / 2, cols - 1).random()
            enemies.add(Tank(ex, 0, 2, false, hp = if (Random.nextFloat() < 0.2) 2 else 1))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame(); resume(); return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (isLevelCleared) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                level++
                isLevelCleared = false
                setupLevel()
                resume()
                return true
            }
            return true
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume(); return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveTank(player, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveTank(player, 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveTank(player, 2)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveTank(player, 3)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun showOptions() {
        pause()
        BattleTanksOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (gameOver) {
                resetGame(); resume(); return true
            }
            if (gamePaused) {
                resume(); return true
            }

            // Quadrant based moves
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            val dir = if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) 1 else 3
            } else {
                if (y > centerY) 2 else 0
            }
            
            // Move if changed, or shoot if same
            if (player.dir == dir) {
                fire(player)
            } else {
                moveTank(player, dir)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun moveTank(tank: Tank, dir: Int) {
        tank.dir = dir
        var nx = tank.x
        var ny = tank.y
        when (dir) {
            0 -> ny--
            1 -> nx++
            2 -> ny++
            3 -> nx--
        }
        if (nx in 0 until cols && ny in 0 until rows && (grid[ny][nx] == 0 || grid[ny][nx] == 4)) {
            tank.x = nx
            tank.y = ny
        }
    }

    private fun fire(tank: Tank) {
        val now = System.currentTimeMillis()
        val fireDelay = if (tank.isPlayer) {
            when(selectedTankType) {
                1 -> 300L // Firestorm (Fast reload)
                2 -> 950L // Mammoth (Slow reload)
                else -> 600L // Titan
            }
        } else {
            600L
        }
        if (now - tank.lastFire > fireDelay) {
            bullets.add(Bullet(tank.x.toFloat(), tank.y.toFloat(), tank.dir, tank.isPlayer))
            tank.lastFire = now
            if (tank.isPlayer) SoundManager.playClick()
        }
    }

    override fun onDraw(canvas: Canvas) {
        cellW = width.toFloat() / cols
        cellH = height.toFloat() / rows

        if (!gamePaused && !gameOver) {
            hintFrameCounter++
            if (hintFrameCounter >= 250) {
                hintFrameCounter = 0
                currentHintIndex = (currentHintIndex + 1) % hints.size
            }
        }

        // update() - Moved to gameLoop

        GameEnvironment.draw(canvas, bgType, isNight = isNight, paint = paint, particles = particles)

        // Draw Map
        for (r in 0 until rows) for (c in 0 until cols) {
            val type = grid[r][c]
            if (type == 0) continue
            paint.style = Paint.Style.FILL
            when (type) {
                1 -> { // Brick
                    paint.color = Color.parseColor("#A1887F")
                    gridRect.set(c * cellW + 2, r * cellH + 2, (c + 1) * cellW - 2, (r + 1) * cellH - 2)
                    canvas.drawRect(gridRect, paint)
                    // Brick mortar lines (dark brown)
                    paint.color = Color.parseColor("#795548")
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    // Horizontal mortar line
                    canvas.drawLine(c * cellW + 2, r * cellH + cellH / 2, (c + 1) * cellW - 2, r * cellH + cellH / 2, paint)
                    // Vertical mortar lines
                    canvas.drawLine(c * cellW + cellW / 2, r * cellH + 2, c * cellW + cellW / 2, r * cellH + cellH / 2, paint)
                    canvas.drawLine(c * cellW + cellW / 4, r * cellH + cellH / 2, c * cellW + cellW / 4, (r + 1) * cellH - 2, paint)
                    canvas.drawLine(c * cellW + 3 * cellW / 4, r * cellH + cellH / 2, c * cellW + 3 * cellW / 4, (r + 1) * cellH - 2, paint)
                    paint.style = Paint.Style.FILL
                }
                2 -> { // Steel
                    paint.color = Color.parseColor("#78909C") // Metallic blue-gray
                    gridRect.set(c * cellW + 2, r * cellH + 2, (c + 1) * cellW - 2, (r + 1) * cellH - 2)
                    canvas.drawRect(gridRect, paint)
                    // Inner plate
                    paint.color = Color.parseColor("#B0BEC5")
                    gridRect.set(c * cellW + 6, r * cellH + 6, (c + 1) * cellW - 6, (r + 1) * cellH - 6)
                    canvas.drawRect(gridRect, paint)
                    // Bolts in 4 corners
                    paint.color = Color.parseColor("#37474F")
                    canvas.drawCircle(c * cellW + 9, r * cellH + 9, 3f, paint)
                    canvas.drawCircle((c + 1) * cellW - 9, r * cellH + 9, 3f, paint)
                    canvas.drawCircle(c * cellW + 9, (r + 1) * cellH - 9, 3f, paint)
                    canvas.drawCircle((c + 1) * cellW - 9, (r + 1) * cellH - 9, 3f, paint)
                }
                3 -> {
                    // Draw outer border (golden shield/brick frame)
                    paint.color = Color.parseColor("#FFC107")
                    gridRect.set(c * cellW + 2, r * cellH + 2, (c + 1) * cellW - 2, (r + 1) * cellH - 2)
                    canvas.drawRoundRect(gridRect, 6f, 6f, paint)

                    // Inner shield background (dark blue)
                    paint.color = Color.parseColor("#1A237E")
                    gridRect.set(c * cellW + 6, r * cellH + 6, (c + 1) * cellW - 6, (r + 1) * cellH - 6)
                    canvas.drawRoundRect(gridRect, 4f, 4f, paint)

                    // Draw golden wings or shield stripes
                    paint.color = Color.parseColor("#FFD54F")
                    tempPath.reset()
                    tempPath.moveTo(c * cellW + 8, r * cellH + cellH * 0.3f)
                    tempPath.lineTo(c * cellW + cellW * 0.3f, r * cellH + cellH * 0.7f)
                    tempPath.lineTo(c * cellW + cellW * 0.5f, r * cellH + cellH * 0.5f)
                    tempPath.lineTo(c * cellW + cellW * 0.7f, r * cellH + cellH * 0.7f)
                    tempPath.lineTo((c + 1) * cellW - 8, r * cellH + cellH * 0.3f)
                    tempPath.lineTo(c * cellW + cellW * 0.5f, (r + 1) * cellH - 8)
                    tempPath.close()
                    canvas.drawPath(tempPath, paint)

                    // Red star in the center
                    paint.color = Color.RED
                    paint.textSize = cellW * 0.5f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("⭐", c * cellW + cellW/2, r * cellH + cellH/2 + cellH/6, paint)
                }
                4 -> { // Grass / Trees
                    paint.color = Color.parseColor("#2E7D32")
                    val cx = c * cellW + cellW / 2
                    val cy = r * cellH + cellH / 2
                    canvas.drawCircle(cx - cellW * 0.15f, cy - cellH * 0.15f, cellW * 0.25f, paint)
                    canvas.drawCircle(cx + cellW * 0.15f, cy - cellH * 0.15f, cellW * 0.25f, paint)
                    canvas.drawCircle(cx - cellW * 0.15f, cy + cellH * 0.15f, cellW * 0.25f, paint)
                    canvas.drawCircle(cx + cellW * 0.15f, cy + cellH * 0.15f, cellW * 0.25f, paint)
                    paint.color = Color.parseColor("#1B5E20") // Darker green detail
                    canvas.drawCircle(cx, cy, cellW * 0.2f, paint)
                }
            }
        }

        // Draw Player
        drawTank(canvas, player, Color.parseColor("#4CAF50"))

        // Draw Enemies
        for (e in enemies) drawTank(canvas, e, if (e.hp > 1) Color.parseColor("#673AB7") else Color.parseColor("#FF5722"))

        // Draw Bullets
        paint.color = Color.YELLOW
        for (b in bullets) {
            canvas.drawCircle(b.fx * cellW + cellW / 2, b.fy * cellH + cellH / 2, 6f, paint)
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.style = Paint.Style.FILL
        val hudY = height * 0.05f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)}: $level", width / 2f, hudY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        // Quick Hint (Top/Left)
        if (!gameOver) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            val alpha = if (gamePaused) {
                255
            } else {
                when {
                    hintFrameCounter < 40 -> ((hintFrameCounter / 40f) * 255).toInt().coerceIn(0, 255)
                    hintFrameCounter > 210 -> (((250 - hintFrameCounter) / 40f) * 255).toInt().coerceIn(0, 255)
                    else -> 255
                }
            }
            paint.alpha = alpha
            canvas.drawText(hints[currentHintIndex], 40f, hudY + 45f, paint)
            paint.alpha = 255
        }

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.mission_failed_label), "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (isLevelCleared) {
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.victory_label)
            drawOverlay(canvas, title, "${context.getString(R.string.level_label)} $level\n${context.getString(R.string.continue_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_tanks), "${context.getString(R.string.level_label)} $level\n${context.getString(R.string.start_game)}")
        }

        if (gameOver || isLevelCleared) {
            celebrationManager.draw(canvas)
        }
    }

    private fun drawTank(canvas: Canvas, tank: Tank, color: Int) {
        val now = System.currentTimeMillis()
        val isBlinking = now < tank.blinkUntil
        val drawColor = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else color

        paint.style = Paint.Style.FILL
        val x = tank.x * cellW
        val y = tank.y * cellH
        val centerX = x + cellW / 2
        val centerY = y + cellH / 2

        // Tracks aligned with direction of movement
        paint.color = Color.DKGRAY
        if (tank.dir == 0 || tank.dir == 2) { // Up or Down
            // Left track
            gridRect.set(x + 2, y + 2, x + 10, y + cellH - 2)
            canvas.drawRect(gridRect, paint)
            // Right track
            gridRect.set(x + cellW - 10, y + 2, x + cellW - 2, y + cellH - 2)
            canvas.drawRect(gridRect, paint)
        } else { // Left or Right
            // Top track
            gridRect.set(x + 2, y + 2, x + cellW - 2, y + 10)
            canvas.drawRect(gridRect, paint)
            // Bottom track
            gridRect.set(x + 2, y + cellH - 10, x + cellW - 2, y + cellH - 2)
            canvas.drawRect(gridRect, paint)
        }
        
        if (tank.isPlayer) {
            val bodyColor = when(selectedTankType) {
                1 -> Color.parseColor("#FBC02D") // Yellow (Firestorm)
                2 -> Color.parseColor("#0288D1") // Blue (Mammoth)
                else -> Color.parseColor("#4CAF50") // Green (Titan)
            }
            val turretColor = when(selectedTankType) {
                1 -> Color.parseColor("#FFB300")
                2 -> Color.parseColor("#90A4AE")
                else -> Color.parseColor("#81C784")
            }
            val starColor = when(selectedTankType) {
                1 -> Color.RED
                2 -> Color.WHITE
                else -> Color.YELLOW
            }
            
            paint.color = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else bodyColor
            gridRect.set(x + 10, y + 10, x + cellW - 10, y + cellH - 10)
            canvas.drawRoundRect(gridRect, 6f, 6f, paint)
            
            paint.color = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else turretColor
            canvas.drawCircle(centerX, centerY, cellW * 0.24f, paint)
            
            paint.color = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else starColor
            canvas.drawCircle(centerX, centerY, cellW * 0.10f, paint)
        } else {
            // Enemy tank body: Blockier (Rect instead of RoundRect)
            gridRect.set(x + 10, y + 10, x + cellW - 10, y + cellH - 10)
            paint.color = drawColor
            canvas.drawRect(gridRect, paint)
            
            // Turret (Square)
            paint.color = if (isBlinking && (now / 80) % 2 == 0L) Color.WHITE else Color.parseColor("#E57373")
            gridRect.set(centerX - cellW * 0.22f, centerY - cellH * 0.22f, centerX + cellW * 0.22f, centerY + cellH * 0.22f)
            canvas.drawRect(gridRect, paint)
        }

        // Barrel
        paint.color = Color.WHITE
        val bw = 5f
        when (tank.dir) {
            0 -> { gridRect.set(centerX-bw, y + 4, centerX+bw, centerY); canvas.drawRect(gridRect, paint) }
            1 -> { gridRect.set(centerX, centerY-bw, x+cellW-4, centerY+bw); canvas.drawRect(gridRect, paint) }
            2 -> { gridRect.set(centerX-bw, centerY, centerX+bw, y+cellH-4); canvas.drawRect(gridRect, paint) }
            3 -> { gridRect.set(x+4, centerY-bw, centerX, centerY+bw); canvas.drawRect(gridRect, paint) }
        }
    }

    private fun update() {
        if (isLevelLoading) return
        val now = System.currentTimeMillis()
        fire(player)

        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            val speed = 0.25f
            when (b.dir) {
                0 -> b.fy -= speed
                1 -> b.fx += speed
                2 -> b.fy += speed
                3 -> b.fx -= speed
            }

            val bx = b.fx.toInt()
            val by = b.fy.toInt()

            if (bx !in 0 until cols || by !in 0 until rows) {
                bIter.remove()
                continue
            }

            val tile = grid[by][bx]
            if (tile != 0 && tile != 4) { // Hits something solid
                if (tile == 1) grid[by][bx] = 0 // Break brick
                if (tile == 3) {
                    onGameOverTriggered()
                }
                bIter.remove()
                continue
            }

            if (b.isPlayer) {
                val eIter = enemies.iterator()
                var hit = false
                while (eIter.hasNext()) {
                    val e = eIter.next()
                    if (e.x == bx && e.y == by) {
                        e.hp--
                        e.blinkUntil = System.currentTimeMillis() + 800L
                        if (e.hp <= 0) {
                            eIter.remove()
                            score += 100
                            SoundManager.playScore()
                            if (score % 1000 == 0) {
                                isLevelCleared = true
                                gamePaused = true
                                currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
                                celebrationManager.startOutcome(
                                    width = width.toFloat(),
                                    height = height.toFloat(),
                                    isWin = true,
                                    score = score,
                                    highScore = best
                                )
                                animHandler.removeCallbacks(animRunnable)
                                animHandler.post(animRunnable)
                                return
                            }
                        }
                        hit = true; break
                    }
                }
                if (hit) {
                    bIter.remove()
                    continue
                }
            } else {
                if (player.x == bx && player.y == by) {
                    bIter.remove()
                    if (now > player.blinkUntil) {
                        playerLives--
                        SoundManager.playError()
                        if (playerLives <= 0) {
                            onGameOverTriggered()
                        } else {
                            player.blinkUntil = now + 2000L
                        }
                    }
                    continue
                }
            }
        }

        if (Random.nextFloat() < 0.02) spawnEnemy()
        for (e in enemies) {
            if (Random.nextFloat() < 0.08) moveTank(e, Random.nextInt(4))
            if (Random.nextFloat() < 0.03) fire(e)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        
        paint.textSize = 30f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
