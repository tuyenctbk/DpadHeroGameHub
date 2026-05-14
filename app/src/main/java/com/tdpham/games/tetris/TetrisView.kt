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
    private val handler = Handler(Looper.getMainLooper())
    
    private val tick = object : Runnable {
        override fun run() {
            if (!paused && !gameOver) {
                if (!tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
                    lockPiece()
                }
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
        requestFocus()
        resume()
    }

    override fun pause() {
        paused = true
        handler.removeCallbacks(tick)
        invalidate()
    }

    override fun resume() {
        if (!gameOver) {
            paused = false
            handler.removeCallbacks(tick)
            handler.post(tick)
            invalidate()
        }
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        for (r in 0 until rows) for (c in 0 until cols) board[r][c] = 0
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        paused = true
        current = spawnPieceData()
        next = spawnPieceData()
        isHardDroppingDown = false
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
        
        if (paused && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resume()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!paused) tryMove(current.r, current.c - 1, current.rot)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!paused) tryMove(current.r, current.c + 1, current.rot)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!paused) fastMoveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (!paused) rotate()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> pause()
            KeyEvent.KEYCODE_SPACE -> if (!paused) hardDrop()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun rotate() {
        val nextRot = (current.rot + 1) % 4
        if (!tryMove(current.r, current.c, nextRot)) {
            // Wall Kick
            if (!tryMove(current.r, current.c - 1, nextRot)) {
                if (!tryMove(current.r, current.c + 1, nextRot)) {
                    if (current.shape == 0) { // I-piece needs more kick room
                        if (!tryMove(current.r, current.c - 2, nextRot)) {
                            tryMove(current.r, current.c + 2, nextRot)
                        }
                    }
                }
            }
        }
    }

    private fun fastMoveDown() {
        // Move down quickly: 2 cells at a time with multiple attempts
        for (_i in 0..2) {
            if (!tryMove(current.r + 1, current.c, current.rot, playSound = false)) {
                lockPiece()
                return
            }
        }
    }

    private fun hardDrop() {
        var r = current.r
        while (isValid(r + 1, current.c, current.rot)) r++
        current = current.copy(r = r)
        lockPiece()
        SoundManager.playClick()
        invalidate()
    }

    private fun tryMove(nr: Int, nc: Int, nrot: Int, playSound: Boolean = false): Boolean {
        if (isValid(nr, nc, nrot)) {
            current = current.copy(r = nr, c = nc, rot = nrot)
            if (playSound) SoundManager.playClick()
            return true
        }
        return false
    }

    private fun isValid(nr: Int, nc: Int, nrot: Int): Boolean {
        val cells = shapeCells(current.shape, nrot)
        return cells.all { (dr, dc) ->
            val rr = nr + dr
            val cc = nc + dc
            cc in 0 until cols && rr < rows && (rr < 0 || board[rr][cc] == 0)
        }
    }

    private fun lockPiece() {
        for ((dr, dc) in shapeCells(current.shape, current.rot)) {
            val rr = current.r + dr
            val cc = current.c + dc
            if (rr in 0 until rows && cc in 0 until cols) board[rr][cc] = current.shape + 1
        }
        SoundManager.playClick() // Piece locked event
        clearLines()
        current = next
        next = spawnPieceData()
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
            score += when(cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
            SoundManager.playScore()
        }
    }

    private fun spawnPieceData() = Piece(0, 3, 0, Random.nextInt(7))

    private fun shapeCells(shape: Int, rot: Int): List<Pair<Int, Int>> {
        val s = when (shape) {
            0 -> arrayOf( // I
                listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
                listOf(-1 to 2, 0 to 2, 1 to 2, 2 to 2),
                listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3),
                listOf(-1 to 1, 0 to 1, 1 to 1, 2 to 1)
            )
            1 -> arrayOf( // J
                listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2),
                listOf(0 to 1, 0 to 2, 1 to 1, 2 to 1),
                listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2),
                listOf(0 to 1, 1 to 1, 2 to 0, 2 to 1)
            )
            2 -> arrayOf( // L
                listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2),
                listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2),
                listOf(1 to 0, 1 to 1, 1 to 2, 2 to 0),
                listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)
            )
            3 -> arrayOf( // O
                listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2),
                listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2),
                listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2),
                listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2)
            )
            4 -> arrayOf( // S
                listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
                listOf(0 to 1, 1 to 1, 1 to 2, 2 to 2),
                listOf(1 to 1, 1 to 2, 2 to 0, 2 to 1),
                listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)
            )
            5 -> arrayOf( // T
                listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2),
                listOf(0 to 1, 1 to 1, 1 to 2, 2 to 1),
                listOf(1 to 0, 1 to 1, 1 to 2, 2 to 1),
                listOf(0 to 1, 1 to 0, 1 to 1, 2 to 1)
            )
            else -> arrayOf( // Z
                listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
                listOf(0 to 2, 1 to 1, 1 to 2, 2 to 1),
                listOf(1 to 0, 1 to 1, 2 to 1, 2 to 2),
                listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0)
            )
        }
        return s[rot % 4]
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        val cell = (height * 0.85f / rows).coerceAtMost(width * 0.07f)
        val gridW = cols * cell
        val gridH = rows * cell
        val left = (width - gridW) / 2f
        val top = (height - gridH) / 2f + 20f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1A1A")
        canvas.drawRect(left, top, left + gridW, top + gridH, paint)
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.parseColor("#333333")
        for (i in 0..cols) canvas.drawLine(left + i * cell, top, left + i * cell, top + gridH, paint)
        for (i in 0..rows) canvas.drawLine(left, top + i * cell, left + gridW, top + i * cell, paint)

        paint.style = Paint.Style.FILL
        for (r in 0 until rows) for (c in 0 until cols) {
            if (board[r][c] != 0) drawBlock(canvas, left + c * cell, top + r * cell, cell, colorFor(board[r][c]))
        }

        if (!paused && !gameOver) {
            var ghostR = current.r
            while (isValid(ghostR + 1, current.c, current.rot)) ghostR++
            paint.alpha = 50
            for ((dr, dc) in shapeCells(current.shape, current.rot)) {
                drawBlock(canvas, left + (current.c + dc) * cell, top + (ghostR + dr) * cell, cell, colorFor(current.shape + 1), isGhost = true)
            }
            paint.alpha = 255
            for ((dr, dc) in shapeCells(current.shape, current.rot)) {
                drawBlock(canvas, left + (current.c + dc) * cell, top + (current.r + dr) * cell, cell, colorFor(current.shape + 1))
            }
        }

        paint.style = Paint.Style.FILL
        paint.textSize = 38f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 60f, paint)

        // Draw next block preview
        val nextBoxLeft = left + gridW + 40f
        val nextBoxTop = top
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawRect(nextBoxLeft, nextBoxTop, nextBoxLeft + cell * 4, nextBoxTop + cell * 4, paint)

        paint.textSize = 24f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("NEXT", nextBoxLeft + 8f, nextBoxTop - 8f, paint)

        paint.style = Paint.Style.FILL
        val nextPreviewLeft = nextBoxLeft + cell * 0.5f
        val nextPreviewTop = nextBoxTop + cell * 0.5f
        for ((dr, dc) in shapeCells(next.shape, 0)) {
            drawBlock(canvas, nextPreviewLeft + dc * cell, nextPreviewTop + dr * cell, cell, colorFor(next.shape + 1))
        }

        if (paused || gameOver) {
            paint.color = GamePalette.OVERLAY
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 60f
            paint.color = Color.WHITE
            canvas.drawText(if (gameOver) "GAME OVER" else "PAUSED", width / 2f, height / 2f, paint)
            paint.textSize = 30f
            canvas.drawText("Press Center to ${if (gameOver) "Restart" else "Resume"}", width / 2f, height / 2f + 60f, paint)
        }
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
        else -> Color.parseColor("#FF1744")
    }

    data class Piece(val r: Int, val c: Int, val rot: Int, val shape: Int)
}
