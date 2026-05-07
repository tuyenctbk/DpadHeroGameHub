package com.tdpham.games.minesweeper

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

class MinesweeperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "minesweeper"

    private val rows = 10
    private val cols = 10
    private val minesCount = 12
    private var cellSize = 0f

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
        totalWins = ScoreManager.getHighScore(context, gameKey)
        setupGame()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }
    
    private val grid = Array(rows) { Array(cols) { Cell() } }
    private var cursorX = 0
    private var cursorY = 0
    private var isGameOver = false
    private var isWin = false
    private var isFirstClick = true
    private var totalWins = 0
    
    private val revealQueue: Queue<Pair<Int, Int>> = LinkedList()
    private val revealHandler = Handler(Looper.getMainLooper())
    private var isProcessingQueue = false

    private val processQueueRunnable = object : Runnable {
        override fun run() {
            if (revealQueue.isEmpty()) {
                isProcessingQueue = false
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
            
            revealHandler.postDelayed(this, 30) // Ripple speed
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        revealHandler.removeCallbacks(processQueueRunnable)
    }

    private fun setupGame() {
        revealHandler.removeCallbacks(processQueueRunnable)
        revealQueue.clear()
        isProcessingQueue = false
        totalWins = ScoreManager.getHighScore(context, gameKey)
        
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c] = Cell()
            }
        }
        
        val random = Random()
        var placedMines = 0
        while (placedMines < minesCount) {
            val r = random.nextInt(rows)
            val c = random.nextInt(cols)
            if (!grid[r][c].isMine) {
                grid[r][c].isMine = true
                placedMines++
            }
        }
        
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
                    // Mark the specific mine that was hit
                    if (r == hitR && c == hitC) {
                        grid[r][c].isExploded = true
                    }
                } else if (grid[r][c].isFlagged) {
                    // Mark incorrectly flagged cells
                    grid[r][c].isBadFlag = true
                }
            }
        }
    }

    private fun revealCell(r: Int, c: Int) {
        if (r !in 0 until rows || c !in 0 until cols || grid[r][c].isRevealed || grid[r][c].isFlagged) return
        
        if (isFirstClick) {
            isFirstClick = false
            if (grid[r][c].isMine) {
                moveMine(r, c)
            }
        }

        if (grid[r][c].isMine) {
            isGameOver = true
            SoundManager.playError()
            revealAllMines(r, c)
            invalidate()
            return
        }

        // Start staggered reveal
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

    private fun checkWin() {
        var revealedCount = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c].isRevealed && !grid[r][c].isMine) revealedCount++
            }
        }
        if (revealedCount == (rows * cols - minesCount)) {
            isWin = true
            totalWins++
            ScoreManager.updateHighScore(context, gameKey, totalWins)
            SoundManager.playSuccess()
            invalidate()
        }
    }

    private fun toggleFlag(r: Int, c: Int) {
        if (!grid[r][c].isRevealed) {
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

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (cursorY > 0) cursorY--; invalidate(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (cursorY < rows - 1) cursorY++; invalidate(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (cursorX > 0) cursorX--; invalidate(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (cursorX < cols - 1) cursorX++; invalidate(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                revealCell(cursorY, cursorX)
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                toggleFlag(cursorY, cursorX)
                true
            }
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

        canvas.drawColor(GamePalette.BACKGROUND)

        val headerY = offsetY - cellSize * 1.2f
        paint.textSize = cellSize * 0.7f
        paint.textAlign = Paint.Align.LEFT
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("MINES: $minesCount", offsetX, headerY, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        paint.color = GamePalette.SCORE
        canvas.drawText("FLAGS: ${getFlaggedCount()}", offsetX + cols * cellSize, headerY, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("WINS: $totalWins", width / 2f, headerY, paint)

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
                canvas.drawRect(rectLeft + 2, rectTop + 2, rectRight - 2, rectBottom - 2, paint)
                
                if (!cell.isRevealed) {
                    paint.color = Color.GRAY
                    canvas.drawLine(rectLeft + 2, rectTop + 2, rectRight - 2, rectTop + 2, paint)
                    canvas.drawLine(rectLeft + 2, rectTop + 2, rectLeft + 2, rectBottom - 2, paint)
                }

                if (cell.isRevealed) {
                    if (cell.isMine) {
                        // Highlight the mine that ended the game
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
                        // Show "Bad Flag" (Crossed out mine)
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
                        paint.textSize = cellSize * 0.7f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("F", rectLeft + cellSize / 2, rectTop + cellSize * 0.75f, paint)
                    }
                }

                if (r == cursorY && c == cursorX) {
                    paint.color = Color.YELLOW
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f
                    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paint)
                }
            }
        }

        if (isGameOver || isWin) {
            // Smaller, less intrusive banner so the board (and mistakes) is still visible
            paint.color = GamePalette.OVERLAY
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, height / 2f - 120f, width.toFloat(), height / 2f + 160f, paint)
            
            paint.color = if (isWin) Color.GREEN else GamePalette.WARNING
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = width / 15f
            canvas.drawText(if (isWin) "MISSION ACCOMPLISHED!" else "BOOM! GAME OVER", width / 2f, height / 2f, paint)
            
            paint.color = GamePalette.TEXT_PRIMARY
            paint.textSize = width / 40f
            canvas.drawText("Press Center to Play Again | Press Back to Exit", width / 2f, height / 2f + 100f, paint)
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
