package com.tdpham.games.tictactoe

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
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.random.Random

class TicTacToeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "tic_tac_toe"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var gridSize = 3
    private var board = Array(gridSize) { IntArray(gridSize) { 0 } }
    private var cursorR = 0
    private var cursorC = 0
    private var gameOver = false
    private var status = ""
    private var wins = 0
    private val winningCells = mutableListOf<Pair<Int, Int>>()
    
    private var turnStarter = 1 // 1 for Player, 2 for CPU
    private var isPlayerTurn = true
    private var animationFrame = 0
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            animationFrame++
            invalidate()
            animationHandler.postDelayed(this, 50)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val cpuMoveRunnable = Runnable { cpuMove() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animationHandler.post(animationRunnable)
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
        handler.removeCallbacks(cpuMoveRunnable)
        board = Array(gridSize) { IntArray(gridSize) { 0 } }
        cursorR = gridSize / 2
        cursorC = gridSize / 2
        gameOver = false
        winningCells.clear()
        wins = ScoreManager.getHighScore(context, gameKey)
        
        isPlayerTurn = (turnStarter == 1)
        status = if (isPlayerTurn) "YOUR TURN" else "CPU STARTING..."
        
        invalidate()
        
        if (!isPlayerTurn) {
            handler.postDelayed(cpuMoveRunnable, 800)
        }
    }

    private fun changeGridSize() {
        gridSize = when(gridSize) {
            3 -> 4
            4 -> 5
            else -> 3
        }
        turnStarter = 1 // Reset turn starter when changing mode
        resetGame()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                // Switch who starts for next match
                turnStarter = if (turnStarter == 1) 2 else 1
                resetGame()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                changeGridSize()
                return true
            }
        }
        
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1).coerceAtMost(gridSize - 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1).coerceAtMost(gridSize - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (isPlayerTurn) playerMove()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB -> {
                changeGridSize()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (gameOver) {
                turnStarter = if (turnStarter == 1) 2 else 1
                resetGame()
                return true
            }

            // Calculate grid bounds (must match onDraw)
            val size = width.coerceAtMost(height) * 0.65f
            val left = (width - size) / 2f
            val top = (height - size) / 2f + 60f
            val cell = size / gridSize

            if (event.x in left..(left + size) && event.y in top..(top + size)) {
                val c = ((event.x - left) / cell).toInt().coerceIn(0, gridSize - 1)
                val r = ((event.y - top) / cell).toInt().coerceIn(0, gridSize - 1)
                
                cursorR = r
                cursorC = c
                if (isPlayerTurn) playerMove()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun playerMove() {
        if (gameOver || !isPlayerTurn || board[cursorR][cursorC] != 0) return
        handler.removeCallbacks(cpuMoveRunnable)
        board[cursorR][cursorC] = 1
        SoundManager.playClick()
        if (resolveEnd("YOU WIN")) {
            val newScore = wins + 1
            if (ScoreManager.updateHighScore(context, gameKey, newScore)) wins = newScore
            return
        }
        isPlayerTurn = false
        status = "CPU TURN"
        invalidate()
        handler.postDelayed(cpuMoveRunnable, 600)
    }

    private fun cpuMove() {
        if (gameOver || isPlayerTurn) return
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until gridSize) for (c in 0 until gridSize) if (board[r][c] == 0) empty.add(r to c)
        
        if (empty.isEmpty()) return
        
        // Basic AI: Try to win or block, else random
        val move = findBestMove() ?: empty[Random.nextInt(empty.size)]
        
        board[move.first][move.second] = 2
        SoundManager.playClick()
        if (!resolveEnd("CPU WINS")) {
            isPlayerTurn = true
            status = "YOUR TURN"
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(cpuMoveRunnable)
        animationHandler.removeCallbacks(animationRunnable)
    }

    private fun findBestMove(): Pair<Int, Int>? {
        // Simple Win/Block logic
        val originalWinningCells = winningCells.toList()
        // Try to win
        for (r in 0 until gridSize) for (c in 0 until gridSize) {
            if (board[r][c] == 0) {
                board[r][c] = 2
                if (winner() == 2) { 
                    board[r][c] = 0
                    winningCells.clear()
                    winningCells.addAll(originalWinningCells)
                    return r to c 
                }
                board[r][c] = 0
            }
        }
        // Try to block player
        for (r in 0 until gridSize) for (c in 0 until gridSize) {
            if (board[r][c] == 0) {
                board[r][c] = 1
                if (winner() == 1) { 
                    board[r][c] = 0
                    winningCells.clear()
                    winningCells.addAll(originalWinningCells)
                    return r to c 
                }
                board[r][c] = 0
            }
        }
        winningCells.clear()
        winningCells.addAll(originalWinningCells)
        return null
    }

    private fun resolveEnd(winLabel: String): Boolean {
        val win = winner()
        return if (win != 0) {
            gameOver = true
            status = "$winLabel - CENTER TO RESTART"
            SoundManager.playSuccess()
            onGameOver?.invoke(wins)
            true
        } else if (isDraw()) {
            gameOver = true
            status = "DRAW - CENTER TO RESTART"
            SoundManager.playError()
            onGameOver?.invoke(wins)
            true
        } else {
            false
        }
    }

    private fun winner(): Int {
        winningCells.clear()
        // Rows
        for (r in 0 until gridSize) {
            if (board[r][0] != 0) {
                var win = true
                for (c in 1 until gridSize) if (board[r][c] != board[r][0]) { win = false; break }
                if (win) {
                    repeat(gridSize) { winningCells.add(r to it) }
                    return board[r][0]
                }
            }
        }
        // Cols
        for (c in 0 until gridSize) {
            if (board[0][c] != 0) {
                var win = true
                for (r in 1 until gridSize) if (board[r][c] != board[0][c]) { win = false; break }
                if (win) {
                    repeat(gridSize) { winningCells.add(it to c) }
                    return board[0][c]
                }
            }
        }
        // Diagonals
        if (board[0][0] != 0) {
            var win = true
            for (i in 1 until gridSize) if (board[i][i] != board[0][0]) { win = false; break }
            if (win) {
                repeat(gridSize) { winningCells.add(it to it) }
                return board[0][0]
            }
        }
        if (board[0][gridSize - 1] != 0) {
            var win = true
            for (i in 1 until gridSize) if (board[i][gridSize - 1 - i] != board[0][gridSize - 1]) { win = false; break }
            if (win) {
                repeat(gridSize) { winningCells.add(it to gridSize - 1 - it) }
                return board[0][gridSize - 1]
            }
        }
        return 0
    }

    private fun isDraw(): Boolean = board.all { row -> row.none { it == 0 } }

    override fun onDraw(canvas: Canvas) {
        // Draw Wooden Background
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.WOOD, paint = paint)
        
        val size = width.coerceAtMost(height) * 0.65f
        val left = (width - size) / 2f
        val top = (height - size) / 2f + 60f
        val cell = size / gridSize

        // Draw Grid Lines
        paint.color = Color.GRAY
        paint.strokeWidth = 4f
        for (i in 1 until gridSize) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + size, paint)
            canvas.drawLine(left, top + i * cell, left + size, top + i * cell, paint)
        }

        // Draw symbols
        for (r in 0 until gridSize) for (c in 0 until gridSize) {
            val cx = left + c * cell + cell / 2
            val cy = top + r * cell + cell / 2
            
            val scale = if (board[r][c] != 0) {
                1.0f + (Math.sin(animationFrame * 0.4).toFloat() * 0.02f)
            } else 1.0f

            if (board[r][c] == 1) {
                paint.color = if (winningCells.contains(r to c)) Color.WHITE else Color.parseColor("#81C784")
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = cell * 0.6f * scale
                canvas.drawText("X", cx, cy + (cell * 0.2f) * scale, paint)
            } else if (board[r][c] == 2) {
                paint.color = if (winningCells.contains(r to c)) Color.WHITE else Color.parseColor("#E57373")
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = cell * 0.6f * scale
                canvas.drawText("O", cx, cy + (cell * 0.2f) * scale, paint)
            }
        }

        // Draw Cursor
        if (!gameOver && isPlayerTurn) {
            val pulse = (Math.sin(animationFrame * 0.3).toFloat() * 3f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f + pulse
            paint.color = Color.YELLOW
            val l = left + cursorC * cell
            val t = top + cursorR * cell
            canvas.drawRect(l + 10 - pulse/2, t + 10 - pulse/2, l + cell - 10 + pulse/2, t + cell - 10 + pulse/2, paint)
            paint.style = Paint.Style.FILL
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 38f
        val hudY1 = Math.round(60f).toFloat()
        canvas.drawText("WINS: $wins", 40f, hudY1, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("MODE: ${gridSize}x$gridSize", width - 40f, hudY1, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 42f
        val centerX = Math.round(width / 2f).toFloat()
        val hudY2 = Math.round(top - 30f).toFloat()
        canvas.drawText(status, centerX, hudY2, paint)
        
        if (gameOver) {
            paint.textSize = 32f
            paint.color = Color.LTGRAY
            canvas.drawText("UP/DOWN to change Board Size", width / 2f, height - 60f, paint)
        }
    }
}
