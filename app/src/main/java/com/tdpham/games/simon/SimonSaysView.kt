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
    private var celebrationTimer = 0
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "simon_settings"
    private val KEY_SPEED = "speed_index"
    private var speedIndex = 1
    private var hintShowFrames = 0

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

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        speedIndex = prefs.getInt(KEY_SPEED, 1).coerceIn(0, 2)

        score = 0
        best = ScoreManager.getHighScore(context, gameKey, speedIndex)
        gameOver = false
        gamePaused = true
        activeQuadrant = -1
        celebrationTimer = 0
        hintShowFrames = 100
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
        val baseDelay = when(speedIndex) { 0 -> 800L; 2 -> 400L; else -> 600L }
        val flashDuration = (baseDelay * 0.6).toLong()
        var delay = 500L
        for (i in sequence.indices) {
            handler.postDelayed({
                activeQuadrant = sequence[i]
                SoundManager.playClick()
                invalidate()
                handler.postDelayed({
                    activeQuadrant = -1
                    invalidate()
                }, flashDuration)
            }, delay)
            delay += baseDelay
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
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
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
            
            val gap = 15f
            val quadrant = when {
                x in left..(cx - gap) && y in top..(cy - gap) -> 0
                x in (cx + gap)..(left + size) && y in top..(cy - gap) -> 1
                x in (cx + gap)..(left + size) && y in (cy + gap)..(top + size) -> 2
                x in left..(cx - gap) && y in (cy + gap)..(top + size) -> 3
                else -> -1
            }

            if (quadrant != -1) {
                handleInput(quadrant)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun showOptions() {
        pause()
        SimonOptionsDialog.show(context) {
            resetGame()
        }
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
                val oldBest = best
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, speedIndex)
                if (isNewHigh) {
                    best = score
                    currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
                    celebrationManager.startOutcome(
                        width = width.toFloat(),
                        height = height.toFloat(),
                        isWin = true,
                        isNewHigh = isNewHigh,
                        score = score,
                        highScore = oldBest
                    )
                    celebrationTimer = 180 // ~3 seconds at 60fps or similar
                }
                SoundManager.playSuccess()
                handler.postDelayed({ startNextRound() }, 800)
            }
        } else {
            gameOver = true
            gamePaused = true
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, speedIndex)
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isWin = false,
                isNewHigh = isNewHigh,
                score = score,
                highScore = best
            )
            SoundManager.playError()
            onGameOver?.invoke(score)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }
        canvas.drawColor(GamePalette.BACKGROUND)
        
        val size = width.coerceAtMost(height) * 0.8f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val gap = 15f

        // Draw Quadrants
        drawQuadrant(canvas, 0, left, top, cx - gap, cy - gap) // Top-Left (Up)
        drawQuadrant(canvas, 1, cx + gap, top, left + size, cy - gap) // Top-Right (Right)
        drawQuadrant(canvas, 2, cx + gap, cy + gap, left + size, top + size) // Bottom-Right (Down)
        drawQuadrant(canvas, 3, left, cy + gap, cx - gap, top + size) // Bottom-Left (Left)

        // Center hub
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, size * 0.15f, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawCircle(cx, cy, size * 0.15f, paint)
        
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.05f
        paint.typeface = Typeface.MONOSPACE
        canvas.drawText(context.getString(R.string.game_simon), cx, cy + size * 0.02f, paint)

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

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 45f, paint)
            paint.alpha = 255
        }

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

        if (gameOver || (score > 0 && celebrationTimer > 0)) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            if (celebrationTimer > 0) celebrationTimer--
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
