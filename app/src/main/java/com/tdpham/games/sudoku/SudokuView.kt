package com.tdpham.games.sudoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager

class SudokuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "sudoku"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val puzzleBaseA: Array<IntArray> = arrayOf(
        intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
        intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
        intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
        intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
        intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
        intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
        intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
        intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
        intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9)
    )

    /** Second classic grid; rotations keep givens consistent and solvable. */
    private val puzzleBaseB: Array<IntArray> = arrayOf(
        intArrayOf(0, 0, 0, 6, 0, 0, 4, 0, 0),
        intArrayOf(7, 0, 0, 0, 0, 3, 6, 0, 0),
        intArrayOf(0, 0, 0, 0, 9, 1, 0, 8, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 5, 0, 1, 8, 0, 0, 0, 3),
        intArrayOf(0, 0, 0, 3, 0, 6, 0, 4, 5),
        intArrayOf(0, 4, 0, 2, 0, 0, 0, 6, 0),
        intArrayOf(9, 0, 3, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 2, 0, 0, 0, 0, 1, 0, 0)
    )

    private val puzzles: List<Array<IntArray>> = buildList {
        fun rotate90(p: Array<IntArray>): Array<IntArray> =
            Array(9) { r -> IntArray(9) { c -> p[8 - c][r] } }
        fun addFourRotations(p: Array<IntArray>) {
            var cur = p
            repeat(4) {
                add(Array(9) { r -> cur[r].clone() })
                cur = rotate90(cur)
            }
        }
        addFourRotations(puzzleBaseA)
        addFourRotations(puzzleBaseB)
    }

    private var puzzleIndex = 0
    private lateinit var given: Array<BooleanArray>
    private lateinit var board: Array<IntArray>
    private var cursorR = 0
    private var cursorC = 0
    private var solved = false
    private var best = 0

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Int>()
    private val moveRunnable = object : Runnable {
        override fun run() {
            if (pressedKeys.isNotEmpty() && !solved) {
                var moved = false
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) { cursorR = (cursorR + 8) % 9; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) { cursorR = (cursorR + 1) % 9; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) { cursorC = (cursorC + 8) % 9; moved = true }
                if (pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) { cursorC = (cursorC + 1) % 9; moved = true }
                
                if (moved) {
                    invalidate()
                    handler.postDelayed(this, 150) // Repeat speed
                }
            }
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        loadPuzzle(0)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() {}
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        puzzleIndex = 0
        loadPuzzle(0)
    }

    private fun loadPuzzle(index: Int) {
        puzzleIndex = index % puzzles.size
        val p = puzzles[puzzleIndex]
        board = Array(9) { r -> p[r].clone() }
        given = Array(9) { r -> BooleanArray(9) { c -> p[r][c] != 0 } }
        cursorR = 0
        cursorC = 0
        solved = false
        best = ScoreManager.getHighScore(context, gameKey)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (solved && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            loadPuzzle(puzzleIndex + 1)
            return true
        }
        
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, 
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (pressedKeys.isEmpty()) {
                    // First press: move immediately
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR + 8) % 9
                        KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1) % 9
                        KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC + 8) % 9
                        KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1) % 9
                    }
                    invalidate()
                    handler.removeCallbacks(moveRunnable)
                    handler.postDelayed(moveRunnable, 400) // Delay before auto-repeat
                }
                pressedKeys.add(keyCode)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                cycleCell()
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                toggleSound()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (solved) {
                loadPuzzle(puzzleIndex + 1)
                return true
            }

            // Calculate grid bounds (must match onDraw)
            val grid = width.coerceAtMost(height) * 0.78f
            val left = (width - grid) / 2f
            val top = (height - grid) / 2f + 32f
            val cell = grid / 9f

            if (event.x in left..(left + grid) && event.y in top..(top + grid)) {
                val c = ((event.x - left) / cell).toInt().coerceIn(0, 8)
                val r = ((event.y - top) / cell).toInt().coerceIn(0, 8)
                
                if (r == cursorR && c == cursorC) {
                    cycleCell()
                } else {
                    cursorR = r
                    cursorC = c
                    SoundManager.playClick()
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        pressedKeys.remove(keyCode)
        if (pressedKeys.isEmpty()) {
            handler.removeCallbacks(moveRunnable)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(moveRunnable)
    }

    /** Cycle 0 (empty) → 1 → … → 9 → 0 on editable cells. */
    private fun cycleCell() {
        if (given[cursorR][cursorC] || solved) return
        board[cursorR][cursorC] = (board[cursorR][cursorC] + 1) % 10
        SoundManager.playClick()
        if (isCompleteAndValid()) {
            solved = true
            val score = (600 + (puzzles.size - puzzleIndex) * 25).coerceAtLeast(100)
            if (ScoreManager.updateHighScore(context, gameKey, score)) best = score
            SoundManager.playSuccess()
            onGameOver?.invoke(score)
        }
    }

    private fun isCompleteAndValid(): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) {
            if (board[r][c] == 0) return false
            if (hasConflict(r, c)) return false
        }
        return true
    }

    private fun hasConflict(r: Int, c: Int): Boolean {
        val v = board[r][c]
        if (v == 0) return false
        for (cc in 0 until 9) {
            if (cc != c && board[r][cc] == v) return true
        }
        for (rr in 0 until 9) {
            if (rr != r && board[rr][c] == v) return true
        }
        val br = r / 3 * 3
        val bc = c / 3 * 3
        for (rr in br until br + 3) {
            for (cc in bc until bc + 3) {
                if ((rr != r || cc != c) && board[rr][cc] == v) return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.GRID, paint = paint)
        val grid = width.coerceAtMost(height) * 0.78f
        val left = (width - grid) / 2f
        val top = (height - grid) / 2f + 32f
        val cell = grid / 9f

        for (r in 0 until 9) for (c in 0 until 9) {
            val x = left + c * cell
            val y = top + r * cell

            // Background shading for 3x3 blocks
            paint.style = Paint.Style.FILL
            paint.color = if ((r / 3 + c / 3) % 2 == 0) Color.parseColor("#1E1E1E") else Color.parseColor("#252525")
            canvas.drawRect(x, y, x + cell, y + cell, paint)

            // Highlight selected row and column (crosshair effect)
            if (!solved && (r == cursorR || c == cursorC)) {
                paint.color = Color.argb(40, 255, 255, 255) // Subtle white highlight
                canvas.drawRect(x, y, x + cell, y + cell, paint)
            }

            // Conflict warning
            val conflict = board[r][c] != 0 && hasConflict(r, c)
            if (conflict) {
                paint.color = Color.argb(100, 183, 28, 28)
                canvas.drawRect(x, y, x + cell, y + cell, paint)
            }

            // Selection cursor with subtle bevel
            if (r == cursorR && c == cursorC && !solved) {
                // Bevel-like highlight
                paint.color = Color.argb(60, 255, 255, 0)
                canvas.drawRect(x, y, x + cell, y + cell, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.YELLOW
                canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, paint)
            }

            val v = board[r][c]
            if (v != 0) {
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = cell * 0.55f
                
                // Add tiny shadow for depth
                paint.color = Color.BLACK
                canvas.drawText(v.toString(), x + cell / 2 + 2, y + cell * 0.68f + 2, paint)

                paint.color = when {
                    conflict -> Color.parseColor("#FF8A80")
                    given[r][c] -> Color.WHITE
                    else -> Color.CYAN
                }
                canvas.drawText(v.toString(), x + cell / 2, y + cell * 0.68f, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        for (i in 0..9) {
            paint.color = if (i % 3 == 0) Color.WHITE else Color.GRAY
            paint.strokeWidth = if (i % 3 == 0) 4f else 1.5f
            canvas.drawLine(left, top + i * cell, left + grid, top + i * cell, paint)
            canvas.drawLine(left + i * cell, top, left + i * cell, top + grid, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 34f
        canvas.drawText("PUZZLE ${puzzleIndex + 1}/${puzzles.size}  BEST: $best", 30f, 52f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 28f
        canvas.drawText(
            when {
                solved -> "SOLVED! CENTER FOR NEXT PUZZLE"
                else -> "CENTER: 0-9 CYCLE (0 CLEAR)  GIVENS LOCKED"
            },
            width / 2f,
            top - 14f,
            paint
        )
    }
}
