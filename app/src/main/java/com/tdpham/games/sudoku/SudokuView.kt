package com.tdpham.games.sudoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager

class SudokuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "sudoku"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val puzzle = arrayOf(
        intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
        intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
        intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
        intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
        intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
        intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
        intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
        intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
        intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
    )
    private val solution = arrayOf(
        intArrayOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
        intArrayOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
        intArrayOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
        intArrayOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
        intArrayOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
        intArrayOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
        intArrayOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
        intArrayOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
        intArrayOf(3, 4, 5, 2, 8, 6, 1, 7, 9)
    )
    private lateinit var board: Array<IntArray>
    private var cursorR = 0
    private var cursorC = 0
    private var solved = false
    private var best = 0

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
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        board = Array(9) { r -> puzzle[r].clone() }
        cursorR = 0
        cursorC = 0
        solved = false
        best = ScoreManager.getHighScore(context, gameKey)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (solved && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resetGame()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR + 8) % 9
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1) % 9
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC + 8) % 9
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1) % 9
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> cycleCell()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun cycleCell() {
        if (puzzle[cursorR][cursorC] != 0 || solved) return
        board[cursorR][cursorC] = (board[cursorR][cursorC] % 9) + 1
        SoundManager.playClick()
        if (isSolved()) {
            solved = true
            val points = 500
            if (ScoreManager.updateHighScore(context, gameKey, points)) best = points
            SoundManager.playSuccess()
        }
    }

    private fun isSolved(): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) if (board[r][c] != solution[r][c]) return false
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        val grid = width.coerceAtMost(height) * 0.78f
        val left = (width - grid) / 2f
        val top = (height - grid) / 2f + 32f
        val cell = grid / 9f

        for (r in 0 until 9) for (c in 0 until 9) {
            val x = left + c * cell
            val y = top + r * cell
            paint.style = Paint.Style.FILL
            paint.color = if ((r / 3 + c / 3) % 2 == 0) Color.parseColor("#1E1E1E") else Color.parseColor("#252525")
            canvas.drawRect(x, y, x + cell, y + cell, paint)
            if (r == cursorR && c == cursorC && !solved) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.YELLOW
                canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, paint)
            }
            val v = board[r][c]
            if (v != 0) {
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = cell * 0.55f
                paint.color = if (puzzle[r][c] == 0) Color.CYAN else Color.WHITE
                canvas.drawText(v.toString(), x + cell / 2, y + cell * 0.68f, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        for (i in 0..9) {
            paint.color = if (i % 3 == 0) Color.WHITE else Color.GRAY
            paint.strokeWidth = if (i % 3 == 0) 4f else 1.5f
            canvas.drawLine(left, top + i * cell, left + grid, top + i * cell, paint)
            canvas.drawLine(left + i * cell, top, left + i * cell, top + grid, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 38f
        canvas.drawText("BEST: $best", 30f, 52f, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            if (solved) "SOLVED! CENTER TO RESTART" else "CENTER: 1-9 CYCLE",
            width / 2f,
            top - 16f,
            paint
        )
    }
}
