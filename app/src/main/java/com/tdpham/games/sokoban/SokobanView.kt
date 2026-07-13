package com.tdpham.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

class SokobanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "sokoban"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val levels = listOf(
        arrayOf(
            "##########",
            "#   .    #",
            "#  $$    #",
            "#   @    #",
            "#   .    #",
            "#        #",
            "##########"
        ),
        arrayOf(
            "########",
            "#   .  #",
            "#  $   #",
            "# .$@$.#",
            "#  $   #",
            "#   .  #",
            "########"
        ),
        arrayOf(
            "#########",
            "#       #",
            "# .$$$  #",
            "# $@$   #",
            "#  $    #",
            "# . .   #",
            "#       #",
            "#########"
        ),
        arrayOf(
            "############",
            "#     @    #",
            "# $ $ $ $  #",
            "#  .....   #",
            "#          #",
            "############"
        ),
        arrayOf(
            "###########",
            "#  .....  #",
            "#  $$$$$  #",
            "# $@    $ #",
            "#  $$$$$  #",
            "#  .....  #",
            "###########"
        )
    )

    private var levelIndex = 0
    private var rows = 0
    private var cols = 0
    private lateinit var board: Array<CharArray>
    private var playerR = 0
    private var playerC = 0
    private var solved = false
    private var allLevelsDone = false
    private var best = 0
    private var pushes = 0
    private var totalPushesAllLevels = 0
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "sokoban_settings"
    private val KEY_START_LEVEL = "start_level"
    private var hintShowFrames = 0
    private val handler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (solved || allLevelsDone || hintShowFrames > 0) {
                celebrationManager.update()
                invalidate()
            }
            handler.postDelayed(this, 50)
        }
    }

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
    override fun resume() {}
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        levelIndex = (prefs.getInt(KEY_START_LEVEL, 1) - 1).coerceIn(0, levels.lastIndex)
        totalPushesAllLevels = 0
        allLevelsDone = false
        loadLevel(levelIndex)
        hintShowFrames = 100
    }

    private fun loadLevel(index: Int) {
        levelIndex = index.coerceIn(0, levels.lastIndex)
        celebrationManager.start(0f, 0f)
        val map = levels[levelIndex]
        rows = map.size
        cols = map[0].length
        board = Array(rows) { r -> map[r].toCharArray() }
        solved = false
        pushes = 0
        best = ScoreManager.getHighScore(context, gameKey, levelIndex)
        for (r in 0 until rows) for (c in 0 until cols) {
            if (board[r][c] == '@') {
                playerR = r
                playerC = c
            }
        }
        invalidate()
    }

    private fun restartCurrentLevel() {
        loadLevel(levelIndex)
    }

    private fun advanceOrRestart() {
        if (allLevelsDone) {
            resetGame()
            return
        }
        loadLevel(levelIndex + 1)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(animRunnable)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (solved || allLevelsDone) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (allLevelsDone) resetGame() else advanceOrRestart()
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> restartCurrentLevel()
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
        SokobanOptionsDialog.show(context) {
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
            if (solved || allLevelsDone) {
                if (allLevelsDone) resetGame() else advanceOrRestart()
                return true
            }

            // Quadrant based moves
            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            if (Math.abs(x - centerX) > Math.abs(y - centerY)) {
                if (x > centerX) move(0, 1) else move(0, -1)
            } else {
                if (y > centerY) move(1, 0) else move(-1, 0)
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun move(dr: Int, dc: Int) {
        if (solved || allLevelsDone) return
        val nr = playerR + dr
        val nc = playerC + dc
        if (nr !in 0 until rows || nc !in 0 until cols) return
        val next = board[nr][nc]
        if (next == '#') return
        if (next == '$' || next == '*') {
            val br = nr + dr
            val bc = nc + dc
            if (br !in 0 until rows || bc !in 0 until cols) return
            val beyond = board[br][bc]
            if (beyond == '#' || beyond == '$' || beyond == '*') return
            board[br][bc] = if (beyond == '.') '*' else '$'
            board[nr][nc] = if (next == '*') '.' else ' '
            pushes++
            totalPushesAllLevels++
            SoundManager.playScore()
        } else {
            SoundManager.playClick()
        }
        board[playerR][playerC] = if (board[playerR][playerC] == '+') '.' else ' '
        playerR = nr
        playerC = nc
        board[playerR][playerC] = if (board[playerR][playerC] == '.') '+' else '@'
        if (isSolved()) {
            solved = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            val score = (5000 - totalPushesAllLevels * 5).coerceAtLeast(50)
            val oldBest = best
            if (levelIndex >= levels.lastIndex) {
                allLevelsDone = true
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, levelIndex)
                if (isNewHigh) best = score
                celebrationManager.startOutcome(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    isWin = true,
                    isNewHigh = isNewHigh,
                    score = score,
                    highScore = oldBest
                )
                onGameOver?.invoke(score)
            } else {
                val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, levelIndex)
                if (isNewHigh) best = score
                celebrationManager.startOutcome(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    isWin = true,
                    isNewHigh = isNewHigh,
                    score = score,
                    highScore = oldBest
                )
            }
            SoundManager.playSuccess()
        }
    }

    private fun isSolved(): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) if (board[r][c] == '$') return false
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(GamePalette.BACKGROUND)

        if (hintShowFrames > 0) {
            hintShowFrames--
        }

        val cell = (width.coerceAtMost(height) / (maxOf(cols, rows) + 2)).toFloat()
        val left = (width - cols * cell) / 2f
        val top = (height - rows * cell) / 2f + 40f
        for (r in 0 until rows) for (c in 0 until cols) {
            val ch = board[r][c]
            val x = left + c * cell
            val y = top + r * cell
            paint.style = Paint.Style.FILL
            paint.color = when (ch) {
                '#' -> Color.DKGRAY
                '.', '+', '*' -> Color.parseColor("#455A64")
                else -> Color.parseColor("#263238")
            }
            canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, paint)
            when (ch) {
                '#' -> {
                    paint.color = Color.DKGRAY
                    canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, paint)
                    // Bevel for walls
                    paint.color = Color.WHITE
                    paint.alpha = 50
                    canvas.drawRect(x + 2, y + 2, x + cell * 0.4f, y + cell * 0.4f, paint)
                    paint.alpha = 255
                }
                '$', '*' -> {
                    paint.color = Color.parseColor("#FFB300")
                    canvas.drawRect(x + cell * 0.2f, y + cell * 0.2f, x + cell * 0.8f, y + cell * 0.8f, paint)
                    // Bevel for crates
                    paint.color = Color.WHITE
                    paint.alpha = 100
                    canvas.drawRect(x + cell * 0.2f, y + cell * 0.2f, x + cell * 0.45f, y + cell * 0.45f, paint)
                    paint.alpha = 255
                }
                '@', '+' -> {
                    paint.color = Color.parseColor("#66BB6A")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.28f, paint)
                    // Simple face for player
                    paint.color = Color.WHITE
                    canvas.drawCircle(x + cell * 0.42f, y + cell * 0.45f, cell * 0.05f, paint)
                    canvas.drawCircle(x + cell * 0.58f, y + cell * 0.45f, cell * 0.05f, paint)
                }
            }
            if (ch == '.' || ch == '+' || ch == '*') {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.CYAN
                canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
            }
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.LEFT
        val hudY = Math.round(52f).toFloat()
        canvas.drawText("${context.getString(R.string.level_label)} ${levelIndex + 1}/${levels.size}  ${context.getString(R.string.pushes_label)}: $pushes", 30f, hudY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${context.getString(R.string.best_label)}: $best", width - 30f, hudY, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 30f
        val centerX = Math.round(width / 2f).toFloat()
        val textY = Math.round(top - 16f).toFloat()

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 30f, hudY + 45f, paint)
            paint.alpha = 255
            paint.textAlign = Paint.Align.CENTER
        }

        when {
            allLevelsDone -> {
                canvas.drawText(currentVictoryWord, centerX, textY, paint)
            }
            solved -> {
                canvas.drawText(
                    if (levelIndex >= levels.lastIndex) currentVictoryWord else context.getString(R.string.grid_mastered_label),
                    centerX,
                    textY,
                    paint
                )
            }
            else -> {
                canvas.drawText(context.getString(R.string.sokoban_restart_hint), centerX, textY, paint)
            }
        }

        if (solved || allLevelsDone) {
            celebrationManager.draw(canvas)
            
            if (allLevelsDone) {
                drawOverlay(canvas, currentVictoryWord, "${context.getString(R.string.pushes_label)}: $totalPushesAllLevels\n${context.getString(R.string.restart_hint)}")
            } else if (solved) {
                drawOverlay(canvas, context.getString(R.string.grid_mastered_label), context.getString(R.string.play_again_hint))
            }
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
}
