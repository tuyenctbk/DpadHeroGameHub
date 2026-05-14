package com.tdpham.games.simon

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
import kotlin.random.Random

class SimonSaysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "simon_says"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())

    private val sequence = mutableListOf<Int>() // 0:Up, 1:Right, 2:Down, 3:Left
    private var playerIdx = 0
    private var isShowingSequence = false
    private var activeQuadrant = -1
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var gamePaused = true

    private val colors = arrayOf(
        Color.YELLOW, // Up
        Color.GREEN,  // Right
        Color.RED,    // Down
        Color.BLUE    // Left
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
        gamePaused = false
        startNextRound()
    }

    override fun pause() { gamePaused = true }
    override fun resume() { gamePaused = false; invalidate() }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        sequence.clear()
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        gamePaused = true
        activeQuadrant = -1
        invalidate()
    }

    private fun startNextRound() {
        sequence.add(Random.nextInt(4))
        playerIdx = 0
        showSequence()
    }

    private fun showSequence() {
        isShowingSequence = true
        activeQuadrant = -1
        var delay = 500L
        for (i in sequence.indices) {
            handler.postDelayed({
                activeQuadrant = sequence[i]
                SoundManager.playClick()
                invalidate()
                handler.postDelayed({
                    activeQuadrant = -1
                    invalidate()
                }, 400)
            }, delay)
            delay += 600
        }
        handler.postDelayed({
            isShowingSequence = false
            invalidate()
        }, delay)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (gamePaused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            startGame()
            return true
        }

        if (isShowingSequence || gamePaused) return true

        val input = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_DOWN -> 2
            KeyEvent.KEYCODE_DPAD_LEFT -> 3
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }

        handleInput(input)
        return true
    }

    private fun handleInput(input: Int) {
        if (input == sequence[playerIdx]) {
            activeQuadrant = input
            SoundManager.playClick()
            invalidate()
            handler.postDelayed({
                activeQuadrant = -1
                invalidate()
            }, 200)

            playerIdx++
            if (playerIdx == sequence.size) {
                score++
                if (score > best) {
                    best = score
                    ScoreManager.updateHighScore(context, gameKey, best)
                }
                handler.postDelayed({ startNextRound() }, 800)
            }
        } else {
            gameOver = true
            gamePaused = true
            SoundManager.playError()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        
        val size = width.coerceAtMost(height) * 0.8f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val padding = 20f

        // Draw Quadrants
        drawQuadrant(canvas, 0, left + padding, top, cx - padding, cy - padding) // Up
        drawQuadrant(canvas, 1, cx + padding, top + padding, left + size, cy - padding) // Right
        drawQuadrant(canvas, 2, cx + padding, cy + padding, left + size - padding, top + size) // Down
        drawQuadrant(canvas, 3, left, cy + padding, cx - padding, top + size - padding) // Left

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        paint.textAlign = Paint.Align.CENTER
        if (isShowingSequence) {
            paint.color = Color.YELLOW
            canvas.drawText("WATCH...", width / 2f, 60f, paint)
        } else if (!gamePaused && !gameOver) {
            paint.color = Color.GREEN
            canvas.drawText("YOUR TURN!", width / 2f, 60f, paint)
        }

        if (gameOver) {
            drawOverlay(canvas, "GAME OVER", "Press Center to Restart")
        } else if (gamePaused) {
            drawOverlay(canvas, "SIMON SAYS", "Press Center to Start")
        }
    }

    private fun drawQuadrant(canvas: Canvas, id: Int, l: Float, t: Float, r: Float, b: Float) {
        paint.color = colors[id]
        paint.alpha = if (activeQuadrant == id) 255 else 80
        canvas.drawRoundRect(l, t, r, b, 30f, 30f, paint)
        paint.alpha = 255
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
