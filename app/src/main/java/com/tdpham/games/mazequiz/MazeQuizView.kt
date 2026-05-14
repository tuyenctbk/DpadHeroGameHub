package com.tdpham.games.mazequiz

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*
import kotlin.random.Random

class MazeQuizView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "maze_quiz"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var stage = 1
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isReviewing = false 
    private var isCorrect = false

    private var mazeWidth = 11
    private var mazeHeight = 11
    private lateinit var maze: Array<IntArray>
    private var startX = 1
    private var startY = 1

    private val options = mutableListOf<Int>()
    private var correctOption = -1
    private var selectedOptionIdx = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() {
        requestFocus()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        stage = 1
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        isReviewing = false
        generateStage()
        invalidate()
    }

    private fun generateStage() {
        // Increase difficulty every 3 stages
        mazeWidth = 11 + (stage / 3) * 2
        mazeHeight = 11 + (stage / 3) * 2
        
        maze = Array(mazeHeight) { IntArray(mazeWidth) { 1 } }
        generateMaze(1, mazeHeight - 2)
        
        startX = 1 + Random.nextInt((mazeWidth - 2) / 2) * 2
        startY = mazeHeight - 2
        
        options.clear()
        val allPossibleExits = (1 until mazeWidth - 1 step 2).toList().shuffled()
        val exitColumns = allPossibleExits.take(4).sorted()
        options.addAll(exitColumns)
        
        // Randomly pick one as correct
        correctOption = options[Random.nextInt(options.size)]
        
        for (opt in options) {
            maze[0][opt] = 0 // Open the exit at the very top wall
            if (opt != correctOption) {
                // BLOCK the path for incorrect options at the final step inside the maze
                maze[1][opt] = 1 
            } else {
                // ENSURE the correct path is open
                maze[1][opt] = 0
            }
        }
        
        selectedOptionIdx = 0
        isReviewing = false
    }

    private fun generateMaze(r: Int, c: Int) {
        maze[r][c] = 0
        val dirs = mutableListOf(0 to 2, 0 to -2, 2 to 0, -2 to 0).shuffled()
        for ((dr, dc) in dirs) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 1 until mazeHeight - 1 && nc in 1 until mazeWidth - 1 && maze[nr][nc] == 1) {
                maze[r + dr / 2][c + dc / 2] = 0
                generateMaze(nr, nc)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isReviewing) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (isCorrect) {
                    stage++
                    generateStage()
                } else {
                    gameOver = true
                }
                invalidate()
                return true
            }
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> selectedOptionIdx = (selectedOptionIdx - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> selectedOptionIdx = (selectedOptionIdx + 1).coerceAtMost(options.size - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> checkSelection()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun checkSelection() {
        val selected = options[selectedOptionIdx]
        isCorrect = hasPath(startX, startY, selected, 0)
        isReviewing = true
        if (isCorrect) {
            score += stage * 100
            if (score > best) {
                best = score
                ScoreManager.updateHighScore(context, gameKey, score)
            }
            SoundManager.playSuccess()
        } else {
            SoundManager.playError()
        }
    }

    private fun hasPath(sx: Int, sy: Int, tx: Int, ty: Int): Boolean {
        val visited = Array(mazeHeight) { BooleanArray(mazeWidth) }
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        queue.add(sx to sy)
        visited[sy][sx] = true
        
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            val (cx, cy) = current
            if (cx == tx && cy == ty) return true
            
            val dirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until mazeWidth && ny in 0 until mazeHeight && 
                    maze[ny][nx] == 0 && !visited[ny][nx]) {
                    visited[ny][nx] = true
                    queue.add(nx to ny)
                }
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        
        val margin = 80f
        val topArea = 120f
        val availableW = width - margin * 2
        val availableH = height - topArea - margin - 150f 
        
        val cellSize = (availableW / mazeWidth).coerceAtMost(availableH / mazeHeight)
        val mazeW = cellSize * mazeWidth
        val mazeH = cellSize * mazeHeight
        val left = (width - mazeW) / 2f
        val top = topArea + (availableH - mazeH) / 2f

        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("STAGE: $stage", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SCORE: $score  BEST: $best", width - 40f, 60f, paint)

        for (r in 0 until mazeHeight) {
            for (c in 0 until mazeWidth) {
                val x = left + c * cellSize
                val y = top + r * cellSize
                if (maze[r][c] == 1) {
                    // Wall with bevel
                    paint.color = Color.DKGRAY
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, paint)
                    paint.color = Color.GRAY
                    canvas.drawRect(x + 2, y + 2, x + cellSize - 2, y + 6, paint)
                    canvas.drawRect(x + 2, y + 2, x + 6, y + cellSize - 2, paint)
                } else {
                    paint.color = Color.BLACK
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, paint)
                }
            }
        }

        // Start point with pulse
        val startPulse = (Math.sin(System.currentTimeMillis() / 200.0).toFloat() * cellSize * 0.05f)
        paint.color = Color.GREEN
        paint.setShadowLayer(15f, 0f, 0f, Color.GREEN)
        canvas.drawCircle(left + startX * cellSize + cellSize / 2, top + startY * cellSize + cellSize / 2, cellSize * 0.3f + startPulse, paint)
        paint.clearShadowLayer()

        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        for (i in options.indices) {
            val opt = options[i]
            val x = left + opt * cellSize + cellSize / 2
            val y = top - 30f
            
            val isSelected = (!isReviewing && i == selectedOptionIdx)
            
            // Draw option marker
            paint.color = if (isSelected) Color.YELLOW else Color.WHITE
            if (isSelected) paint.setShadowLayer(10f, 0f, 0f, Color.YELLOW)
            canvas.drawText(('A' + i).toString(), x, y, paint)
            paint.clearShadowLayer()
            
            if (isReviewing) {
                if (opt == correctOption) {
                    paint.color = Color.GREEN
                    paint.setShadowLayer(10f, 0f, 0f, Color.GREEN)
                    canvas.drawCircle(x, y - 40f, 12f, paint)
                    paint.clearShadowLayer()
                } else if (i == selectedOptionIdx && !isCorrect) {
                    paint.color = Color.RED
                    paint.setShadowLayer(10f, 0f, 0f, Color.RED)
                    canvas.drawCircle(x, y - 40f, 12f, paint)
                    paint.clearShadowLayer()
                }
            }
        }

        val optionsY = top + mazeH + 80f
        val optionSpacing = 150f
        val startOptionsX = (width - (options.size - 1) * optionSpacing) / 2f
        
        for (i in options.indices) {
            val x = startOptionsX + i * optionSpacing
            val isSelected = (i == selectedOptionIdx)
            
            // Outer glow for selected
            if (isSelected) {
                paint.color = Color.argb(100, 255, 255, 0)
                canvas.drawCircle(x, optionsY, 50f, paint)
            }
            
            paint.color = if (isSelected) Color.YELLOW else Color.LTGRAY
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, optionsY, 40f, paint)
            
            // Bevel for the button
            paint.color = Color.argb(80, 255, 255, 255)
            canvas.drawCircle(x - 10, optionsY - 10, 15f, paint)

            paint.color = Color.BLACK
            paint.textSize = 36f
            canvas.drawText(('A' + i).toString(), x, optionsY + 12f, paint)
        }

        if (isReviewing) {
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRect(0f, height / 2f - 100f, width.toFloat(), height / 2f + 100f, paint)
            paint.color = if (isCorrect) Color.GREEN else Color.RED
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (isCorrect) "CORRECT!" else "WRONG PATH!", width / 2f, height / 2f, paint)
            paint.textSize = 30f
            paint.color = Color.WHITE
            canvas.drawText("Press Center to ${if (isCorrect) "Next Stage" else "Finish"}", width / 2f, height / 2f + 60f, paint)
        }

        if (gameOver) {
            paint.color = GamePalette.OVERLAY
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 80f
            canvas.drawText("GAME OVER", width / 2f, height / 2f, paint)
            paint.textSize = 40f
            canvas.drawText("Final Score: $score", width / 2f, height / 2f + 80f, paint)
            paint.textSize = 30f
            canvas.drawText("Press Center to Restart", width / 2f, height / 2f + 140f, paint)
        }
    }
}
