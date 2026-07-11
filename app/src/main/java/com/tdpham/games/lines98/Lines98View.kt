package com.tdpham.games.lines98

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*

class Lines98View @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "lines98"
    override var onGameOver: ((Int) -> Unit)? = null
    private val gridSize = 9
    private var board = Array(gridSize) { IntArray(gridSize) { 0 } }
    private var selectedX = -1
    private var selectedY = -1
    private var cursorX = 4
    private var cursorY = 4

    private var pulseFactor = 1.0f
    private var pulseDirection = 1
    
    private var score = 0
    private var isGameOver = false
    private var isPaused = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    
    private val ballColors = intArrayOf(
        Color.parseColor("#F44336"), // Red
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#FFEB3B"), // Yellow
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#00BCD4")  // Cyan
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private var nextBalls = mutableListOf<Int>()
    private var nextPositions = mutableListOf<Pair<Int, Int>>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        isPaused = false
        invalidate()
    }

    override fun pause() {
        isPaused = true
        invalidate()
    }

    override fun resume() {
        isPaused = false
        invalidate()
    }

    override fun resetGame() {
        board = Array(gridSize) { IntArray(gridSize) { 0 } }
        score = 0
        isGameOver = false
        celebrationManager.start(0f, 0f)
        selectedX = -1
        selectedY = -1
        nextBalls.clear()
        nextPositions.clear()
        generateNextBalls()
        spawnBalls()
        generateNextBalls()
        invalidate()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    private fun generateNextBalls() {
        nextBalls.clear()
        nextPositions.clear()
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) emptyCells.add(r to c)
            }
        }

        val count = Math.min(emptyCells.size, 3)
        repeat(count) {
            nextBalls.add(random.nextInt(ballColors.size) + 1)
            val idx = random.nextInt(emptyCells.size)
            nextPositions.add(emptyCells.removeAt(idx))
        }
    }

    private fun spawnBalls() {
        if (nextPositions.isEmpty()) return

        for (i in nextPositions.indices) {
            val (r, c) = nextPositions[i]
            if (board[r][c] == 0 && i < nextBalls.size) {
                board[r][c] = nextBalls[i]
                checkLines(r, c)
            }
        }
        
        nextBalls.clear()
        nextPositions.clear()

        // Check if game over after spawning
        var hasEmpty = false
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) {
                    hasEmpty = true
                    break
                }
            }
            if (hasEmpty) break
        }
        if (!hasEmpty) {
            isGameOver = true
            val oldHighScore = ScoreManager.getHighScore(context, gameKey)
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            if (isNewHigh) {
                currentVictoryWord = celebrationManager.getRandomVictoryWord(context, "win_highscore")
            } else {
                currentVictoryWord = ""
            }
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = isNewHigh, score = score, highScore = oldHighScore)
            onGameOver?.invoke(score)
        }
    }

    private fun checkLines(r: Int, c: Int): Boolean {
        val color = board[r][c]
        if (color == 0) return false

        val directions = arrayOf(
            1 to 0,  // Vertical
            0 to 1,  // Horizontal
            1 to 1,  // Diagonal \
            1 to -1  // Diagonal /
        )

        val toRemove = mutableSetOf<Pair<Int, Int>>()

        for ((dr, dc) in directions) {
            val line = mutableSetOf<Pair<Int, Int>>()
            line.add(r to c)
            
            // Check in positive direction
            var nr = r + dr
            var nc = c + dc
            while (nr in 0 until gridSize && nc in 0 until gridSize && board[nr][nc] == color) {
                line.add(nr to nc)
                nr += dr
                nc += dc
            }
            
            // Check in negative direction
            nr = r - dr
            nc = c - dc
            while (nr in 0 until gridSize && nc in 0 until gridSize && board[nr][nc] == color) {
                line.add(nr to nc)
                nr -= dr
                nc -= dc
            }

            if (line.size >= 5) {
                toRemove.addAll(line)
            }
        }

        if (toRemove.isNotEmpty()) {
            for ((rr, cc) in toRemove) {
                board[rr][cc] = 0
            }
            score += toRemove.size * 2
            ScoreManager.updateHighScore(context, gameKey, score)
            SoundManager.playScore()
            return true
        }
        return false
    }

    private fun canMove(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (board[toY][toX] != 0) return false
        
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        val visited = Array(gridSize) { BooleanArray(gridSize) }
        
        queue.add(fromY to fromX)
        visited[fromY][fromX] = true
        
        val dr = intArrayOf(0, 0, 1, -1)
        val dc = intArrayOf(1, -1, 0, 0)
        
        while (queue.isNotEmpty()) {
            val (r, c) = queue.poll()!!
            if (r == toY && c == toX) return true
            
            for (i in 0 until 4) {
                val nr = r + dr[i]
                val nc = c + dc[i]
                if (nr in 0 until gridSize && nc in 0 until gridSize && !visited[nr][nc] && board[nr][nc] == 0) {
                    visited[nr][nc] = true
                    queue.add(nr to nc)
                }
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorY = (cursorY - 1 + gridSize) % gridSize
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorY = (cursorY + 1) % gridSize
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorX = (cursorX - 1 + gridSize) % gridSize
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorX = (cursorX + 1) % gridSize
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                handleSelection()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver) {
                resetGame()
                return true
            }

            // Calculate grid bounds (must match onDraw)
            val cellSize = Math.min(width, height) / (gridSize + 1f)
            val offsetX = (width - cellSize * gridSize) / 2f
            val offsetY = (height - cellSize * gridSize) / 2f
            
            val x = event.x
            val y = event.y

            if (x >= offsetX && x < offsetX + gridSize * cellSize && y >= offsetY && y < offsetY + gridSize * cellSize) {
                val cx = ((x - offsetX) / cellSize).toInt().coerceIn(0, gridSize - 1)
                val ry = ((y - offsetY) / cellSize).toInt().coerceIn(0, gridSize - 1)
                
                cursorX = cx
                cursorY = ry
                handleSelection()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleSelection() {
        if (selectedX == -1) {
            if (board[cursorY][cursorX] != 0) {
                selectedX = cursorX
                selectedY = cursorY
                SoundManager.playClick()
            }
        } else {
            if (selectedX == cursorX && selectedY == cursorY) {
                selectedX = -1
                selectedY = -1
            } else if (board[cursorY][cursorX] != 0) {
                selectedX = cursorX
                selectedY = cursorY
                SoundManager.playClick()
            } else {
                if (canMove(selectedX, selectedY, cursorX, cursorY)) {
                    board[cursorY][cursorX] = board[selectedY][selectedX]
                    board[selectedY][selectedX] = 0
                    if (!checkLines(cursorY, cursorX)) {
                        spawnBalls()
                        generateNextBalls()
                    }
                    selectedX = -1
                    selectedY = -1
                } else {
                    SoundManager.playError()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.SOLID, paint = paint)
        
        // Update pulse animation
        if (selectedX != -1 || !isGameOver) {
            pulseFactor += 0.02f * pulseDirection
            if (pulseFactor > 1.1f) {
                pulseFactor = 1.1f
                pulseDirection = -1
            } else if (pulseFactor < 0.9f) {
                pulseFactor = 0.9f
                pulseDirection = 1
            }
            invalidate()
        }

        val cellSize = Math.min(width, height) / (gridSize + 1f)
        val offsetX = (width - cellSize * gridSize) / 2f
        val offsetY = (height - cellSize * gridSize) / 2f

        // Draw Board Background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1A1A")
        canvas.drawRect(offsetX, offsetY, offsetX + gridSize * cellSize, offsetY + gridSize * cellSize, paint)

        // Draw Grid
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.parseColor("#333333")
        for (i in 0..gridSize) {
            canvas.drawLine(offsetX, offsetY + i * cellSize, offsetX + gridSize * cellSize, offsetY + i * cellSize, paint)
            canvas.drawLine(offsetX + i * cellSize, offsetY, offsetX + i * cellSize, offsetY + gridSize * cellSize, paint)
        }

        // Draw Future Balls (Dots)
        paint.style = Paint.Style.FILL
        for (i in nextPositions.indices) {
            val (r, c) = nextPositions[i]
            if (board[r][c] == 0 && i < nextBalls.size) {
                paint.color = ballColors[nextBalls[i] - 1]
                paint.alpha = 180
                val cx = offsetX + c * cellSize + cellSize / 2
                val cy = offsetY + r * cellSize + cellSize / 2
                canvas.drawCircle(cx, cy, cellSize * 0.15f, paint)
                paint.alpha = 255
            }
        }

        // Draw Balls
        paint.style = Paint.Style.FILL
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val ballColorIdx = board[r][c]
                if (ballColorIdx > 0) {
                    val cx = offsetX + c * cellSize + cellSize / 2
                    val cy = offsetY + r * cellSize + cellSize / 2
                    
                    // Shadow
                    paint.color = Color.BLACK
                    paint.alpha = 50
                    canvas.drawCircle(cx + 4, cy + 4, cellSize * 0.4f, paint)
                    paint.alpha = 255

                    // Ball
                    paint.color = ballColors[ballColorIdx - 1]
                    val drawRadius = if (c == selectedX && r == selectedY) cellSize * 0.4f * pulseFactor else cellSize * 0.4f
                    canvas.drawCircle(cx, cy, drawRadius, paint)

                    // Highlight (3D effect)
                    val gradient = RadialGradient(
                        cx - cellSize * 0.15f, cy - cellSize * 0.15f, cellSize * 0.3f,
                        Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
                    )
                    paint.shader = gradient
                    paint.alpha = 150
                    canvas.drawCircle(cx, cy, drawRadius, paint)
                    paint.shader = null
                    paint.alpha = 255

                    if (c == selectedX && r == selectedY) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        paint.color = Color.WHITE
                        canvas.drawCircle(cx, cy, drawRadius + 4f, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }
        }

        // Draw Cursor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f * pulseFactor
        paint.color = Color.parseColor("#FF4081") // Pinkish
        val pad = 4f * (1f - pulseFactor)
        canvas.drawRect(
            offsetX + cursorX * cellSize + pad,
            offsetY + cursorY * cellSize + pad,
            offsetX + (cursorX + 1) * cellSize - pad,
            offsetY + (cursorY + 1) * cellSize - pad,
            paint
        )

        // Draw Info
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 40f
        val hudY1 = Math.round(80f).toFloat()
        val hudY2 = Math.round(130f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 50f, hudY1, paint)
        canvas.drawText("${context.getString(R.string.high_score_label)}: ${ScoreManager.getHighScore(context, gameKey)}", 50f, hudY2, paint)

        // Next Balls
        canvas.drawText("${context.getString(R.string.next_label)}:", width - 300f, hudY1, paint)
        for (i in nextBalls.indices) {
            paint.color = ballColors[nextBalls[i] - 1]
            canvas.drawCircle(width - 150f + i * 60f, hudY1 - 10f, 20f, paint)
        }

        if (isGameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)
            drawOverlay(canvas, title, "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (isPaused) {
            drawOverlay(canvas, context.getString(R.string.paused), context.getString(R.string.resume_hint))
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        paint.isFakeBoldText = true
        canvas.drawText(title, width / 2f, height / 2f - 40, paint)

        paint.textSize = 40f
        paint.isFakeBoldText = false
        val lines = subtitle.split("\n")
        var yOffset = height / 2f + 40
        for (line in lines) {
            canvas.drawText(line, width / 2f, yOffset, paint)
            yOffset += 50
        }
        paint.textAlign = Paint.Align.LEFT
    }
}
