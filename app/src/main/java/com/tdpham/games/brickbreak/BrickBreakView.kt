package com.tdpham.games.brickbreak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R

class BrickBreakView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView, Choreographer.FrameCallback {
    override var gameKey: String = "brick_break"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var lastFrameTimeNanos: Long = 0L

    override fun doFrame(frameTimeNanos: Long) {
        if (!isGameOver && !isWin) {
            val dtSec = if (lastFrameTimeNanos == 0L) {
                0f
            } else {
                ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_DELTA_SEC)
            }
            lastFrameTimeNanos = frameTimeNanos
            update(dtSec)
        } else {
            celebrationManager.update()
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private val ball = RectF()
    private val paddle = RectF()
    private val bricks = mutableListOf<RectF>()
    private val brickFlashes = mutableMapOf<RectF, Long>()
    private var frameCount = 0

    private var ballDx = 0f
    private var ballDy = 0f
    private var ballRadius = 0f
    private val pressedKeys = mutableSetOf<Int>()

    private var isBallLaunched = false
    private var isPaused = true
    private var isGameOver = false
    private var isWin = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()

    private var score = 0
    private var highScore = 0
    private var lives = INITIAL_LIVES

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setOnClickListener {
            if (!isFocused) {
                requestFocus()
            }
        }
    }

    override fun startGame() {
        if (isGameOver || isWin) {
            resetGame()
            return
        }
        resume()
        requestFocus()
    }

