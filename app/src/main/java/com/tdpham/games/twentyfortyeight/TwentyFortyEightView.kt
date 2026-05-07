package com.tdpham.games.twentyfortyeight

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

class TwentyFortyEightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "4096"
    private val gridSize = 5 // 5x5 for "4096" on larger TV screens
    private var board = Array(gridSize) { IntArray(gridSize) { 0 } }
    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isWin = false
    private var cellSize = 0f
    
    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() {}

    override fun resetGame() {
        board = Array(gridSize) { IntArray(gridSize) { 0 } }
        score = 0
        isGameOver = false
        isWin = false
        highScore = ScoreManager.getHighScore(context, gameKey)
        addRandomTile()
        addRandomTile()
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    private fun addRandomTile() {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) emptyCells.add(r to c)
            }
        }
        if (emptyCells.isNotEmpty()) {
            val (r, c) = emptyCells[Random().nextInt(emptyCells.size)]
            board[r][c] = if (Random().nextFloat() < 0.9) 2 else 4
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver || isWin) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        val moved = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            else -> false
        }

        if (moved) {
            addRandomTile()
            checkGameState()
            SoundManager.playClick()
            invalidate()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun move(dx: Int, dy: Int): Boolean {
        var moved = false
        
        if (dx != 0) { // Horizontal move
            for (r in 0 until gridSize) {
                val row = board[r]
                val newRow = if (dx < 0) shift(row) else shift(row.reversedArray()).reversedArray()
                if (!row.contentEquals(newRow)) {
                    board[r] = newRow
                    moved = true
                }
            }
        } else { // Vertical move
            for (c in 0 until gridSize) {
                val col = IntArray(gridSize) { r -> board[r][c] }
                val newCol = if (dy < 0) shift(col) else shift(col.reversedArray()).reversedArray()
                if (!col.contentEquals(newCol)) {
                    for (r in 0 until gridSize) board[r][c] = newCol[r]
                    moved = true
                }
            }
        }
        return moved
    }

    private fun shift(arr: IntArray): IntArray {
        val result = mutableListOf<Int>()
        val filtered = arr.filter { it != 0 }
        var i = 0
        while (i < filtered.size) {
            if (i + 1 < filtered.size && filtered[i] == filtered[i+1]) {
                val newValue = filtered[i] * 2
                result.add(newValue)
                score += newValue
                i += 2
            } else {
                result.add(filtered[i])
                i++
            }
        }
        while (result.size < gridSize) result.add(0)
        return result.toIntArray()
    }

    private fun checkGameState() {
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] >= 4096) isWin = true
            }
        }

        // Check if any move is possible
        var movePossible = false
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) movePossible = true
                if (r + 1 < gridSize && board[r][c] == board[r + 1][c]) movePossible = true
                if (c + 1 < gridSize && board[r][c] == board[r][c + 1]) movePossible = true
            }
        }
        
        if (!movePossible) {
            isGameOver = true
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            if (isNewHigh) highScore = score
            SoundManager.playError()
        } else if (isWin) {
            SoundManager.playSuccess()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellSize = width.coerceAtMost(height).toFloat() / (gridSize + 2.5f)
        val offsetX = (width - cellSize * gridSize) / 2
        val offsetY = (height - cellSize * gridSize) / 2 + cellSize * 1.0f

        canvas.drawColor(GamePalette.BACKGROUND)

        // Draw Score (Left Side)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = cellSize * 0.3f
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("SCORE", offsetX, offsetY - cellSize * 0.8f, paint)
        paint.textSize = cellSize * 0.5f
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("$score", offsetX, offsetY - cellSize * 0.3f, paint)
        
        // Draw Best (Right Side)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = cellSize * 0.3f
        paint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("BEST", offsetX + gridSize * cellSize, offsetY - cellSize * 0.8f, paint)
        paint.textSize = cellSize * 0.5f
        paint.color = GamePalette.SCORE
        canvas.drawText("$highScore", offsetX + gridSize * cellSize, offsetY - cellSize * 0.3f, paint)

        // Draw Grid Background
        paint.color = Color.parseColor("#333333")
        canvas.drawRoundRect(offsetX - 10, offsetY - 10, offsetX + gridSize * cellSize + 10, offsetY + gridSize * cellSize + 10, 20f, 20f, paint)

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                drawTile(canvas, r, c, offsetX, offsetY)
            }
        }

        if (isGameOver || isWin) {
            drawOverlay(canvas)
        }
    }

    private fun drawTile(canvas: Canvas, r: Int, c: Int, offsetX: Float, offsetY: Float) {
        val value = board[r][c]
        val left = offsetX + c * cellSize + 5
        val top = offsetY + r * cellSize + 5
        val right = left + cellSize - 10
        val bottom = top + cellSize - 10

        paint.color = getTileColor(value)
        canvas.drawRoundRect(left, top, right, bottom, 15f, 15f, paint)

        if (value > 0) {
            paint.color = if (value <= 4) Color.parseColor("#776e65") else Color.WHITE
            paint.textSize = if (value < 100) cellSize * 0.4f else if (value < 1000) cellSize * 0.3f else cellSize * 0.25f
            paint.textAlign = Paint.Align.CENTER
            val text = value.toString()
            val textRect = Rect()
            paint.getTextBounds(text, 0, text.length, textRect)
            canvas.drawText(text, left + (cellSize-10)/2, top + (cellSize-10)/2 + textRect.height()/2, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width / 15f
        paint.color = if (isWin) Color.GREEN else GamePalette.WARNING
        canvas.drawText(if (isWin) "4096 REACHED!" else "GAME OVER", width / 2f, height / 2f, paint)
        
        paint.textSize = width / 40f
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("Press Center to Restart | Back to Exit", width / 2f, height / 2f + 80f, paint)
    }

    private fun getTileColor(value: Int): Int {
        return when (value) {
            0 -> Color.parseColor("#444444")
            2 -> Color.parseColor("#eee4da")
            4 -> Color.parseColor("#ede0c8")
            8 -> Color.parseColor("#f2b179")
            16 -> Color.parseColor("#f59563")
            32 -> Color.parseColor("#f67c5f")
            64 -> Color.parseColor("#f65e3b")
            128 -> Color.parseColor("#edcf72")
            256 -> Color.parseColor("#edcc61")
            512 -> Color.parseColor("#edc850")
            1024 -> Color.parseColor("#edc53f")
            2048 -> Color.parseColor("#edc22e")
            4096 -> Color.parseColor("#3c3a32")
            else -> Color.parseColor("#3c3a32")
        }
    }
}
