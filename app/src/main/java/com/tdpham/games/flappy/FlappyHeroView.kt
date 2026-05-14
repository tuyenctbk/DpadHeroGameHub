package com.tdpham.games.flappy

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

class FlappyHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "flappy_hero"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var birdY = 0f
    private var birdV = 0f
    private val gravity = 0.8f
    private val jump = -15f
    private val birdSize = 40f

    private val pipes = mutableListOf<Pipe>()
    private var pipeSpeed = 8f
    private var pipeSpawnTime = 0L
    private val pipeInterval = 1500L

    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true
    private var lastUpdate = 0L

    data class Pipe(var x: Float, val gapY: Float, val gapH: Float, var passed: Boolean = false)

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
        birdY = height / 2f
        birdV = 0f
        pipes.clear()
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        pipeSpawnTime = 0L
        invalidate()
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

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            birdV = jump
            SoundManager.playClick()
            return true
        }
        
        if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            toggleSound()
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#4FC3F7")) // Sky Blue

        if (!gamePaused && !gameOver) update()

        // Draw Pipes
        paint.color = Color.parseColor("#388E3C") // Pipe Green
        for (pipe in pipes) {
            canvas.drawRect(pipe.x, 0f, pipe.x + 100f, pipe.gapY, paint)
            canvas.drawRect(pipe.x, pipe.gapY + pipe.gapH, pipe.x + 100f, height.toFloat(), paint)
        }

        // Draw Bird
        paint.color = Color.YELLOW
        canvas.drawCircle(150f, birdY, birdSize, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(170f, birdY - 10f, 5f, paint) // Eye

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        if (gameOver) {
            drawOverlay(canvas, "CRASHED!", "Score: $score\nPress Center to Restart")
        } else if (gamePaused) {
            drawOverlay(canvas, "FLAPPY HERO", "Press Center to Flap")
        }

        if (!gamePaused && !gameOver) invalidate()
    }

    private fun update() {
        val now = System.currentTimeMillis()
        if (lastUpdate == 0L) lastUpdate = now
        lastUpdate = now

        // Physics
        birdV += gravity
        birdY += birdV

        if (birdY < 0 || birdY > height) {
            die()
        }

        // Pipes
        if (now - pipeSpawnTime > pipeInterval) {
            val gapH = 300f
            val gapY = Random.nextFloat() * (height - gapH - 200f) + 100f
            pipes.add(Pipe(width.toFloat(), gapY, gapH))
            pipeSpawnTime = now
        }

        val pIter = pipes.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x -= pipeSpeed
            
            if (p.x < -100) {
                pIter.remove()
                continue
            }

            // Collision
            if (150f + birdSize > p.x && 150f - birdSize < p.x + 100f) {
                if (birdY - birdSize < p.gapY || birdY + birdSize > p.gapY + p.gapH) {
                    die()
                }
            }

            // Score
            if (!p.passed && p.x < 150f) {
                p.passed = true
                score++
                SoundManager.playScore()
                if (score > best) {
                    best = score
                    ScoreManager.updateHighScore(context, gameKey, best)
                }
            }
        }
    }

    private fun die() {
        gameOver = true
        gamePaused = true
        SoundManager.playError()
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