    override fun pause() {
        isPaused = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun resume() {
        if (!isGameOver && !isWin) {
            isPaused = false
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun resetGame() {
        score = 0
        lives = INITIAL_LIVES
        isGameOver = false
        isWin = false
        isPaused = true
        isBallLaunched = false
        celebrationManager.start(0f, 0f)
        highScore = ScoreManager.getHighScore(context, gameKey)
        brickFlashes.clear()
        initializeBoard()
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().removeFrameCallback(this)
        Choreographer.getInstance().postFrameCallback(this)
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
        } else {
            pressedKeys.clear()
            pause()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initializeBoard()
            highScore = ScoreManager.getHighScore(context, gameKey)
        }
    }

    private fun initializeBoard() {
        if (width == 0 || height == 0) return

        val paddleWidth = width * 0.22f
        val paddleHeight = height * 0.025f
        val paddleTop = height * 0.88f
        paddle.set(
            width / 2f - paddleWidth / 2f,
            paddleTop,
            width / 2f + paddleWidth / 2f,
            paddleTop + paddleHeight
        )

        ballRadius = width * 0.012f
        resetBall()
        createBricks()
    }

    private fun createBricks() {
        bricks.clear()
        val areaTop = height * 0.12f
        val areaHeight = height * 0.33f
        val rowHeight = areaHeight / BRICK_ROWS
        val columnWidth = width / BRICK_COLUMNS.toFloat()

        for (row in 0 until BRICK_ROWS) {
            for (col in 0 until BRICK_COLUMNS) {
                val left = col * columnWidth + BRICK_PADDING
                val top = areaTop + row * rowHeight + BRICK_PADDING
                val right = (col + 1) * columnWidth - BRICK_PADDING
                val bottom = areaTop + (row + 1) * rowHeight - BRICK_PADDING
                bricks.add(RectF(left, top, right, bottom))
            }
        }
    }

    private fun resetBall() {
        val centerX = (paddle.left + paddle.right) / 2f
        val centerY = paddle.top - ballRadius * 1.8f
        ball.set(
            centerX - ballRadius,
            centerY - ballRadius,
            centerX + ballRadius,
            centerY + ballRadius
        )
        ballDx = 0f
        ballDy = 0f
        isBallLaunched = false
    }

    private fun launchBall() {
        if (!isBallLaunched) {
            isBallLaunched = true
            ballDx = width * 0.5f // Screen widths per second
            ballDy = -height * 0.6f // Screen heights per second
            SoundManager.playClick()
        }
    }

    private fun clampPaddleHorizontal() {
        if (paddle.left < 0f) paddle.offset(-paddle.left, 0f)
        if (paddle.right > width) paddle.offset(width - paddle.right, 0f)
    }

    private fun update(dtSec: Float) {
        frameCount++
        // Paddle: speed in fractions of screen width per second (smooth, frame-rate independent).
        var dir = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) dir -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) dir += 1f
        if (dir != 0f) {
            val effectiveDt = when {
                dtSec > 0f -> dtSec
                else -> FIRST_FRAME_DT_SEC
            }
            val distance = dir * width * PADDLE_SPEED_PER_SCREEN_WIDTH_PER_SEC * effectiveDt
            paddle.offset(distance, 0f)
            clampPaddleHorizontal()
        }

        if (isPaused && isBallLaunched) return

        if (!isBallLaunched) {
            val centerX = (paddle.left + paddle.right) / 2f
            ball.offsetTo(centerX - ballRadius, paddle.top - ballRadius * 2f)
            return
        }

        // Move ball based on time
        ball.offset(ballDx * dtSec, ballDy * dtSec)

        if (ball.left <= 0f) {
            ball.offset(-ball.left, 0f)
            ballDx = kotlin.math.abs(ballDx)
            SoundManager.playClick()
        } else if (ball.right >= width) {
            ball.offset(width - ball.right, 0f)
            ballDx = -kotlin.math.abs(ballDx)
            SoundManager.playClick()
        }

        if (ball.top <= 0f) {
            ball.offset(0f, -ball.top)
            ballDy = kotlin.math.abs(ballDy)
            SoundManager.playClick()
        }

        if (RectF.intersects(ball, paddle) && ballDy > 0f) {
            val impact = ((ball.centerX() - paddle.centerX()) / (paddle.width() / 2f)).coerceIn(-1f, 1f)
            ballDy = -kotlin.math.abs(ballDy)
            
            // Influence horizontal speed based on impact point
            val maxHorizontalSpeed = width * 0.8f
            ballDx = impact * maxHorizontalSpeed
            
            // Ensure a minimum vertical speed to avoid too shallow angles
            val minVerticalSpeed = -height * 0.4f
            if (ballDy > minVerticalSpeed) ballDy = minVerticalSpeed
            
            SoundManager.playClick()
        }

        var hitBrick: RectF? = null
        for (brick in bricks) {
            if (RectF.intersects(ball, brick)) {
                hitBrick = brick
                break
            }
        }

        if (hitBrick != null) {
            // Determine which side of the brick was hit
            val overlapLeft = ball.right - hitBrick.left
            val overlapRight = hitBrick.right - ball.left
            val overlapTop = ball.bottom - hitBrick.top
            val overlapBottom = hitBrick.bottom - ball.top

            val minOverlap = minOf(overlapLeft, overlapRight, overlapTop, overlapBottom)

            if (minOverlap == overlapLeft || minOverlap == overlapRight) {
                ballDx = -ballDx
            } else {
                ballDy = -ballDy
            }

            bricks.remove(hitBrick)
            brickFlashes[hitBrick] = System.currentTimeMillis()
            score += 10
            SoundManager.playScore()
            if (bricks.isEmpty()) {
                isWin = true
                isPaused = true
                currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
                val oldHighScore = ScoreManager.getHighScore(context, gameKey)
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
                if (isNewHigh) highScore = score
                celebrationManager.startOutcome(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    isWin = true,
                    isNewHigh = isNewHigh,
                    score = score,
                    highScore = oldHighScore
                )
                SoundManager.playSuccess()
                onGameOver?.invoke(score)
                // Choreographer callback continues for celebration
            }
        }

        if (ball.top > height) {
            lives -= 1
            SoundManager.playError()
            if (lives <= 0) {
                isGameOver = true
                isPaused = true
                val oldHighScore = ScoreManager.getHighScore(context, gameKey)
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
                if (isNewHigh) highScore = score
                celebrationManager.startOutcome(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    isWin = false,
                    isNewHigh = isNewHigh,
                    score = score,
                    highScore = oldHighScore
                )
                onGameOver?.invoke(score)
                // Choreographer callback continues for celebration
            } else {
                resetBall()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Always consume D-Pad and Enter keys to keep focus on the game
        val isDpadKey = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        keyCode == KeyEvent.KEYCODE_DPAD_UP || 
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                        keyCode == KeyEvent.KEYCODE_ENTER

        if (isGameOver || isWin) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                resume()
                requestFocus()
            }
            return isDpadKey || super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressedKeys.add(keyCode)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (isPaused) {
                    resume()
                    launchBall()
                } else if (!isBallLaunched) {
                    launchBall()
                } else {
                    pause()
                }
                true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Consume to prevent focus navigation
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pressedKeys.remove(keyCode)
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_MOVE || event.action == android.view.MotionEvent.ACTION_DOWN) {
            if (isGameOver || isWin) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    performClick()
                    resetGame()
                    resume()
                    requestFocus()
                }
                return true
            }

