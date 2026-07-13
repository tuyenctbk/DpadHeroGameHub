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
    private var hasKey = false
    private var level = 1
    private var score = 0
    private var best = 0
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
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
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

    data class Sentinel(var x: Float, var y: Float, val dx: Float, val dy: Float, val isVertical: Boolean)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animHandler.post(animRunnable)
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
        level = 1
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        celebrationManager.start(0f, 0f)
        setupLevel()
        gamePaused = true
        gameOver = false
        invalidate()
    }

    private fun setupLevel() {
        sentinels.clear()
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) 1 else 0
        }
        
        // Random obstacles
        val obstacleCount = (level * 3 + 10).coerceAtMost(50)
        repeat(obstacleCount) {
            val r = Random.nextInt(1, rows - 1)
            val c = Random.nextInt(1, cols - 1)
            if ((r != 1 || c != 1) && (r != rows - 2 || c != cols - 2)) {
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

        playerX = 1
        playerY = 1
        hasKey = false
        grid[rows - 2][cols - 2] = 4 // Door
        
        // Place key
        var kr = Random.nextInt(1, rows - 1)
        var kc = Random.nextInt(1, cols - 1)
        while (grid[kr][kc] != 0 || (kr == 1 && kc == 1)) {
            kr = Random.nextInt(1, rows - 1)
            kc = Random.nextInt(1, cols - 1)
        }
        grid[kr][kc] = 3
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
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
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
        cellS = (width / cols).coerceAtMost(height / rows).toFloat()
        offsetX = (width - cols * cellS) / 2f
        offsetY = (height - rows * cellS) / 2f

        // update() - Moved to gameLoop

        canvas.drawColor(Color.parseColor("#1A1A1A"))

        for (r in 0 until rows) for (c in 0 until cols) {
            val x = offsetX + c * cellS
            val y = offsetY + r * cellS
            val type = grid[r][c]
            
            paint.style = Paint.Style.FILL
            paint.color = when (type) {
                1 -> Color.parseColor("#424242") // Wall
                2 -> Color.parseColor("#D32F2F") // Spike
                3 -> Color.parseColor("#FFD600") // Key
                4 -> if (hasKey) Color.parseColor("#4CAF50") else Color.parseColor("#5D4037") // Door
                else -> Color.parseColor("#212121") // Floor
            }
            canvas.drawRect(x + 1, y + 1, x + cellS - 1, y + cellS - 1, paint)
            
            // Subtle texture/detail
            if (type == 1) { // Wall bevel
                paint.color = Color.parseColor("#616161")
                canvas.drawRect(x + 2, y + 2, x + cellS - 2, y + 6, paint)
                canvas.drawRect(x + 2, y + 2, x + 6, y + cellS - 2, paint)
            } else if (type == 0) { // Floor detail
                if ((r + c) % 4 == 0) {
                    paint.color = Color.parseColor("#252525")
                    canvas.drawCircle(x + cellS/2, y + cellS/2, 4f, paint)
                }
            } else if (type == 3) { // Key glow
                paint.setShadowLayer(10f, 0f, 0f, Color.YELLOW)
                canvas.drawCircle(x + cellS/2, y + cellS/2, cellS * 0.2f, paint)
                paint.clearShadowLayer()
            }
            
            if (type == 2) { // Draw spike triangles
                paint.color = Color.WHITE
                spikePath.reset()
                spikePath.moveTo(x + cellS * 0.2f, y + cellS * 0.8f)
                spikePath.lineTo(x + cellS * 0.5f, y + cellS * 0.2f)
                spikePath.lineTo(x + cellS * 0.8f, y + cellS * 0.8f)
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
        
        paint.color = Color.CYAN
        paint.setShadowLayer(15f, 0f, 0f, Color.CYAN)
        canvas.drawCircle(px, py, cellS * 0.35f * jumpScale, paint)
        paint.clearShadowLayer()
        
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
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(40f).toFloat()
        canvas.drawText("${context.getString(R.string.level_label)}: $level  ${context.getString(R.string.score_label)}: $score", 20f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 20f, hudY, paint)

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.trapped_label), "${context.getString(R.string.final_score_label)}: $level\n${context.getString(R.string.restart_hint)}")
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
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
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
