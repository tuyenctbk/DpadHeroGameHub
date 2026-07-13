package com.tdpham.games.wordquest

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
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import kotlin.random.Random

class WordQuestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "word_quest"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val allWords = listOf(
        "BRAIN", "STORM", "LIGHT", "SPACE", "GAMES", "PIXEL", "CLOCK", "POWER", "BOARD", "MUSIC",
        "HEART", "DREAM", "WORLD", "PILOT", "SHINE", "LEVEL", "TRACK", "STAGE", "MATCH", "BRICK",
        "POINT", "SNAKE", "ROBOT", "SUPER", "SOUND", "GUIDE", "QUICK", "FLASH", "TIMER", "SMART",
        "COLOR", "ROUND", "QUEST", "BRAVE", "CLEAR", "EARTH", "FINAL", "GREAT", "HOUSE", "IMAGE",
        "JOINT", "KNIFE", "LARGE", "MODEL", "NORTH", "OCEAN", "PLANT", "SPACE", "TOUCH", "VALVE"
    ).map { it.uppercase() }.filter { it.length == 5 }

    private val natureWords = listOf(
        "OCEAN", "PLANT", "EARTH", "WORLD", "FIELD", "WATER", "FLOWER", "RIVER", "CLOUD", "STORM",
        "GRASS", "TREES", "LEAFY", "STONE", "BEACH", "MOUNTS", "SUNNY", "RAINY", "WINDY", "FROST"
    ).map { it.uppercase() }.filter { it.length == 5 }

    private val techWords = listOf(
        "ROBOT", "PIXEL", "POWER", "CLOCK", "TIMER", "FLASH", "MODEL", "VALVE", "SPACE", "TRACK",
        "MOUSE", "BOARD", "LOGIC", "DATA", "MICRO", "CHIPY", "CODEX", "SOUND", "AUDIO", "VIDEO"
    ).map { it.uppercase() }.filter { it.length == 5 }

    private var targetWord = ""
    private val guesses = mutableListOf<String>()
    private var currentGuess = ""
    private var gameOver = false
    private var won = false
    private var score = 0
    private var best = 0
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "wordquest_settings"
    private val KEY_CATEGORY = "selected_category_index"
    private var currentCategoryIndex = 0
    private var hintShowFrames = 0
    private val cellRect = RectF()
    private val keyRect = RectF()
    private val animHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

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
        animHandler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animHandler.removeCallbacks(animRunnable)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() { requestFocus() }
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        // Load category from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentCategoryIndex = prefs.getInt(KEY_CATEGORY, 0).coerceIn(0, 2)

        val wordList = when(currentCategoryIndex) {
            1 -> natureWords
            2 -> techWords
            else -> allWords
        }

        targetWord = wordList[Random.nextInt(wordList.size)]
        guesses.clear()
        currentGuess = ""
        usedKeys.clear()
        gameOver = false
        won = false
        celebrationManager.start(0f, 0f)
        best = ScoreManager.getHighScore(context, gameKey, currentCategoryIndex)
        keyR = 0
        keyC = 0
        
        hintShowFrames = 100
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
        WordQuestOptionsDialog.show(context) {
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

            // Keyboard layout (must match onDraw)
            val cellS = 80f
            val margin = 10f
            val startY = 80f
            val keyS = 60f
            val keyM = 8f
            val kStartY = startY + 6 * (cellS + margin) + 40f
            
            val x = event.x
            val y = event.y

            for (r in keyboard.indices) {
                val kRow = keyboard[r]
                val rowW = kRow.length * (keyS + keyM)
                val rowX = (width - rowW) / 2f
                for (c in kRow.indices) {
                    val kx = rowX + c * (keyS + keyM)
                    val ky = kStartY + r * (keyS + keyM)
                    
                    if (x >= kx && x <= kx + keyS && y >= ky && y <= ky + keyS) {
                        keyR = r
                        keyC = c
                        selectKey()
                        invalidate()
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
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
            // Safety check for targetWord length
            val status = if (i < targetWord.length && char == targetWord[i]) 2 
                        else if (targetWord.contains(char)) 1 
                        else 0
            val currentStatus = usedKeys[char] ?: -1
            if (status > currentStatus) {
                usedKeys[char] = status
            }
        }

        if (currentGuess == targetWord) {
            won = true
            gameOver = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            score = (6 - guesses.size + 1) * 1000
            val oldBest = best
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentCategoryIndex)
            if (isNewHigh) best = score
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isWin = true,
                isNewHigh = isNewHigh,
                score = score,
                highScore = oldBest
            )
            SoundManager.playSuccess()
            onGameOver?.invoke(score)
        } else if (guesses.size == 6) {
            gameOver = true
            celebrationManager.startOutcome(
                width = width.toFloat(),
                height = height.toFloat(),
                isWin = false,
                score = score,
                highScore = best
            )
            SoundManager.playError()
            onGameOver?.invoke(score)
        } else {
            SoundManager.playScore()
        }
        currentGuess = ""
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)

        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }

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
                cellRect.set(x, y, x + cellS, y + cellS)
                
                // Draw cell with bevel
                paint.color = color
                canvas.drawRoundRect(cellRect, 10f, 10f, paint)
                
                // Bevel highlight
                paint.color = Color.argb(40, 255, 255, 255)
                canvas.drawRect(x + 5, y + 5, x + cellS - 5, y + 12, paint)

                if (char != ' ') {
                    // Subtle "pop" animation by slight scaling
                    val isCurrentChar = (r == guesses.size && c == word.length - 1)
                    val charScale = if (isCurrentChar) 1.2f else 1.0f
                    
                    paint.color = Color.WHITE
                    paint.textSize = 44f
                    paint.textAlign = Paint.Align.CENTER
                    
                    val textX = x + cellS / 2
                    val textY = y + cellS / 2 + 15f
                    
                    if (charScale > 1.0f) {
                        canvas.save()
                        canvas.scale(charScale, charScale, textX, textY)
                        // Shadow
                        paint.color = Color.BLACK
                        canvas.drawText(char.toString(), textX + 2, textY + 2, paint)
                        paint.color = Color.WHITE
                        canvas.drawText(char.toString(), textX, textY, paint)
                        canvas.restore()
                    } else {
                        // Shadow
                        paint.color = Color.BLACK
                        canvas.drawText(char.toString(), textX + 2, textY + 2, paint)
                        paint.color = Color.WHITE
                        canvas.drawText(char.toString(), textX, textY, paint)
                    }
                } else {
                    // Empty cell border
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.GRAY
                    canvas.drawRoundRect(cellRect, 10f, 10f, paint)
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
                keyRect.set(x - pulse, y - pulse, x + keyS + pulse, y + keyS + pulse)

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
                canvas.drawRoundRect(keyRect, 8f, 8f, paint)
                paint.clearShadowLayer()
                
                paint.color = if (isSelected) Color.BLACK else Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(kRow[c].toString(), keyRect.centerX(), keyRect.centerY() + 10f, paint)
            }
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(40f).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        val catStr = context.getString(when(currentCategoryIndex) {
            1 -> R.string.word_quest_category_nature
            2 -> R.string.word_quest_category_tech
            else -> R.string.word_quest_category_all
        })
        canvas.drawText("${context.getString(R.string.category_label)}: $catStr", width / 2f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 24f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 40f, paint)
            paint.alpha = 255
        }

        if (gameOver) {
            celebrationManager.draw(canvas)

            val title = if (won) currentVictoryWord else context.getString(R.string.out_of_tries)
            val sub = if (won) "${context.getString(R.string.game_word_quest)}: $targetWord" else "${context.getString(R.string.answer_was_label)}: $targetWord"
            drawOverlay(canvas, title, "$sub\n${context.getString(R.string.restart_hint)}")
        }
    }

    private fun getCharColor(char: Char, pos: Int, guess: String): Int {
        if (pos < targetWord.length && char == targetWord[pos]) return Color.parseColor("#4CAF50") // Green
        
        // Count how many times this char appears in target word
        val targetCount = targetWord.count { it == char }
        if (targetCount == 0) return Color.parseColor("#757575") // Gray

        // Count how many times it was already marked Green in this guess
        var greenCount = 0
        for (i in 0 until guess.length) {
            if (i < targetWord.length && guess[i] == char && targetWord[i] == char) greenCount++
        }

        // Count how many times it appeared BEFORE this position in the guess and was not Green
        var beforeCount = 0
        for (i in 0 until pos) {
            if (i < targetWord.length && guess[i] == char && targetWord[i] != char) beforeCount++
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
