package com.tdpham.games.slidepuzzle

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class SlidePuzzleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "slide_puzzle"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var gridSize = 3
    private var tiles = mutableListOf<Int>()
    private var emptyIdx = 8
    private var cursorIdx = 0
    private var moves = 0
    private var bestMoves = Int.MAX_VALUE
    private var gameOver = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private var puzzleBitmap: Bitmap? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
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
        tiles = (0 until gridSize * gridSize).toMutableList()
        emptyIdx = gridSize * gridSize - 1
        shuffleTiles()
        
        celebrationManager.start(0f, 0f)
        moves = 0
        gameOver = false
        val highScore = ScoreManager.getHighScore(context, gameKey)
        bestMoves = if (highScore == 0) Int.MAX_VALUE else (10000 - highScore)
        
        if (puzzleBitmap == null) createPuzzleBitmap()
        invalidate()
    }

    private fun createPuzzleBitmap() {
        val size = 900
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Sky
        p.color = Color.parseColor("#81D4FA")
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        
        // Sun
        p.color = Color.parseColor("#FFF176")
        canvas.drawCircle(size * 0.75f, size * 0.2f, size * 0.12f, p)
        
        // Mountains
        p.color = Color.parseColor("#90A4AE")
        val path = Path()
        path.moveTo(0f, size.toFloat())
        path.lineTo(size * 0.3f, size * 0.4f)
        path.lineTo(size * 0.6f, size.toFloat())
        canvas.drawPath(path, p)
        
        p.color = Color.parseColor("#78909C")
        path.reset()
        path.moveTo(size * 0.4f, size.toFloat())
        path.lineTo(size * 0.75f, size * 0.5f)
        path.lineTo(size.toFloat(), size.toFloat())
        canvas.drawPath(path, p)

        // Grass
        p.color = Color.parseColor("#66BB6A")
        canvas.drawRect(0f, size * 0.8f, size.toFloat(), size.toFloat(), p)
        
        // A simple house
        p.color = Color.parseColor("#A1887F")
        canvas.drawRect(size * 0.15f, size * 0.7f, size * 0.35f, size * 0.85f, p)
        p.color = Color.parseColor("#D84315")
        path.reset()
        path.moveTo(size * 0.12f, size * 0.7f)
        path.lineTo(size * 0.25f, size * 0.58f)
        path.lineTo(size * 0.38f, size * 0.7f)
        canvas.drawPath(path, p)
        
        puzzleBitmap = bitmap
    }

    private fun shuffleTiles() {
        repeat(200) {
            val neighbors = getNeighbors(emptyIdx)
            val moveIdx = neighbors[Random.nextInt(neighbors.size)]
            swap(emptyIdx, moveIdx)
            emptyIdx = moveIdx
        }
        cursorIdx = emptyIdx
    }

    private fun getNeighbors(idx: Int): List<Int> {
        val r = idx / gridSize
        val c = idx % gridSize
        val neighbors = mutableListOf<Int>()
        if (r > 0) neighbors.add(idx - gridSize)
        if (r < gridSize - 1) neighbors.add(idx + gridSize)
        if (c > 0) neighbors.add(idx - 1)
        if (c < gridSize - 1) neighbors.add(idx + 1)
        return neighbors
    }

    private fun swap(i: Int, j: Int) {
        val temp = tiles[i]
        tiles[i] = tiles[j]
        tiles[j] = temp
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resetGame()
            return true
        }

        if (gameOver) return true

        val r = cursorIdx / gridSize
        val c = cursorIdx % gridSize

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (r > 0) cursorIdx -= gridSize
            KeyEvent.KEYCODE_DPAD_DOWN -> if (r < gridSize - 1) cursorIdx += gridSize
            KeyEvent.KEYCODE_DPAD_LEFT -> if (c > 0) cursorIdx -= 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (c < gridSize - 1) cursorIdx += 1
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> tryMove(cursorIdx)
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
                resetGame()
                return true
            }

            // Calculate grid bounds (must match onDraw)
            val margin = 60f
            val topArea = 140f
            val boardSize = (width - margin * 2).coerceAtMost(height - topArea - margin)
            val left = (width - boardSize) / 2f
            val top = topArea + (height - topArea - margin - boardSize) / 2f
            val tileSize = boardSize / gridSize

            if (event.x in left..(left + boardSize) && event.y in top..(top + boardSize)) {
                val c = ((event.x - left) / tileSize).toInt().coerceIn(0, gridSize - 1)
                val r = ((event.y - top) / tileSize).toInt().coerceIn(0, gridSize - 1)
                
                cursorIdx = r * gridSize + c
                tryMove(cursorIdx)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun tryMove(idx: Int) {
        if (isAdjacent(idx, emptyIdx)) {
            swap(idx, emptyIdx)
            emptyIdx = idx
            moves++
            SoundManager.playScore() // Swap event
            checkWin()
        }
    }

    private fun isAdjacent(i1: Int, i2: Int): Boolean {
        val r1 = i1 / gridSize; val c1 = i1 % gridSize
        val r2 = i2 / gridSize; val c2 = i2 % gridSize
        return (Math.abs(r1 - r2) == 1 && c1 == c2) || (Math.abs(c1 - c2) == 1 && r1 == r2)
    }

    private fun checkWin() {
        if (tiles.withIndex().all { it.value == it.index }) {
            gameOver = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            val score = (10000 - moves).coerceAtLeast(0)
            val oldHighScore = ScoreManager.getHighScore(context, gameKey)
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score)
            if (isNewHigh) bestMoves = moves
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), isWin = true, score = score, highScore = oldHighScore)
            SoundManager.playSuccess()
            onGameOver?.invoke(score)
        }
    }

    override fun onDraw(canvas: Canvas) {
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.WOOD, paint = paint)
        
        val margin = 60f
        val topArea = 140f
        val boardSize = (width - margin * 2).coerceAtMost(height - topArea - margin)
        val left = (width - boardSize) / 2f
        val top = topArea + (height - topArea - margin - boardSize) / 2f
        val tileSize = boardSize / gridSize

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.moves_label)}: $moves", 40f, 70f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: ${if (bestMoves == Int.MAX_VALUE) "-" else bestMoves}", width - 40f, 70f, paint)

        // Draw Tiles
        for (i in 0 until tiles.size) {
            val tileVal = tiles[i]
            val x = left + (i % gridSize) * tileSize
            val y = top + (i / gridSize) * tileSize
            
            if (tileVal == gridSize * gridSize - 1 && !gameOver) {
                paint.color = Color.parseColor("#121212")
                canvas.drawRect(x + 2, y + 2, x + tileSize - 2, y + tileSize - 2, paint)
            } else {
                drawTile(canvas, x, y, tileSize, tileVal)
            }
            
            if (i == cursorIdx && !gameOver) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                paint.color = Color.YELLOW
                canvas.drawRect(x + 4, y + 4, x + tileSize - 4, y + tileSize - 4, paint)
                paint.style = Paint.Style.FILL
            }
        }

        if (gameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()

            drawOverlay(canvas, currentVictoryWord, "${context.getString(R.string.moves_label)}: $moves\n${context.getString(R.string.restart_hint)}")
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 35f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 50f + i * 45f, paint)
        }
    }

    private fun drawTile(canvas: Canvas, x: Float, y: Float, size: Float, tileVal: Int) {
        puzzleBitmap?.let { bitmap ->
            val srcSize = bitmap.width / gridSize
            val srcRect = Rect((tileVal % gridSize) * srcSize, (tileVal / gridSize) * srcSize, 
                               ((tileVal % gridSize) + 1) * srcSize, ((tileVal / gridSize) + 1) * srcSize)
            val destRect = RectF(x + 2, y + 2, x + size - 2, y + size - 2)
            canvas.drawBitmap(bitmap, srcRect, destRect, paint)
            
            // Subtle bevel/border for each tile
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(100, 255, 255, 255) // Top-left highlight
            canvas.drawLine(destRect.left, destRect.top, destRect.right, destRect.top, paint)
            canvas.drawLine(destRect.left, destRect.top, destRect.left, destRect.bottom, paint)
            
            paint.color = Color.argb(100, 0, 0, 0) // Bottom-right shadow
            canvas.drawLine(destRect.right, destRect.top, destRect.right, destRect.bottom, paint)
            canvas.drawLine(destRect.left, destRect.bottom, destRect.right, destRect.bottom, paint)
            
            paint.style = Paint.Style.FILL
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        puzzleBitmap?.recycle()
        puzzleBitmap = null
    }
}
