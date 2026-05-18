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
import com.tdpham.games.common.GameEnvironment
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
    private var flashFrames = 0
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!paused && !gameOver) {
                if (!tryMove(current.r + 1, current.c, current.rot)) {
                    lockPiece()
                }
                if (flashFrames > 0) flashFrames--
                invalidate()
                val delay = (450 - (score / 400) * 25).coerceAtLeast(100).toLong()
                handler.postDelayed(this, delay)
            }
        }
    }

    private var bgType = GameEnvironment.BackgroundType.GRADIENT
    private var isNight = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        paused = false
        handler.removeCallbacks(tick)
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
        bgType = listOf(GameEnvironment.BackgroundType.GRADIENT, GameEnvironment.BackgroundType.GRID, GameEnvironment.BackgroundType.STRIPES).random()
        isNight = Random.nextBoolean()
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
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun rotate() {
        val nextRot = (current.rot + 1) % 4
        if (tryMove(current.r, current.c, nextRot, playSound = true)) {
            // Rotated successfully
        } else {
            // Simple wall kick attempt
            if (tryMove(current.r, current.c - 1, nextRot, playSound = true)) return
            if (tryMove(current.r, current.c + 1, nextRot, playSound = true)) return
        }
    }

    private fun fastMoveDown() {
        if (tryMove(current.r + 1, current.c, current.rot)) {
            score += 1
        } else {
            lockPiece()
        }
    }

    private fun hardDrop() {
        var dropLines = 0
        while (tryMove(current.r + 1, current.c, current.rot)) {
            dropLines++
        }
        score += dropLines * 2
        lockPiece()
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
        var r = rows - 1
        while (r >= 0) {
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
                // Stay on same row index to check the newly dropped line
            } else {
                r--
            }
        }
        if (linesCleared > 0) {
            flashFrames = 2
            score += when (linesCleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                4 -> 800
                else -> 1000
            }
            SoundManager.playScore()
        }
    }

    private fun spawnPieceData(): Piece {
        val shape = Random.nextInt(7)
        // Adjust start row if some cells are negative (none are in rot 0 with new def)
        return Piece(0, cols / 2, 0, shape)
    }

    private fun shapeCells(shape: Int, rot: Int): List<Pair<Int, Int>> {
        return when (shape) {
            0 -> // I
                when (rot % 2) {
                    0 -> listOf(0 to -1, 0 to 0, 0 to 1, 0 to 2)
                    else -> listOf(-1 to 0, 0 to 0, 1 to 0, 2 to 0)
                }
            1 -> // J
                when (rot) {
                    0 -> listOf(0 to -1, 0 to 0, 0 to 1, 1 to 1)
                    1 -> listOf(-1 to 0, 0 to 0, 1 to 0, 1 to -1)
                    2 -> listOf(0 to 1, 0 to 0, 0 to -1, -1 to -1)
                    else -> listOf(1 to 0, 0 to 0, -1 to 0, -1 to 1)
                }
            2 -> // L
                when (rot) {
                    0 -> listOf(0 to -1, 0 to 0, 0 to 1, 1 to -1)
                    1 -> listOf(-1 to 0, 0 to 0, 1 to 0, -1 to -1)
                    2 -> listOf(0 to 1, 0 to 0, 0 to -1, -1 to 1)
                    else -> listOf(1 to 0, 0 to 0, -1 to 0, 1 to 1)
                }
            3 -> listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1) // O
            4 -> // S
                when (rot % 2) {
                    0 -> listOf(0 to 0, 0 to 1, 1 to -1, 1 to 0)
                    else -> listOf(-1 to 0, 0 to 0, 0 to 1, 1 to 1)
                }
            5 -> // Z
                when (rot % 2) {
                    0 -> listOf(0 to -1, 0 to 0, 1 to 0, 1 to 1)
                    else -> listOf(-1 to 1, 0 to 1, 0 to 0, 1 to 0)
                }
            6 -> // T
                when (rot) {
                    0 -> listOf(0 to -1, 0 to 0, 0 to 1, 1 to 0)
                    1 -> listOf(-1 to 0, 0 to 0, 1 to 0, 0 to -1)
                    2 -> listOf(0 to 1, 0 to 0, 0 to -1, -1 to 0)
                    else -> listOf(1 to 0, 0 to 0, -1 to 0, 0 to 1)
                }
            else -> emptyList()
        }
    }

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val size = (height / (rows + 2)).coerceAtMost(width / (cols + 8)).toFloat()
        val offsetX = (width - cols * size) / 2f
        val offsetY = (height - rows * size) / 2f

        // Draw background
        GameEnvironment.draw(canvas, bgType, isNight = isNight, paint = paint)

        // Draw board area
        boardPaint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRect(offsetX, offsetY, offsetX + cols * size, offsetY + rows * size, boardPaint)

        // Draw Grid lines
        boardPaint.color = GamePalette.GRID_LINE
        boardPaint.strokeWidth = 1f
        for (i in 0..cols) canvas.drawLine(offsetX + i * size, offsetY, offsetX + i * size, offsetY + rows * size, boardPaint)
        for (i in 0..rows) canvas.drawLine(offsetX, offsetY + i * size, offsetX + cols * size, offsetY + i * size, boardPaint)

        // Draw flash effect
        if (flashFrames > 0) {
            boardPaint.color = Color.WHITE
            boardPaint.alpha = 100
            canvas.drawRect(offsetX, offsetY, offsetX + cols * size, offsetY + rows * size, boardPaint)
            boardPaint.alpha = 255
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
        textPaint.color = GamePalette.TEXT_PRIMARY
        textPaint.textSize = size * 0.8f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", offsetX + cols * size + 40, offsetY + size, textPaint)
        textPaint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("BEST: $best", offsetX + cols * size + 40, offsetY + size * 2.5f, textPaint)
        canvas.drawText("NEXT:", offsetX + cols * size + 40, offsetY + size * 4.5f, textPaint)

        // Draw next piece
        for (cell in shapeCells(next.shape, next.rot)) {
            drawBlock(canvas, offsetX + (cols + 3 + cell.second) * size, offsetY + (6 + cell.first) * size, size, colorFor(next.shape + 1))
        }

        if (gameOver) drawOverlay(canvas, "GAME OVER", "Score: $score\nPress Center to Restart")
        else if (paused) drawOverlay(canvas, "PAUSED", "Press Center to Resume")
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        overlayPaint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        overlayPaint.color = Color.WHITE
        overlayPaint.textSize = 60f
        overlayPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width / 2f, height / 2f - 40, overlayPaint)
        overlayPaint.textSize = 30f
        overlayPaint.color = Color.LTGRAY
        canvas.drawText(subtitle, width / 2f, height / 2f + 40, overlayPaint)
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, isGhost: Boolean = false) {
        blockPaint.color = color
        if (isGhost) {
            blockPaint.style = Paint.Style.STROKE
            blockPaint.strokeWidth = 2f
            canvas.drawRect(x + 2, y + 2, x + size - 2, y + size - 2, blockPaint)
            blockPaint.style = Paint.Style.FILL
        } else {
            blockPaint.style = Paint.Style.FILL
            canvas.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, blockPaint)
            // Bevel effect
            blockPaint.color = Color.WHITE
            blockPaint.alpha = 70
            canvas.drawRect(x + 2, y + 2, x + size * 0.35f, y + size * 0.35f, blockPaint)
            blockPaint.alpha = 255
        }
    }

    private fun colorFor(v: Int): Int = when (v) {
        1 -> Color.parseColor("#00E5FF") // I: Cyan
        2 -> Color.parseColor("#2979FF") // J: Blue
        3 -> Color.parseColor("#FF9100") // L: Orange
        4 -> Color.parseColor("#FFEA00") // O: Yellow
        5 -> Color.parseColor("#00E676") // S: Green
        6 -> Color.parseColor("#F44336") // Z: Red
        7 -> Color.parseColor("#D500F9") // T: Purple
        else -> Color.GRAY
    }

    data class Piece(val r: Int, val c: Int, val rot: Int, val shape: Int)
}
