package com.tdpham.games.tetris

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
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import kotlin.random.Random

class TetrisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "tetris"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rows = 20
    private val cols = 10
    private val board = Array(rows) { IntArray(cols) }
    private var current = spawnPieceData()
    private var next = spawnPieceData()
    private var score = 0
    private var best = 0
    private var startLevel = 1
    private val PREFS_NAME = "tetris_settings"
    private val KEY_START_LEVEL = "start_level"
    private var hintShowFrames = 0
    private var paused = true
    private var gameOver = false
    private var currentVictoryWord = ""
    private var flashFrames = 0
    private val linesClearing = mutableListOf<Int>()
    private var lineClearAnimationTimer = 0
    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val celebrationManager = CelebrationManager()
    private val screenShake = com.tdpham.games.common.ScreenShake()
    private val handler = Handler(Looper.getMainLooper())
    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || paused) {
                celebrationManager.update()
                invalidate()
            }
            if (linesClearing.isNotEmpty()) {
                lineClearAnimationTimer--
                if (lineClearAnimationTimer <= 0) {
                    finalizeLineClear()
                }
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!paused && !gameOver && linesClearing.isEmpty()) {
                if (!tryMove(current.r + 1, current.c, current.rot)) {
                    lockPiece()
                }
                if (flashFrames > 0) flashFrames--
                invalidate()
                val currentLevel = (startLevel + score / 1000).coerceAtMost(15)
                val delay = (450 - (currentLevel - 1) * 30).coerceAtLeast(80).toLong()
                handler.postDelayed(this, delay)
            } else if (!paused && !gameOver) {
                // Keep ticking but skip movement while animating line clear
                handler.postDelayed(this, 100)
            }
        }
    }

    private var bgType = GameEnvironment.BackgroundType.GRADIENT
    private var isNight = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animHandler.post(animRunnable)
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
        // Load start level from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        startLevel = prefs.getInt(KEY_START_LEVEL, 1).coerceIn(1, 10)

        for (r in 0 until rows) {
            for (c in 0 until cols) board[r][c] = 0
        }
        score = 0
        celebrationManager.start(0f, 0f)
        best = ScoreManager.getHighScore(context, gameKey, startLevel)
        current = spawnPieceData()
        next = spawnPieceData()
        gameOver = false
        paused = true
        flashFrames = 0
        particles.clear()
        handler.removeCallbacks(tick)
        
        hintShowFrames = 100
        bgType = listOf(GameEnvironment.BackgroundType.GRADIENT, GameEnvironment.BackgroundType.GRID, GameEnvironment.BackgroundType.STRIPES).random()
        isNight = Random.nextBoolean()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
        animHandler.removeCallbacks(animRunnable)
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
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O -> {
                showOptions()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun showOptions() {
        pause()
        TetrisOptionsDialog.show(context) {
            resetGame()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (gameOver || paused) {
                if (gameOver) resetGame()
                startGame()
                return true
            }

            val x = event.x
            val y = event.y
            
            // Mouse controls:
            // Top 1/4: Rotate
            // Left 1/4: Left
            // Right 1/4: Right
            // Bottom 1/4: Hard drop
            if (y < height * 0.25f) rotate()
            else if (y > height * 0.75f) hardDrop()
            else if (x < width * 0.5f) tryMove(current.r, current.c - 1, current.rot, playSound = true)
            else tryMove(current.r, current.c + 1, current.rot, playSound = true)
            
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
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
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, startLevel)
            if (isNewHigh) {
                best = score
                currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            } else {
                currentVictoryWord = ""
            }
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isNewHigh = isNewHigh,
                score = score,
                highScore = best
            )
            SoundManager.playError()
            onGameOver?.invoke(score)
        }
    }

    private fun clearLines() {
        linesClearing.clear()
        for (r in rows - 1 downTo 0) {
            var full = true
            for (c in 0 until cols) {
                if (board[r][c] == 0) {
                    full = false
                    break
                }
            }
            if (full) {
                linesClearing.add(r)
            }
        }

        if (linesClearing.isNotEmpty()) {
            lineClearAnimationTimer = 10 // ~500ms at 20fps-ish (animRunnable)
            screenShake.trigger(10, 8f + linesClearing.size * 4f)
            SoundManager.playScore()
            invalidate()
        }
    }

    private fun finalizeLineClear() {
        if (linesClearing.isEmpty()) return
        
        score += when (linesClearing.size) {
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> 1000
        }

        val linesToClear = linesClearing.sorted() // e.g., [18, 19]
        
        for (r in linesToClear) {
            // Shift everything above r down by 1
            for (rr in r downTo 1) {
                for (cc in 0 until cols) {
                    board[rr][cc] = board[rr - 1][cc]
                }
            }
            for (cc in 0 until cols) board[0][cc] = 0
        }

        // Spawn particles at the end of animation
        for (lineY in linesClearing) {
            for (c in 0 until cols) {
                repeat(2) {
                    val angle = Random.nextDouble() * 2.0 * Math.PI
                    val speed = Random.nextFloat() * 0.4f + 0.1f
                    particles.add(GameEnvironment.Particle(
                        c.toFloat(), lineY.toFloat(), 
                        speed,
                        Math.cos(angle).toFloat() * speed,
                        Random.nextFloat() * 4f + 2f,
                        colorFor(Random.nextInt(7) + 1)
                    ))
                }
            }
        }

        linesClearing.clear()
        lineClearAnimationTimer = 0
        
        // Resume tick if not paused
        handler.removeCallbacks(tick)
        handler.post(tick)
        invalidate()
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
        val needsInvalidate = screenShake.apply(canvas)
        
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

        val size = (height / (rows + 2)).coerceAtMost(width / (cols + 8)).toFloat()
        val offsetX = (width - cols * size) / 2f
        val offsetY = (height - rows * size) / 2f

        if (needsInvalidate) invalidate()

        // Draw background
        GameEnvironment.draw(canvas, bgType, isNight = isNight, paint = paint)

        // Draw particles
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            paint.color = p.color
            paint.alpha = (p.speed * 400).toInt().coerceIn(0, 255)
            canvas.drawCircle(offsetX + p.x * size + size / 2, offsetY + p.y * size + size / 2, p.size, paint)
            p.y += (p.speed * 0.5f) // gravity-like
            p.x += p.vx
            p.speed *= 0.92f
            if (p.speed < 0.05f) iterator.remove()
        }
        paint.alpha = 255

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
                    if (linesClearing.contains(r)) {
                        // Special animation for clearing lines
                        val alpha = (lineClearAnimationTimer * 25).coerceIn(0, 255)
                        drawBlock(canvas, offsetX + c * size, offsetY + r * size, size, Color.WHITE, alpha = alpha)
                    } else {
                        drawBlock(canvas, offsetX + c * size, offsetY + r * size, size, colorFor(board[r][c]))
                    }
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
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.color = GamePalette.TEXT_PRIMARY
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = size * 0.8f
        textPaint.textAlign = Paint.Align.LEFT
        val hudX = Math.round(offsetX + cols * size + 40).toFloat()
        val hudY1 = Math.round(offsetY + size).toFloat()
        val hudY2 = Math.round(offsetY + size * 2.5f).toFloat()
        val hudY3 = Math.round(offsetY + size * 4.5f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", hudX, hudY1, textPaint)
        textPaint.color = GamePalette.TEXT_SECONDARY
        canvas.drawText("${context.getString(R.string.best_label)}: $best", hudX, hudY2, textPaint)
        
        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = size * 0.5f
            textPaint.color = Color.WHITE
            textPaint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, 60f, textPaint)
            textPaint.alpha = 255
        }

        canvas.drawText("${context.getString(R.string.next_label)}:", hudX, hudY3, textPaint)

        // Draw next piece
        for (cell in shapeCells(next.shape, next.rot)) {
            drawBlock(canvas, offsetX + (cols + 3 + cell.second) * size, offsetY + (6 + cell.first) * size, size, colorFor(next.shape + 1))
        }

        if (gameOver) {
            celebrationManager.draw(canvas)
            val title = if (currentVictoryWord.isNotEmpty()) currentVictoryWord else context.getString(R.string.game_over)
            drawOverlay(canvas, title, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        }
        else if (paused) drawOverlay(canvas, context.getString(R.string.paused), context.getString(R.string.resume_hint))
        
        if (!paused && !gameOver) {
            celebrationManager.update()
            invalidate()
        }
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
        
        val lines = subtitle.split("\n")
        var y = height / 2f + 40
        for (line in lines) {
            canvas.drawText(line, width / 2f, y, overlayPaint)
            y += overlayPaint.textSize + 10
        }
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, isGhost: Boolean = false, alpha: Int = 255) {
        if (isGhost) {
            blockPaint.color = color
            blockPaint.style = Paint.Style.STROKE
            blockPaint.strokeWidth = 3f // Thicker stroke for ghost
            blockPaint.alpha = (180 * alpha / 255) // More visible ghost
            canvas.drawRect(x + 4, y + 4, x + size - 4, y + size - 4, blockPaint)
            
            blockPaint.style = Paint.Style.FILL
            blockPaint.alpha = (40 * alpha / 255)
            canvas.drawRect(x + 4, y + 4, x + size - 4, y + size - 4, blockPaint)
            blockPaint.alpha = 255
        } else {
            blockPaint.color = color
            blockPaint.alpha = alpha
            blockPaint.style = Paint.Style.FILL
            canvas.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, blockPaint)
            // Bevel effect
            blockPaint.color = Color.WHITE
            blockPaint.alpha = (70 * alpha / 255)
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
