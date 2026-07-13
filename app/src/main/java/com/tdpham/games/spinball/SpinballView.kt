package com.tdpham.games.spinball

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*
import kotlin.math.*

class SpinballView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "spinball"
    override var onGameOver: ((Int) -> Unit)? = null
    private var isPaused = true
    private var isGameOver = false
    private var score = 0
    private var highScore = 0
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "spinball_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var currentDifficultyIndex = 1
    private var hintShowFrames = 0

    // Physics constants
    private val radius = 300f
    private val ballRadius = 15f
    private val rotationSpeed = 5f
    private var currentRotation = 0f

    // Ball properties
    private var ballX = 0f
    private var ballY = 0f
    private var ballVX = 5f
    private var ballVY = 5f

    // Obstacles and Stars
    private val stars = mutableListOf<PointF>()
    private val spikes = mutableListOf<Float>() // Angles in degrees
    private val random = Random()
    private val spikePath = Path()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isGameOver) {
                update()
                invalidate()
                mainHandler.postDelayed(this, 16)
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        
        // Pre-create spike path
        spikePath.moveTo(radius - 30f, -15f)
        spikePath.lineTo(radius, 0f)
        spikePath.lineTo(radius - 30f, 15f)
        spikePath.close()
    }

    private fun resetBall() {
        ballX = 0f
        ballY = 0f
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val speed = when(currentDifficultyIndex) {
            0 -> 4.5f
            2 -> 8.5f
            else -> 6.5f
        }
        ballVX = cos(angle) * speed
        ballVY = sin(angle) * speed
    }

    private fun spawnItems() {
        stars.clear()
        spikes.clear()
        for (i in 0 until 5) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val dist = random.nextFloat() * (radius - 50f)
            stars.add(PointF(cos(angle) * dist, sin(angle) * dist))
        }
        for (i in 0 until 3) {
            spikes.add(random.nextFloat() * 360f)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(updateRunnable)
    }

    override fun startGame() {
        requestFocus()
        isPaused = false
        isGameOver = false
        score = 0
        celebrationManager.start(0f, 0f)
        resetBall()
        spawnItems()
        mainHandler.removeCallbacks(updateRunnable)
        mainHandler.post(updateRunnable)
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        if (isPaused && !isGameOver) {
            isPaused = false
            mainHandler.removeCallbacks(updateRunnable)
            mainHandler.post(updateRunnable)
        }
    }

    override fun resetGame() {
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficultyIndex = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)

        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        hintShowFrames = 100
        startGame()
        isPaused = true
        invalidate()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    private fun update() {
        ballX += ballVX
        ballY += ballVY

        val distSq = ballX * ballX + ballY * ballY
        if (distSq > (radius - ballRadius).pow(2)) {
            // Collision with boundary
            val angleToBall = Math.toDegrees(atan2(ballY.toDouble(), ballX.toDouble())).toFloat()
            var normalizedAngle = (angleToBall - currentRotation) % 360
            if (normalizedAngle < 0) normalizedAngle += 360

            // Check spikes
            for (spikeAngle in spikes) {
                val diff = abs(normalizedAngle - spikeAngle)
                if (diff < 15 || diff > 345) {
                    gameOver()
                    return
                }
            }

            // Simple bounce physics
            val normalX = ballX / sqrt(distSq)
            val normalY = ballY / sqrt(distSq)
            val dot = ballVX * normalX + ballVY * normalY
            ballVX -= 2 * dot * normalX
            ballVY -= 2 * dot * normalY

            // Keep inside
            val scale = (radius - ballRadius) / sqrt(distSq)
            ballX *= scale
            ballY *= scale
            
            SoundManager.playClick()
        }

        // Star collection
        val iterator = stars.iterator()
        var needsSpawn = false
        while (iterator.hasNext()) {
            val star = iterator.next()
            val dx = ballX - star.x
            val dy = ballY - star.y
            if (dx * dx + dy * dy < (ballRadius + 20f).pow(2)) {
                iterator.remove()
                score += 10
                SoundManager.playScore()
                if (stars.isEmpty()) needsSpawn = true
            }
        }
        if (needsSpawn) spawnItems()
    }

    private fun gameOver() {
        isPaused = true
        isGameOver = true
        SoundManager.playError()
        val finalScore = score
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, finalScore, currentDifficultyIndex)
        if (isNewHigh) {
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
        } else {
            currentVictoryWord = ""
        }
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isNewHigh = isNewHigh,
            score = finalScore,
            highScore = highScore
        )
        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        onGameOver?.invoke(finalScore)
    }

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        // Draw Boundary
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.WHITE
        canvas.drawCircle(0f, 0f, radius, paint)

        // Draw Spikes (Rotating with currentRotation)
        canvas.save()
        canvas.rotate(currentRotation)
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        for (spikeAngle in spikes) {
            canvas.save()
            canvas.rotate(spikeAngle)
            canvas.drawPath(spikePath, paint)
            canvas.restore()
        }
        canvas.restore()

        // Draw Stars
        paint.color = Color.YELLOW
        for (star in stars) {
            canvas.drawCircle(star.x, star.y, 10f, paint)
        }

        // Draw Ball
        paint.color = Color.CYAN
        paint.style = Paint.Style.FILL
        canvas.drawCircle(ballX, ballY, ballRadius, paint)

        // Score
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        val hudY = Math.round(-radius - 40f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.best_label)}: $highScore", 0f, hudY, paint)
        
        paint.color = Color.LTGRAY
        val modeStr = context.getString(when(currentDifficultyIndex) {
            0 -> R.string.spinball_difficulty_1
            2 -> R.string.spinball_difficulty_3
            else -> R.string.spinball_difficulty_2
        })
        canvas.drawText("${context.getString(R.string.mode_label)}: $modeStr", 0f, hudY + 40f, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), -width/2 + 30f, -height/2 + 60f, paint)
            paint.alpha = 255
        }
        canvas.restore()

        if (isGameOver || isPaused) {
            val title = if (isGameOver) (if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)) else context.getString(R.string.paused)
            val sub = if (isGameOver) "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}" else context.getString(R.string.resume_hint)
            
            if (isGameOver) {
                celebrationManager.update()
                celebrationManager.draw(canvas)
                invalidate()
            }
            
            drawOverlay(canvas, title, sub)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 35f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 50f + i * 45f, paint)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (isPaused) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resume()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                currentRotation -= rotationSpeed
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                currentRotation += rotationSpeed
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showOptions() {
        pause()
        SpinballOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
                if (isGameOver) {
                    resetGame()
                    startGame()
                    return true
                }
                if (isPaused) {
                    resume()
                    return true
                }
            }
            
            // Mouse/Touch controls: Rotate shield towards touch angle
            val dx = event.x - width / 2f
            val dy = event.y - height / 2f
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            currentRotation = angle
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
