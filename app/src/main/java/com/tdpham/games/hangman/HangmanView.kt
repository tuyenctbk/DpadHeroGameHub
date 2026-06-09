package com.tdpham.games.hangman

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class HangmanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "hangman"
    override var onGameOver: ((Int) -> Unit)? = null

    private val words = mapOf(
        "ANIMALS" to listOf("ELEPHANT", "GIRAFFE", "KANGAROO", "PENGUIN", "HAMSTER", "LEOPARD", "DOLPHIN"),
        "FRUITS" to listOf("PINEAPPLE", "BANANA", "WATERMELON", "STRAWBERRY", "ORANGE", "BLUEBERRY"),
        "COUNTRIES" to listOf("VIETNAM", "CANADA", "BRAZIL", "GERMANY", "JAPAN", "AUSTRALIA", "FRANCE"),
        "SPORTS" to listOf("FOOTBALL", "BASKETBALL", "TENNIS", "VOLLEYBALL", "CRICKET", "SWIMMING")
    )

    private var currentCategory = ""
    private var targetWord = ""
    private var guessedLetters = mutableSetOf<Char>()
    private var remainingAttempts = 6
    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isWin = false
    private var isPaused = true

    private var cursorRow = 0
    private var cursorCol = 0
    private val alphabet = ('A'..'Z').toList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }

    private var bgType = GameEnvironment.BackgroundType.entries.random()
    private var isNight = Random().nextBoolean()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        isPaused = false
        invalidate()
    }

    override fun pause() { isPaused = true; invalidate() }
    override fun resume() { isPaused = false; invalidate() }

    override fun resetGame() {
        val categories = words.keys.toList()
        currentCategory = categories.random()
        targetWord = words[currentCategory]!!.random()
        guessedLetters.clear()
        remainingAttempts = 6
        isGameOver = false
        isWin = false
        isPaused = true
        cursorRow = 0
        cursorCol = 0
        highScore = ScoreManager.getHighScore(context, gameKey)
        bgType = GameEnvironment.BackgroundType.entries.random()
        isNight = Random().nextBoolean()
        invalidate()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver || isWin) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame(); startGame(); return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (isPaused) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                startGame(); return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorRow = (cursorRow - 1 + 3) % 3
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorRow = (cursorRow + 1) % 3
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorCol = (cursorCol - 1 + 9) % 9
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorCol = (cursorCol + 1) % 9
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> makeGuess()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
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
            if (isGameOver || isWin) {
                resetGame(); startGame(); return true
            }
            if (isPaused) {
                startGame(); return true
            }

            // Keyboard layout (must match drawKeyboard)
            val topH = height * 0.45f
            val startY = topH + 30f
            val kW = 90f; val kH = 75f; val s = 15f
            val totalW = 9 * kW + 8 * s
            val sX = (width - totalW) / 2f
            
            val x = event.x
            val y = event.y

            if (x >= sX && x <= sX + totalW && y >= startY && y <= startY + 3 * (kH + s)) {
                val c = ((x - sX) / (kW + s)).toInt().coerceIn(0, 8)
                val r = ((y - startY) / (kH + s)).toInt().coerceIn(0, 2)
                
                cursorRow = r
                cursorCol = c
                makeGuess()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun makeGuess() {
        val index = cursorRow * 9 + cursorCol
        if (index < alphabet.size) {
            val letter = alphabet[index]
            if (letter !in guessedLetters) {
                guessedLetters.add(letter)
                if (letter !in targetWord) {
                    remainingAttempts--
                    SoundManager.playError()
                    if (remainingAttempts == 0) {
                        isGameOver = true
                        onGameOver?.invoke(score)
                    }
                } else {
                    SoundManager.playClick()
                    if (targetWord.all { it in guessedLetters }) {
                        isWin = true
                        score += 10 + remainingAttempts
                        ScoreManager.updateHighScore(context, gameKey, score)
                        SoundManager.playSuccess()
                        onGameOver?.invoke(score)
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        GameEnvironment.draw(canvas, bgType, isNight = isNight, paint = paint)

        val padding = 100f
        val topH = height * 0.45f

        // Hangman
        drawHangman(canvas, padding, 120f, topH - 180f)

        // Text
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CATEGORY: $currentCategory", width / 2f + 150f, 100f, paint)

        paint.textSize = 70f
        val displayWord = targetWord.map { if (it in guessedLetters) it else '_' }.joinToString(" ")
        canvas.drawText(displayWord, width / 2f + 150f, topH / 2f + 50f, paint)

        drawKeyboard(canvas, topH + 30f)

        // HUD
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 60f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $highScore", width - 40f, 60f, paint)

        if (isGameOver) drawOverlay(canvas, "GAME OVER", "The word was: $targetWord")
        else if (isWin) drawOverlay(canvas, "YOU WON!", "Press Center to Next Word")
        else if (isPaused) drawOverlay(canvas, "HANGMAN", "Press Center to Start")
    }

    private fun drawHangman(canvas: Canvas, x: Float, y: Float, h: Float) {
        paint.color = Color.WHITE
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x, y + h, x + 200f, y + h, paint)
        canvas.drawLine(x + 50f, y + h, x + 50f, y, paint)
        canvas.drawLine(x + 50f, y, x + 150f, y, paint)
        canvas.drawLine(x + 150f, y, x + 150f, y + 40f, paint)

        val hX = x + 150f; val hY = y + 70f; val r = 30f
        if (remainingAttempts <= 5) canvas.drawCircle(hX, hY, r, paint)
        if (remainingAttempts <= 4) canvas.drawLine(hX, hY + r, hX, hY + r + 90f, paint)
        if (remainingAttempts <= 3) canvas.drawLine(hX, hY + r + 20f, hX - 40f, hY + r + 60f, paint)
        if (remainingAttempts <= 2) canvas.drawLine(hX, hY + r + 20f, hX + 40f, hY + r + 60f, paint)
        if (remainingAttempts <= 1) canvas.drawLine(hX, hY + r + 90f, hX - 40f, hY + r + 140f, paint)
        if (remainingAttempts == 0) canvas.drawLine(hX, hY + r + 90f, hX + 40f, hY + r + 140f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawKeyboard(canvas: Canvas, startY: Float) {
        val kW = 90f; val kH = 75f; val s = 15f
        val totalW = 9 * kW + 8 * s
        val sX = (width - totalW) / 2f
        for (i in alphabet.indices) {
            val r = i / 9; val c = i % 9
            val x = sX + c * (kW + s); val y = startY + r * (kH + s)
            val letter = alphabet[i]
            val isSelected = (r == cursorRow && c == cursorCol)
            val isGuessed = (letter in guessedLetters)
            paint.color = if (isSelected) Color.YELLOW else if (isGuessed) Color.DKGRAY else Color.argb(160, 255, 255, 255)
            canvas.drawRoundRect(x, y, x + kW, y + kH, 10f, 10f, paint)
            paint.color = if (isGuessed) Color.GRAY else Color.BLACK
            paint.textSize = 36f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(letter.toString(), x + kW / 2f, y + kH / 2f + 12f, paint)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER; paint.color = Color.WHITE
        paint.textSize = 90f; canvas.drawText(title, width / 2f, height / 2f, paint)
        paint.textSize = 35f; canvas.drawText(sub, width / 2f, height / 2f + 80f, paint)
    }
}
