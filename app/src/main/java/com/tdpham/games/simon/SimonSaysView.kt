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
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class SimonSaysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "simon_says"
    override var onGameOver: ((Int) -> Unit)? = null
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
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()

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
        handler.removeCallbacksAndMessages(null)
        sequence.clear()
        celebrationManager.start(0f, 0f)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (gameOver) {
                resetGame()
                startGame()
                return true
            }
            if (gamePaused) {
                startGame()
                return true
            }

            if (isShowingSequence) return true

            val cx = width / 2f
            val cy = height / 2f
            val x = event.x
            val y = event.y
            
            val size = width.coerceAtMost(height) * 0.8f
            val left = (width - size) / 2f
            val top = (height - size) / 2f
            val padding = 20f
            
            val quadrant = when {
                x in (left + padding)..(cx - padding) && y in top..(cy - padding) -> 0
                x in (cx + padding)..(left + size) && y in (top + padding)..(cy - padding) -> 1
                x in (cx + padding)..(left + size - padding) && y in (cy + padding)..(top + size) -> 2
                x in left..(cx - padding) && y in (cy + padding)..(top + size - padding) -> 3
                else -> -1
            }

            if (quadrant != -1) {
                handleInput(quadrant)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun handleInput(input: Int) {
        if (playerIdx >= sequence.size || isShowingSequence || gameOver || gamePaused) return

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
                val isNewHigh = if (score > best) {
                    best = score
                    ScoreManager.updateHighScore(context, gameKey, best)
                } else false
                if (isNewHigh) {
                    currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
                    celebrationManager.startOutcome(width.toFloat(), height.toFloat(), true, score, best)
                }
                handler.postDelayed({ startNextRound() }, 800)
            }
        } else {
            gameOver = true
            gamePaused = true
            SoundManager.playError()
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), false, score, best)
            onGameOver?.invoke(score)
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
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(60f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        paint.textAlign = Paint.Align.CENTER
        val centerX = Math.round(width / 2f).toFloat()
        if (isShowingSequence) {
            paint.color = Color.YELLOW
            canvas.drawText(context.getString(R.string.watch_hint), centerX, hudY, paint)
        } else if (!gamePaused && !gameOver) {
            paint.color = Color.GREEN
            canvas.drawText(context.getString(R.string.your_turn_hint), centerX, hudY, paint)
        }

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (gamePaused) {
            drawOverlay(canvas, context.getString(R.string.game_simon), context.getString(R.string.start_game))
        }

        if (gameOver || (score > 0 && currentVictoryWord.isNotEmpty())) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
        }
    }

    private fun drawQuadrant(canvas: Canvas, id: Int, l: Float, t: Float, r: Float, b: Float) {
        val isActive = activeQuadrant == id
        paint.color = colors[id]
        
        if (isActive) {
            paint.alpha = 255
            // Glow effect for active quadrant with pulse
            val pulse = (Math.sin(System.currentTimeMillis() / 100.0).toFloat() * 10f)
            paint.setShadowLayer(40f + pulse, 0f, 0f, colors[id])
            invalidate() // Continuous animation while active
        } else {
            paint.alpha = 80
            paint.clearShadowLayer()
        }
        
        val rect = RectF(l, t, r, b)
        if (isActive) {
            // Subtle scale up for active button
            rect.inset(-5f, -5f)
        }
        
        canvas.drawRoundRect(rect, 30f, 30f, paint)
        paint.clearShadowLayer()
        
        // Simple bevel/highlight for the button
        paint.color = Color.WHITE
        paint.alpha = if (isActive) 100 else 40
        canvas.drawRoundRect(l + 10, t + 10, l + 40, t + 25, 10f, 10f, paint)
        
        paint.alpha = 255
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
