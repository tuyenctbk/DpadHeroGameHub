package com.tdpham.games.minesweeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
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

class MinesweeperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "minesweeper"
    override var onGameOver: ((Int) -> Unit)? = null

    private val rows = 10
    private val cols = 10
    private val minesCount = 12
    private var cellSize = 0f

    private val grid = Array(rows) { Array(cols) { Cell() } }
    private var cursorX = 0
    private var cursorY = 0
    private var isGameOver = false
    private var isWin = false
    private var isFirstClick = true
    private var totalWins = 0
    private var lastBoardConfig = ""

    private val celebrationManager = CelebrationManager()
    private var currentVictoryWord = ""
    private val random = Random()
    
    private val pressedKeys = mutableSetOf<Int>()
    private val moveHandler = Handler(Looper.getMainLooper())
    private val moveRunnable = object : Runnable {
        override fun run() {
            if (pressedKeys.isNotEmpty() && !isGameOver && !isWin) {
                var moved = false
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP) && cursorY > 0) { cursorY--; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN) && cursorY < rows - 1) { cursorY++; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT) && cursorX > 0) { cursorX--; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT) && cursorX < cols - 1) { cursorX++; moved = true }
                
                if (moved) {
                    invalidate()
                    moveHandler.postDelayed(this, 150)
                }
            }
        }
    }

    private val revealQueue: Queue<Pair<Int, Int>> = LinkedList()
    private val revealHandler = Handler(Looper.getMainLooper())
    private var isProcessingQueue = false
    
    private var animationFrame = 0
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            animationFrame++
            if (isWin || isGameOver) celebrationManager.update()
            invalidate()
            animationHandler.postDelayed(this, 50)
        }
    }

    private val processQueueRunnable = object : Runnable {
        override fun run() {
            if (revealQueue.isEmpty() || isGameOver || isWin) {
                isProcessingQueue = false
                revealQueue.clear()
                return
            }
            
            val (r, c) = revealQueue.poll()!!
            if (!grid[r][c].isRevealed && !grid[r][c].isFlagged) {
                grid[r][c].isRevealed = true
                SoundManager.playClick()
                invalidate()
                
                if (grid[r][c].neighborMines == 0) {
                    enqueueNeighbors(r, c)
                }
                checkWin()
            }
            
            revealHandler.postDelayed(this, 30)
        }
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setupGame()
        animationHandler.post(animationRunnable)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {
        revealHandler.removeCallbacks(processQueueRunnable)
    }

    override fun resume() {
        if (isProcessingQueue && !isGameOver && !isWin) {
            revealHandler.post(processQueueRunnable)
        }
    }

    override fun resetGame() {
        setupGame()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        revealHandler.removeCallbacks(processQueueRunnable)
        animationHandler.removeCallbacks(animationRunnable)
        moveHandler.removeCallbacks(moveRunnable)
    }

    private fun setupGame() {
        revealHandler.removeCallbacks(processQueueRunnable)
        revealQueue.clear()
        isProcessingQueue = false
        totalWins = ScoreManager.getHighScore(context, gameKey)
        
        var attempts = 0
        var currentConfig = ""
        
        do {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    grid[r][c] = Cell()
                }
            }
            
            var placedMines = 0
            while (placedMines < minesCount) {
                val r = random.nextInt(rows)
                val c = random.nextInt(cols)
                if (!grid[r][c].isMine) {
                    grid[r][c].isMine = true
                    placedMines++
                }
            }
            
            val configList = mutableListOf<String>()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (grid[r][c].isMine) {
                        configList.add("$r,$c")
                    }
                }
            }
            currentConfig = configList.sorted().joinToString(";")
            attempts++
        } while (currentConfig == lastBoardConfig && attempts < 10)
        
        lastBoardConfig = currentConfig
        
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!grid[r][c].isMine) {
                    grid[r][c].neighborMines = countMinesAround(r, c)
                }
            }
        }
        
        isGameOver = false
        isWin = false
        isFirstClick = true
        cursorX = 0
        cursorY = 0
        invalidate()
    }

    private fun countMinesAround(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc].isMine) {
                    count++
                }
            }
        }
        return count
    }

    private fun revealAllMines(hitR: Int, hitC: Int) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isMine) {
                    grid[r][c].isRevealed = true
                    if (r == hitR && c == hitC) grid[r][c].isExploded = true
                } else if (grid[r][c].isFlagged) {
                    grid[r][c].isBadFlag = true
                }
            }
        }
    }

    private fun revealCell(r: Int, c: Int) {
        if (r !in 0 until rows || c !in 0 until cols) return
        val cell = grid[r][c]
        
        if (cell.isRevealed) {
            if (cell.neighborMines > 0) {
                chordCell(r, c)
            }
            return
        }

        if (cell.isFlagged) return
        
        if (isFirstClick) {
            isFirstClick = false
            if (grid[r][c].isMine) moveMine(r, c)
        }

        if (grid[r][c].isMine) {
            isGameOver = true
            SoundManager.playError()
            revealAllMines(r, c)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = false, score = 0, highScore = 100)
            onGameOver?.invoke(totalWins)
            invalidate()
            return
        }

        revealQueue.add(Pair(r, c))
        if (!isProcessingQueue) {
            isProcessingQueue = true
            revealHandler.post(processQueueRunnable)
        }
    }

    private fun enqueueNeighbors(r: Int, c: Int) {
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && !grid[nr][nc].isRevealed && !grid[nr][nc].isFlagged) {
                    if (!revealQueue.any { it.first == nr && it.second == nc }) {
                        revealQueue.add(Pair(nr, nc))
                    }
                }
            }
        }
    }

    private fun moveMine(r: Int, c: Int) {
        grid[r][c].isMine = false
        val random = Random()
        var moved = false
        while (!moved) {
            val nr = random.nextInt(rows)
            val nc = random.nextInt(cols)
            if (!grid[nr][nc].isMine && (nr != r || nc != c)) {
                grid[nr][nc].isMine = true
                moved = true
            }
        }
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (!grid[row][col].isMine) {
                    grid[row][col].neighborMines = countMinesAround(row, col)
                }
            }
        }
    }

    private fun countFlagsAround(r: Int, c: Int): Int {
        var count = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols && grid[nr][nc].isFlagged) {
                    count++
                }
            }
        }
        return count
    }

    private fun chordCell(r: Int, c: Int) {
        if (countFlagsAround(r, c) == grid[r][c].neighborMines) {
            var anyRevealed = false
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols && !grid[nr][nc].isRevealed && !grid[nr][nc].isFlagged) {
                        revealCell(nr, nc)
                        anyRevealed = true
                    }
                }
            }
            if (anyRevealed) SoundManager.playClick()
        } else {
            // Visual feedback that chord is not ready? Maybe shake or sound?
            // Standard minesweeper usually does nothing or a minor click.
        }
    }

    private fun checkWin() {
        var revealedCount = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isRevealed && !grid[r][c].isMine) revealedCount++
            }
        }
        if (revealedCount == (rows * cols - minesCount)) {
            isWin = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            val oldWins = totalWins
            totalWins++
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, totalWins)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = true, isNewHigh = isNewHigh, score = totalWins, highScore = oldWins)
            SoundManager.playSuccess()
            onGameOver?.invoke(totalWins)
            invalidate()
        }
    }

    private fun toggleFlag(r: Int, c: Int) {
        if (!grid[r][c].isRevealed && !isGameOver && !isWin) {
            grid[r][c].isFlagged = !grid[r][c].isFlagged
            SoundManager.playFlag()
            invalidate()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver || isWin) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                setupGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (pressedKeys.isEmpty()) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> if (cursorY > 0) cursorY--
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (cursorY < rows - 1) cursorY++
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (cursorX > 0) cursorX--
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (cursorX < cols - 1) cursorX++
                    }
                    invalidate()
                    moveHandler.removeCallbacks(moveRunnable)
                    moveHandler.postDelayed(moveRunnable, 400)
                }
                pressedKeys.add(keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                revealCell(cursorY, cursorX)
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                toggleFlag(cursorY, cursorX)
                return true
            }
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
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
            if (isGameOver || isWin) {
                setupGame()
                return true
            }

            cellSize = width.coerceAtMost(height).toFloat() / (rows + 5)
            val offsetX = (width - cellSize * cols) / 2
            val offsetY = (height - cellSize * rows) / 2 + cellSize * 1.5f
            
            val x = event.x
            val y = event.y
            
            if (x >= offsetX && x < offsetX + cols * cellSize && y >= offsetY && y < offsetY + rows * cellSize) {
                val c = ((x - offsetX) / cellSize).toInt().coerceIn(0, cols - 1)
                val r = ((y - offsetY) / cellSize).toInt().coerceIn(0, rows - 1)
                
                // If clicking same cell that was focused, reveal it. Otherwise focus it.
                if (r == cursorY && c == cursorX) {
                    // For mouse, we can differentiate with buttons if we want, 
                    // but for simplicity: Reveal on click.
                    revealCell(r, c)
                } else {
                    cursorX = c
                    cursorY = r
                    SoundManager.playClick()
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        if (pressedKeys.isEmpty()) {
            moveHandler.removeCallbacks(moveRunnable)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun getFlaggedCount(): Int {
        var count = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isFlagged) count++
            }
        }
        return count
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellSize = width.coerceAtMost(height).toFloat() / (rows + 5)
        val offsetX = (width - cellSize * cols) / 2
        val offsetY = (height - cellSize * rows) / 2 + cellSize * 1.5f

        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.GRID, paint = paint)

        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val headerY = Math.round(offsetY - cellSize * 1.0f).toFloat()
        paint.textSize = cellSize * 0.55f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("${context.getString(R.string.mines_label)}: $minesCount", Math.round(offsetX).toFloat(), headerY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("${context.getString(R.string.wins_label)}: $totalWins", Math.round(offsetX + cols * cellSize).toFloat(), headerY, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.color = GamePalette.SCORE
        canvas.drawText("${context.getString(R.string.flags_label)}: ${getFlaggedCount()}", Math.round(width / 2f).toFloat(), headerY, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = grid[r][c]
                val rectLeft = offsetX + c * cellSize
                val rectTop = offsetY + r * cellSize
                val rectRight = rectLeft + cellSize
                val rectBottom = rectTop + cellSize

                if (cell.isRevealed) {
                    paint.color = Color.LTGRAY
                } else {
                    paint.color = Color.parseColor("#424242")
                }
                paint.style = Paint.Style.FILL
                
                val margin = if (cell.isRevealed) 1f else 2f
                canvas.drawRect(rectLeft + margin, rectTop + margin, rectRight - margin, rectBottom - margin, paint)
                
                if (!cell.isRevealed) {
                    // Constant stroke for uniform 3D bevel look
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f 
                    
                    paint.color = Color.parseColor("#616161")
                    canvas.drawLine(rectLeft + 2, rectTop + 2, rectRight - 2, rectTop + 2, paint)
                    canvas.drawLine(rectLeft + 2, rectTop + 2, rectLeft + 2, rectBottom - 2, paint)
                    
                    paint.color = Color.parseColor("#212121")
                    canvas.drawLine(rectRight - 2, rectTop + 2, rectRight - 2, rectBottom - 2, paint)
                    canvas.drawLine(rectLeft + 2, rectBottom - 2, rectRight - 2, rectBottom - 2, paint)
                }

                if (cell.isRevealed) {
                    if (cell.isMine) {
                        paint.color = if (cell.isExploded) Color.RED else Color.BLACK
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(rectLeft + 2, rectTop + 2, rectRight - 2, rectBottom - 2, paint)
                        
                        paint.color = if (cell.isExploded) Color.WHITE else Color.RED
                        canvas.drawCircle(rectLeft + cellSize / 2, rectTop + cellSize / 2, cellSize / 3, paint)
                    } else if (cell.neighborMines > 0) {
                        paint.color = getNumberColor(cell.neighborMines)
                        paint.textSize = cellSize * 0.7f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(cell.neighborMines.toString(), rectLeft + cellSize / 2, rectTop + cellSize * 0.75f, paint)
                    }
                } else if (cell.isFlagged) {
                    if (isGameOver && !cell.isMine) {
                        paint.color = Color.LTGRAY
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(rectLeft + 2, rectTop + 2, rectRight - 2, rectBottom - 2, paint)
                        paint.color = Color.BLACK
                        canvas.drawCircle(rectLeft + cellSize / 2, rectTop + cellSize / 2, cellSize / 3, paint)
                        paint.color = Color.RED
                        paint.strokeWidth = 3f
                        canvas.drawLine(rectLeft + 5, rectTop + 5, rectRight - 5, rectBottom - 5, paint)
                        canvas.drawLine(rectRight - 5, rectTop + 5, rectLeft + 5, rectBottom - 5, paint)
                    } else {
                        paint.color = Color.RED
                        paint.textSize = cellSize * 0.6f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("🚩", rectLeft + cellSize / 2, rectTop + cellSize * 0.72f, paint)
                    }
                }

                if (r == cursorY && c == cursorX && !isGameOver && !isWin) {
                    val pulse = (Math.sin(animationFrame * 0.3).toFloat() * 2f)
                    paint.color = Color.YELLOW
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f + pulse
                    canvas.drawRect(rectLeft - pulse/2, rectTop - pulse/2, rectRight + pulse/2, rectBottom + pulse/2, paint)
                    
                    // Crucial: Reset both style AND width to prevent state leaking to next cells
                    paint.style = Paint.Style.FILL 
                    paint.strokeWidth = 1f
                }
            }
        }

        if (isWin || isGameOver) celebrationManager.draw(canvas)

        if (isGameOver || isWin) {
            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

            paint.color = GamePalette.OVERLAY
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            
            paint.color = if (isWin) Color.GREEN else GamePalette.WARNING
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = width / 18f
            canvas.drawText(if (isWin) currentVictoryWord else "BOOM! GAME OVER", width / 2f, height / 2f - 20f, paint)
            
            paint.color = GamePalette.TEXT_PRIMARY
            paint.textSize = width / 45f
            canvas.drawText("${context.getString(R.string.total_wins_label)}: $totalWins", width / 2f, height / 2f + 50f, paint)
            canvas.drawText(context.getString(R.string.play_again_hint), width / 2f, height / 2f + 90f, paint)
            canvas.drawText(context.getString(R.string.exit_hint), width / 2f, height / 2f + 130f, paint)
        }
    }

    private fun getNumberColor(num: Int): Int {
        return when (num) {
            1 -> Color.BLUE
            2 -> Color.GREEN
            3 -> Color.RED
            4 -> Color.parseColor("#000080")
            5 -> Color.parseColor("#800000")
            else -> Color.BLACK
        }
    }

    class Cell {
        var isMine = false
        var isRevealed = false
        var isFlagged = false
        var isExploded = false
        var isBadFlag = false
        var neighborMines = 0
    }
}
