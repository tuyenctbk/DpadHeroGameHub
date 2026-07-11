package com.tdpham.games.twentyfortyeight

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.R
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import java.util.*

class TwentyFortyEightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "4096"
    override var onGameOver: ((Int) -> Unit)? = null
    private val gridSize = 5 // 5x5 for "4096" on larger TV screens
    private var board = Array(gridSize) { IntArray(gridSize) { 0 } }
    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isWin = false
    private var cellSize = 0f

    private val celebrationManager = CelebrationManager()
    private var animationFrame = 0
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            animationFrame++
            if (isGameOver || isWin) celebrationManager.update()
            invalidate()
            animationHandler.postDelayed(this, 50)
        }
    }

    private val mergedTiles = mutableSetOf<Pair<Int, Int>>()
    
    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animationHandler.post(animationRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationHandler.removeCallbacks(animationRunnable)
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
        celebrationManager.start(0f, 0f) // Just clear/reset
        highScore = ScoreManager.getHighScore(context, gameKey)
        mergedTiles.clear()
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
            invalidate()
            return true
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
                resetGame()
                return true
            }

            // Quadrant based swipe-tap for TV Mouse
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            val moved = if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) move(1, 0) else move(-1, 0)
            } else {
                if (y > centerY) move(0, 1) else move(0, -1)
            }

            if (moved) {
                addRandomTile()
                checkGameState()
                invalidate()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun move(dx: Int, dy: Int): Boolean {
        var moved = false
        mergedTiles.clear()
        
        if (dx != 0) { // Horizontal move
            for (r in 0 until gridSize) {
                val row = board[r]
                val newRow = if (dx < 0) shift(row, r, false) else shift(row.reversedArray(), r, true).reversedArray()
                if (!row.contentEquals(newRow)) {
                    board[r] = newRow
                    moved = true
                }
            }
        } else { // Vertical move
            for (c in 0 until gridSize) {
                val col = IntArray(gridSize) { r -> board[r][c] }
                val newCol = if (dy < 0) shift(col, c, false, true) else shift(col.reversedArray(), c, true, true).reversedArray()
                if (!col.contentEquals(newCol)) {
                    for (r in 0 until gridSize) board[r][c] = newCol[r]
                    moved = true
                }
            }
        }
        return moved
    }

    private fun shift(arr: IntArray, index: Int, reversed: Boolean, isVertical: Boolean = false): IntArray {
        val result = mutableListOf<Int>()
        val filtered = arr.filter { it != 0 }
        var i = 0
        while (i < filtered.size) {
            if (i + 1 < filtered.size && filtered[i] == filtered[i+1]) {
                val newValue = filtered[i] * 2
                result.add(newValue)
                score += newValue
                SoundManager.playScore() // Play sound only on merge
                
                val pos = result.size - 1
                val finalIdx = if (reversed) gridSize - 1 - pos else pos
                if (isVertical) mergedTiles.add(Pair(finalIdx, index))
                else mergedTiles.add(Pair(index, finalIdx))
                
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
                if (board[r][c] >= 4096 && !isWin) {
                    isWin = true
                    onGameOver?.invoke(score)
                }
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
            val oldBest = highScore
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            if (isNewHigh) highScore = score
            SoundManager.playError()
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = false, isNewHigh = isNewHigh, score = score, highScore = oldBest)
            onGameOver?.invoke(score)
        } else if (isWin) {
            val oldBest = highScore
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            if (isNewHigh) highScore = score
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = true, isNewHigh = isNewHigh, score = score, highScore = oldBest)
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
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL
        
        paint.textSize = cellSize * 0.3f
        paint.color = GamePalette.TEXT_SECONDARY
        val scoreLabelY = Math.round(offsetY - cellSize * 0.8f).toFloat()
        val scoreNumY = Math.round(offsetY - cellSize * 0.3f).toFloat()
        val labelX = Math.round(offsetX).toFloat()
        canvas.drawText(context.getString(R.string.score_label), labelX, scoreLabelY, paint)
        
        paint.textSize = cellSize * 0.5f
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("$score", labelX, scoreNumY, paint)
        
        // Draw Best (Right Side)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = cellSize * 0.3f
        paint.color = GamePalette.TEXT_SECONDARY
        val bestX = Math.round(offsetX + gridSize * cellSize).toFloat()
        canvas.drawText(context.getString(R.string.best_label), bestX, scoreLabelY, paint)

        paint.textSize = cellSize * 0.5f
        paint.color = GamePalette.SCORE
        canvas.drawText("$highScore", bestX, scoreNumY, paint)

        // Draw Grid Background
        paint.color = Color.parseColor("#333333")
        canvas.drawRoundRect(offsetX - 10, offsetY - 10, offsetX + gridSize * cellSize + 10, offsetY + gridSize * cellSize + 10, 20f, 20f, paint)

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                drawTile(canvas, r, c, offsetX, offsetY)
            }
        }

        if (isGameOver || isWin) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas)
        }
    }

    private fun drawTile(canvas: Canvas, r: Int, c: Int, offsetX: Float, offsetY: Float) {
        val value = board[r][c]
        var left = offsetX + c * cellSize + 5
        var top = offsetY + r * cellSize + 5
        var right = left + cellSize - 10
        var bottom = top + cellSize - 10

        // Subtle scale animation for merged tiles
        if (value > 0 && mergedTiles.contains(Pair(r, c))) {
            val scale = 1.0f + (Math.sin(animationFrame * 0.5).toFloat() * 0.05f).coerceAtLeast(0f)
            val cx = left + (cellSize - 10) / 2
            val cy = top + (cellSize - 10) / 2
            val halfW = (cellSize - 10) / 2 * scale
            val halfH = (cellSize - 10) / 2 * scale
            left = cx - halfW
            top = cy - halfH
            right = cx + halfW
            bottom = cy + halfH
        }

        paint.color = getTileColor(value)
        canvas.drawRoundRect(left, top, right, bottom, 15f, 15f, paint)

        if (value > 0) {
            paint.color = if (value <= 4) Color.parseColor("#776e65") else Color.WHITE
            paint.textSize = if (value < 100) cellSize * 0.4f else if (value < 1000) cellSize * 0.3f else cellSize * 0.25f
            paint.textAlign = Paint.Align.CENTER
            val text = value.toString()
            val textRect = Rect()
            paint.getTextBounds(text, 0, text.length, textRect)
            canvas.drawText(text, left + (right - left) / 2, top + (bottom - top) / 2 + textRect.height() / 2, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width / 15f
        paint.color = if (isWin) Color.GREEN else GamePalette.WARNING
        canvas.drawText(if (isWin) context.getString(R.string.win_4096_label) else context.getString(R.string.game_over), width / 2f, height / 2f - 20f, paint)
        
        paint.textSize = width / 40f
        paint.color = GamePalette.TEXT_PRIMARY
        val restartHint = context.getString(R.string.restart_hint)
        val exitHint = context.getString(R.string.exit_hint)
        canvas.drawText("${context.getString(R.string.score_label)}: $score", width / 2f, height / 2f + 50f, paint)
        canvas.drawText(restartHint, width / 2f, height / 2f + 100f, paint)
        canvas.drawText(exitHint, width / 2f, height / 2f + 140f, paint)
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
