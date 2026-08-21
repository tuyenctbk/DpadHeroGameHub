package com.tdpham.games.connectfour

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
import com.tdpham.games.common.DailyRewardManager
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ConnectFourView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "connect_four"
    override var onGameOver: ((Int) -> Unit)? = null

    companion object {
        const val COLS = 7
        const val ROWS = 6
        const val EMPTY = 0
        const val PLAYER_1 = 1
        const val PLAYER_2 = 2
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val board = Array(ROWS) { IntArray(COLS) { EMPTY } }
    private var selectedCol = 3 // Start in center
    private var currentPlayer = PLAYER_1
    private var isPlayerTurn = true
    private var isVsAi = true
    private var aiDifficulty = 1 // 0: Easy, 1: Normal, 2: Master
    private var themeIndex = 0

    private var gameOver = false
    private var winner = EMPTY // EMPTY if draw
    private val winningCells = mutableListOf<Pair<Int, Int>>()

    private var score = 0
    private var winStreak = 0
    private var moveCount = 0

    // Dropping animation state
    private var isDropping = false
    private var dropCol = -1
    private var targetRow = -1
    private var currentDropY = 0f
    private var dropSpeed = 0f
    private var dropPlayer = EMPTY

    // Celebration
    private val celebrationManager = CelebrationManager()
    private var isEndCelebration = false
    private var pulseFrame = 0

    private val gameHandler = Handler(Looper.getMainLooper())
    private val cpuMoveRunnable = Runnable { executeCpuMove() }

    private val loopRunnable = object : Runnable {
        override fun run() {
            updateGame()
            invalidate()
            gameHandler.postDelayed(this, 16)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        loadSettings()
        resetGame()
        gameHandler.post(loopRunnable)
    }

    private fun loadSettings() {
        val prefs = context.getSharedPreferences("connect_four_settings", Context.MODE_PRIVATE)
        isVsAi = (prefs.getInt(ConnectFourOptionsDialog.KEY_MODE, 0) == 0)
        aiDifficulty = prefs.getInt(ConnectFourOptionsDialog.KEY_DIFFICULTY, 1)
        themeIndex = prefs.getInt(ConnectFourOptionsDialog.KEY_THEME, 0)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {
        gameHandler.removeCallbacks(cpuMoveRunnable)
    }

    override fun resume() {
        requestFocus()
        loadSettings()
        if (!gameOver && isVsAi && !isPlayerTurn && !isDropping) {
            gameHandler.postDelayed(cpuMoveRunnable, 400)
        }
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        loadSettings()
        gameHandler.removeCallbacks(cpuMoveRunnable)

        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                board[r][c] = EMPTY
            }
        }

        selectedCol = 3
        currentPlayer = PLAYER_1
        isPlayerTurn = true
        gameOver = false
        winner = EMPTY
        winningCells.clear()
        isDropping = false
        isEndCelebration = false
        moveCount = 0
        score = ScoreManager.getHighScore(context, gameKey, aiDifficulty)

        invalidate()
    }

    private fun updateGame() {
        pulseFrame++

        if (isDropping) {
            val cellH = (height * 0.72f) / ROWS
            val startY = height * 0.16f
            val targetY = startY + targetRow * cellH + cellH / 2f

            dropSpeed += 3.2f
            currentDropY += dropSpeed

            if (currentDropY >= targetY) {
                currentDropY = targetY
                board[targetRow][dropCol] = dropPlayer
                isDropping = false
                HapticManager.vibrateClick(context)
                SoundManager.playClick()

                // Check outcome
                if (checkWin(targetRow, dropCol, dropPlayer)) {
                    handleVictory(dropPlayer)
                } else if (isBoardFull()) {
                    handleDraw()
                } else {
                    // Next turn
                    currentPlayer = if (currentPlayer == PLAYER_1) PLAYER_2 else PLAYER_1
                    if (isVsAi) {
                        isPlayerTurn = (currentPlayer == PLAYER_1)
                        if (!isPlayerTurn) {
                            gameHandler.postDelayed(cpuMoveRunnable, 500)
                        }
                    } else {
                        isPlayerTurn = true
                    }
                }
            }
        }

        if (isEndCelebration) {
            celebrationManager.update()
        }
    }

    private fun handleVictory(p: Int) {
        gameOver = true
        winner = p
        isDropping = false

        if (p == PLAYER_1 || !isVsAi) {
            winStreak++
            val ptsEarned = 100 + (max(0, 42 - moveCount) * 10) + (aiDifficulty * 50)
            score += ptsEarned
            val best = ScoreManager.getHighScore(context, gameKey, aiDifficulty)
            ScoreManager.updateHighScore(context, gameKey, score, aiDifficulty)
            DailyRewardManager.addCoins(context, 25 + (aiDifficulty * 15))
            SoundManager.playSuccess()
            HapticManager.vibrateSuccess(context)
            isEndCelebration = true
            val w = width.toFloat()
            val h = height.toFloat()
            celebrationManager.startOutcome(w, h, isWin = true, isNewHigh = score > best, score = score, highScore = best)
            onGameOver?.invoke(score)
        } else {
            winStreak = 0
            SoundManager.playError()
            HapticManager.vibrateExplosion(context)
            onGameOver?.invoke(score)
        }
    }

    private fun handleDraw() {
        gameOver = true
        winner = EMPTY
        isDropping = false
        SoundManager.playClick()
        onGameOver?.invoke(score)
    }

    private fun isBoardFull(): Boolean {
        for (c in 0 until COLS) {
            if (board[0][c] == EMPTY) return false
        }
        return true
    }

    private fun checkWin(r: Int, c: Int, player: Int): Boolean {
        val directions = arrayOf(
            Pair(0, 1),  // Horizontal
            Pair(1, 0),  // Vertical
            Pair(1, 1),  // Diagonal Down-Right
            Pair(1, -1)  // Diagonal Down-Left
        )

        for (dir in directions) {
            val line = mutableListOf<Pair<Int, Int>>()
            line.add(Pair(r, c))

            // Forward
            var step = 1
            while (true) {
                val nr = r + dir.first * step
                val nc = c + dir.second * step
                if (nr in 0 until ROWS && nc in 0 until COLS && board[nr][nc] == player) {
                    line.add(Pair(nr, nc))
                    step++
                } else break
            }

            // Backward
            step = 1
            while (true) {
                val nr = r - dir.first * step
                val nc = c - dir.second * step
                if (nr in 0 until ROWS && nc in 0 until COLS && board[nr][nc] == player) {
                    line.add(Pair(nr, nc))
                    step++
                } else break
            }

            if (line.size >= 4) {
                winningCells.clear()
                winningCells.addAll(line)
                return true
            }
        }
        return false
    }

    private fun dropDisc(col: Int): Boolean {
        if (isDropping || gameOver || col !in 0 until COLS) return false
        if (board[0][col] != EMPTY) return false // Column is full

        // Find lowest open row
        var targetR = ROWS - 1
        while (targetR >= 0 && board[targetR][col] != EMPTY) {
            targetR--
        }
        if (targetR < 0) return false

        moveCount++
        dropCol = col
        targetRow = targetR
        dropPlayer = currentPlayer
        val startY = height * 0.16f
        currentDropY = startY - 40f
        dropSpeed = 2f
        isDropping = true

        SoundManager.playClick()
        HapticManager.vibrateClick(context)
        return true
    }

    private fun executeCpuMove() {
        if (gameOver || isDropping || !isVsAi || isPlayerTurn) return

        val chosenCol = when (aiDifficulty) {
            0 -> getEasyAiMove()
            1 -> getMediumAiMove()
            else -> getMasterAiMove()
        }

        if (chosenCol in 0 until COLS) {
            dropDisc(chosenCol)
        }
    }

    private fun getEasyAiMove(): Int {
        val validCols = (0 until COLS).filter { board[0][it] == EMPTY }
        if (validCols.isEmpty()) return -1

        // Check if CPU can win in 1 move
        for (c in validCols) {
            val r = getLowestRow(c)
            if (r >= 0 && checkHypotheticalWin(r, c, PLAYER_2)) return c
        }
        // 50% chance to block player win
        if (Random.nextBoolean()) {
            for (c in validCols) {
                val r = getLowestRow(c)
                if (r >= 0 && checkHypotheticalWin(r, c, PLAYER_1)) return c
            }
        }
        return validCols.random()
    }

    private fun getMediumAiMove(): Int {
        val validCols = (0 until COLS).filter { board[0][it] == EMPTY }
        if (validCols.isEmpty()) return -1

        // 1. Take immediate win
        for (c in validCols) {
            val r = getLowestRow(c)
            if (r >= 0 && checkHypotheticalWin(r, c, PLAYER_2)) return c
        }
        // 2. Block immediate player win
        for (c in validCols) {
            val r = getLowestRow(c)
            if (r >= 0 && checkHypotheticalWin(r, c, PLAYER_1)) return c
        }
        // 3. Prefer center columns: 3, 2, 4, 1, 5, 0, 6
        val preferenceOrder = listOf(3, 2, 4, 1, 5, 0, 6)
        for (c in preferenceOrder) {
            if (c in validCols) {
                // Avoid placing where opponent gets an immediate win on top
                val r = getLowestRow(c)
                if (r > 0 && checkHypotheticalWin(r - 1, c, PLAYER_1)) {
                    continue
                }
                return c
            }
        }
        return validCols.random()
    }

    private fun getMasterAiMove(): Int {
        val validCols = (0 until COLS).filter { board[0][it] == EMPTY }
        if (validCols.isEmpty()) return -1

        var bestScore = Int.MIN_VALUE
        var bestCol = validCols.first()

        val order = listOf(3, 2, 4, 1, 5, 0, 6).filter { it in validCols }
        for (c in order) {
            val r = getLowestRow(c)
            if (r < 0) continue

            board[r][c] = PLAYER_2
            val moveScore = minimax(depth = 4, alpha = Int.MIN_VALUE, beta = Int.MAX_VALUE, isMaximizing = false)
            board[r][c] = EMPTY

            if (moveScore > bestScore) {
                bestScore = moveScore
                bestCol = c
            }
        }
        return bestCol
    }

    private fun minimax(depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        val validCols = (0 until COLS).filter { board[0][it] == EMPTY }
        if (depth == 0 || validCols.isEmpty()) {
            return evaluateBoard()
        }

        var curAlpha = alpha
        var curBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (c in validCols) {
                val r = getLowestRow(c)
                if (checkHypotheticalWin(r, c, PLAYER_2)) return 10000 + depth
                board[r][c] = PLAYER_2
                val eval = minimax(depth - 1, curAlpha, curBeta, false)
                board[r][c] = EMPTY
                maxEval = max(maxEval, eval)
                curAlpha = max(curAlpha, eval)
                if (curBeta <= curAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (c in validCols) {
                val r = getLowestRow(c)
                if (checkHypotheticalWin(r, c, PLAYER_1)) return -10000 - depth
                board[r][c] = PLAYER_1
                val eval = minimax(depth - 1, curAlpha, curBeta, true)
                board[r][c] = EMPTY
                minEval = min(minEval, eval)
                curBeta = min(curBeta, eval)
                if (curBeta <= curAlpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(): Int {
        var score = 0
        // Center column control bonus
        for (r in 0 until ROWS) {
            if (board[r][3] == PLAYER_2) score += 6
            else if (board[r][3] == PLAYER_1) score -= 6
        }
        return score
    }

    private fun getLowestRow(c: Int): Int {
        for (r in ROWS - 1 downTo 0) {
            if (board[r][c] == EMPTY) return r
        }
        return -1
    }

    private fun checkHypotheticalWin(r: Int, c: Int, player: Int): Boolean {
        board[r][c] = player
        val won = checkWinInternal(r, c, player)
        board[r][c] = EMPTY
        return won
    }

    private fun checkWinInternal(r: Int, c: Int, player: Int): Boolean {
        val dirs = arrayOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))
        for (dir in dirs) {
            var count = 1
            var step = 1
            while (true) {
                val nr = r + dir.first * step
                val nc = c + dir.second * step
                if (nr in 0 until ROWS && nc in 0 until COLS && board[nr][nc] == player) {
                    count++
                    step++
                } else break
            }
            step = 1
            while (true) {
                val nr = r - dir.first * step
                val nc = c - dir.second * step
                if (nr in 0 until ROWS && nc in 0 until COLS && board[nr][nc] == player) {
                    count++
                    step++
                } else break
            }
            if (count >= 4) return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isDropping || (!isPlayerTurn && isVsAi)) return true

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedCol > 0) {
                    selectedCol--
                    SoundManager.playClick()
                    HapticManager.vibrateClick(context)
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selectedCol < COLS - 1) {
                    selectedCol++
                    SoundManager.playClick()
                    HapticManager.vibrateClick(context)
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_DOWN -> {
                dropDisc(selectedCol)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            if (gameOver) {
                if (event.action == MotionEvent.ACTION_DOWN) resetGame()
                return true
            }
            val boardL = width * 0.12f
            val boardW = width * 0.76f
            val cellW = boardW / COLS
            val touchedCol = ((event.x - boardL) / cellW).toInt().coerceIn(0, COLS - 1)
            selectedCol = touchedCol

            if (event.action == MotionEvent.ACTION_DOWN) {
                dropDisc(touchedCol)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Background
        paint.color = Color.parseColor("#070B14")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)

        // Theme colors
        val (p1Color, p2Color, boardColor) = when (themeIndex) {
            1 -> Triple(Color.parseColor("#00E5FF"), Color.parseColor("#FF007F"), Color.parseColor("#101C38"))
            2 -> Triple(Color.parseColor("#FFD700"), Color.parseColor("#E0E0E0"), Color.parseColor("#1B263B"))
            else -> Triple(Color.parseColor("#FFD600"), Color.parseColor("#FF1744"), Color.parseColor("#0D47A1"))
        }

        // 2. Top Status Bar (Score, Turn, Streaks)
        drawHeader(canvas, w, h, p1Color, p2Color)

        // 3. Board Calculations
        val boardL = w * 0.12f
        val boardT = h * 0.18f
        val boardW = w * 0.76f
        val boardH = h * 0.74f
        val cellW = boardW / COLS
        val cellH = boardH / ROWS
        val discRadius = min(cellW, cellH) * 0.40f

        // 4. Drop Selector Indicator above top row
        if (!gameOver && (!isVsAi || isPlayerTurn)) {
            val indicatorX = boardL + selectedCol * cellW + cellW / 2f
            val indicatorY = boardT - 28f

            // Disc Preview
            paint.color = if (currentPlayer == PLAYER_1) p1Color else p2Color
            paint.alpha = 230
            canvas.drawCircle(indicatorX, indicatorY - 14f, discRadius * 0.7f, paint)

            // Pointer Arrow
            paint.color = Color.WHITE
            paint.alpha = 255
            val arrowPath = android.graphics.Path().apply {
                moveTo(indicatorX, indicatorY + 12f)
                lineTo(indicatorX - 14f, indicatorY)
                lineTo(indicatorX + 14f, indicatorY)
                close()
            }
            canvas.drawPath(arrowPath, paint)
        }

        // 5. Dropping Disc Animation
        if (isDropping) {
            val dropX = boardL + dropCol * cellW + cellW / 2f
            paint.color = if (dropPlayer == PLAYER_1) p1Color else p2Color
            paint.alpha = 255
            canvas.drawCircle(dropX, currentDropY, discRadius, paint)

            // Inner shine highlight
            paint.color = Color.WHITE
            paint.alpha = 120
            canvas.drawCircle(dropX - discRadius * 0.3f, currentDropY - discRadius * 0.3f, discRadius * 0.3f, paint)
        }

        // 6. Draw Board Shell with Cutout Circles
        // Back grid layer (holes)
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val cx = boardL + c * cellW + cellW / 2f
                val cy = boardT + r * cellH + cellH / 2f
                val cellVal = board[r][c]

                // Draw disc if present
                if (cellVal != EMPTY) {
                    val color = if (cellVal == PLAYER_1) p1Color else p2Color
                    paint.color = color
                    paint.alpha = 255
                    canvas.drawCircle(cx, cy, discRadius, paint)

                    // Disc 3D Bevel Edge
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = discRadius * 0.18f
                    paint.color = Color.BLACK
                    paint.alpha = 60
                    canvas.drawCircle(cx, cy, discRadius * 0.82f, paint)
                    paint.style = Paint.Style.FILL

                    // Highlight reflection
                    paint.color = Color.WHITE
                    paint.alpha = 110
                    canvas.drawCircle(cx - discRadius * 0.28f, cy - discRadius * 0.28f, discRadius * 0.25f, paint)
                } else {
                    // Empty dark slot
                    paint.color = Color.parseColor("#060A12")
                    paint.alpha = 255
                    canvas.drawCircle(cx, cy, discRadius, paint)
                }
            }
        }

        // Board Front Plate
        paint.color = boardColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cellW * 0.22f
        val boardRect = RectF(boardL, boardT, boardL + boardW, boardT + boardH)
        canvas.drawRoundRect(boardRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        // 7. Winning Line Animation & Halos
        if (winningCells.isNotEmpty()) {
            val pulseAlpha = (180 + (kotlin.math.sin(pulseFrame * 0.2) * 75)).toInt().coerceIn(0, 255)
            for (cell in winningCells) {
                val cx = boardL + cell.second * cellW + cellW / 2f
                val cy = boardT + cell.first * cellH + cellH / 2f

                // Outer Halo
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                paint.alpha = pulseAlpha
                canvas.drawCircle(cx, cy, discRadius + 6f, paint)

                // Gold Star
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FFD700")
                paint.alpha = pulseAlpha
                canvas.drawCircle(cx, cy, discRadius * 0.35f, paint)
            }
            paint.style = Paint.Style.FILL
        }

        // 8. Celebration Confetti
        if (isEndCelebration) {
            celebrationManager.draw(canvas)
        }

        // 9. Game Over Banner Overlay
        if (gameOver) {
            drawGameOverOverlay(canvas, w, h, p1Color, p2Color)
        }
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float, p1Color: Int, p2Color: Int) {
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 22f
        paint.color = Color.WHITE
        paint.isFakeBoldText = true

        // Mode & Streak
        val modeTitle = if (isVsAi) {
            when (aiDifficulty) {
                0 -> "VS CPU (EASY)"
                1 -> "VS CPU (NORMAL)"
                else -> "VS CPU (MASTER)"
            }
        } else "2-PLAYER BATTLE"
        canvas.drawText(modeTitle, w * 0.04f, 38f, paint)

        paint.textSize = 16f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("STREAK: $winStreak 🔥", w * 0.04f, 66f, paint)

        // Center Turn Indicator Banner
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24f
        if (!gameOver) {
            if (currentPlayer == PLAYER_1) {
                paint.color = p1Color
                val label = if (isVsAi) "YOUR TURN (YELLOW)" else "PLAYER 1 TURN"
                canvas.drawText(label, w * 0.5f, 48f, paint)
            } else {
                paint.color = p2Color
                val label = if (isVsAi) "CPU THINKING..." else "PLAYER 2 TURN"
                canvas.drawText(label, w * 0.5f, 48f, paint)
            }
        }

        // Right Score & Best
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 20f
        paint.color = Color.WHITE
        canvas.drawText("SCORE: $score", w * 0.96f, 38f, paint)

        val best = ScoreManager.getHighScore(context, gameKey, aiDifficulty)
        paint.textSize = 15f
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawText("BEST: $best", w * 0.96f, 64f, paint)
    }

    private fun drawGameOverOverlay(canvas: Canvas, w: Float, h: Float, p1Color: Int, p2Color: Int) {
        // Scrim
        paint.color = Color.parseColor("#CC000000")
        canvas.drawRect(0f, 0f, w, h, paint)

        val cardW = w * 0.60f
        val cardH = h * 0.52f
        val cardL = (w - cardW) / 2f
        val cardT = (h - cardH) / 2f
        val cardRect = RectF(cardL, cardT, cardL + cardW, cardT + cardH)

        // Card bg
        paint.color = Color.parseColor("#152033")
        canvas.drawRoundRect(cardRect, 28f, 28f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = when (winner) {
            PLAYER_1 -> p1Color
            PLAYER_2 -> p2Color
            else -> Color.LTGRAY
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        // Text
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        val (titleText, titleColor) = when {
            winner == PLAYER_1 -> Pair("VICTORY! 🏆", p1Color)
            winner == PLAYER_2 && isVsAi -> Pair("DEFEAT 💀", p2Color)
            winner == PLAYER_2 -> Pair("PLAYER 2 WINS! 👑", p2Color)
            else -> Pair("DRAW MATCH 🤝", Color.WHITE)
        }

        paint.textSize = 38f
        paint.color = titleColor
        canvas.drawText(titleText, w / 2f, cardT + 70f, paint)

        paint.textSize = 20f
        paint.color = Color.WHITE
        val subText = if (winner == PLAYER_1 || (!isVsAi && winner != EMPTY)) {
            "4 in a row achieved in $moveCount moves!"
        } else if (winner == EMPTY) {
            "Grid fully locked! No more open slots."
        } else {
            "CPU connected 4 discs first! Try again."
        }
        canvas.drawText(subText, w / 2f, cardT + 120f, paint)

        // Coin Reward pill
        if (winner == PLAYER_1) {
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 18f
            canvas.drawText("+${25 + (aiDifficulty * 15)} COINS BONUS EARNED 🪙", w / 2f, cardT + 165f, paint)
        }

        // Restart prompt button
        val btnW = 280f
        val btnH = 50f
        val btnL = (w - btnW) / 2f
        val btnT = cardT + cardH - 80f
        val btnRect = RectF(btnL, btnT, btnL + btnW, btnT + btnH)

        paint.color = Color.parseColor("#00E676")
        canvas.drawRoundRect(btnRect, 25f, 25f, paint)

        paint.color = Color.BLACK
        paint.textSize = 20f
        canvas.drawText("PRESS [ENTER] TO PLAY", w / 2f, btnT + 32f, paint)
    }
}
