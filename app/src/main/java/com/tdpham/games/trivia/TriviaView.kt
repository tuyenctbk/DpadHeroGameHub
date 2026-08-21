package com.tdpham.games.trivia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.tdpham.games.R
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.common.GameView
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.math.max

class TriviaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView, TriviaEngine.TriviaEngineListener {

    override var gameKey: String = "trivia"
    override var onGameOver: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val engine = TriviaEngine(context)

    // Focus state for TV D-Pad: 0..3 for options A..D, 4: 50:50, 5: Freeze, 6: Skip
    private var focusedIndex = 0
    private var selectedIndex = -1
    private var statusMessage = ""
    private var explanationText = ""
    private var timerFraction = 1f

    private val celebrationManager = CelebrationManager()
    private var isEndCelebration = false

    private val loopHandler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (isEndCelebration) {
                celebrationManager.update()
            }
            invalidate()
            loopHandler.postDelayed(this, 30)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        engine.listener = this
        resetGame()
        loopHandler.post(loopRunnable)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {
        // Engine handles background/pause gracefully
    }

    override fun resume() {
        requestFocus()
        engine.loadSettings()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        isEndCelebration = false
        celebrationManager.clear()
        focusedIndex = 0
        selectedIndex = -1
        explanationText = ""
        statusMessage = "LOADING GAUNTLET..."
        engine.startGauntlet(fetchFromApi = true)
        invalidate()
    }

    // --- TriviaEngineListener Callbacks ---

    override fun onEngineStateChanged(state: TriviaEngine.EngineState) {
        when (state) {
            TriviaEngine.EngineState.LOADING -> {
                statusMessage = "LOADING TRIVIA QUESTIONS..."
            }
            TriviaEngine.EngineState.QUESTION_ACTIVE -> {
                selectedIndex = -1
                explanationText = ""
                statusMessage = "QUESTION ${engine.currentIndex + 1} OF ${engine.activeQuestions.size}"
            }
            TriviaEngine.EngineState.ANSWER_REVEALED -> {
                // Handled in onAnswerEvaluated
            }
            TriviaEngine.EngineState.GAUNTLET_COMPLETED -> {
                statusMessage = "GAUNTLET COMPLETE!"
            }
            else -> {}
        }
        invalidate()
    }

    override fun onQuestionReady(question: TriviaQuestion, index: Int, total: Int) {
        statusMessage = "QUESTION ${index + 1} OF $total"
        timerFraction = 1f
        focusedIndex = 0
        invalidate()
    }

    override fun onTimerTick(remainingSeconds: Float, timeFraction: Float) {
        this.timerFraction = timeFraction
        invalidate()
    }

    override fun onAnswerEvaluated(
        isCorrect: Boolean,
        selectedIndex: Int,
        correctIndex: Int,
        pointsEarned: Int,
        currentScore: Int,
        streak: Int,
        explanation: String
    ) {
        this.selectedIndex = selectedIndex
        this.explanationText = explanation

        if (isCorrect) {
            HapticManager.vibrateSuccess(context)
            statusMessage = "CORRECT! +$pointsEarned PTS 🔥"
        } else {
            HapticManager.vibrateExplosion(context)
            statusMessage = if (selectedIndex == -1) "TIME'S UP! ⏰" else "INCORRECT! ❌"
        }
        invalidate()
    }

    override fun onLifelineUsed(type: TriviaEngine.LifelineType, eliminatedIndices: Set<Int>) {
        HapticManager.vibrateClick(context)
        invalidate()
    }

    override fun onGauntletCompleted(
        finalScore: Int,
        correctCount: Int,
        totalQuestions: Int,
        isNewHighScore: Boolean
    ) {
        if (correctCount >= totalQuestions / 2) {
            isEndCelebration = true
            val w = width.toFloat()
            val h = height.toFloat()
            celebrationManager.startOutcome(
                width = w,
                height = h,
                isWin = true,
                isNewHigh = isNewHighScore,
                score = finalScore,
                highScore = ScoreManager.getHighScore(context, gameKey)
            )
            HapticManager.vibrateSuccess(context)
        }
        onGameOver?.invoke(finalScore)
        invalidate()
    }

