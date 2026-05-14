package com.tdpham.games.tetris

import android.content.Context
import android.graphics.*
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
    private val rows = 20
    private val cols = 10
    private val board = Array(rows) { IntArray(cols) }
    private var current = spawnPieceData()
    private var next = spawnPieceData()
    private var score = 0
    private var best = 0
    private var paused = true
    private var gameOver = false
    private var isHardDroppingDown = false
    private var flashFrames = 0
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!paused && !gameOver) {
                if (!tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
                    lockPiece()
                }
                if (flashFrames > 0) flashFrames--
                invalidate()
                val delay = (450 - (score / 400) * 25).coerceAtLeast(100).toLong()
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
        paused = false
        handler.post(tick)
    }

    override fun pause() {
        paused = true
        handler.removeCallbacks(tick)
        invalidate()
    }

    override fun resume() {
        if (paused && !gameOver) {
            paused = false
            handler.post(tick)
            invalidate()
        }
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        for (r in 0 until rows) {
            for (c in 0 until cols) board[r][c] = 0
        }
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        current = spawnPieceData()
        next = spawnPieceData()
        gameOver = false
        paused = true
        handler.removeCallbacks(tick)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (paused) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                startGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> tryMove(current.r, current.c - 1, current.rot, playSound = true)
            KeyEvent.KEYCODE_DPAD_RIGHT -> tryMove(current.r, current.c + 1, current.rot, playSound = true)
            KeyEvent.KEYCODE_DPAD_DOWN -> fastMoveDown()
            KeyEvent.KEYCODE_DPAD_UP -> rotate()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> hardDrop()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
        }
        invalidate()
        return true
    }

    private fun rotate() {
        val nextRot = (current.rot + 1) % 4
        if (tryMove(current.r, current.c, nextRot, playSound = true)) {
            // Success
        }
    }

    private fun fastMoveDown() {
        if (tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
            score += 1
        } else {
            lockPiece()
        }
    }

    private fun hardDrop() {
        isHardDroppingDown = true
        var dropLines = 0
        while (tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
            dropLines++
        }
        score += dropLines * 2
        lockPiece()
        isHardDroppingDown = false
        SoundManager.playClick()
    }

    private fun tryMove(nr: Int, nc: Int, nrot: Int, playSound: Boolean = false): Boolean {
        if (isValid(nr, nc, nrot)) {
            current = Piece(nr, nc, nrot, current.shape)
            if (playSound) SoundManager.playClick()
            return true
        }
        return false
    }

    private fun isValid(nr: Int, nc: Int, nrot: Int): Boolean {
        for (cell in shapeCells(current.shape, nrot)) {
            val r = nr + cell.first
            val c = nc + cell.second
            if (r < 0 || r >= rows || c < 0 || c >= cols || board[r][c] != 0) return false
        }
        return true
    }

    private fun lockPiece() {
        for (cell in shapeCells(current.shape, current.rot)) {
            val r = current.r + cell.first
            val c = current.c + cell.second
            if (r >= 0) board[r][c] = current.shape + 1
        }
        clearLines()
        current = next
        next = spawnPieceData()
        if (!isValid(current.r, current.c, current.rot)) {
            gameOver = true
            handler.removeCallbacks(tick)
            ScoreManager.updateHighScore(context, gameKey, score)
            SoundManager.playError()
        }
    }

    private fun clearLines() {
        var linesCleared = 0
        for (r in rows - 1 downTo 0) {
            var full = true
            for (c in 0 until cols) {
                if (board[r][c] == 0) {
                    full = false
                    break
                }
            }
            if (full) {
                linesCleared++
                for (rr in r downTo 1) {
                    for (cc in 0 until cols) board[rr][cc] = board[rr - 1][cc]
                }
                for (cc in 0 until cols) board[0][cc] = 0
                // Re-check same row
                clearLines()
                return
            }
        }
        if (linesCleared > 0) {
            flashFrames = 3
            score += when (linesCleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                4 -> 800
                else -> 0
            }
            SoundManager.playScore()
        }
    }

    private fun spawnPieceData(): Piece {
        return Piece(0, cols / 2 - 1, 0, Random.nextInt(7))
    }

    private fun shapeCells(shape: Int, rot: Int): List<Pair<Int, Int>> {
        val s = when (shape) {
            0 -> listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3) // I
            1 -> listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2) // J
            2 -> listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0) // L
            3 -> listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1) // O
            4 -> listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1) // S
            5 -> listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2) // Z
            6 -> listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1) // T
            else -> emptyList()
        }
        return when (rot) {
            0 -> s
            1 -> s.map { it.second to -it.first }
            2 -> s.map { -it.first to -it.second }
            3 -> s.map { -it.second to it.first }
            else -> s
        }
    }

    override fun onDraw(canvas: Canvas) {
        val size = (height / rows).coerceAtMost(width / (cols + 6)).toFloat()
        val offsetX = (width - cols * size) / 2f
        val offsetY = (height - rows * size) / 2f

        // Draw board background
        paint.color = Color.DKGRAY
        canvas.drawRect(offsetX, offsetY, offsetX + cols * size, offsetY + rows * size, paint)

        // Draw Grid lines
        paint.color = Color.parseColor("#33FFFFFF")
        paint.strokeWidth = 1f
        for (i in 0..cols) canvas.drawLine(offsetX + i * size, offsetY, offsetX + i * size, offsetY + rows * size, paint)
        for (i in 0..rows) canvas.drawLine(offsetX, offsetY + i * size, offsetX + cols * size, offsetY + i * size, paint)

        // Draw flash effect
        if (flashFrames > 0) {
            paint.color = Color.WHITE
            paint.alpha = 100
            canvas.drawRect(offsetX, offsetY, offsetX + cols * size, offsetY + rows * size, paint)
            paint.alpha = 255
        }

        // Draw ghost piece
        var ghostR = current.r
        while (isValid(ghostR + 1, current.c, current.rot)) {
            ghostR++
        }
        for (cell in shapeCells(current.shape, current.rot)) {
            drawBlock(canvas, offsetX + (current.c + cell.second) * size, offsetY + (ghostR + cell.first) * size, size, colorFor(current.shape + 1), isGhost = true)
        }

        // Draw settled blocks
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (board[r][c] != 0) {
                    drawBlock(canvas, offsetX + c * size, offsetY + r * size, size, colorFor(board[r][c]))
                }
            }
        }

        // Draw current piece
        if (!paused && !gameOver) {
            for (cell in shapeCells(current.shape, current.rot)) {
                drawBlock(canvas, offsetX + (current.c + cell.second) * size, offsetY + (current.r + cell.first) * size, size, colorFor(current.shape + 1))
            }
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = size * 0.8f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", offsetX + cols * size + 20, offsetY + size, paint)
        canvas.drawText("BEST: $best", offsetX + cols * size + 20, offsetY + size * 2.5f, paint)
        canvas.drawText("NEXT:", offsetX + cols * size + 20, offsetY + size * 4.5f, paint)

        // Draw next piece
        for (cell in shapeCells(next.shape, next.rot)) {
            drawBlock(canvas, offsetX + (cols + 2 + cell.second) * size, offsetY + (6 + cell.first) * size, size, colorFor(next.shape + 1))
        }

        if (gameOver) drawOverlay(canvas, "GAME OVER", "Score: $score\nPress Center to Restart")
        else if (paused) drawOverlay(canvas, "PAUSED", "Press Center to Resume")
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width / 2f, height / 2f - 40, paint)
        paint.textSize = 30f
        canvas.drawText(subtitle, width / 2f, height / 2f + 40, paint)
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, isGhost: Boolean = false) {
        paint.color = color
        if (isGhost) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(x + 2, y + 2, x + size - 2, y + size - 2, paint)
        } else {
            paint.style = Paint.Style.FILL
            canvas.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, paint)
            // Bevel effect
            paint.color = Color.WHITE
            paint.alpha = 70
            canvas.drawRect(x + 2, y + 2, x + size * 0.35f, y + size * 0.35f, paint)
            paint.alpha = 255
        }
    }

    private fun colorFor(v: Int): Int = when (v) {
        1 -> Color.parseColor("#00E5FF")
        2 -> Color.parseColor("#2979FF")
        3 -> Color.parseColor("#FF9100")
        4 -> Color.parseColor("#FFEA00")
        5 -> Color.parseColor("#00E676")
        6 -> Color.parseColor("#D500F9")
        else -> Color.parseColor("#F44336")
    }

    data class Piece(val r: Int, val c: Int, val rot: Int, val shape: Int)
}
