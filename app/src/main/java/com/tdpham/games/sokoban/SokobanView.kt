package com.tdpham.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager

class SokobanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "sokoban"
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

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        loadLevel(0)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() {}
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        levelIndex = 0
        totalPushesAllLevels = 0
        allLevelsDone = false
        loadLevel(0)
    }

    private fun loadLevel(index: Int) {
        levelIndex = index.coerceIn(0, levels.lastIndex)
        val map = levels[levelIndex]
        rows = map.size
        cols = map[0].length
        board = Array(rows) { r -> map[r].toCharArray() }
        solved = false
        pushes = 0
        best = ScoreManager.getHighScore(context, gameKey)
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
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> toggleSound()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
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
            if (levelIndex >= levels.lastIndex) {
                allLevelsDone = true
                val score = (5000 - totalPushesAllLevels * 5).coerceAtLeast(50)
                if (ScoreManager.updateHighScore(context, gameKey, score)) best = score
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
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("LEVEL ${levelIndex + 1}/${levels.size}  PUSHES: $pushes", 30f, 52f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST: $best", width - 30f, 52f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 30f
        when {
            allLevelsDone -> {
                canvas.drawText("ALL LEVELS CLEARED! CENTER TO PLAY AGAIN", width / 2f, top - 16f, paint)
            }
            solved -> {
                canvas.drawText(
                    if (levelIndex >= levels.lastIndex) "FINAL LEVEL - CENTER TO FINISH" else "CLEARED - CENTER FOR NEXT",
                    width / 2f,
                    top - 16f,
                    paint
                )
            }
            else -> {
                canvas.drawText("CENTER: RESTART LEVEL", width / 2f, top - 16f, paint)
            }
        }
    }
}