    // --- D-Pad Controller & Touch Input ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (engine.state == TriviaEngine.EngineState.GAUNTLET_COMPLETED) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (engine.state != TriviaEngine.EngineState.QUESTION_ACTIVE) return true

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (focusedIndex) {
                    2 -> focusedIndex = 0
                    3 -> focusedIndex = 1
                    4, 5, 6 -> focusedIndex = 2
                }
                SoundManager.playDpadMove()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (focusedIndex) {
                    0 -> focusedIndex = 2
                    1 -> focusedIndex = 3
                    2, 3 -> focusedIndex = 4
                }
                SoundManager.playDpadMove()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (focusedIndex) {
                    1 -> focusedIndex = 0
                    3 -> focusedIndex = 2
                    5 -> focusedIndex = 4
                    6 -> focusedIndex = 5
                }
                SoundManager.playDpadMove()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (focusedIndex) {
                    0 -> focusedIndex = 1
                    2 -> focusedIndex = 3
                    4 -> focusedIndex = 5
                    5 -> focusedIndex = 6
                }
                SoundManager.playDpadMove()
                HapticManager.vibrateClick(context)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                when (focusedIndex) {
                    in 0..3 -> {
                        if (!engine.eliminatedIndices.contains(focusedIndex)) {
                            SoundManager.playDpadSelect()
                            engine.evaluateAnswer(focusedIndex)
                        }
                    }
                    4 -> engine.use5050Lifeline()
                    5 -> engine.useFreezeLifeline()
                    6 -> engine.useSkipLifeline()
                }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                engine.use5050Lifeline()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                engine.useFreezeLifeline()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB -> {
                engine.useSkipLifeline()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            val w = width.toFloat()
            val h = height.toFloat()
            val x = event.x
            val y = event.y

            if (engine.state == TriviaEngine.EngineState.GAUNTLET_COMPLETED) {
                resetGame()
                return true
            }

            if (engine.state != TriviaEngine.EngineState.QUESTION_ACTIVE) return true

            // Check options A, B, C, D
            val cardL = w * 0.08f
            val cardW = w * 0.84f
            val startY = h * 0.46f
            val optW = (cardW - 20f) / 2f
            val optH = h * 0.17f

            for (i in 0..3) {
                val row = i / 2
                val col = i % 2
                val ox = cardL + col * (optW + 20f)
                val oy = startY + row * (optH + 16f)
                val rect = RectF(ox, oy, ox + optW, oy + optH)
                if (rect.contains(x, y) && !engine.eliminatedIndices.contains(i)) {
                    focusedIndex = i
                    SoundManager.playDpadSelect()
                    engine.evaluateAnswer(i)
                    return true
                }
            }

            // Check lifelines
            val ly = h * 0.86f
            val btnW = w * 0.24f
            val btnH = 46f
            val spacing = w * 0.04f
            val lStartX = (w - (3 * btnW + 2 * spacing)) / 2f

            for (i in 0..2) {
                val lx = lStartX + i * (btnW + spacing)
                val lRect = RectF(lx, ly, lx + btnW, ly + btnH)
                if (lRect.contains(x, y)) {
                    when (i) {
                        0 -> engine.use5050Lifeline()
                        1 -> engine.useFreezeLifeline()
                        2 -> engine.useSkipLifeline()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Background
        paint.color = Color.parseColor("#0A061C")
        canvas.drawRect(0f, 0f, w, h, paint)

        // Radial ambient glow
        paint.color = Color.parseColor("#1A1140")
        canvas.drawCircle(w * 0.5f, h * 0.45f, w * 0.45f, paint)

        // 2. Header
        drawHeader(canvas, w, h)

        if (engine.state == TriviaEngine.EngineState.GAUNTLET_COMPLETED) {
            drawSummaryCard(canvas, w, h)
        } else {
            // 3. Question Card & Timer Bar
            drawQuestionCard(canvas, w, h)

            // 4. Options A, B, C, D
            drawOptionCards(canvas, w, h)

            // 5. Lifelines Bottom Bar
            drawLifelines(canvas, w, h)
        }

        // 6. Celebration
        if (isEndCelebration) {
            celebrationManager.draw(canvas)
        }
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.color = Color.WHITE

        val catLabel = engine.currentQuestion?.category ?: "TRIVIA ARENA"
        canvas.drawText(catLabel.uppercase(), w * 0.05f, 36f, paint)

        paint.textSize = 15f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("STREAK: ${engine.streak} 🔥", w * 0.05f, 60f, paint)

        // Center Status Banner
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 22f
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawText(statusMessage, w * 0.5f, 44f, paint)

        // Score Right
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 20f
        paint.color = Color.parseColor("#00E676")
        canvas.drawText("SCORE: ${engine.score}", w * 0.95f, 36f, paint)

        val best = ScoreManager.getHighScore(context, gameKey)
        paint.textSize = 15f
        paint.color = Color.WHITE
        canvas.drawText("BEST: $best", w * 0.95f, 60f, paint)
    }

    private fun drawQuestionCard(canvas: Canvas, w: Float, h: Float) {
        val q = engine.currentQuestion ?: return
        val cardL = w * 0.08f
        val cardT = h * 0.14f
        val cardW = w * 0.84f
        val cardH = h * 0.28f
        val cardRect = RectF(cardL, cardT, cardL + cardW, cardT + cardH)

        // Question card background
        paint.color = Color.parseColor("#16103A")
        canvas.drawRoundRect(cardRect, 22f, 22f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#7C4DFF")
        canvas.drawRoundRect(cardRect, 22f, 22f, paint)
        paint.style = Paint.Style.FILL

        // Timer Bar at top of card
        if (!engine.isUntimed) {
            val barW = (cardW - 8f) * timerFraction.coerceIn(0f, 1f)
            val timerRect = RectF(cardL + 4f, cardT + 4f, cardL + 4f + barW, cardT + 12f)

            paint.color = when {
                engine.isTimerFrozen -> Color.parseColor("#00E5FF")
                timerFraction < 0.25f -> Color.parseColor("#FF1744")
                timerFraction < 0.50f -> Color.parseColor("#FFD600")
                else -> Color.parseColor("#00E676")
            }
            canvas.drawRoundRect(timerRect, 4f, 4f, paint)
        }

        // Question text
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 21f
        paint.color = Color.WHITE
        paint.isFakeBoldText = true

        val lines = wrapText(q.question, cardW * 0.90f, paint)
        val lineHeight = 30f
        val startY = cardT + (cardH - lines.size * lineHeight) / 2f + 20f
        for (i in lines.indices) {
            canvas.drawText(lines[i], w * 0.5f, startY + i * lineHeight, paint)
        }

        // Explanation text on Reveal
        if (engine.state == TriviaEngine.EngineState.ANSWER_REVEALED && explanationText.isNotEmpty()) {
            paint.textSize = 14f
            paint.color = Color.parseColor("#FFCA28")
            canvas.drawText("💡 $explanationText", w * 0.5f, cardT + cardH - 16f, paint)
        }
    }

    private fun drawOptionCards(canvas: Canvas, w: Float, h: Float) {
        val q = engine.currentQuestion ?: return
        val cardL = w * 0.08f
        val cardW = w * 0.84f
        val startY = h * 0.46f
        val optW = (cardW - 20f) / 2f
        val optH = h * 0.17f
        val badges = listOf("A", "B", "C", "D")

        for (i in 0..3) {
            val row = i / 2
            val col = i % 2
            val x = cardL + col * (optW + 20f)
            val y = startY + row * (optH + 16f)
            val rect = RectF(x, y, x + optW, y + optH)

            val isEliminated = engine.eliminatedIndices.contains(i)
            val isFocused = (focusedIndex == i && engine.state == TriviaEngine.EngineState.QUESTION_ACTIVE)
            val isSelected = (selectedIndex == i)
            val isCorrect = (q.correctIndex == i)
            val isRevealed = (engine.state == TriviaEngine.EngineState.ANSWER_REVEALED)

            // Card Color
            paint.color = when {
                isEliminated -> Color.parseColor("#0F0B24")
                isRevealed && isCorrect -> Color.parseColor("#1B5E20") // Glowing Green
                isRevealed && isSelected -> Color.parseColor("#B71C1C") // Crimson Red
                isFocused -> Color.parseColor("#281C5C")
                else -> Color.parseColor("#1A1342")
            }
            canvas.drawRoundRect(rect, 18f, 18f, paint)

            // Focus / State Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isFocused) 5f else 2f
            paint.color = when {
                isEliminated -> Color.parseColor("#2A2055")
                isRevealed && isCorrect -> Color.parseColor("#00E676")
                isRevealed && isSelected -> Color.parseColor("#FF1744")
                isFocused -> Color.parseColor("#FFD700")
                else -> Color.parseColor("#4A3B8C")
            }
            canvas.drawRoundRect(rect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL

            if (isEliminated) continue

            // Badge (A, B, C, D)
            val badgeX = x + 36f
            val badgeY = y + optH / 2f
            paint.color = if (isFocused) Color.parseColor("#FFD700") else Color.parseColor("#6200EA")
            canvas.drawCircle(badgeX, badgeY, 18f, paint)

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 16f
            paint.color = if (isFocused) Color.BLACK else Color.WHITE
            paint.isFakeBoldText = true
            canvas.drawText(badges[i], badgeX, badgeY + 6f, paint)

            // Option text
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 17f
            paint.color = Color.WHITE
            val optText = if (i < q.options.size) q.options[i] else ""
            val optLines = wrapText(optText, optW - 80f, paint)
            val optLineH = 22f
            val optStartY = y + (optH - optLines.size * optLineH) / 2f + 14f

            for (l in optLines.indices) {
                canvas.drawText(optLines[l], x + 66f, optStartY + l * optLineH, paint)
            }
        }
    }

    private fun drawLifelines(canvas: Canvas, w: Float, h: Float) {
        val y = h * 0.86f
        val btnW = w * 0.24f
        val btnH = 46f
        val spacing = w * 0.04f
        val startX = (w - (3 * btnW + 2 * spacing)) / 2f

        val lifelines = listOf(
            Triple("50:50 ✂️", engine.is5050Used, 4),
            Triple("FREEZE ❄️", engine.isFreezeUsed, 5),
            Triple("SKIP ⏩", engine.isSkipUsed, 6)
        )

        for (i in lifelines.indices) {
            val (label, isUsed, focusIdx) = lifelines[i]
            val x = startX + i * (btnW + spacing)
            val rect = RectF(x, y, x + btnW, y + btnH)
            val isFocused = (focusedIndex == focusIdx && engine.state == TriviaEngine.EngineState.QUESTION_ACTIVE)

            paint.color = if (isUsed) Color.parseColor("#15112B") else Color.parseColor("#2D1B69")
            canvas.drawRoundRect(rect, 14f, 14f, paint)

            if (isFocused && !isUsed) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.parseColor("#FFD700")
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.style = Paint.Style.FILL
            }

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 15f
            paint.color = if (isUsed) Color.parseColor("#666666") else Color.WHITE
            paint.isFakeBoldText = true
            canvas.drawText(label, x + btnW / 2f, y + 28f, paint)
        }
    }

    private fun drawSummaryCard(canvas: Canvas, w: Float, h: Float) {
        val cardW = w * 0.64f
        val cardH = h * 0.62f
        val cardL = (w - cardW) / 2f
        val cardT = (h - cardH) / 2f
        val cardRect = RectF(cardL, cardT, cardL + cardW, cardT + cardH)

        paint.color = Color.parseColor("#19103C")
        canvas.drawRoundRect(cardRect, 28f, 28f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(cardRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        paint.textSize = 34f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("GAUNTLET COMPLETED! 🏆", w * 0.5f, cardT + 65f, paint)

        paint.textSize = 22f
        paint.color = Color.WHITE
        canvas.drawText("Final Score: ${engine.score} Points", w * 0.5f, cardT + 120f, paint)

        paint.textSize = 18f
        paint.color = Color.parseColor("#00E5FF")
        val totalQ = max(1, engine.activeQuestions.size)
        canvas.drawText("Accuracy: ${engine.correctCount} / $totalQ Correct (${(engine.correctCount * 100) / totalQ}%)", w * 0.5f, cardT + 160f, paint)

        val totalCoins = engine.correctCount * 10 + if (engine.correctCount == totalQ) 100 else engine.correctCount * 5
        paint.textSize = 18f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("+$totalCoins Coins Earned 🪙", w * 0.5f, cardT + 200f, paint)

        // Play Again Button
        val btnW = 280f
        val btnH = 50f
        val btnL = (w - btnW) / 2f
        val btnT = cardT + cardH - 80f
        val btnRect = RectF(btnL, btnT, btnL + btnW, btnT + btnH)

        paint.color = Color.parseColor("#00E676")
        canvas.drawRoundRect(btnRect, 25f, 25f, paint)

        paint.color = Color.BLACK
        paint.textSize = 20f
        canvas.drawText("PLAY AGAIN [ENTER]", w * 0.5f, btnT + 32f, paint)
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loopHandler.removeCallbacksAndMessages(null)
        engine.release()
    }
}
