package com.tdpham.games.mentalmath

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

class MentalMathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "mental_math"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val animHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (gameOver || isReviewing || isPaused || hintShowFrames > 0) {
                celebrationManager.update()
                invalidate()
            }
            animHandler.postDelayed(this, 50)
        }
    }

    private var stage = 1
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isReviewing = false
    private var isCorrect = false
    private var isPaused = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "mentalmath_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var currentMode = 1 // 0:Easy, 1:Normal, 2:Hard
    private var hintShowFrames = 0
    private var isInitialized = false

    private var question = ""
    private var correctAnswer = 0
    private val options = mutableListOf<Int>()
    private var selectedOptionIdx = 0
    
    private val timerMax = 10000L // 10 seconds per question
    private var timerStart = 0L
    private var timeLeft = timerMax

    private val nextQuestionRunnable = Runnable {
        if (!isPaused && !gameOver && isReviewing && isCorrect) {
            stage++
            generateQuestion()
            isReviewing = false
            isCorrect = false
            invalidate()
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        animHandler.post(animRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !isInitialized) {
            resetGame()
            isInitialized = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animHandler.removeCallbacks(animRunnable)
        animHandler.removeCallbacks(nextQuestionRunnable)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {
        if (!isPaused && !gameOver && !isReviewing) {
            val now = System.currentTimeMillis()
            timeLeft = (timerMax - (now - timerStart)).coerceAtLeast(0)
            isPaused = true
        }
    }

    override fun resume() {
        if (isPaused) {
            timerStart = System.currentTimeMillis() - (timerMax - timeLeft)
            isPaused = false
            invalidate()
        }
        requestFocus()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        animHandler.removeCallbacks(nextQuestionRunnable)
        // Load difficulty from settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentMode = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)

        stage = 1
        score = 0
        best = ScoreManager.getHighScore(context, gameKey, currentMode)
        gameOver = false
        isReviewing = false
        isCorrect = false
        isPaused = false
        celebrationManager.start(0f, 0f)
        generateQuestion()
        
        hintShowFrames = 100
        invalidate()
    }

    private fun generateQuestion() {
        celebrationManager.clear()
        val op = when (currentMode) {
            0 -> if (Random.nextBoolean()) "+" else "-"
            1 -> listOf("+", "-", "*").random()
            else -> listOf("+", "-", "*", "/").random()
        }

        var a = 0
        var b = 0
        
        when (op) {
            "+" -> {
                val range = (stage * 5 + 10).coerceAtMost(200)
                a = Random.nextInt(1, range)
                b = Random.nextInt(1, range)
                correctAnswer = a + b
            }
            "-" -> {
                val range = (stage * 5 + 15).coerceAtMost(200)
                a = Random.nextInt(10, range)
                b = Random.nextInt(1, a)
                correctAnswer = a - b
            }
            "*" -> {
                val rangeA = (stage / 3 + 5).coerceAtMost(20)
                val rangeB = (stage / 5 + 5).coerceAtMost(12)
                a = Random.nextInt(2, rangeA)
                b = Random.nextInt(2, rangeB)
                correctAnswer = a * b
            }
            "/" -> {
                val rangeB = (stage / 4 + 5).coerceAtMost(12)
                val rangeRes = (stage / 5 + 5).coerceAtMost(15)
                b = Random.nextInt(2, rangeB)
                val res = Random.nextInt(2, rangeRes)
                a = b * res
                correctAnswer = res
            }
        }

        question = "$a $op $b = ?"
        generateOptions()
        
        timerStart = System.currentTimeMillis()
        timeLeft = timerMax
        selectedOptionIdx = 0
        isReviewing = false
    }

    private fun generateOptions() {
        options.clear()
        options.add(correctAnswer)
        
        while (options.size < 4) {
            val decoy = when (Random.nextInt(5)) {
                0 -> correctAnswer + 10
                1 -> correctAnswer - 10
                2 -> {
                    // Same last digit decoy: e.g. 42 -> 72
                    val offset = (Random.nextInt(1, 4) * 10) * (if (Random.nextBoolean()) 1 else -1)
                    correctAnswer + offset
                }
                3 -> {
                    // Off by 1 or 2, but maybe keep same last digit logic for higher stages
                    if (stage > 10 && Random.nextBoolean()) {
                         val offset = (Random.nextInt(4, 9) * 10) * (if (Random.nextBoolean()) 1 else -1)
                         correctAnswer + offset
                    } else {
                        val smallOffset = Random.nextInt(1, 4) * (if (Random.nextBoolean()) 1 else -1)
                        correctAnswer + smallOffset
                    }
                }
                else -> {
                    // Mix digits or swap
                    if (correctAnswer > 10) {
                        val tens = correctAnswer / 10
                        val ones = correctAnswer % 10
                        tens + ones * 10
                    } else {
                        correctAnswer + Random.nextInt(5, 15)
                    }
                }
            }
            
            if (decoy != correctAnswer && decoy >= 0 && !options.contains(decoy)) {
                options.add(decoy)
            }
        }
        options.shuffle()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isPaused) return true

        if (isReviewing) {
            if (isCorrect) return true
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                endGame()
                return true
            }
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (selectedOptionIdx % 2 != 0) selectedOptionIdx--
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (selectedOptionIdx % 2 == 0) selectedOptionIdx++
            KeyEvent.KEYCODE_DPAD_UP -> if (selectedOptionIdx >= 2) selectedOptionIdx -= 2
            KeyEvent.KEYCODE_DPAD_DOWN -> if (selectedOptionIdx < 2) selectedOptionIdx += 2
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> checkAnswer()
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
        MentalMathOptionsDialog.show(context) {
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
            if (isPaused) return true

            if (isReviewing) {
                if (isCorrect) return true
                endGame()
                return true
            }

            // Options layout (must match onDraw)
            val optW = 280f
            val optH = 120f
            val centerX = width / 2f
            val centerY = height / 2f + 150f
            
            val x = event.x
            val y = event.y

            for (i in 0 until 4) {
                val r = i / 2
                val c = i % 2
                val ox = centerX + (c - 0.5f) * (optW + 40f)
                val oy = centerY + (r - 0.5f) * (optH + 40f)
                
                if (x in (ox - optW/2)..(ox + optW/2) && y in (oy - optH/2)..(oy + optH/2)) {
                    selectedOptionIdx = i
                    checkAnswer()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkAnswer() {
        isCorrect = options[selectedOptionIdx] == correctAnswer
        isReviewing = true
        if (isCorrect) {
            score += (100 * stage) + (timeLeft / 100).toInt()
            val oldBest = best
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, currentMode)
            if (isNewHigh) {
                best = score
            }
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            SoundManager.playSuccess()
            
            animHandler.removeCallbacks(nextQuestionRunnable)
            animHandler.postDelayed(nextQuestionRunnable, 1500L)
        } else {
            SoundManager.playError()
        }
    }

    private fun endGame() {
        gameOver = true
        val isNewHigh = score >= best && score > 0
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = isNewHigh,
            isNewHigh = isNewHigh,
            score = score,
            highScore = best
        )
        onGameOver?.invoke(score)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)

        if (hintShowFrames > 0) {
            hintShowFrames--
        }

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.style = Paint.Style.FILL
        val hudY = height * 0.05f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${context.getString(R.string.stage_label)}: $stage", 40f, hudY, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.LTGRAY
        val modeStr = context.getString(when(currentMode) {
            0 -> R.string.mental_math_mode_easy
            2 -> R.string.mental_math_mode_hard
            else -> R.string.mental_math_mode_normal
        })
        canvas.drawText("${context.getString(R.string.mode_label)}: $modeStr", width / 2f, hudY, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.WHITE
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 40f, hudY + 80f, paint)
            paint.alpha = 255
        }

        // Timer bar
        if (!isReviewing && !gameOver && !isPaused) {
            val now = System.currentTimeMillis()
            timeLeft = (timerMax - (now - timerStart)).coerceAtLeast(0)
            if (timeLeft <= 0) {
                isCorrect = false
                isReviewing = true
                SoundManager.playError()
            }
            
            paint.style = Paint.Style.FILL
            paint.color = if (timeLeft > 3000) Color.GREEN else Color.RED
            val barW = (width - 80f) * (timeLeft / timerMax.toFloat())
            canvas.drawRect(40f, 100f, 40f + barW, 120f, paint)
            invalidate()
        }

        // Only draw question and options if we're NOT showing a full-screen overlay (GameOver or Wrong Answer)
        val showOverlay = gameOver || (isReviewing && !isCorrect)
        if (!showOverlay) {
            // Question
            paint.color = Color.WHITE
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(question, width / 2f, height / 2f - 100f, paint)

            // If correct and reviewing, draw the victory word (e.g. "EXCELLENT!", "CORRECT!") in the middle space
            if (isReviewing && isCorrect) {
                paint.color = Color.GREEN
                paint.textSize = 48f
                canvas.drawText(currentVictoryWord, width / 2f, height / 2f + 40f, paint)
            }

            // Options
            val optW = 280f
            val optH = 120f
            val centerX = width / 2f
            val centerY = height / 2f + 150f
            
            for (i in 0 until 4) {
            val r = i / 2
            val c = i % 2
            val x = centerX + (c - 0.5f) * (optW + 40f)
            val y = centerY + (r - 0.5f) * (optH + 40f)
            
            val isSelected = (i == selectedOptionIdx)
            
            // Subtle pulse for selection
            val pulse = if (isSelected && !isReviewing) (Math.sin(System.currentTimeMillis() / 150.0).toFloat() * 5f) else 0f
            
            paint.style = Paint.Style.FILL
            paint.color = if (isSelected) Color.YELLOW else Color.parseColor("#333333")
            
            // Draw option box with bevel
            val rect = RectF(x - optW/2 - pulse, y - optH/2 - pulse, x + optW/2 + pulse, y + optH/2 + pulse)
            canvas.drawRoundRect(rect, 20f, 20f, paint)
            
            // Highlight/Shadow
            paint.color = Color.argb(40, 255, 255, 255)
            canvas.drawRect(rect.left + 5, rect.top + 5, rect.right - 5, rect.top + 12, paint)
            
            paint.color = if (isSelected) Color.BLACK else Color.WHITE
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            // Text shadow
            if (!isSelected) {
                paint.color = Color.BLACK
                canvas.drawText(options[i].toString(), x + 2, y + 18f, paint)
                paint.color = Color.WHITE
            }
            canvas.drawText(options[i].toString(), x, y + 16f, paint)
            
            if (isReviewing) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                if (options[i] == correctAnswer) {
                    paint.color = Color.GREEN
                    paint.setShadowLayer(15f, 0f, 0f, Color.GREEN)
                    canvas.drawRoundRect(rect, 20f, 20f, paint)
                    paint.clearShadowLayer()
                } else if (isSelected && !isCorrect) {
                    paint.color = Color.RED
                    paint.setShadowLayer(15f, 0f, 0f, Color.RED)
                    canvas.drawRoundRect(rect, 20f, 20f, paint)
                    paint.clearShadowLayer()
                }
            }
        }

        celebrationManager.draw(canvas)

        } // Close: if (!showOverlay)

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        } else if (isReviewing && !isCorrect) {
            val title = "${context.getString(R.string.wrong_label)} ${context.getString(R.string.answer_was_label)} $correctAnswer"
            val sub = context.getString(R.string.finish_hint)
            drawOverlay(canvas, title, sub)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 70f 
        canvas.drawText(title, width / 2f, height / 2f - 80f, paint)
        paint.textSize = 40f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 40f + i * 50f, paint)
        }
    }
}
