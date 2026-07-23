package com.tdpham.games.dungeon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class DungeonEscapeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "dungeon_escape"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rows = 12
    private val cols = 16
    private var cellS = 0f
    
    private val grid = Array(rows) { IntArray(cols) } // 0:Floor, 1:Wall, 2:Spike, 3:Key, 4:Door
    private val sentinels = mutableListOf<Sentinel>()
    private var playerX = 1
    private var playerY = 1
    private var doorRow = rows - 2
    private var doorCol = cols - 2
    private var hasKey = false
    private var level = 1
    private var startingLevel = 1
    private var score = 0
    private var best = 0
    private val PREFS_NAME = "dungeon_settings"
    private val KEY_START_LEVEL = "start_level"
    private var hintShowFrames = 0
    private var isInitialized = false
    private var gameOver = false
    private var gamePaused = true
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || gamePaused) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }
    private val spikePath = Path()
    private var lastUpdate = 0L
    
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!gamePaused && !gameOver) {
                update()
                invalidate()
                handler.postDelayed(this, 20)
            }
        }
    }

    private var offsetX = 0f
    private var offsetY = 0f
    private val gridRect = RectF()

    data class Sentinel(var x: Float, var y: Float, val dx: Float, val dy: Float, val isVertical: Boolean)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        animHandler.post(animRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        animHandler.removeCallbacks(animRunnable)
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
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

    override fun resetGame() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        startingLevel = prefs.getInt(KEY_START_LEVEL, 1).coerceIn(1, 10)
        level = startingLevel
        score = 0
        best = ScoreManager.getHighScore(context, gameKey, startingLevel)
        celebrationManager.start(0f, 0f)
        setupLevel()
        gamePaused = true
        gameOver = false
        hintShowFrames = 100
        invalidate()
    }

    private fun setupLevel() {
        var attempts = 0
        do {
            generateMap(attempts)
            attempts++
        } while (!isSolvable() && attempts < 100)
    }

    private fun generateMap(attempt: Int = 0) {
        sentinels.clear()
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) 1 else 0
        }
        
        // Enforce distant start and door placement (opposite corners)
        val startCorner = Random.nextInt(4)
        val (startR, startC) = when (startCorner) {
            0 -> 1 to 1                         // Top-Left
            1 -> 1 to cols - 2                  // Top-Right
            2 -> rows - 2 to 1                  // Bottom-Left
            else -> rows - 2 to cols - 2        // Bottom-Right
        }
        playerX = startC
        playerY = startR
        
        val (endR, endC) = when (startCorner) {
            0 -> rows - 2 to cols - 2        // Bottom-Right
            1 -> rows - 2 to 1               // Bottom-Left
            2 -> 1 to cols - 2               // Top-Right
            else -> 1 to 1                   // Top-Left
        }
        doorRow = endR
        doorCol = endC

        grid[doorRow][doorCol] = 4 // Door
        hasKey = false
        
        // Random obstacles (relaxed dynamically as attempts increase)
        val baseObstacleCount = (level * 3 + 10).coerceAtMost(50)
        val obstacleCount = (baseObstacleCount - attempt).coerceAtLeast(0)
        repeat(obstacleCount) {
            val r = Random.nextInt(1, rows - 1)
            val c = Random.nextInt(1, cols - 1)
            if ((r != playerY || c != playerX) && (r != doorRow || c != doorCol)) {
                grid[r][c] = if (Random.nextBoolean()) 1 else 2
            }
        }

        // Add Moving Sentinels (Red Arrows)
        val sentinelCount = (level / 2).coerceAtMost(8)
        repeat(sentinelCount) {
            val isVert = Random.nextBoolean()
            if (isVert) {
                val c = Random.nextInt(2, cols - 2)
                sentinels.add(Sentinel(c.toFloat(), 1f, 0f, 0.1f + (level * 0.02f), true))
            } else {
                val r = Random.nextInt(2, rows - 2)
                sentinels.add(Sentinel(1f, r.toFloat(), 0.1f + (level * 0.02f), 0f, false))
            }
        }

        // Place key (must be on empty floor and not at start or door)
        var kr = Random.nextInt(1, rows - 1)
        var kc = Random.nextInt(1, cols - 1)
        while (grid[kr][kc] != 0 || (kr == playerY && kc == playerX) || (kr == doorRow && kc == doorCol)) {
            kr = Random.nextInt(1, rows - 1)
            kc = Random.nextInt(1, cols - 1)
        }
        grid[kr][kc] = 3
    }

    private fun isSolvable(): Boolean {
        // Find key position
        var kr = -1
        var kc = -1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == 3) { kr = r; kc = c; break }
            }
        }
        if (kr == -1) return false

        // Path from player start position to Key
        val toKey = hasPath(playerX, playerY, kc, kr)
        if (!toKey) return false

        // Path from Key to Door
        return hasPath(kc, kr, doorCol, doorRow)
    }

    private fun hasPath(sx: Int, sy: Int, ex: Int, ey: Int): Boolean {
        val q: java.util.Queue<Pair<Int, Int>> = java.util.LinkedList()
        val visited = Array(rows) { BooleanArray(cols) }
        
        q.add(sy to sx)
        visited[sy][sx] = true
        
        val dr = intArrayOf(0, 0, 1, -1)
        val dc = intArrayOf(1, -1, 0, 0)
        
        while (q.isNotEmpty()) {
            val (r, c) = q.poll()!!
            if (r == ey && c == ex) return true
            
            for (i in 0 until 4) {
                val nr = r + dr[i]
                val nc = c + dc[i]
                // Wall (1) is impassable, Spikes (2) are impassable for pathfinding check to ensure safety
                if (nr in 0 until rows && nc in 0 until cols && !visited[nr][nc] && grid[nr][nc] != 1 && grid[nr][nc] != 2) {
                    visited[nr][nc] = true
                    q.add(nr to nc)
                }
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                resume()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> movePlayer(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> movePlayer(0, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> movePlayer(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> movePlayer(1, 0)
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
        DungeonOptionsDialog.show(context) {
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
                resetGame()
                resume()
                return true
            }
            if (gamePaused) {
                resume()
                return true
            }

            // Quadrant based moves
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) movePlayer(1, 0) else movePlayer(-1, 0)
            } else {
                if (y > centerY) movePlayer(0, 1) else movePlayer(0, -1)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun movePlayer(dx: Int, dy: Int) {
        if (gamePaused || gameOver) return
        val nx = playerX + dx
        val ny = playerY + dy
        
        if (nx in 0 until cols && ny in 0 until rows) {
            val cell = grid[ny][nx]
            if (cell == 1) return // Wall
            
            playerX = nx
            playerY = ny
            
            if (cell == 2) { // Spike
                die()
            } else if (cell == 3) { // Key
                hasKey = true
                grid[ny][nx] = 0
                SoundManager.playScore()
            } else if (cell == 4) { // Door
                if (hasKey) {
                    level++
                    score += 100 * level
                    currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
                    celebrationManager.startOutcome(
                        width = width.toFloat(),
                        height = height.toFloat(),
                        isWin = true,
                        score = score,
                        highScore = best
                    )
                    SoundManager.playSuccess()
                    setupLevel()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

        cellS = (width / cols).coerceAtMost(height / rows).toFloat()
        offsetX = (width - cols * cellS) / 2f
        offsetY = (height - rows * cellS) / 2f

        // update() - Moved to gameLoop

        canvas.drawColor(Color.parseColor("#150E0C")) // Dark stone background

        for (r in 0 until rows) for (c in 0 until cols) {
            val x = offsetX + c * cellS
            val y = offsetY + r * cellS
            val type = grid[r][c]
            
            if (type == 1) { // Wall: bricks
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#3E2723") // Dark brick
                gridRect.set(x + 1, y + 1, x + cellS - 1, y + cellS - 1)
                canvas.drawRect(gridRect, paint)

                // Horizontal brick lines
                paint.color = Color.parseColor("#271510")
                paint.strokeWidth = 3f
                canvas.drawLine(x, y + cellS * 0.33f, x + cellS, y + cellS * 0.33f, paint)
                canvas.drawLine(x, y + cellS * 0.66f, x + cellS, y + cellS * 0.66f, paint)

                // Vertical brick joints
                canvas.drawLine(x + cellS * 0.5f, y, x + cellS * 0.5f, y + cellS * 0.33f, paint)
                canvas.drawLine(x + cellS * 0.25f, y + cellS * 0.33f, x + cellS * 0.25f, y + cellS * 0.66f, paint)
                canvas.drawLine(x + cellS * 0.75f, y + cellS * 0.33f, x + cellS * 0.75f, y + cellS * 0.66f, paint)
                canvas.drawLine(x + cellS * 0.5f, y + cellS * 0.66f, x + cellS * 0.5f, y + cellS, paint)

                // Bevel glow
                paint.color = Color.parseColor("#5D4037")
                canvas.drawLine(x, y, x + cellS, y, paint)
                canvas.drawLine(x, y, x, y + cellS, paint)
            } else if (type == 0) { // Floor
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#211512")
                gridRect.set(x + 1, y + 1, x + cellS - 1, y + cellS - 1)
                canvas.drawRect(gridRect, paint)

                // Stone joints
                paint.color = Color.parseColor("#150E0C")
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                canvas.drawRect(gridRect, paint)

                // Floor cracks
                if ((r * 7 + c * 13) % 11 == 0) {
                    paint.color = Color.parseColor("#1B110E")
                    canvas.drawLine(x + cellS * 0.2f, y + cellS * 0.2f, x + cellS * 0.5f, y + cellS * 0.6f, paint)
                    canvas.drawLine(x + cellS * 0.5f, y + cellS * 0.6f, x + cellS * 0.8f, y + cellS * 0.5f, paint)
                }
            } else if (type == 3) { // Key
                // Draw floor background first
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#211512")
                gridRect.set(x + 1, y + 1, x + cellS - 1, y + cellS - 1)
                canvas.drawRect(gridRect, paint)

                // Draw Golden Key
                paint.color = Color.parseColor("#FFD600")
                paint.style = Paint.Style.FILL
                // Handle ring
                canvas.drawCircle(x + cellS * 0.4f, y + cellS * 0.5f, cellS * 0.15f, paint)
                paint.color = Color.parseColor("#211512")
                canvas.drawCircle(x + cellS * 0.4f, y + cellS * 0.5f, cellS * 0.08f, paint)
                // Stem
                paint.color = Color.parseColor("#FFD600")
                paint.strokeWidth = 4f
                canvas.drawLine(x + cellS * 0.55f, y + cellS * 0.5f, x + cellS * 0.85f, y + cellS * 0.5f, paint)
                // Teeth
                canvas.drawLine(x + cellS * 0.75f, y + cellS * 0.5f, x + cellS * 0.75f, y + cellS * 0.65f, paint)
                canvas.drawLine(x + cellS * 0.82f, y + cellS * 0.5f, x + cellS * 0.82f, y + cellS * 0.65f, paint)

                // Key glow
                paint.setShadowLayer(15f, 0f, 0f, Color.YELLOW)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(x + cellS * 0.4f, y + cellS * 0.5f, cellS * 0.15f, paint)
                paint.clearShadowLayer()
            } else if (type == 4) { // Door
                paint.style = Paint.Style.FILL
                // Wooden planks
                paint.color = if (hasKey) Color.parseColor("#388E3C") else Color.parseColor("#5D4037")
                gridRect.set(x + 1, y + 1, x + cellS - 1, y + cellS - 1)
                canvas.drawRect(gridRect, paint)

                // Vertical planks lines
                paint.color = Color.parseColor("#271510")
                paint.strokeWidth = 3f
                canvas.drawLine(x + cellS * 0.33f, y, x + cellS * 0.33f, y + cellS, paint)
                canvas.drawLine(x + cellS * 0.66f, y, x + cellS * 0.66f, y + cellS, paint)

                // Iron bands
                paint.color = Color.parseColor("#78909C")
                canvas.drawRect(x + 4f, y + cellS * 0.2f, x + cellS - 4f, y + cellS * 0.32f, paint)
                canvas.drawRect(x + 4f, y + cellS * 0.7f, x + cellS - 4f, y + cellS * 0.82f, paint)

                // Golden handle
                paint.color = Color.parseColor("#FFD600")
                canvas.drawCircle(x + cellS * 0.8f, y + cellS * 0.5f, 6f, paint)
            } else if (type == 2) { // Spikes
                // Floor background first
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#211512")
                gridRect.set(x + 1, y + 1, x + cellS - 1, y + cellS - 1)
                canvas.drawRect(gridRect, paint)

                // Draw spikes
                paint.color = Color.parseColor("#B0BEC5")
                spikePath.reset()
                // Left spike
                spikePath.moveTo(x + cellS * 0.15f, y + cellS * 0.85f)
                spikePath.lineTo(x + cellS * 0.35f, y + cellS * 0.15f)
                spikePath.lineTo(x + cellS * 0.55f, y + cellS * 0.85f)
                // Right spike
                spikePath.moveTo(x + cellS * 0.45f, y + cellS * 0.85f)
                spikePath.lineTo(x + cellS * 0.65f, y + cellS * 0.15f)
                spikePath.lineTo(x + cellS * 0.85f, y + cellS * 0.85f)
                spikePath.close()
                canvas.drawPath(spikePath, paint)
            }
        }

        // Draw Sentinels (Moving Spikes)
        paint.color = Color.parseColor("#FF1744")
        for (s in sentinels) {
            val sx = offsetX + s.x * cellS
            val sy = offsetY + s.y * cellS
            drawSpike(canvas, sx, sy)
            // Add glow
            paint.alpha = 50
            canvas.drawCircle(sx + cellS/2, sy + cellS/2, cellS * 0.6f, paint)
            paint.alpha = 255
        }

        // Draw Player with simple animation
        val jumpScale = if (gamePaused || gameOver) 1.0f else (1.0f + 0.05f * Math.sin(System.currentTimeMillis() / 150.0).toFloat())
        val px = offsetX + playerX * cellS + cellS/2
        val py = offsetY + playerY * cellS + cellS/2
        
        val radius = cellS * 0.35f * jumpScale

        // Draw Plume (Red)
        paint.color = Color.parseColor("#D32F2F")
        canvas.drawCircle(px, py - radius * 0.9f, radius * 0.3f, paint)

        // Draw Armor/Body (Iron gray)
        paint.color = Color.parseColor("#455A64")
        canvas.drawCircle(px, py, radius, paint)

        // Draw Helmet Visor (Black)
        paint.color = Color.parseColor("#212121")
        val visorRect = RectF(px - radius * 0.7f, py - radius * 0.4f, px + radius * 0.7f, py)
        canvas.drawRoundRect(visorRect, 4f, 4f, paint)

        // Glowing Eyes
        paint.color = Color.parseColor("#00E676") // Glowing green eyes
        canvas.drawCircle(px - radius * 0.3f, py - radius * 0.2f, radius * 0.1f, paint)
        canvas.drawCircle(px + radius * 0.3f, py - radius * 0.2f, radius * 0.1f, paint)

        // Shield (Silver)
        paint.color = Color.parseColor("#B0BEC5")
        val shieldPath = Path()
        shieldPath.moveTo(px - radius * 0.8f, py + radius * 0.1f)
        shieldPath.lineTo(px - radius * 0.3f, py + radius * 0.1f)
        shieldPath.lineTo(px - radius * 0.55f, py + radius * 0.7f)
        shieldPath.close()
        canvas.drawPath(shieldPath, paint)
        
        // Torch flicker effect (simulated on floor near player)
        if (!gamePaused && !gameOver) {
            paint.color = Color.argb(30, 255, 160, 0)
            val torchSize = cellS * (1.5f + 0.2f * Random.nextFloat())
            canvas.drawCircle(px, py, torchSize, paint)
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.style = Paint.Style.FILL
        val hudY = height * 0.05f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.level_label)}: $level  ${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 45f, paint)
            paint.alpha = 255
        }

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.trapped_label), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_dungeon), context.getString(R.string.start_game))
        }

        celebrationManager.draw(canvas)

        if (!gamePaused && !gameOver) {
            celebrationManager.update()
            invalidate()
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        lastUpdate = now

        for (s in sentinels) {
            s.x += s.dx
            s.y += s.dy
            
            // Continuous loop movement
            if (s.x < 1) s.x = cols - 2f
            if (s.x > cols - 2) s.x = 1f
            if (s.y < 1) s.y = rows - 2f
            if (s.y > rows - 2) s.y = 1f

            // Collision with Player
            val dx = (playerX - s.x)
            val dy = (playerY - s.y)
            if (Math.sqrt((dx * dx + dy * dy).toDouble()) < 0.7) {
                die()
            }
        }
    }

    private fun die() {
        SoundManager.playError()
        gameOver = true
        gamePaused = true
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, startingLevel)
        if (isNewHigh) best = score
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = false,
            isNewHigh = isNewHigh,
            score = score,
            highScore = best
        )
        onGameOver?.invoke(score)
    }

    private fun drawSpike(canvas: Canvas, x: Float, y: Float) {
        spikePath.reset()
        spikePath.moveTo(x + cellS * 0.2f, y + cellS * 0.8f)
        spikePath.lineTo(x + cellS * 0.5f, y + cellS * 0.2f)
        spikePath.lineTo(x + cellS * 0.8f, y + cellS * 0.8f)
        spikePath.close()
        canvas.drawPath(spikePath, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        paint.color = Color.WHITE
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 30f
        val lines = sub.split("\n")
        for (i in lines.indices) {
            canvas.drawText(lines[i], width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
