package com.tdpham.games.maze

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

class MazeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "maze"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var stage = 1
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isReviewing = false 
    private var isCorrect = false
    private var correctPath = mutableListOf<Pair<Int, Int>>()

    private var mazeWidth = 9
    private var mazeHeight = 9
    private lateinit var maze: Array<IntArray>
    private var startX = 1
    private var startY = 1

    private val options = mutableListOf<Int>()
    private var correctOption = -1
    private var selectedOptionIdx = 0

    private val emojiCategories = listOf(
        listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼"), // Animals
        listOf("🍎", "🍌", "🍉", "🍇", "🍓", "🍍", "🥝", "🍒"), // Fruits
        listOf("🌸", "🌹", "🌻", "🌼", "🌷", "🌵", "🌿", "🍀"), // Flowers
        listOf("🚗", "🚀", "🚁", "🚲", "🚂", "⛵", "🛸", "🚜"), // Vehicles/Toys
        listOf("⚽", "🏀", "🎾", "🏐", "🎱", "🎮", "🎲", "🧩"), // Games/Toys
        listOf("🍔", "🍕", "🍟", "🍦", "🍩", "🍭", "🍰", "🌮")  // Foods
    )
    private var currentEmojis = mutableListOf<String>()

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
        var success = false
        var attempts = 0
        while (!success && attempts < 100) {
            attempts++
            success = tryGenerateStage()
        }
        
        if (!success) {
            // Fallback: simple path if complex generation fails
            maze = Array(mazeHeight) { IntArray(mazeWidth) { 1 } }
            startX = 1
            startY = mazeHeight - 2
            correctOption = 1
            options.clear()
            options.add(1)
            options.add(3)
            options.add(5)
            options.add(7)
            options.sort()
            forcePath(startX, startY, correctOption, 1)
            for (opt in options) {
                maze[0][opt] = 0
                maze[1][opt] = 0
            }
        }
        
        selectedOptionIdx = 0
        isReviewing = false
        
        // Select 4 random emojis from a random category
        val category = emojiCategories.random()
        currentEmojis.clear()
        currentEmojis.addAll(category.shuffled().take(4))
    }

    private fun countOpenCells(): Int {
        var count = 0
        for (r in 0 until mazeHeight) {
            for (c in 0 until mazeWidth) {
                if (maze[r][c] == 0) count++
            }
        }
        return count
    }

    private fun tryGenerateStage(): Boolean {
        val baseSize = 43 + ((stage - 1) / 2) * 4
        mazeWidth = baseSize * 3
        mazeHeight = (baseSize * 1.5).toInt()
        
        if (mazeWidth % 2 == 0) mazeWidth++
        if (mazeHeight % 2 == 0) mazeHeight++
        
        maze = Array(mazeHeight) { IntArray(mazeWidth) { 1 } }
        
        // Target at the BOTTOM wall
        startY = mazeHeight - 2
        startX = (mazeWidth / 2)
        if (startX % 2 == 0) startX++
        if (startX >= mazeWidth - 1) startX = mazeWidth - 2
        
        options.clear()
        
        // 4 Entrances at the TOP wall
        val possibleEntrances = (1 until mazeWidth - 1 step 2).toMutableList()
        val selectedEntrances = mutableListOf<Int>()
        val minDistance = mazeWidth / 5
        
        var attempts = 0
        while (selectedEntrances.size < 4 && attempts < 100) {
            val candidate = possibleEntrances.random()
            if (selectedEntrances.all { Math.abs(it - candidate) >= minDistance }) {
                selectedEntrances.add(candidate)
            }
            attempts++
        }
        
        if (selectedEntrances.size < 4) {
            selectedEntrances.clear()
            val step = (mazeWidth - 2) / 4
            for (i in 0 until 4) {
                var ent = 1 + i * step
                if (ent % 2 == 0) ent++
                if (ent >= mazeWidth - 1) ent = mazeWidth - 2
                selectedEntrances.add(ent)
            }
        }
        
        correctOption = selectedEntrances.random()
        options.addAll(selectedEntrances)
        options.sort()

        // 1. Generate path for the correct option (Top to Bottom)
        maze[1][correctOption] = 1 
        val maxCorrectPathCells = (mazeWidth * mazeHeight) / 12
        generatePath(correctOption, 1, startX, startY, maxCells = maxCorrectPathCells)
        val correctPathLength = countOpenCells()

        // 2. Generate paths for incorrect options (starting from Top)
        val fakeOptions = options.filter { it != correctOption }.shuffled()
        for (opt in fakeOptions) {
            var pathGenerated = false
            var pathAttempts = 0
            val minFakePathCells = (correctPathLength * 0.9).toInt().coerceAtLeast((mazeWidth * mazeHeight) / 15)
            
            while (!pathGenerated && pathAttempts < 30) {
                pathAttempts++
                val mazeBackup = Array(mazeHeight) { maze[it].copyOf() }
                maze[1][opt] = 0
                val cellsBefore = countOpenCells()
                generatePath(opt, 1, -1, -1, isFake = true)
                val cellsAfter = countOpenCells()
                
                if (cellsAfter - cellsBefore >= minFakePathCells) {
                    pathGenerated = true
                } else {
                    for (i in 0 until mazeHeight) {
                        System.arraycopy(mazeBackup[i], 0, maze[i], 0, mazeWidth)
                    }
                }
            }
            if (!pathGenerated) return false
        }

        for (opt in options) {
            maze[0][opt] = 0 
            maze[1][opt] = 0 
        }
        maze[mazeHeight - 1][startX] = 0 
        
        if (!hasPath(startX, startY, correctOption, 0)) return false
        for (opt in options) {
            if (opt != correctOption && hasPath(startX, startY, opt, 0)) return false
        }
        
        return true
    }

    private fun generatePath(sx: Int, sy: Int, tx: Int, ty: Int, isFake: Boolean = false, maxCells: Int = -1) {
        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(sx to sy)
        maze[sy][sx] = 0
        
        var targetReached = false
        var cellsGenerated = 0
        val actualMaxCells = if (maxCells > 0) maxCells else if (isFake) (mazeWidth * mazeHeight) / 3 else (mazeWidth * mazeHeight) // Increased for fake, added parameter
        
        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.last()
            
            if (!isFake && cx == tx && cy == ty) {
                targetReached = true
                stack.removeAt(stack.size - 1)
                continue
            }

            if (cellsGenerated >= actualMaxCells) {
                stack.removeAt(stack.size - 1)
                continue
            }

            val dirs = mutableListOf(0 to 2, 0 to -2, 2 to 0, -2 to 0).shuffled()
            var foundNext = false
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                
                if (nx in 1 until mazeWidth - 1 && ny in 1 until mazeHeight - 1) {
                    if (maze[ny][nx] == 1) {
                        // Check if neighbors are all walls to keep paths strictly separate
                        var neighbors = 0
                        for ((dx2, dy2) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                            if (maze[ny + dy2][nx + dx2] == 0) neighbors++
                        }
                        
                        if (neighbors == 0) {
                            maze[cy + dy / 2][cx + dx / 2] = 0
                            maze[ny][nx] = 0
                            stack.add(nx to ny)
                            foundNext = true
                            cellsGenerated++
                            break
                        }
                    }
                }
            }
            
            if (!foundNext) {
                stack.removeAt(stack.size - 1)
            }
        }
        
        // Ensure correct path actually reached target if not fake
        if (!isFake && !targetReached) {
            forcePath(sx, sy, tx, ty)
        }
    }

    private fun forcePath(sx: Int, sy: Int, tx: Int, ty: Int) {
        var cx = sx
        var cy = sy
        maze[cy][cx] = 0
        while (cx != tx || cy != ty) {
            if (Random.nextBoolean()) {
                if (cx < tx) cx++ else if (cx > tx) cx--
            } else {
                if (cy < ty) cy++ else if (cy > ty) cy--
            }
            maze[cy][cx] = 0
        }
    }

    private fun calculateCorrectPath() {
        correctPath.clear()
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        queue.add(correctOption to 0)
        val parent = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val visited = Array(mazeHeight) { BooleanArray(mazeWidth) }
        visited[0][correctOption] = true
        
        var found = false
        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.poll()!!
            if (cx == startX && cy == mazeHeight - 1) {
                found = true
                break
            }
            for ((dx, dy) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until mazeWidth && ny in 0 until mazeHeight && 
                    maze[ny][nx] == 0 && !visited[ny][nx]) {
                    visited[ny][nx] = true
                    parent[nx to ny] = cx to cy
                    queue.add(nx to ny)
                }
            }
        }
        
        if (found) {
            var curr: Pair<Int, Int>? = startX to mazeHeight - 1
            while (curr != null) {
                correctPath.add(curr)
                curr = parent[curr]
            }
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
                    score += 10
                    if (score > best) {
                        best = score
                        ScoreManager.updateHighScore(context, gameKey, best)
                    }
                    generateStage()
                } else {
                    gameOver = true
                    onGameOver?.invoke(score)
                }
                invalidate()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selectedOptionIdx = (selectedOptionIdx - 1 + options.size) % options.size
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selectedOptionIdx = (selectedOptionIdx + 1) % options.size
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                isReviewing = true
                isCorrect = (options[selectedOptionIdx] == correctOption)
                calculateCorrectPath()
                invalidate()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
                return true
            }

            if (isReviewing) {
                if (isCorrect) {
                    stage++
                    score += 10
                    if (score > best) {
                        best = score
                        ScoreManager.updateHighScore(context, gameKey, best)
                    }
                    generateStage()
                } else {
                    gameOver = true
                    onGameOver?.invoke(score)
                }
                invalidate()
                return true
            }

            // Options layout (must match onDraw)
            val mazeH = (width - 160f / mazeWidth * mazeWidth).coerceAtMost(height - 120f - 80f - 220f)
            // Wait, let's just use the drawn buttons at the bottom.
            
            val topArea = 120f
            val margin = 80f
            val availableW = width - margin * 2
            val availableH = height - topArea - margin - 220f 
            val cellSize = (availableW / mazeWidth).coerceAtMost(availableH / mazeHeight)
            val actualMazeH = cellSize * mazeHeight
            val top = topArea + (availableH - actualMazeH) / 2f + 20f
            
            val optionsY = top + actualMazeH + 60f
            val optionSpacing = width / 5f
            val startOptionsX = (width - (options.size - 1) * optionSpacing) / 2f
            
            val x = event.x
            val y = event.y
            
            for (i in options.indices) {
                val ox = startOptionsX + i * optionSpacing
                if (x in (ox - 60)..(ox + 60) && y in (optionsY - 60)..(optionsY + 60)) {
                    selectedOptionIdx = i
                    isReviewing = true
                    isCorrect = (options[selectedOptionIdx] == correctOption)
                    calculateCorrectPath()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        
        val margin = 80f
        val topArea = 120f
        val availableW = width - margin * 2
        val availableH = height - topArea - margin - 220f 
        
        val cellSize = (availableW / mazeWidth).coerceAtMost(availableH / mazeHeight)
        val mazeW = cellSize * mazeWidth
        val mazeH = cellSize * mazeHeight
        val left = (width - mazeW) / 2f
        val top = topArea + (availableH - mazeH) / 2f + 20f

        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("STAGE: $stage", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SCORE: $score  BEST: $best", width - 40f, 60f, paint)

        // Draw maze background once
        paint.color = Color.BLACK
        canvas.drawRect(left, top, left + mazeW, top + mazeH, paint)

        // Draw walls simply
        paint.color = Color.DKGRAY
        for (r in 0 until mazeHeight) {
            for (c in 0 until mazeWidth) {
                if (maze[r][c] == 1) {
                    val x = left + c * cellSize
                    val y = top + r * cellSize
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, paint)
                }
            }
        }

        // Highlight correct path if reviewing
        if (isReviewing) {
            paint.color = Color.GREEN
            paint.strokeWidth = (cellSize * 0.4f).coerceAtLeast(6f)
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            
            val path = Path()
            if (correctPath.isNotEmpty()) {
                val first = correctPath.first()
                path.moveTo(left + first.first * cellSize + cellSize / 2, top + first.second * cellSize + cellSize / 2)
                for (i in 1 until correctPath.size) {
                    val p = correctPath[i]
                    path.lineTo(left + p.first * cellSize + cellSize / 2, top + p.second * cellSize + cellSize / 2)
                }
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL // Reset style
        }

        // Target at the bottom center-ish
        paint.color = Color.parseColor("#FFD700") // Gold Trophy Color
        val targetX = left + startX * cellSize + cellSize / 2
        val targetY = top + (mazeHeight - 1) * cellSize + cellSize / 2
        canvas.drawCircle(targetX, targetY, cellSize * 0.6f, paint)
        paint.color = Color.BLACK
        paint.textSize = cellSize * 0.7f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏆", targetX, targetY + cellSize * 0.25f, paint)

        for (i in options.indices) {
            val opt = options[i]
            val x = left + opt * cellSize + cellSize / 2
            val y = top - 15f 
            
            // Draw simple label above exit at the TOP
            paint.color = Color.WHITE
            paint.textSize = cellSize.coerceIn(24f, 40f)
            paint.textAlign = Paint.Align.CENTER
            val emoji = if (i < currentEmojis.size) currentEmojis[i] else ('A' + i).toString()
            canvas.drawText(emoji, x, y, paint)
            
            if (isReviewing) {
                if (opt == correctOption) {
                    paint.color = Color.GREEN
                    canvas.drawCircle(x, y - 25f, cellSize * 0.4f, paint) 
                } else if (i == selectedOptionIdx && !isCorrect) {
                    paint.color = Color.RED
                    canvas.drawCircle(x, y - 25f, cellSize * 0.3f, paint)
                }
            }
        }

        val optionsY = top + mazeH + 60f
        val optionSpacing = width / 5f
        val startOptionsX = (width - (options.size - 1) * optionSpacing) / 2f
        
        for (i in options.indices) {
            val x = startOptionsX + i * optionSpacing
            val isSelected = (i == selectedOptionIdx)
            
            // Outer glow for selected
            if (isSelected) {
                paint.color = Color.argb(120, 255, 255, 0)
                canvas.drawCircle(x, optionsY, 60f, paint)
            }
            
            paint.color = if (isSelected) Color.YELLOW else Color.LTGRAY
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, optionsY, 50f, paint)
            
            // Bevel for the button
            paint.color = Color.argb(100, 255, 255, 255)
            canvas.drawCircle(x - 12, optionsY - 12, 18f, paint)

            paint.color = Color.BLACK
            paint.textSize = 48f
            paint.isFakeBoldText = true
            val emoji = if (i < currentEmojis.size) currentEmojis[i] else ('A' + i).toString()
            canvas.drawText(emoji, x, optionsY + 16f, paint)
            paint.isFakeBoldText = false
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
