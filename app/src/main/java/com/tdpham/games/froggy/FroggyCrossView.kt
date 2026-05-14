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
import kotlin.random.Random

class FroggyCrossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "froggy_cross"
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

    private val lanes = mutableListOf<Lane>()
    private var lastUpdate = 0L

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

    override fun pause() { gamePaused = true }
    override fun resume() { gamePaused = false; invalidate() }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        score = 0
        lives = 3
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
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

    private fun moveFrog(dr: Int, dc: Int) {
        if (gamePaused || gameOver) return
        frogR = (frogR + dr).coerceIn(0, rows - 1)
        frogX = (frogX + dc).coerceIn(0f, (cols - 1).toFloat())
        
        if (frogR == 0) {
            score += 1000
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
            ScoreManager.updateHighScore(context, gameKey, score)
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

        if (!gamePaused && !gameOver) update()

        // Draw Entities
        for (lane in lanes) {
            for (e in lane.entities) {
                paint.color = e.color
                canvas.drawRect(e.c * cellW, lane.r * cellH + 5, (e.c + e.length) * cellW, (lane.r + 1) * cellH - 5, paint)
                // Wrap around drawing
                if (e.c + e.length > cols) {
                    canvas.drawRect((e.c - cols) * cellW, lane.r * cellH + 5, (e.c + e.length - cols) * cellW, (lane.r + 1) * cellH - 5, paint)
                }
            }
        }

        // Draw Frog
        paint.color = Color.GREEN
        canvas.drawCircle(frogX * cellW + cellW / 2, frogR * cellH + cellH / 2, cellH * 0.35f, paint)

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score  LIVES: $lives", 20f, 40f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 20f, 40f, paint)

        if (gameOver) {
            drawOverlay(canvas, "GAME OVER", "Press Center to Restart")
        } else if (gamePaused) {
            drawOverlay(canvas, "FROGGY CROSS", "Press Center to Start")
        }

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
        canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 30f
        canvas.drawText(sub, width / 2f, height / 2f + 60f, paint)
    }
}
