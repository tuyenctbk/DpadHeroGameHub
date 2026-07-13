package com.tdpham.games.snake

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import com.tdpham.games.R
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import java.util.*

class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "snake"
    override var onGameOver: ((Int) -> Unit)? = null

    enum class Difficulty(val speed: Long, val wallsLethal: Boolean) {
        LEVEL_1(150L, false),
        LEVEL_2(120L, true),
        LEVEL_3(80L, true)
    }

    private var currentDifficulty = Difficulty.LEVEL_2
    private val PREFS_NAME = "snake_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var hintShowFrames = 0
    private var isInitialized = false

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    
    private val snake = mutableListOf<Point>()
    private var food: Point = Point(5, 5)
    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT
    private var isGameOver = false
    private var gameOverReason = ""
    private var isPaused = false
    private var score = 0
    private var highScore = 0

    private val particles = mutableListOf<GameEnvironment.Particle>()
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val random = Random()

    private val gridSize = 20
    private var cellSize = 0f
    private var animationFrame = 0
    private var headScale = 1.0f
    private var scorePopScale = 1.0f
    private val screenShake = com.tdpham.games.common.ScreenShake()

    private val handler = Handler(Looper.getMainLooper())
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            animationFrame++
            if (isGameOver) {
                celebrationManager.update()
            }
            invalidate()
            animationHandler.postDelayed(this, 50)
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {
                direction = nextDirection
                moveSnake()
                animationFrame++
                invalidate()
            } else {
                animationFrame++
                invalidate()
            }
            if (!isGameOver) {
                // Speed up game slightly as score increases, starting from difficulty base speed
                val delay = (currentDifficulty.speed - (score / 20) * 5).coerceAtLeast(60)
                handler.postDelayed(this, delay)
            }
        }
    }

    private var bgType = GameEnvironment.BackgroundType.CHECKERBOARD
    private var isNight = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        animationHandler.post(animationRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun startGame() {
        if (!isGameOver && isPaused) {
            resume()
        }
    }

    override fun resume() {
        isPaused = false
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }

    override fun pause() {
        isPaused = true
        handler.removeCallbacks(gameLoop)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameLoop)
        animationHandler.removeCallbacks(animationRunnable)
    }

    override fun resetGame() {
        snake.clear()
        celebrationManager.start(0f, 0f)
        
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diffIndex = prefs.getInt(KEY_DIFFICULTY, 1) // Default to Level 2
        currentDifficulty = Difficulty.entries[diffIndex.coerceIn(0, 2)]
        
        snake.add(Point(10, 10))
        snake.add(Point(9, 10))
        snake.add(Point(8, 10))
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        isGameOver = false
        gameOverReason = ""
        isPaused = true // Start paused
        highScore = ScoreManager.getHighScore(context, gameKey, currentDifficulty.ordinal)
        score = 0
        spawnFood()
        particles.clear()
        
        hintShowFrames = 100 // Show hint for ~5 seconds (50ms * 100)
        
        bgType = listOf(GameEnvironment.BackgroundType.CHECKERBOARD, GameEnvironment.BackgroundType.GRID, GameEnvironment.BackgroundType.DOTS).random()
        isNight = Random().nextBoolean()
    }

    fun updateDirection(newDirection: Direction) {
        if (isGameOver) {
            resetGame()
            return
        }

        // Prevent 180 degree turns by checking against the planned direction
        if ((nextDirection == Direction.UP && newDirection != Direction.DOWN) ||
            (nextDirection == Direction.DOWN && newDirection != Direction.UP) ||
            (nextDirection == Direction.LEFT && newDirection != Direction.RIGHT) ||
            (nextDirection == Direction.RIGHT && newDirection != Direction.LEFT)) {
            nextDirection = newDirection
        }
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

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    private fun moveSnake() {
        val head = snake.first()
        var nx = when (direction) {
            Direction.LEFT -> head.x - 1
            Direction.RIGHT -> head.x + 1
            else -> head.x
        }
        var ny = when (direction) {
            Direction.UP -> head.y - 1
            Direction.DOWN -> head.y + 1
            else -> head.y
        }

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
        
        // Check for self-collision (excluding the tail as it will move forward)
        var selfHit = false
        for (i in 0 until snake.size - 1) {
            if (snake[i] == newHead) {
                selfHit = true
                break
            }
        }
        if (selfHit) {
            isGameOver = true
            gameOverReason = context.getString(R.string.bit_self_label)
            handleGameOver()
            return
        }

        snake.add(0, newHead)

        if (newHead == food) {
            score += 10
            headScale = 1.4f
            scorePopScale = 1.3f
            SoundManager.playScore()
            spawnFood()
            spawnBiteParticles(newHead)
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun handleGameOver() {
        SoundManager.playError()
        screenShake.trigger(15, 20f)
        val oldBest = highScore
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentDifficulty.ordinal)
        if (isNewHigh) {
            highScore = score
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            gameOverReason = currentVictoryWord
        }
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
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
        val centerX = head.x.toFloat()
        val centerY = head.y.toFloat()
        repeat(12) {
            val angle = random.nextDouble() * 2.0 * Math.PI
            val speed = random.nextFloat() * 0.5f + 0.2f
            particles.add(GameEnvironment.Particle(
                centerX, 
                centerY, 
                speed, 
                Math.cos(angle).toFloat() * speed,
                random.nextFloat() * 5f + 2f,
                "#FFD700".toColorInt() // Gold particles for eating
            ))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> updateDirection(Direction.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> updateDirection(Direction.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> updateDirection(Direction.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> updateDirection(Direction.RIGHT)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> togglePause()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> showOptions()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
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

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver || isPaused) {
                togglePause()
                return true
            }

            // Mouse/Touch control: Divide screen into 4 quadrants
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) updateDirection(Direction.RIGHT)
                else updateDirection(Direction.LEFT)
            } else {
                if (y > centerY) updateDirection(Direction.DOWN)
                else updateDirection(Direction.UP)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val needsInvalidate = screenShake.apply(canvas)
        super.onDraw(canvas)
        
        if (hintShowFrames > 0) {
            hintShowFrames--
        }
        
        // Increase divisor to gridSize + 8 to provide safe margins for TV screens
        cellSize = width.coerceAtMost(height).toFloat() / (gridSize + 8)
        val offsetX = (width - cellSize * gridSize) / 2
        val offsetY = (height - cellSize * gridSize) / 2 + cellSize // Shift down slightly for score room

        // Draw background
        GameEnvironment.draw(canvas, bgType, GameEnvironment.SceneType.FIELD, isNight = isNight, paint = paint)

        // Draw particles
        paint.style = Paint.Style.FILL
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            paint.color = p.color
            paint.alpha = (p.speed * 500).toInt().coerceIn(0, 255)
            canvas.drawCircle(offsetX + p.x * cellSize + cellSize/2, offsetY + p.y * cellSize + cellSize/2, p.size, paint)
            p.x += p.vx
            p.y += (random.nextFloat() - 0.5f) * 0.1f // slight jitter
            p.speed *= 0.9f // decelerate
            if (p.speed < 0.05f) iterator.remove()
        }
        paint.alpha = 255
        
        if (needsInvalidate) invalidate()

        // Draw grid border
        paint.color = Color.GRAY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(offsetX - 4, offsetY - 4, offsetX + cellSize * gridSize + 4, offsetY + cellSize * gridSize + 4, paint)
        paint.style = Paint.Style.FILL

        // Draw snake
        for (i in snake.indices) {
            val point = snake[i]
            // Head is a different green
            if (i == 0 && isGameOver) {
                paint.color = Color.RED // Highlight head on death
            } else {
                paint.color = if (i == 0) Color.parseColor("#4CAF50") else Color.parseColor("#81C784")
            }
            
            val rectLeft = offsetX + point.x * cellSize + 1
            val rectTop = offsetY + point.y * cellSize + 1
            val rectRight = offsetX + (point.x + 1) * cellSize - 1
            val rectBottom = offsetY + (point.y + 1) * cellSize - 1
            
            if (i == 0 && headScale > 1.01f) {
                canvas.withSave {
                    val cx = rectLeft + cellSize / 2f
                    val cy = rectTop + cellSize / 2f
                    canvas.scale(headScale, headScale, cx, cy)
                    val cornerRadius = cellSize / 2f
                    canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, cornerRadius, cornerRadius, paint)
                    
                    // Draw eyes inside the scaled head
                    paint.color = Color.WHITE
                    val eyeSize = cellSize / 6f
                    val eyeOffset = cellSize / 4f
                    when (direction) {
                        Direction.UP -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                        }
                        Direction.DOWN -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                        Direction.LEFT -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectLeft + eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                        Direction.RIGHT -> {
                            canvas.drawCircle(rectRight - eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                    }
                }
                headScale *= 0.9f
                invalidate()
            } else {
                val cornerRadius = if (i == 0) cellSize / 2f else 8f
                canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, cornerRadius, cornerRadius, paint)

                // Draw eyes for the head (non-scaled)
                if (i == 0) {
                    paint.color = Color.WHITE
                    val eyeSize = cellSize / 6f
                    val eyeOffset = cellSize / 4f
                    
                    // Position eyes based on direction
                    when (direction) {
                        Direction.UP -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                        }
                        Direction.DOWN -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                        Direction.LEFT -> {
                            canvas.drawCircle(rectLeft + eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectLeft + eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                        Direction.RIGHT -> {
                            canvas.drawCircle(rectRight - eyeOffset, rectTop + eyeOffset, eyeSize, paint)
                            canvas.drawCircle(rectRight - eyeOffset, rectBottom - eyeOffset, eyeSize, paint)
                        }
                    }
                }
            }
        }

        // Draw food with pulsing animation
        paint.color = GamePalette.WARNING // Vibrant Red
        val foodCenterX = offsetX + food.x * cellSize + cellSize / 2
        val foodCenterY = offsetY + food.y * cellSize + cellSize / 2
        
        val pulse = (Math.sin(animationFrame * 0.4).toFloat() * 2f)
        val foodRadius = (cellSize / 2 - 2) + pulse
        
        canvas.drawCircle(foodCenterX, foodCenterY, foodRadius, paint)
        // Add a small shine to food
        paint.color = Color.WHITE
        canvas.drawCircle(foodCenterX - cellSize / 6, foodCenterY - cellSize / 6, cellSize / 8, paint)

        if (isGameOver) {
            val restartHint = context.getString(R.string.restart_hint)
            val exitHint = context.getString(R.string.exit_hint)
            
            celebrationManager.draw(canvas)
            invalidate()

            drawOverlay(canvas, gameOverReason, "$restartHint\n$exitHint")
        } else if (isPaused) {
            val resumeHint = context.getString(R.string.resume_hint)
            val exitHint = context.getString(R.string.exit_hint)
            drawOverlay(canvas, context.getString(R.string.paused), "$resumeHint\n$exitHint")
        }

        // Draw Score Header
        val labelSize = cellSize * 1.0f
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_SECONDARY
        paint.style = Paint.Style.FILL
        
        val scoreLabel = context.getString(R.string.score_label) + ": "
        val labelX = Math.round(offsetX).toFloat()
        val labelY = Math.round(offsetY - cellSize * 1.2f).toFloat()
        canvas.drawText(scoreLabel, labelX, labelY, paint)
        val scoreLabelWidth = paint.measureText(scoreLabel)
        
        // Only the score number pops, scaling from its center
        val scoreStr = score.toString()
        val scoreNumX = labelX + scoreLabelWidth
        
        if (scorePopScale > 1.01f) {
            canvas.withSave {
                // Approximate center for scaling
                val pivotX = scoreNumX + (scoreStr.length * paint.textSize * 0.3f)
                val pivotY = labelY - (paint.textSize * 0.4f)
                canvas.scale(scorePopScale, scorePopScale, pivotX, pivotY)
                paint.color = GamePalette.SCORE
                canvas.drawText(scoreStr, scoreNumX, labelY, paint)
            }
            
            scorePopScale *= 0.9f
            invalidate()
        } else {
            paint.color = GamePalette.SCORE
            canvas.drawText(scoreStr, scoreNumX, labelY, paint)
            scorePopScale = 1.0f
        }
        
        paint.textSize = labelSize
        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.TEXT_SECONDARY
        val bestX = Math.round(offsetX + gridSize * cellSize).toFloat()
        canvas.drawText("${context.getString(R.string.best_label)}: $highScore", bestX, labelY, paint)

        // Draw Difficulty Mode
        paint.textSize = labelSize * 0.7f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        canvas.drawText("${context.getString(R.string.level_label)} ${currentDifficulty.ordinal + 1}", width / 2f, labelY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = labelSize * 0.6f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), offsetX, labelY + cellSize * 1.5f, paint)
            paint.alpha = 255
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        // Semi-transparent dimming
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.textAlign = Paint.Align.CENTER
        
        // Title shadow
        paint.color = Color.parseColor("#333333")
        paint.textSize = width / 12f
        canvas.drawText(title, width / 2f + 5, height / 2f - 15, paint)
        
        // Title text
        paint.color = if (title.contains("!")) GamePalette.WARNING else GamePalette.TEXT_PRIMARY
        canvas.drawText(title, width / 2f, height / 2f - 20, paint)
        
        // Subtitle text (Handle multiple lines if present)
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
