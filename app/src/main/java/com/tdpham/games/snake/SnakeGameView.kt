package com.tdpham.games.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import com.tdpham.games.R
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.common.DailyRewardManager
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.ScreenShake
import com.tdpham.games.common.SoundManager
import java.util.ArrayDeque
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Performance Canvas-based Game View for Rank 3: Snake.
 * Optimized for low-latency physical D-pad / gamepad response times on Android TV,
 * featuring a non-blocking FIFO input queue, 60 FPS Choreographer render loop,
 * dynamic particles, and score celebration effects.
 */
class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "snake"
    override var onGameOver: ((Int) -> Unit)? = null

    enum class Difficulty(val speedMs: Long, val wallsLethal: Boolean) {
        LEVEL_1(140L, false),
        LEVEL_2(105L, true),
        LEVEL_3(75L, true)
    }

    private var currentDifficulty = Difficulty.LEVEL_2
    private val PREFS_NAME = "snake_settings"
    private val KEY_DIFFICULTY = "difficulty_index"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val snake = mutableListOf<Point>()
    private var food: Point = Point(5, 5)
    private var currentDirection = Direction.RIGHT

    // Non-blocking FIFO input queue for zero-latency D-pad response
    private val inputQueue = ArrayDeque<Direction>(4)

    private var isGameOver = false
    private var gameOverReason = ""
    private var isPaused = false
    private var score = 0
    private var highScore = 0

    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val celebrationManager = CelebrationManager()
    private val screenShake = ScreenShake()
    private val random = Random()

    private val gridSize = 20
    private var cellSize = 0f
    private var animationFrame = 0
    private var headScale = 1.0f
    private var scorePopScale = 1.0f
    private var hintShowFrames = 0
    private var isInitialized = false

    private var bgType = GameEnvironment.BackgroundType.CHECKERBOARD
    private var isNight = false

    // High-performance timing
    private var lastLogicTickTime = 0L
    private var isRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            val nowMs = System.currentTimeMillis()
            val currentStepInterval = (currentDifficulty.speedMs - (score / 20) * 4).coerceAtLeast(50)

            if (!isGameOver && !isPaused) {
                if (nowMs - lastLogicTickTime >= currentStepInterval) {
                    processLogicStep()
                    lastLogicTickTime = nowMs
                }
            }

            animationFrame++
            if (isGameOver) {
                celebrationManager.update()
            }

            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun startGame() {
        requestFocus()
        if (!isGameOver && isPaused) {
            resume()
        }
    }

    override fun resume() {
        requestFocus()
        isPaused = false
        lastLogicTickTime = System.currentTimeMillis()
    }

    override fun pause() {
        isPaused = true
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        snake.clear()
        inputQueue.clear()
        celebrationManager.clear()
        particles.clear()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diffIndex = prefs.getInt(KEY_DIFFICULTY, 1)
        currentDifficulty = Difficulty.entries[diffIndex.coerceIn(0, 2)]

        snake.add(Point(10, 10))
        snake.add(Point(9, 10))
        snake.add(Point(8, 10))

        currentDirection = Direction.RIGHT
        isGameOver = false
        gameOverReason = ""
        isPaused = true // Start in ready state
        score = 0
        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficulty.ordinal)

        spawnFood()
        hintShowFrames = 90
        lastLogicTickTime = System.currentTimeMillis()

        bgType = listOf(
            GameEnvironment.BackgroundType.CHECKERBOARD,
            GameEnvironment.BackgroundType.GRID,
            GameEnvironment.BackgroundType.DOTS
        ).random()
        isNight = random.nextBoolean()

        SoundManager.playProfileSound(SoundManager.SoundProfile.RETRO_ARCADE, SoundManager.GameSoundEvent.START)
    }

    /**
     * Enqueues a new direction with instant validation to ensure zero input lag.
     */
    fun enqueueDirection(newDir: Direction) {
        if (isGameOver) {
            resetGame()
            return
        }

        if (isPaused) {
            isPaused = false
            lastLogicTickTime = System.currentTimeMillis()
        }

        // Determine what direction we are comparing against
        val referenceDir = if (inputQueue.isNotEmpty()) inputQueue.last else currentDirection

        val isOpposite = when (newDir) {
            Direction.UP -> referenceDir == Direction.DOWN
            Direction.DOWN -> referenceDir == Direction.UP
            Direction.LEFT -> referenceDir == Direction.RIGHT
            Direction.RIGHT -> referenceDir == Direction.LEFT
        }

        if (!isOpposite && referenceDir != newDir) {
            if (inputQueue.size < 3) {
                inputQueue.addLast(newDir)
                SoundManager.playSnakeTurn()
                HapticManager.vibrateClick(context)
            }
        }
    }

    private fun processLogicStep() {
        if (inputQueue.isNotEmpty()) {
            currentDirection = inputQueue.removeFirst()
        }

        val head = snake.first()
        var nx = when (currentDirection) {
            Direction.LEFT -> head.x - 1
            Direction.RIGHT -> head.x + 1
            else -> head.x
        }
        var ny = when (currentDirection) {
            Direction.UP -> head.y - 1
            Direction.DOWN -> head.y + 1
            else -> head.y
        }

        // Wall collisions
        if (nx !in 0 until gridSize || ny !in 0 until gridSize) {
            if (currentDifficulty.wallsLethal) {
                isGameOver = true
                gameOverReason = context.getString(R.string.hit_wall_label)
                handleGameOver()
                return
            } else {
                nx = (nx + gridSize) % gridSize
                ny = (ny + gridSize) % gridSize
            }
        }

        val newHead = Point(nx, ny)

        // Self collisions
        for (i in 0 until snake.size - 1) {
            if (snake[i] == newHead) {
                isGameOver = true
                gameOverReason = context.getString(R.string.bit_self_label)
                handleGameOver()
                return
            }
        }

        snake.add(0, newHead)

        // Food consumption
        if (newHead == food) {
            val pts = 10 + (currentDifficulty.ordinal * 5)
            score += pts
            headScale = 1.35f
            scorePopScale = 1.3f
            DailyRewardManager.addCoins(context, 2)
            SoundManager.playSnakeEat()
            HapticManager.vibrateClick(context)
            spawnFood()
            spawnBiteParticles(newHead)
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun handleGameOver() {
        SoundManager.playSnakeDie()
        HapticManager.vibrateExplosion(context)
        screenShake.trigger(16, 22f)

        val oldBest = highScore
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficulty.ordinal)
        if (isNewHigh) {
            highScore = score
            gameOverReason = celebrationManager.getRandomVictoryWord(context, gameKey)
        }

        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = isNewHigh,
            isNewHigh = isNewHigh,
            score = score,
            highScore = oldBest
        )
        onGameOver?.invoke(score)
    }

    private fun spawnFood() {
        if (snake.size >= gridSize * gridSize) return
        do {
            food = Point(random.nextInt(gridSize), random.nextInt(gridSize))
        } while (snake.contains(food))
    }

    private fun spawnBiteParticles(head: Point) {
        val cx = head.x.toFloat()
        val cy = head.y.toFloat()
        repeat(14) {
            val angle = random.nextDouble() * 2.0 * Math.PI
            val speed = random.nextFloat() * 0.6f + 0.2f
            particles.add(
                GameEnvironment.Particle(
                    cx,
                    cy,
                    speed,
                    cos(angle).toFloat() * speed,
                    random.nextFloat() * 5f + 3f,
                    "#FFD700".toColorInt()
                )
            )
        }
    }

    // --- Low-Latency TV Controller Key Handling ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_SPACE ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A
            ) {
                resetGame()
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_I -> {
                enqueueDirection(Direction.UP)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_K -> {
                enqueueDirection(Direction.DOWN)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_J -> {
                enqueueDirection(Direction.LEFT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_L -> {
                enqueueDirection(Direction.RIGHT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A -> {
                togglePause()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun togglePause() {
        if (isGameOver) {
            resetGame()
        } else {
            isPaused = !isPaused
            if (!isPaused) resume()
        }
        invalidate()
    }

    private fun showOptions() {
        pause()
        SnakeOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver || isPaused) {
                togglePause()
                return true
            }

            val cx = width / 2f
            val cy = height / 2f
            val dx = event.x - cx
            val dy = event.y - cy

            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx > 0) enqueueDirection(Direction.RIGHT) else enqueueDirection(Direction.LEFT)
            } else {
                if (dy > 0) enqueueDirection(Direction.DOWN) else enqueueDirection(Direction.UP)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val needsInvalidate = screenShake.apply(canvas)
        super.onDraw(canvas)

        if (hintShowFrames > 0) hintShowFrames--

        // TV Safe Area Margins
        cellSize = width.coerceAtMost(height).toFloat() / (gridSize + 7.5f)
        val offsetX = (width - cellSize * gridSize) / 2f
        val offsetY = (height - cellSize * gridSize) / 2f + (cellSize * 0.8f)

        // 1. Background
        GameEnvironment.draw(canvas, bgType, GameEnvironment.SceneType.FIELD, isNight = isNight, paint = paint)

        // 2. Bite Particles
        paint.style = Paint.Style.FILL
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            paint.color = p.color
            paint.alpha = (p.speed * 450).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                offsetX + p.x * cellSize + cellSize / 2f,
                offsetY + p.y * cellSize + cellSize / 2f,
                p.size,
                paint
            )
            p.x += p.vx
            p.y += (random.nextFloat() - 0.5f) * 0.08f
            p.speed *= 0.88f
            if (p.speed < 0.04f) iterator.remove()
        }
        paint.alpha = 255

        if (needsInvalidate) invalidate()

        // 3. Grid Border
        paint.color = Color.parseColor("#33FFFFFF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(
            offsetX - 2,
            offsetY - 2,
            offsetX + cellSize * gridSize + 2,
            offsetY + cellSize * gridSize + 2,
            paint
        )
        paint.style = Paint.Style.FILL

        // 4. Snake Body and Animated Head
        for (i in snake.indices) {
            val pt = snake[i]
            paint.color = when {
                i == 0 && isGameOver -> Color.RED
                i == 0 -> Color.parseColor("#4CAF50")
                else -> Color.parseColor("#81C784")
            }

            val rLeft = offsetX + pt.x * cellSize + 1f
            val rTop = offsetY + pt.y * cellSize + 1f
            val rRight = offsetX + (pt.x + 1) * cellSize - 1f
            val rBottom = offsetY + (pt.y + 1) * cellSize - 1f

            if (i == 0 && headScale > 1.01f) {
                canvas.withSave {
                    val cx = rLeft + cellSize / 2f
                    val cy = rTop + cellSize / 2f
                    canvas.scale(headScale, headScale, cx, cy)
                    val radius = cellSize / 2f
                    canvas.drawRoundRect(rLeft, rTop, rRight, rBottom, radius, radius, paint)
                    drawSnakeEyes(canvas, rLeft, rTop, rRight, rBottom)
                }
                headScale *= 0.92f
            } else {
                val radius = if (i == 0) cellSize / 2f else 7f
                canvas.drawRoundRect(rLeft, rTop, rRight, rBottom, radius, radius, paint)
                if (i == 0) {
                    drawSnakeEyes(canvas, rLeft, rTop, rRight, rBottom)
                }
            }
        }

        // 5. Pulsing Food
        paint.color = GamePalette.WARNING
        val foodCx = offsetX + food.x * cellSize + cellSize / 2f
        val foodCy = offsetY + food.y * cellSize + cellSize / 2f
        val pulse = (sin(animationFrame * 0.35) * 2.2f).toFloat()
        val foodR = (cellSize / 2f - 2f) + pulse

        canvas.drawCircle(foodCx, foodCy, foodR, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(foodCx - cellSize / 5f, foodCy - cellSize / 5f, cellSize / 7f, paint)

        // 6. Overlays
        if (isGameOver) {
            val restartHint = context.getString(R.string.restart_hint)
            val exitHint = context.getString(R.string.exit_hint)
            celebrationManager.draw(canvas)
            drawOverlay(canvas, gameOverReason, "$restartHint\n$exitHint")
        } else if (isPaused) {
            val resumeHint = context.getString(R.string.resume_hint)
            val exitHint = context.getString(R.string.exit_hint)
            val title = if (score == 0) context.getString(R.string.snake) else context.getString(R.string.paused)
            drawOverlay(canvas, title, "$resumeHint\n$exitHint")
        }

        // 7. Header Stats
        drawHeaderStats(canvas, offsetX, offsetY)
    }

    private fun drawSnakeEyes(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        paint.color = Color.WHITE
        val eyeR = cellSize / 6f
        val offset = cellSize / 4f

        when (currentDirection) {
            Direction.UP -> {
                canvas.drawCircle(left + offset, top + offset, eyeR, paint)
                canvas.drawCircle(right - offset, top + offset, eyeR, paint)
            }
            Direction.DOWN -> {
                canvas.drawCircle(left + offset, bottom - offset, eyeR, paint)
                canvas.drawCircle(right - offset, bottom - offset, eyeR, paint)
            }
            Direction.LEFT -> {
                canvas.drawCircle(left + offset, top + offset, eyeR, paint)
                canvas.drawCircle(left + offset, bottom - offset, eyeR, paint)
            }
            Direction.RIGHT -> {
                canvas.drawCircle(right - offset, top + offset, eyeR, paint)
                canvas.drawCircle(right - offset, bottom - offset, eyeR, paint)
            }
        }
    }

    private fun drawHeaderStats(canvas: Canvas, offsetX: Float, offsetY: Float) {
        val labelSize = cellSize * 1.05f
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_SECONDARY
        paint.style = Paint.Style.FILL

        val scoreLabel = context.getString(R.string.score_label) + ": "
        val labelX = offsetX
        val labelY = offsetY - cellSize * 1.15f
        canvas.drawText(scoreLabel, labelX, labelY, paint)

        val scoreWidth = paint.measureText(scoreLabel)
        val scoreStr = score.toString()
        val scoreNumX = labelX + scoreWidth

        if (scorePopScale > 1.01f) {
            canvas.withSave {
                val pivotX = scoreNumX + (scoreStr.length * paint.textSize * 0.3f)
                val pivotY = labelY - (paint.textSize * 0.4f)
                canvas.scale(scorePopScale, scorePopScale, pivotX, pivotY)
                paint.color = GamePalette.SCORE
                canvas.drawText(scoreStr, scoreNumX, labelY, paint)
            }
            scorePopScale *= 0.92f
        } else {
            paint.color = GamePalette.SCORE
            canvas.drawText(scoreStr, scoreNumX, labelY, paint)
            scorePopScale = 1.0f
        }

        // Best Score Right
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.TEXT_SECONDARY
        val bestX = offsetX + gridSize * cellSize
        canvas.drawText("${context.getString(R.string.best_label)}: $highScore", bestX, labelY, paint)

        // Difficulty Center
        paint.textSize = labelSize * 0.72f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)} ${currentDifficulty.ordinal + 1}", width / 2f, labelY, paint)

        // Hint
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = labelSize * 0.62f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), offsetX, labelY + cellSize * 1.5f, paint)
            paint.alpha = 255
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#333333")
        paint.textSize = width / 12f
        canvas.drawText(title, width / 2f + 5, height / 2f - 15, paint)

        paint.color = if (title.contains("!")) GamePalette.WARNING else GamePalette.TEXT_PRIMARY
        canvas.drawText(title, width / 2f, height / 2f - 20, paint)

        paint.color = Color.LTGRAY
        paint.textSize = width / 35f
        val lines = subtitle.split("\n")
        var yOffset = 60f
        for (line in lines) {
            canvas.drawText(line, width / 2f, height / 2f + yOffset, paint)
            yOffset += paint.textSize + 10f
        }
    }

    data class Point(val x: Int, val y: Int)
}
