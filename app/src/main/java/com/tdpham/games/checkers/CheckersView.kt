package com.tdpham.games.checkers

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
import kotlin.random.Random

class CheckersView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "checkers"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val board = Array(8) { IntArray(8) }
    private var cursorR = 5
    private var cursorC = 0
    private var selected: Pair<Int, Int>? = null
    private var gameOver = false
    private var status = "YOUR TURN"
    private var wins = 0

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
        for (r in 0..7) for (c in 0..7) board[r][c] = 0
        for (r in 0..2) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = 2
        for (r in 5..7) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = 1
        cursorR = 5
        cursorC = 0
        selected = null
        gameOver = false
        status = "YOUR TURN"
        wins = ScoreManager.getHighScore(context, gameKey)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resetGame()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR + 7) % 8
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1) % 8
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC + 7) % 8
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1) % 8
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> onSelect()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun onSelect() {
        if (gameOver) return
        if (!hasAnyMoves(1)) {
            gameOver = true
            status = "NO MOVES - CPU WINS"
            SoundManager.playError()
            return
        }
        val piece = board[cursorR][cursorC]
        val sel = selected
        if (sel == null) {
            if (piece == 1) {
                selected = cursorR to cursorC
                SoundManager.playClick()
            }
            return
        }
        if (tryMove(sel.first, sel.second, cursorR, cursorC, 1)) {
            selected = null
            if (countPieces(2) == 0) {
                gameOver = true
                status = "YOU WIN - CENTER TO RESTART"
                val newWins = wins + 1
                if (ScoreManager.updateHighScore(context, gameKey, newWins)) wins = newWins
                SoundManager.playSuccess()
                return
            }
            cpuTurn()
        } else {
            selected = null
        }
    }

    private fun tryMove(fr: Int, fc: Int, tr: Int, tc: Int, player: Int): Boolean {
        if (tr !in 0..7 || tc !in 0..7 || board[tr][tc] != 0) return false
        val dir = if (player == 1) -1 else 1
        val dr = tr - fr
        val dc = tc - fc
        if (dr == dir && kotlin.math.abs(dc) == 1) {
            board[tr][tc] = player
            board[fr][fc] = 0
            SoundManager.playClick()
            return true
        }
        if (dr == dir * 2 && kotlin.math.abs(dc) == 2) {
            val mr = (fr + tr) / 2
            val mc = (fc + tc) / 2
            if (board[mr][mc] == (if (player == 1) 2 else 1)) {
                board[mr][mc] = 0
                board[tr][tc] = player
                board[fr][fc] = 0
                SoundManager.playScore()
                return true
            }
        }
        return false
    }

    private fun cpuTurn() {
        val moves = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) if (board[r][c] == 2) {
            listOf(-1, 1).forEach { dc ->
                val tr = r + 1
                val tc = c + dc
                if (tr in 0..7 && tc in 0..7 && board[tr][tc] == 0) moves.add(Move(r, c, tr, tc))
                val jr = r + 2
                val jc = c + dc * 2
                if (jr in 0..7 && jc in 0..7 && board[jr][jc] == 0 && board[r + 1][c + dc] == 1) {
                    moves.add(Move(r, c, jr, jc))
                }
            }
        }
        if (moves.isEmpty()) {
            gameOver = true
            status = "YOU WIN - CENTER TO RESTART"
            val newWins = wins + 1
            if (ScoreManager.updateHighScore(context, gameKey, newWins)) wins = newWins
            SoundManager.playSuccess()
            return
        }
        val m = moves[Random.nextInt(moves.size)]
        tryMove(m.fr, m.fc, m.tr, m.tc, 2)
        if (countPieces(1) == 0) {
            gameOver = true
            status = "CPU WINS - CENTER TO RESTART"
            SoundManager.playError()
        } else if (!hasAnyMoves(1)) {
            gameOver = true
            status = "NO MOVES - CPU WINS"
            SoundManager.playError()
        } else {
            status = "YOUR TURN"
        }
    }

    private fun hasAnyMoves(player: Int): Boolean {
        val dir = if (player == 1) -1 else 1
        val enemy = if (player == 1) 2 else 1
        for (r in 0..7) {
            for (c in 0..7) {
                if (board[r][c] != player) continue
                for (dc in listOf(-1, 1)) {
                    val tr = r + dir
                    val tc = c + dc
                    if (tr in 0..7 && tc in 0..7 && board[tr][tc] == 0) return true
                    val jr = r + dir * 2
                    val jc = c + dc * 2
                    val mr = r + dir
                    val mc = c + dc
                    if (jr in 0..7 && jc in 0..7 && mr in 0..7 && mc in 0..7 &&
                        board[mr][mc] == enemy && board[jr][jc] == 0
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun countPieces(player: Int): Int {
        var n = 0
        for (r in 0..7) for (c in 0..7) if (board[r][c] == player) n++
        return n
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)
        val size = width.coerceAtMost(height) * 0.8f
        val left = (width - size) / 2f
        val top = (height - size) / 2f + 28f
        val cell = size / 8f
        for (r in 0..7) for (c in 0..7) {
            val x = left + c * cell
            val y = top + r * cell
            paint.style = Paint.Style.FILL
            paint.color = if ((r + c) % 2 == 0) Color.parseColor("#BCAAA4") else Color.parseColor("#6D4C41")
            canvas.drawRect(x, y, x + cell, y + cell, paint)
            when (board[r][c]) {
                1 -> {
                    paint.color = Color.parseColor("#EF5350")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                }
                2 -> {
                    paint.color = Color.parseColor("#EEEEEE")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                }
            }
        }
        selected?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.CYAN
            val x = left + it.second * cell
            val y = top + it.first * cell
            canvas.drawRect(x + 3, y + 3, x + cell - 3, y + cell - 3, paint)
        }
        if (!gameOver) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.YELLOW
            val x = left + cursorC * cell
            val y = top + cursorR * cell
            canvas.drawRect(x + 6, y + 6, x + cell - 6, y + cell - 6, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("WINS: $wins", 30f, 52f, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(status, width / 2f, top - 16f, paint)
    }

    data class Move(val fr: Int, val fc: Int, val tr: Int, val tc: Int)
}
