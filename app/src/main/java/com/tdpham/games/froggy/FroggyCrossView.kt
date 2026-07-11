package com.tdpham.games.froggy

import android.content.Context
import android.graphics.*
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

class FroggyCrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "froggy_cross"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rows = 11 // Start, 4 Road lanes, Grass, 4 River lanes, End
    private val cols = 13
    private var cellW = 0f
    private var cellH = 0f

    private var frogR = rows - 1
    private var frogX = (cols / 2).toFloat()
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var lives = 3
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()

    private val lanes = mutableListOf<Lane>()
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

    data class Entity(var c: Float, val length: Int, val color: Int, val isLog: Boolean = false)
    data class Lane(val r: Int, val speed: Float, val entities: MutableList<Entity>, val isRiver: Boolean)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
    }

    override fun resetGame() {
        score = 0
        lives = 3
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        celebrationManager.start(0f, 0f)
        resetFrog()
        setupLanes()
        invalidate()
    }

    private fun resetFrog() {
        frogR = rows - 1
        frogX = (cols / 2).toFloat()
    }

    private fun setupLanes() {
        lanes.clear()
        // Road lanes (1 to 4)
        for (r in 6..9) {
            val speed = (Random.nextFloat() * 0.05f + 0.03f) * (if (r % 2 == 0) 1 else -1)
            val entities = mutableListOf<Entity>()
            repeat(3) { i ->
                entities.add(Entity(i * 5f, 2, Color.RED))
            }
            lanes.add(Lane(r, speed, entities, false))
        }
        // River lanes (1 to 4)
        for (r in 1..4) {
            val speed = (Random.nextFloat() * 0.04f + 0.02f) * (if (r % 2 == 0) 1 else -1)
            val entities = mutableListOf<Entity>()
            repeat(3) { i ->
                entities.add(Entity(i * 5f, 3, Color.parseColor("#8D6E63"), true))
            }
            lanes.add(Lane(r, speed, entities, true))
        }
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
            KeyEvent.KEYCODE_DPAD_UP -> moveFrog(-1, 0)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveFrog(1, 0)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveFrog(0, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFrog(0, 1)
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
                if (x > centerX) moveFrog(0, 1) else moveFrog(0, -1)
            } else {
                if (y > centerY) moveFrog(1, 0) else moveFrog(-1, 0)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun moveFrog(dr: Int, dc: Int) {
        if (gamePaused || gameOver) return
        frogR = (frogR + dr).coerceIn(0, rows - 1)
        frogX = (frogX + dc).coerceIn(0f, (cols - 1).toFloat())
        
        if (frogR == 0) {
            score += 1000
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = true, score = score, highScore = best)
            SoundManager.playSuccess()
            resetFrog()
        }
    }

    private fun die() {
        lives--
        SoundManager.playError()
        if (lives <= 0) {
            gameOver = true
            gamePaused = true
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = isNewHigh, score = score, highScore = best)
            onGameOver?.invoke(score)
        } else {
            resetFrog()
        }
    }

    override fun onDraw(canvas: Canvas) {
        cellW = width.toFloat() / cols
        cellH = height.toFloat() / rows

        // Draw Background
        for (r in 0 until rows) {
            paint.color = when (r) {
                0, 5, 10 -> Color.parseColor("#2E7D32") // Grass/Safe
                in 1..4 -> Color.parseColor("#0277BD") // River
                else -> Color.parseColor("#212121") // Road
            }
            canvas.drawRect(0f, r * cellH, width.toFloat(), (r + 1) * cellH, paint)
        }

        // update() - Moved to gameLoop

    // Draw Entities
        for (lane in lanes) {
            for (e in lane.entities) {
                paint.color = e.color
                val r = RectF(e.c * cellW, lane.r * cellH + 5, (e.c + e.length) * cellW, (lane.r + 1) * cellH - 5)
                
                if (lane.isRiver) {
                    // Logs with texture/lines
                    paint.color = Color.parseColor("#5D4037")
                    canvas.drawRoundRect(r, 10f, 10f, paint)
                    paint.color = Color.parseColor("#4E342E")
                    paint.strokeWidth = 2f
                    canvas.drawLine(r.left + 10, r.top + r.height()/2, r.right - 10, r.top + r.height()/2, paint)
                } else {
                    // Cars with simple 3D effect
                    canvas.drawRoundRect(r, 8f, 8f, paint)
                    paint.color = Color.BLACK
                    canvas.drawRect(r.left + r.width()*0.2f, r.top + 5, r.right - r.width()*0.2f, r.bottom - 5, paint)
                    paint.color = Color.YELLOW
                    canvas.drawRect(r.left + 2, r.top + 10, r.left + 10, r.bottom - 10, paint) // Headlights
                }
                
                // Wrap around drawing
                if (e.c + e.length > cols) {
                    val r2 = RectF((e.c - cols) * cellW, lane.r * cellH + 5, (e.c + e.length - cols) * cellW, (lane.r + 1) * cellH - 5)
                    paint.color = e.color
                    if (lane.isRiver) {
                        paint.color = Color.parseColor("#5D4037")
                        canvas.drawRoundRect(r2, 10f, 10f, paint)
                    } else {
                        canvas.drawRoundRect(r2, 8f, 8f, paint)
                    }
                }
            }
        }

        // Draw Frog with simple animation (jumping scale)
        val jumpScale = if (gamePaused || gameOver) 1.0f else (1.0f + 0.1f * Math.sin(System.currentTimeMillis() / 150.0).toFloat())
        val frogRadius = cellH * 0.35f * jumpScale
        val fx = frogX * cellW + cellW / 2
        val fy = frogR * cellH + cellH / 2
        
        paint.color = Color.parseColor("#1B5E20") // Darker green shadow
        canvas.drawCircle(fx, fy + 5, frogRadius, paint)
        
        paint.color = Color.GREEN
        canvas.drawCircle(fx, fy, frogRadius, paint)
        
        // Eyes
        paint.color = Color.WHITE
        canvas.drawCircle(fx - frogRadius * 0.4f, fy - frogRadius * 0.4f, frogRadius * 0.25f, paint)
        canvas.drawCircle(fx + frogRadius * 0.4f, fy - frogRadius * 0.4f, frogRadius * 0.25f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(fx - frogRadius * 0.4f, fy - frogRadius * 0.5f, frogRadius * 0.1f, paint)
        canvas.drawCircle(fx + frogRadius * 0.4f, fy - frogRadius * 0.5f, frogRadius * 0.1f, paint)

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(40f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.lives_label)}: $lives", 20f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 20f, hudY, paint)

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_froggy), context.getString(R.string.start_game))
        }

        celebrationManager.update()
        celebrationManager.draw(canvas)

        if (!gamePaused && !gameOver) invalidate()
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        val dt = (now - lastUpdate).coerceAtMost(50L)
        lastUpdate = now

        var onLog = false
        var logSpeed = 0f
        for (lane in lanes) {
            for (e in lane.entities) {
                e.c += lane.speed
                if (e.c > cols) e.c -= cols
                if (e.c < 0) e.c += cols

                // Collision Check
                if (frogR == lane.r) {
                    val frogPos = frogX
                    val hit = if (e.c + e.length > cols) {
                        frogPos >= e.c || frogPos < (e.c + e.length - cols)
                    } else {
                        frogPos >= e.c && frogPos < (e.c + e.length)
                    }

                    if (lane.isRiver) {
                        if (hit) {
                            onLog = true
                            logSpeed = lane.speed
                        }
                    } else {
                        if (hit) die()
                    }
                }
            }
        }

        if (frogR in 1..4) {
            if (!onLog) {
                die()
            } else {
                // Move frog with log drift
                frogX = (frogX + logSpeed).coerceIn(0f, (cols - 1).toFloat())
            }
        }
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
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
