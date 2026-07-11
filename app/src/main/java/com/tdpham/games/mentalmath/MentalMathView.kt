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

    private var stage = 1
    private var score = 0
    private var best = 0
    private var gameOver = false
    private var isReviewing = false
    private var isCorrect = false
    private var isPaused = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()

    private var question = ""
    private var correctAnswer = 0
    private val options = mutableListOf<Int>()
    private var selectedOptionIdx = 0
    
    private val timerMax = 10000L // 10 seconds per question
    private var timerStart = 0L
    private var timeLeft = timerMax

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
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
        stage = 1
        score = 0
        best = ScoreManager.getHighScore(context, gameKey)
        gameOver = false
        isReviewing = false
        isPaused = false
        celebrationManager.start(0f, 0f)
        generateQuestion()
        invalidate()
    }

    private fun generateQuestion() {
        val op = when {
            stage < 5 -> if (Random.nextBoolean()) "+" else "-"
            stage < 15 -> listOf("+", "-", "*").random()
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
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (isCorrect) {
                    stage++
                    generateQuestion()
                } else {
                    gameOver = true
                    onGameOver?.invoke(score)
                }
                invalidate()
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
            if (isPaused) return true

            if (isReviewing) {
                if (isCorrect) {
                    stage++
                    generateQuestion()
                } else {
                    gameOver = true
                    onGameOver?.invoke(score)
                }
                invalidate()
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
            if (score > best) {
                best = score
                ScoreManager.updateHighScore(context, gameKey, score)
            }
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), true, score, best)
            SoundManager.playSuccess()
        } else {
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), false, score, best)
            SoundManager.playError()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)

        // HUD
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(60f).toFloat()
        canvas.drawText("${context.getString(R.string.stage_label)}: $stage", 40f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.score_label)}: $score  ${context.getString(R.string.best_label)}: $best", width - 40f, hudY, paint)

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

        // Question
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(question, width / 2f, height / 2f - 100f, paint)

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

        if (isReviewing || gameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            
            if (isReviewing) {
                val title = if (isCorrect) currentVictoryWord else "${context.getString(R.string.wrong_label)} ${context.getString(R.string.answer_was_label)} $correctAnswer"
                val sub = if (isCorrect) context.getString(R.string.continue_hint) else context.getString(R.string.finish_hint)
                drawOverlay(canvas, title, sub)
            }
        }

        if (gameOver) {
            drawOverlay(canvas, context.getString(R.string.game_over), "${context.getString(R.string.final_score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 60f // Smaller title for long math labels
        canvas.drawText(title, width / 2f, height / 2f - 30f, paint)
        paint.textSize = 35f
        val lines = sub.split("\n")
        lines.forEachIndexed { i, s ->
            canvas.drawText(s, width / 2f, height / 2f + 50f + i * 45f, paint)
        }
    }
}
