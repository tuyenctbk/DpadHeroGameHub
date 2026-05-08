package com.tdpham.games.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.random.Random

class TetrisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "tetris"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rows = 18
    private val cols = 10
    private val board = Array(rows) { IntArray(cols) }
    private var current = Piece(0, 3, 0, randomShape())
    private var score = 0
    private var best = 0
    private var paused = true
    private var gameOver = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!paused && !gameOver) {
                if (!tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
                    lockPiece()
                }
                invalidate()
                handler.postDelayed(this, 380L)
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
        resume()
    }

    override fun pause() {
        paused = true
        handler.removeCallbacks(tick)
    }

    override fun resume() {
        if (!gameOver) {
            paused = false
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        for (r in 0 until rows) for (c in 0 until cols) board[r][c] = 0
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        paused = true
        spawnPiece()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resetGame()
            resume()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!paused) tryMove(current.r, current.c - 1, current.rot)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!paused) tryMove(current.r, current.c + 1, current.rot)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!paused) tryMove(current.r + 1, current.c, current.rot)
            KeyEvent.KEYCODE_DPAD_UP -> if (!paused) tryMove(current.r, current.c, (current.rot + 1) % 4)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (paused) resume() else pause()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun tryMove(nr: Int, nc: Int, nrot: Int, playSound: Boolean = true): Boolean {
        val cells = shapeCells(current.shape, nrot)
        if (cells.any { nr + it.first !in 0 until rows || nc + it.second !in 0 until cols || board[nr + it.first][nc + it.second] != 0 }) {
            return false
        }
        current = current.copy(r = nr, c = nc, rot = nrot)
        if (playSound) {
            SoundManager.playClick()
        }
        return true
    }

    private fun lockPiece() {
        for ((dr, dc) in shapeCells(current.shape, current.rot)) {
            val rr = current.r + dr
            val cc = current.c + dc
            if (rr in 0 until rows && cc in 0 until cols) board[rr][cc] = current.shape + 1
        }
        clearLines()
        spawnPiece()
        if (!isValid(current.r, current.c, current.rot)) {
            gameOver = true
            pause()
            if (ScoreManager.updateHighScore(context, gameKey, score)) best = score
            SoundManager.playError()
        }
    }

    private fun clearLines() {
        var cleared = 0
        var r = rows - 1
        while (r >= 0) {
            if (board[r].all { it != 0 }) {
                for (rr in r downTo 1) board[rr] = board[rr - 1].clone()
                board[0] = IntArray(cols)
                cleared++
            } else r--
        }
        if (cleared > 0) {
            score += cleared * 100
            SoundManager.playScore()
        }
    }

    private fun spawnPiece() {
        current = Piece(0, 3, 0, randomShape())
    }

    private fun isValid(nr: Int, nc: Int, nrot: Int): Boolean {
        val cells = shapeCells(current.shape, nrot)
        return cells.none { nr + it.first !in 0 until rows || nc + it.second !in 0 until cols || board[nr + it.first][nc + it.second] != 0 }
    }

    private fun shapeCells(shape: Int, rot: Int): List<Pair<Int, Int>> {
        return when (shape) {
            0 -> if (rot % 2 == 0) listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3) else listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1) // I
            1 -> listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1) // O
            else -> when (rot % 4) { // T
                0 -> listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2)
                1 -> listOf(0 to 1, 1 to 1, 1 to 2, 2 to 1)
                2 -> listOf(1 to 0, 1 to 1, 1 to 2, 2 to 1)
                else -> listOf(0 to 1, 1 to 0, 1 to 1, 2 to 1)
            }
        }
    }

    private fun randomShape(): Int = Random.nextInt(3)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        val cell = (height * 0.8f / rows).coerceAtMost(width * 0.045f)
        val gridW = cols * cell
        val gridH = rows * cell
        val left = (width - gridW) / 2f
        val top = (height - gridH) / 2f + 20f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1F1F1F")
        canvas.drawRect(left, top, left + gridW, top + gridH, paint)

        for (r in 0 until rows) for (c in 0 until cols) {
            if (board[r][c] != 0) {
                paint.color = colorFor(board[r][c])
                val x = left + c * cell
                val y = top + r * cell
                canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, paint)
            }
        }
        for ((dr, dc) in shapeCells(current.shape, current.rot)) {
            val rr = current.r + dr
            val cc = current.c + dc
            if (rr in 0 until rows && cc in 0 until cols) {
                paint.color = colorFor(current.shape + 1)
                val x = left + cc * cell
                val y = top + rr * cell
                canvas.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, paint)
            }
        }

        paint.textSize = 38f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 30f, 52f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 30f, 52f, paint)
        paint.textAlign = Paint.Align.CENTER
        if (paused && !gameOver) canvas.drawText("PAUSED - CENTER TO START", width / 2f, top - 12f, paint)
        if (gameOver) canvas.drawText("GAME OVER - CENTER TO RESTART", width / 2f, top - 12f, paint)
    }

    private fun colorFor(v: Int): Int = when (v) {
        1 -> Color.CYAN
        2 -> Color.YELLOW
        else -> Color.MAGENTA
    }

    data class Piece(val r: Int, val c: Int, val rot: Int, val shape: Int)
}
