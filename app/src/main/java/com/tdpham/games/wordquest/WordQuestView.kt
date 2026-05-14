package com.tdpham.games.wordquest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.random.Random

class WordQuestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "word_quest"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val words = listOf(
        "BRAIN", "STORM", "LIGHT", "SPACE", "GAMES", "PIXEL", "CLOCK", "POWER", "BOARD", "MUSIC",
        "HEART", "DREAM", "WORLD", "PILOT", "SHINE", "LEVEL", "TRACK", "STAGE", "MATCH", "BRICK",
        "POINT", "SNAKE", "ROBOT", "SUPER", "HERO", "DUNGE", "SOUND", "GUIDE", "MAZE", "QUICK",
        "FLASH", "TIMER", "SMART", "COLOR", "ROUND", "QUEST", "Brave", "Clear", "Earth", "Final",
        "Great", "House", "Image", "Joint", "Knife", "Large", "Model", "North", "Ocean", "Plant"
    ).map { it.uppercase() }
    private var targetWord = ""
    private val guesses = mutableListOf<String>()
    private var currentGuess = ""
    private var gameOver = false
    private var won = false
    private var score = 0
    private var best = 0

    private val usedKeys = mutableMapOf<Char, Int>() // 0:gray, 1:yellow, 2:green

    private val keyboard = arrayOf(
        "QWERTYUIOP",
        "ASDFGHJKL",
        "ZXCVBNM⌫"
    )
    private var keyR = 0
    private var keyC = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() { requestFocus() }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        targetWord = words[Random.nextInt(words.size)]
        guesses.clear()
        currentGuess = ""
        usedKeys.clear()
        gameOver = false
        won = false
        best = ScoreManager.getHighScore(context, gameKey)
        keyR = 0
        keyC = 0
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                keyR = (keyR - 1).coerceAtLeast(0)
                if (keyC >= keyboard[keyR].length) keyC = keyboard[keyR].length - 1
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                keyR = (keyR + 1).coerceAtMost(keyboard.size - 1)
                if (keyC >= keyboard[keyR].length) keyC = keyboard[keyR].length - 1
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> keyC = (keyC - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> keyC = (keyC + 1).coerceAtMost(keyboard[keyR].length - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> selectKey()
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        
        invalidate()
        return true
    }

    private fun selectKey() {
        val key = keyboard[keyR][keyC]
        if (key == '⌫') {
            if (currentGuess.isNotEmpty()) currentGuess = currentGuess.dropLast(1)
            SoundManager.playClick()
        } else {
            if (currentGuess.length < 5) {
                currentGuess += key
                SoundManager.playClick()
                if (currentGuess.length == 5) {
                    submitGuess()
                }
            }
        }
    }

    private fun submitGuess() {
        guesses.add(currentGuess)
        
        // Update keyboard colors
        for (i in currentGuess.indices) {
            val char = currentGuess[i]
            val status = if (char == targetWord[i]) 2 else if (targetWord.contains(char)) 1 else 0
            val currentStatus = usedKeys[char] ?: -1
            if (status > currentStatus) {
                usedKeys[char] = status
            }
        }

        if (currentGuess == targetWord) {
            won = true
            gameOver = true
            score = (6 - guesses.size + 1) * 1000
            if (score > best) {
                best = score
                ScoreManager.updateHighScore(context, gameKey, best)
            }
            SoundManager.playSuccess()
        } else if (guesses.size == 6) {
            gameOver = true
            SoundManager.playError()
        } else {
            SoundManager.playScore()
        }
        currentGuess = ""
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)

        val cellS = 80f
        val margin = 10f
        val startX = (width - (5 * cellS + 4 * margin)) / 2f
        val startY = 80f

        // Draw Guesses
        for (r in 0 until 6) {
            val word = if (r < guesses.size) guesses[r] else if (r == guesses.size) currentGuess else ""
            for (c in 0 until 5) {
                val char = if (c < word.length) word[c] else ' '
                val color = if (r < guesses.size) getCharColor(char, c, guesses[r]) else Color.parseColor("#333333")
                
                val x = startX + c * (cellS + margin)
                val y = startY + r * (cellS + margin)
                val rect = RectF(x, y, x + cellS, y + cellS)
                
                // Draw cell with bevel
                paint.color = color
                canvas.drawRoundRect(rect, 10f, 10f, paint)
                
                // Bevel highlight
                paint.color = Color.argb(40, 255, 255, 255)
                canvas.drawRect(x + 5, y + 5, x + cellS - 5, y + 12, paint)

                if (char != ' ') {
                    // Subtle "pop" animation by slight scaling (simulated)
                    val isCurrentChar = (r == guesses.size && c == word.length - 1)
                    val charScale = if (isCurrentChar) 1.2f else 1.0f
                    
                    paint.color = Color.WHITE
                    paint.textSize = 44f * charScale
                    paint.textAlign = Paint.Align.CENTER
                    
                    // Shadow
                    paint.color = Color.BLACK
                    canvas.drawText(char.toString(), x + cellS / 2 + 2, y + cellS / 2 + 17f, paint)
                    
                    paint.color = Color.WHITE
                    canvas.drawText(char.toString(), x + cellS / 2, y + cellS / 2 + 15f, paint)
                } else {
                    // Empty cell border
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.GRAY
                    canvas.drawRoundRect(rect, 10f, 10f, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }

        // Draw On-screen Keyboard
        val keyS = 60f
        val keyM = 8f
        val kStartY = startY + 6 * (cellS + margin) + 40f
        
        for (r in keyboard.indices) {
            val kRow = keyboard[r]
            val rowW = kRow.length * (keyS + keyM)
            val rowX = (width - rowW) / 2f
            for (c in kRow.indices) {
                val x = rowX + c * (keyS + keyM)
                val y = kStartY + r * (keyS + keyM)
                
                val isSelected = (r == keyR && c == keyC)
                val keyChar = kRow[c]
                val keyStatus = usedKeys[keyChar]
                
                // Selection Pulse
                val pulse = if (isSelected) (Math.sin(System.currentTimeMillis() / 150.0).toFloat() * 3f) else 0f
                val rect = RectF(x - pulse, y - pulse, x + keyS + pulse, y + keyS + pulse)

                paint.color = when {
                    isSelected -> Color.YELLOW
                    keyStatus == 2 -> Color.parseColor("#4CAF50")
                    keyStatus == 1 -> Color.parseColor("#FFC107")
                    keyStatus == 0 -> Color.parseColor("#424242")
                    else -> Color.GRAY
                }
                
                if (isSelected) {
                    paint.setShadowLayer(15f, 0f, 0f, Color.YELLOW)
                }
                canvas.drawRoundRect(rect, 8f, 8f, paint)
                paint.clearShadowLayer()
                
                paint.color = if (isSelected) Color.BLACK else Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(kRow[c].toString(), rect.centerX(), rect.centerY() + 10f, paint)
            }
        }

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE: $score", 40f, 40f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 40f, 40f, paint)

        if (gameOver) {
            val title = if (won) "WELL DONE!" else "OUT OF TRIES"
            val sub = if (won) "Word: $targetWord" else "Answer: $targetWord"
            drawOverlay(canvas, title, "$sub\nPress Center to Restart")
        }
    }

    private fun getCharColor(char: Char, pos: Int, guess: String): Int {
        if (char == targetWord[pos]) return Color.parseColor("#4CAF50") // Green
        
        // Count how many times this char appears in target word
        val targetCount = targetWord.count { it == char }
        if (targetCount == 0) return Color.parseColor("#757575") // Gray

        // Count how many times it was already marked Green in this guess
        var greenCount = 0
        for (i in 0 until 5) {
            if (guess[i] == char && targetWord[i] == char) greenCount++
        }

        // Count how many times it appeared BEFORE this position in the guess and was not Green
        var beforeCount = 0
        for (i in 0 until pos) {
            if (guess[i] == char && targetWord[i] != char) beforeCount++
        }

        return if (greenCount + beforeCount < targetCount) {
            Color.parseColor("#FFC107") // Yellow
        } else {
            Color.parseColor("#757575") // Gray
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 70f
        paint.color = Color.WHITE
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 30f
        val lines = sub.split("\n")
        for (i in lines.indices) {
            canvas.drawText(lines[i], width / 2f, height / 2f + 40f + i * 40f, paint)
        }
    }
}