            if (isPaused) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    performClick()
                    resume()
                    launchBall()
                }
                return true
            }

            if (!isBallLaunched) {
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    performClick()
                    launchBall()
                }
            }

            // Paddle follows mouse/touch
            val targetX = event.x
            paddle.offsetTo(targetX - paddle.width() / 2f, paddle.top)
            clampPaddleHorizontal()
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Always try to keep focus on the game view
        if (!isGameOver && !isWin && !isFocused) {
            requestFocus()
        }

        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.STRIPES, paint = paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2C2C2C")
        canvas.drawRect(0f, height * 0.1f, width.toFloat(), height.toFloat(), paint)

        // Draw Ball
        drawBricks(canvas)

        // Draw brick flashes
        val currentTime = System.currentTimeMillis()
        val iterator = brickFlashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val brick = entry.key
            val startTime = entry.value
            val elapsed = currentTime - startTime
            if (elapsed > 200) {
                iterator.remove()
            } else {
                val alpha = (255 * (1f - elapsed / 200f)).toInt()
                paint.color = Color.WHITE
                paint.alpha = alpha
                canvas.drawRoundRect(brick, 8f, 8f, paint)
            }
        }
        paint.alpha = 255

        paint.color = Color.WHITE
        canvas.drawRoundRect(paddle, 12f, 12f, paint)

        paint.color = Color.parseColor("#FFEB3B")
        canvas.drawOval(ball, paint)

        // Add a small shine to the ball
        paint.color = Color.WHITE
        canvas.drawCircle(ball.centerX() - ballRadius * 0.3f, ball.centerY() - ballRadius * 0.3f, ballRadius * 0.2f, paint)

        drawHud(canvas)

        if (isPaused && !isGameOver && !isWin) {
            drawOverlay(canvas, context.getString(R.string.game_brick_break), context.getString(R.string.launch_hint))
        } else if (isGameOver || isWin) {
            celebrationManager.draw(canvas)
            
            if (isGameOver) {
                drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
            } else {
                drawOverlay(canvas, currentVictoryWord, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.play_again_hint)}")
            }
        }
    }

    private fun drawBricks(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for ((index, brick) in bricks.withIndex()) {
            val row = index / BRICK_COLUMNS
            paint.color = when (row % 5) {
                0 -> Color.parseColor("#EF5350")
                1 -> Color.parseColor("#AB47BC")
                2 -> Color.parseColor("#42A5F5")
                3 -> Color.parseColor("#66BB6A")
                else -> Color.parseColor("#FFA726")
            }
            canvas.drawRoundRect(brick, 8f, 8f, paint)

            // Add a highlight and shadow to the brick
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.WHITE
            paint.alpha = 100
            canvas.drawLine(brick.left + 2, brick.top + 2, brick.right - 2, brick.top + 2, paint)
            canvas.drawLine(brick.left + 2, brick.top + 2, brick.left + 2, brick.bottom - 2, paint)

            paint.color = Color.BLACK
            paint.alpha = 50
            canvas.drawLine(brick.right - 2, brick.top + 2, brick.right - 2, brick.bottom - 2, paint)
            canvas.drawLine(brick.left + 2, brick.bottom - 2, brick.right - 2, brick.bottom - 2, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }
    }

    private fun drawHud(canvas: Canvas) {
        paint.textSize = width / 35f
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, 60f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = GamePalette.WARNING
        canvas.drawText("${context.getString(R.string.lives_label)}: $lives", width / 2f, 60f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.SCORE
        canvas.drawText("${context.getString(R.string.best_label)}: $highScore", width - 40f, 60f, paint)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.style = Paint.Style.FILL
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = width / 14f
        canvas.drawText(title, width / 2f, height / 2f - 20f, paint)

        paint.color = Color.LTGRAY
        paint.textSize = width / 36f
        val lines = subtitle.split("\n")
        var yOffset = 60f
        for (line in lines) {
            canvas.drawText(line, width / 2f, height / 2f + yOffset, paint)
            yOffset += paint.textSize + 10f
        }
    }

    companion object {
        private const val BRICK_ROWS = 6
        private const val BRICK_COLUMNS = 10
        private const val BRICK_PADDING = 6f
        private const val INITIAL_LIVES = 3
        /** Screen widths per second; movement is scaled by frame delta for smooth, consistent speed. */
        private const val PADDLE_SPEED_PER_SCREEN_WIDTH_PER_SEC = 1.5f
        private const val MAX_FRAME_DELTA_SEC = 0.05f
        private const val FIRST_FRAME_DT_SEC = 1f / 60f
    }
}
