package com.tdpham.games.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "snake"

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

    private val gridSize = 20
    private var cellSize = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {
                direction = nextDirection
                moveSnake()
                invalidate()
            }
            if (!isGameOver) {
                // Speed up game slightly as score increases
                val delay = (150 - (score / 20) * 5).coerceAtLeast(80).toLong()
                handler.postDelayed(this, delay)
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
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
    }

    override fun resetGame() {
        snake.clear()
        snake.add(Point(10, 10))
        snake.add(Point(9, 10))
        snake.add(Point(8, 10))
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        isGameOver = false
        gameOverReason = ""
        isPaused = true // Start paused
        highScore = ScoreManager.getHighScore(context, gameKey)
        score = 0
        spawnFood()
    }

    fun updateDirection(newDirection: Direction) {
        if (isGameOver) {
            resetGame()
            return
        }

        // Prevent 180 degree turns by checking against current moving direction
        if ((direction == Direction.UP && newDirection != Direction.DOWN) ||
            (direction == Direction.DOWN && newDirection != Direction.UP) ||
            (direction == Direction.LEFT && newDirection != Direction.RIGHT) ||
            (direction == Direction.RIGHT && newDirection != Direction.LEFT)) {
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
        val newHead = when (direction) {
            Direction.UP -> Point(head.x, head.y - 1)
            Direction.DOWN -> Point(head.x, head.y + 1)
            Direction.LEFT -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        if (newHead.x !in 0 until gridSize || newHead.y !in 0 until gridSize) {
            isGameOver = true
            gameOverReason = "HIT THE WALL!"
            handleGameOver()
            return
        }
        
        if (snake.contains(newHead)) {
            isGameOver = true
            gameOverReason = "BIT YOURSELF!"
            handleGameOver()
            return
        }

        snake.add(0, newHead)

        if (newHead == food) {
            score += 10
            SoundManager.playScore()
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun handleGameOver() {
        SoundManager.playError()
        val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
        if (isNewHigh) {
            highScore = score
            gameOverReason = "NEW HIGH SCORE!"
        }
    }

    private fun spawnFood() {
        if (snake.size >= gridSize * gridSize) return
        val random = Random()
        do {
            food = Point(random.nextInt(gridSize), random.nextInt(gridSize))
        } while (snake.contains(food))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                updateDirection(Direction.UP)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                updateDirection(Direction.DOWN)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                updateDirection(Direction.LEFT)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                updateDirection(Direction.RIGHT)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePause()
                true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Increase divisor to gridSize + 8 to provide safe margins for TV screens
        cellSize = width.coerceAtMost(height).toFloat() / (gridSize + 8)
        val offsetX = (width - cellSize * gridSize) / 2
        val offsetY = (height - cellSize * gridSize) / 2 + cellSize // Shift down slightly for score room

        // Draw background
        paint.color = GamePalette.BACKGROUND
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

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
            
            canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 8f, 8f, paint)
        }

        // Draw food
        paint.color = GamePalette.WARNING // Vibrant Red
        val foodCenterX = offsetX + food.x * cellSize + cellSize / 2
        val foodCenterY = offsetY + food.y * cellSize + cellSize / 2
        canvas.drawCircle(foodCenterX, foodCenterY, cellSize / 2 - 2, paint)
        // Add a small shine to food
        paint.color = Color.WHITE
        canvas.drawCircle(foodCenterX - cellSize / 6, foodCenterY - cellSize / 6, cellSize / 8, paint)

        if (isGameOver) {
            drawOverlay(canvas, gameOverReason, "Press Center to Restart\nPress Back to Exit")
        } else if (isPaused) {
            drawOverlay(canvas, "PAUSED", "Press Center to Resume\nPress Back to Exit")
        }

        // Draw Score Header
        paint.textSize = cellSize * 1.0f
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("SCORE: ", offsetX, offsetY - cellSize * 1.2f, paint)
        val scoreWidth = paint.measureText("SCORE: ")
        paint.color = GamePalette.SCORE
        canvas.drawText("$score", offsetX + scoreWidth, offsetY - cellSize * 1.2f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("BEST: $highScore", offsetX + gridSize * cellSize, offsetY - cellSize * 1.2f, paint)
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
        var yOffset = 80f
        for (line in lines) {
            canvas.drawText(line, width / 2f, height / 2f + yOffset, paint)
            yOffset += paint.textSize + 10f
        }
    }

    data class Point(val x: Int, val y: Int)
}
