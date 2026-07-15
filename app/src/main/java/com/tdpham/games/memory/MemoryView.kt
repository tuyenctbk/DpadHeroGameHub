package com.tdpham.games.memory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R

class MemoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "memory"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    enum class Difficulty(val rows: Int, val cols: Int) {
        LEVEL_1(3, 4),
        LEVEL_2(4, 4),
        LEVEL_3(5, 6),
        LEVEL_4(6, 6)
    }

    private var currentDifficulty = Difficulty.LEVEL_2
    private val PREFS_NAME = "memory_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var hintShowFrames = 0
    private var rows = currentDifficulty.rows
    private var cols = currentDifficulty.cols
    private var cards = mutableListOf<Card>()
    private var cursorR = 0
    private var cursorC = 0
    private var selectedIdx1: Int? = null
    private var selectedIdx2: Int? = null
    private var moves = 0
    private var matches = 0
    private var bestMoves = Int.MAX_VALUE
    private var gameOver = false
    private var isProcessing = false
    private val celebrationManager = CelebrationManager()
    private var currentVictoryWord = ""
    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || isProcessing || hintShowFrames > 0) {
                celebrationManager.update()
                invalidate()
            }
            handler.postDelayed(this, 50)
        }
    }

    private val symbolThemes = listOf(
        listOf("🍎", "🍌", "🍒", "🍇", "🍓", "🍍", "🥝", "🍉"), // Fruits
        listOf("🐶", "🐱", "🦁", "🐯", "🐼", "🐻", "🐨", "🦊"), // Animals
        listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱"), // Sports
        listOf("🚗", "🚲", "✈️", "🚀", "⛵", "🚁", "🚂", "🛸"), // Vehicles
        listOf("🍕", "🍔", "🍟", "🌭", "🍿", "🍩", "🍪", "🍫")  // Foods
    )
    private var currentThemeIndex = -1
    private val random = java.util.Random()

    data class Card(val symbol: String, var isFlipped: Boolean = false, var isMatched: Boolean = false)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        handler.post(animRunnable)
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
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diffIndex = prefs.getInt(KEY_DIFFICULTY, 1)
        val difficulty = Difficulty.entries[diffIndex.coerceIn(0, 3)]

        val newRows = difficulty.rows
        val newCols = difficulty.cols
        
        handler.removeCallbacksAndMessages(null)
        celebrationManager.start(0f, 0f)
        
        var nextThemeIndex = random.nextInt(symbolThemes.size)
        while (nextThemeIndex == currentThemeIndex && symbolThemes.size > 1) {
            nextThemeIndex = random.nextInt(symbolThemes.size)
        }
        val themeIndex = nextThemeIndex
        val allSymbols = symbolThemes[themeIndex].shuffled()
        
        val pairCount = (newRows * newCols) / 2
        val activeSymbols = mutableListOf<String>()
        while (activeSymbols.size < pairCount) {
            val needed = pairCount - activeSymbols.size
            activeSymbols.addAll(allSymbols.shuffled().take(needed))
        }

        val deck = (activeSymbols + activeSymbols).shuffled()
        val newCards = deck.map { Card(it) }

        // Atomic update of state to avoid sync issues in onDraw
        this.currentDifficulty = difficulty
        this.currentThemeIndex = themeIndex
        this.rows = newRows
        this.cols = newCols
        this.cards = newCards.toMutableList()
        
        cursorR = 0
        cursorC = 0
        selectedIdx1 = null
        selectedIdx2 = null
        moves = 0
        matches = 0
        gameOver = false
        isProcessing = false
        // For Best Moves, we store (1000 - moves) to use higherIsBetter logic of ScoreManager
        val highScore = ScoreManager.getHighScore(context, gameKey, currentDifficulty.ordinal)
        bestMoves = if (highScore == 0) Int.MAX_VALUE else (1000 - highScore)
        
        hintShowFrames = 100
        handler.post(animRunnable)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1).coerceAtMost(rows - 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1).coerceAtMost(cols - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!isProcessing) flipCard(cursorR * cols + cursorC)
            }
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
        MemoryOptionsDialog.show(context) {
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
            if (gameOver) {
                resetGame()
                return true
            }

            if (isProcessing) return true

            // Calculate grid bounds (must match onDraw)
            val margin = 40f
            val topArea = 120f
            val boardW = width - margin * 2
            val boardH = height - topArea - margin
            val cellSize = (boardW / cols).coerceAtMost(boardH / rows)
            val left = (width - cellSize * cols) / 2f
            val top = topArea + (boardH - cellSize * rows) / 2f

            if (event.x in left..(left + cellSize * cols) && event.y in top..(top + cellSize * rows)) {
                val c = ((event.x - left) / cellSize).toInt().coerceIn(0, cols - 1)
                val r = ((event.y - top) / cellSize).toInt().coerceIn(0, rows - 1)
                
                cursorR = r
                cursorC = c
                flipCard(r * cols + c)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    private fun flipCard(idx: Int) {
        val card = cards[idx]
        if (card.isFlipped || card.isMatched || idx == selectedIdx1) return

        card.isFlipped = true
        SoundManager.playClick()
        invalidate() // Added to ensure animation if needed

        if (selectedIdx1 == null) {
            selectedIdx1 = idx
            invalidate()
        } else {
            selectedIdx2 = idx
            moves++
            isProcessing = true
            invalidate()
            handler.postDelayed({
                checkMatch()
            }, 800)
        }
    }

    private fun checkMatch() {
        val idx1 = selectedIdx1 ?: return
        val idx2 = selectedIdx2 ?: return
        val card1 = cards[idx1]
        val card2 = cards[idx2]

        if (card1.symbol == card2.symbol) {
            card1.isMatched = true
            card2.isMatched = true
            matches++
            SoundManager.playScore()
            if (matches == (cards.size / 2)) {
                gameOver = true
                currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
                val currentScore = (1000 - moves).coerceAtLeast(0)
                val oldHighScore = ScoreManager.getHighScore(context, gameKey, currentDifficulty.ordinal)
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, currentScore, currentDifficulty.ordinal)
                if (isNewHigh) {
                    bestMoves = moves
                }
                celebrationManager.startOutcome(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    isWin = true,
                    score = currentScore,
                    highScore = oldHighScore
                )
                SoundManager.playSuccess()
                onGameOver?.invoke(currentScore)
            }
        } else {
            card1.isFlipped = false
            card2.isFlipped = false
        }

        selectedIdx1 = null
        selectedIdx2 = null
        isProcessing = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.CHECKERBOARD, paint = paint)
        
        if (hintShowFrames > 0) {
            hintShowFrames--
        }

        val margin = 40f
        val topArea = 120f
        val boardW = width - margin * 2
        val boardH = height - topArea - margin
        val cellSize = (boardW / cols).coerceAtMost(boardH / rows)
        val left = (width - cellSize * cols) / 2f
        val top = topArea + (boardH - cellSize * rows) / 2f

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(60f).toFloat()
        canvas.drawText("${context.getString(R.string.moves_label)}: $moves", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: ${if (bestMoves == Int.MAX_VALUE) "-" else bestMoves}", width - 40f, hudY, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${context.getString(R.string.level_label)} ${currentDifficulty.ordinal + 1}", width / 2f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 40f, paint)
            paint.alpha = 255
            paint.textAlign = Paint.Align.CENTER
        }

        // Board
        val currentCards = cards
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (idx < currentCards.size) {
                    val card = currentCards[idx]
                    val x = left + c * cellSize
                    val y = top + r * cellSize
                    
                    drawCard(canvas, x, y, cellSize, card, !gameOver && idx == (cursorR * cols + cursorC))
                }
            }
        }

        if (gameOver) {
            celebrationManager.draw(canvas)
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

    private fun drawCard(canvas: Canvas, x: Float, y: Float, size: Float, card: Card, isCursor: Boolean) {
        val padding = size * 0.1f
        val rect = RectF(x + padding, y + padding, x + size - padding, y + size - padding)
        val radius = 16f

        if (card.isMatched) {
            // Very subtle placeholder for matched cards
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.DKGRAY
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
            return
        }

        if (isCursor) {
            // Outer glow for cursor
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(100, 255, 255, 0)
            val cursorPulse = (Math.sin(System.currentTimeMillis() / 200.0).toFloat() * 4f)
            canvas.drawRoundRect(RectF(rect).apply { inset(-8f - cursorPulse, -8f - cursorPulse) }, radius + 8, radius + 8, paint)
            
            paint.color = Color.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
            invalidate() // Ensure continuous animation for cursor pulse
        }

        if (card.isFlipped) {
            // Front of the card with subtle bevel
            paint.color = Color.WHITE
            canvas.drawRoundRect(rect, radius, radius, paint)
            
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.LTGRAY
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL

            paint.textSize = size * 0.5f
            paint.textAlign = Paint.Align.CENTER
            val fontMetrics = paint.fontMetrics
            val baseline = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
            
            // Text shadow for depth
            paint.color = Color.argb(40, 0, 0, 0)
            canvas.drawText(card.symbol, rect.centerX() + 2, baseline + 2, paint)
            
            paint.color = Color.BLACK
            canvas.drawText(card.symbol, rect.centerX(), baseline, paint)
        } else {
            // Back of the card with 3D look
            paint.color = Color.parseColor("#3F51B5")
            canvas.drawRoundRect(rect, radius, radius, paint)
            
            // Highlight (top-left)
            paint.color = Color.argb(80, 255, 255, 255)
            canvas.drawRect(rect.left + 5, rect.top + 5, rect.right - 5, rect.top + 10, paint)
            canvas.drawRect(rect.left + 5, rect.top + 5, rect.left + 10, rect.bottom - 5, paint)
            
            // Shadow (bottom-right)
            paint.color = Color.argb(80, 0, 0, 0)
            canvas.drawRect(rect.left + 5, rect.bottom - 10, rect.right - 5, rect.bottom - 5, paint)
            canvas.drawRect(rect.right - 10, rect.top + 5, rect.right - 5, rect.bottom - 5, paint)
            
            // Back pattern
            paint.color = Color.parseColor("#1A237E")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(rect.centerX(), rect.centerY(), size * 0.2f, paint)
            canvas.drawCircle(rect.centerX(), rect.centerY(), size * 0.1f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
