package com.tdpham.games.twentyfortyeight

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import com.tdpham.games.R
import com.tdpham.games.common.*
import java.util.*
import kotlin.math.abs

/**
 * 2048 / 4096 Game View extending BaseGameView SurfaceView engine.
 * Features 60FPS fixed-timestep physics, animated tile merging,
 * custom haptic feedback, and deadzone-filtered analog/D-pad controls.
 */
class TwentyFortyEightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseGameView(context, attrs, defStyleAttr) {

    override var gameKey: String = "4096"
    private var gridSize = 4
    private val PREFS_NAME = "twentyfortyeight_settings"
    private val KEY_GRID_SIZE = "grid_size"
    private var hintShowFrames = 120

    private var board = Array(gridSize) { IntArray(gridSize) { 0 } }
    private var previousBoard: Array<IntArray>? = null
    private var previousScore: Int = 0
    private var canUndo: Boolean = false

    private var score = 0
    private var highScore = 0
    private var isGameOver = false
    private var isWin = false
    private var cellSize = 0f

    private val celebrationManager = CelebrationManager()

    // Tile Animation State tracking
    private class AnimatedTile(
        var r: Int,
        var c: Int,
        var value: Int,
        var scale: Float = 1.0f,
        var isNew: Boolean = false,
        var isMerged: Boolean = false
    )

    private val animatedTiles = mutableListOf<AnimatedTile>()
    private val mergedTiles = mutableSetOf<Pair<Int, Int>>()

    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // Touch Swipe Gesture Detector
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 50f
        private val SWIPE_VELOCITY_THRESHOLD = 50f

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (isGameOver || isWin) {
                resetGame()
                return true
            }
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) handleDirectionalMove(1, 0)
                    else handleDirectionalMove(-1, 0)
                    return true
                }
            } else {
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) handleDirectionalMove(0, 1)
                    else handleDirectionalMove(0, -1)
                    return true
                }
            }
            return false
        }
    })

    init {
        resetGame()
    }

    override fun resetGame() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        gridSize = prefs.getInt(KEY_GRID_SIZE, 4).coerceIn(4, 5)

        board = Array(gridSize) { IntArray(gridSize) { 0 } }
        previousBoard = null
        previousScore = 0
        canUndo = false
        score = 0
        isGameOver = false
        isWin = false
        celebrationManager.clear()
        highScore = ScoreManager.getHighScore(context, gameKey, gridSize)
        mergedTiles.clear()
        animatedTiles.clear()

        addRandomTile(isFirst = true)
        addRandomTile(isFirst = true)

        hintShowFrames = 120
    }

    override fun canRevive(): Boolean = isGameOver && score > 200

    override fun reviveGame(): Boolean {
        if (!canRevive()) return false
        isGameOver = false
        celebrationManager.clear()

        var cleared = 0
        for (v in listOf(2, 4, 8)) {
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (board[r][c] == v && cleared < 3) {
                        board[r][c] = 0
                        cleared++
                    }
                }
            }
            if (cleared >= 3) break
        }
        HapticManager.vibrateSuccess(context)
        SoundManager.playSuccess()
        return true
    }

    /**
     * Undo last move if available.
     */
    fun undo(): Boolean {
        if (!canUndo || previousBoard == null || isGameOver) {
            SoundManager.playError()
            return false
        }
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                board[r][c] = previousBoard!![r][c]
            }
        }
        score = previousScore
        canUndo = false
        SoundEffectLibrary.play(SoundEffectLibrary.SoundEffectEvent.MOVE)
        HapticManager.vibrateClick(context)
        syncAnimatedTiles()
        return true
    }

    private fun saveUndoState() {
        previousBoard = Array(gridSize) { r -> board[r].clone() }
        previousScore = score
        canUndo = true
    }

    private fun addRandomTile(isFirst: Boolean = false) {
        val emptyCells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) emptyCells.add(r to c)
            }
        }
        if (emptyCells.isNotEmpty()) {
            val (r, c) = emptyCells[Random().nextInt(emptyCells.size)]
            val value = if (Random().nextFloat() < 0.9) 2 else 4
            board[r][c] = value
            animatedTiles.add(AnimatedTile(r, c, value, scale = if (isFirst) 1.0f else 0.1f, isNew = true))
        }
    }

    private fun updateTileAnimations() {
        val iterator = animatedTiles.iterator()
        while (iterator.hasNext()) {
            val tile = iterator.next()
            if (tile.isNew) {
                tile.scale += 0.15f
                if (tile.scale >= 1.0f) {
                    tile.scale = 1.0f
                    tile.isNew = false
                }
            } else if (tile.isMerged) {
                tile.scale -= 0.08f
                if (tile.scale <= 1.0f) {
                    tile.scale = 1.0f
                    tile.isMerged = false
                }
            }
        }
    }

    private fun syncAnimatedTiles() {
        animatedTiles.clear()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val v = board[r][c]
                if (v > 0) {
                    val isMerged = mergedTiles.contains(Pair(r, c))
                    animatedTiles.add(
                        AnimatedTile(
                            r, c, v,
                            scale = if (isMerged) 1.25f else 1.0f,
                            isMerged = isMerged
                        )
                    )
                }
            }
        }
    }

    private fun handleDirectionalMove(dx: Int, dy: Int): Boolean {
        saveUndoState()
        val moved = move(dx, dy)
        if (moved) {
            SoundManager.playDpadMove()
            HapticManager.vibrateClick(context)
            addRandomTile()
            checkGameState()
            syncAnimatedTiles()
            return true
        }
        return false
    }

    // --- BASE GAME VIEW LIFECYCLE & ENGINE HOOKS ---
    override fun onGameUpdate(deltaSec: Float) {
        if (isGameOver || isWin) {
            celebrationManager.update()
        }
        updateTileAnimations()
        if (hintShowFrames > 0) hintShowFrames--
    }

    override fun onRender(canvas: Canvas, interpolation: Float) {
        val w = viewWidth.toFloat()
        val h = viewHeight.toFloat()
        if (w <= 0f || h <= 0f) return

        cellSize = w.coerceAtMost(h) / (gridSize + 2.4f)
        val offsetX = (w - cellSize * gridSize) / 2f
        val offsetY = (h - cellSize * gridSize) / 2f + cellSize * 0.9f

        canvas.drawColor(GamePalette.BACKGROUND)

        // Draw Score (Left Side)
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.FILL

        paint.textSize = cellSize * 0.28f
        paint.color = GamePalette.TEXT_SECONDARY
        val scoreLabelY = (offsetY - cellSize * 0.75f).coerceAtLeast(40f)
        val scoreNumY = scoreLabelY + cellSize * 0.45f
        val labelX = offsetX
        canvas.drawText(context.getString(R.string.score_label), labelX, scoreLabelY, paint)

        paint.textSize = cellSize * 0.48f
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("$score", labelX, scoreNumY, paint)

        // Mode & Grid Size (Center)
        paint.textSize = cellSize * 0.32f
        paint.color = Color.parseColor("#00E5FF")
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${gridSize}x$gridSize PRO", w / 2f, scoreLabelY, paint)

        if (canUndo) {
            paint.textSize = cellSize * 0.24f
            paint.color = Color.parseColor("#FFD700")
            canvas.drawText("↩ [U] UNDO READY", w / 2f, scoreNumY, paint)
        }

        // Draw Best (Right Side)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = cellSize * 0.28f
        paint.color = GamePalette.TEXT_SECONDARY
        val bestX = offsetX + gridSize * cellSize
        canvas.drawText(context.getString(R.string.best_label), bestX, scoreLabelY, paint)

        paint.textSize = cellSize * 0.48f
        paint.color = GamePalette.SCORE
        canvas.drawText("$highScore", bestX, scoreNumY, paint)

        // Draw Grid Background Board
        paint.color = Color.parseColor("#1B263B")
        val boardPadding = 12f
        canvas.drawRoundRect(
            offsetX - boardPadding,
            offsetY - boardPadding,
            offsetX + gridSize * cellSize + boardPadding,
            offsetY + gridSize * cellSize + boardPadding,
            24f, 24f, paint
        )

        // Draw Empty Grid Slots
        paint.color = Color.parseColor("#0E1626")
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val l = offsetX + c * cellSize + 6
                val t = offsetY + r * cellSize + 6
                val rt = l + cellSize - 12
                val b = t + cellSize - 12
                canvas.drawRoundRect(l, t, rt, b, 16f, 16f, paint)
            }
        }

        // Draw Active Tiles
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val value = board[r][c]
                if (value > 0) {
                    val animTile = animatedTiles.firstOrNull { it.r == r && it.c == c }
                    val scale = animTile?.scale ?: 1.0f
                    drawTile(canvas, r, c, value, offsetX, offsetY, scale)
                }
            }
        }

        // Quick Navigation Controls Hint
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = cellSize * 0.22f
        paint.color = Color.parseColor("#78909C")
        val hintY = offsetY + gridSize * cellSize + cellSize * 0.5f
        canvas.drawText("🎮 D-Pad/Swipe: Move  •  [U] Undo  •  [MENU] Size Mode", w / 2f, hintY, paint)

        if (isGameOver || isWin) {
            celebrationManager.draw(canvas)
            drawOverlay(canvas, w, h)
        }
    }

    override fun onDpadInput(direction: DpadDirection, isPressed: Boolean): Boolean {
        if (!isPressed) return false
        if (isGameOver || isWin) {
            resetGame()
            return true
        }

        return when (direction) {
            DpadDirection.UP -> handleDirectionalMove(0, -1)
            DpadDirection.DOWN -> handleDirectionalMove(0, 1)
            DpadDirection.LEFT -> handleDirectionalMove(-1, 0)
            DpadDirection.RIGHT -> handleDirectionalMove(1, 0)
            DpadDirection.NONE -> false
        }
    }

    override fun onActionButton(keyCode: Int, isPressed: Boolean): Boolean {
        if (!isPressed) return false
        if (isGameOver || isWin) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                resetGame()
                return true
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BUTTON_X -> undo()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_BUTTON_Y -> {
                showOptions()
                true
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver || isWin) {
                resetGame()
                return true
            }

            val centerX = width / 2f
            val centerY = height / 2f
            val x = event.x
            val y = event.y

            if (abs(x - centerX) > abs(y - centerY)) {
                if (x > centerX) handleDirectionalMove(1, 0) else handleDirectionalMove(-1, 0)
            } else {
                if (y > centerY) handleDirectionalMove(0, 1) else handleDirectionalMove(0, -1)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun showOptions() {
        TwentyFortyEightOptionsDialog.show(context) {
            resetGame()
        }
    }

    private fun move(dx: Int, dy: Int): Boolean {
        var moved = false
        mergedTiles.clear()
        var mergeCount = 0

        if (dx != 0) { // Horizontal move
            for (r in 0 until gridSize) {
                val row = board[r]
                val (newRow, merges) = shift(row, r, dx > 0, isVertical = false)
                if (!row.contentEquals(newRow)) {
                    board[r] = newRow
                    moved = true
                    mergeCount += merges
                }
            }
        } else { // Vertical move
            for (c in 0 until gridSize) {
                val col = IntArray(gridSize) { r -> board[r][c] }
                val (newCol, merges) = shift(col, c, dy > 0, isVertical = true)
                if (!col.contentEquals(newCol)) {
                    for (r in 0 until gridSize) board[r][c] = newCol[r]
                    moved = true
                    mergeCount += merges
                }
            }
        }

        if (mergeCount > 1) {
            SoundManager.playCombo(mergeCount)
        }
        return moved
    }

    private fun shift(arr: IntArray, index: Int, reversed: Boolean, isVertical: Boolean = false): Pair<IntArray, Int> {
        val working = if (reversed) arr.reversedArray() else arr
        val result = mutableListOf<Int>()
        val filtered = working.filter { it != 0 }
        var i = 0
        var merges = 0

        while (i < filtered.size) {
            if (i + 1 < filtered.size && filtered[i] == filtered[i + 1]) {
                val newValue = filtered[i] * 2
                result.add(newValue)
                score += newValue
                merges++

                // Audio feedback for tile merge
                SoundEffectLibrary.playTileMerge(newValue)

                // Tactile Haptic Feedback on Tile Merges:
                if (newValue >= 1024) {
                    HapticManager.vibrateSuccess(context)
                } else if (newValue >= 64) {
                    HapticManager.vibrateScore(context)
                } else {
                    HapticManager.vibrateClick(context)
                }

                val pos = result.size - 1
                val finalIdx = if (reversed) gridSize - 1 - pos else pos
                if (isVertical) mergedTiles.add(Pair(finalIdx, index))
                else mergedTiles.add(Pair(index, finalIdx))

                i += 2
            } else {
                result.add(filtered[i])
                i++
            }
        }
        while (result.size < gridSize) result.add(0)
        val finalArr = if (reversed) result.reversed().toIntArray() else result.toIntArray()
        return Pair(finalArr, merges)
    }

    private fun checkGameState() {
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] >= 4096 && !isWin) {
                    isWin = true
                    onGameOver?.invoke(score)
                }
            }
        }

        var movePossible = false
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                if (board[r][c] == 0) movePossible = true
                if (r + 1 < gridSize && board[r][c] == board[r + 1][c]) movePossible = true
                if (c + 1 < gridSize && board[r][c] == board[r][c + 1]) movePossible = true
            }
        }

        val w = if (viewWidth > 0) viewWidth.toFloat() else 800f
        val h = if (viewHeight > 0) viewHeight.toFloat() else 1200f

        if (!movePossible) {
            isGameOver = true
            val oldBest = highScore
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, gridSize)
            if (isNewHigh) highScore = score
            SoundManager.playError()
            HapticManager.vibrateDamage(context)
            celebrationManager.startOutcome(w, h, isWin = false, isNewHigh = isNewHigh, score = score, highScore = oldBest)
            onGameOver?.invoke(score)
        } else if (isWin) {
            val oldBest = highScore
            val isNewHigh = ScoreManager.updateHighScore(context, gameKey, score, gridSize)
            if (isNewHigh) highScore = score
            celebrationManager.startOutcome(w, h, isWin = true, isNewHigh = isNewHigh, score = score, highScore = oldBest)
            SoundEffectLibrary.play(SoundEffectLibrary.SoundEffectEvent.VICTORY_FANFARE)
            HapticManager.vibrateSuccess(context)
        }
    }

    private fun drawTile(
        canvas: Canvas,
        r: Int,
        c: Int,
        value: Int,
        offsetX: Float,
        offsetY: Float,
        scale: Float
    ) {
        val baseLeft = offsetX + c * cellSize + 6
        val baseTop = offsetY + r * cellSize + 6
        val baseRight = baseLeft + cellSize - 12
        val baseBottom = baseTop + cellSize - 12

        val cx = (baseLeft + baseRight) / 2f
        val cy = (baseTop + baseBottom) / 2f
        val halfW = (baseRight - baseLeft) / 2f * scale
        val halfH = (baseBottom - baseTop) / 2f * scale

        val left = cx - halfW
        val top = cy - halfH
        val right = cx + halfW
        val bottom = cy + halfH

        // Draw Glow for high-tier tiles
        if (value >= 1024) {
            paint.color = if (value >= 4096) Color.parseColor("#FFD700") else Color.parseColor("#00E5FF")
            paint.alpha = 110
            canvas.drawRoundRect(left - 4, top - 4, right + 4, bottom + 4, 20f, 20f, paint)
            paint.alpha = 255
        }

        paint.color = getTileColor(value)
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, paint)

        // Draw Tile Number Text
        paint.color = if (value <= 4) Color.parseColor("#141C2E") else Color.WHITE
        paint.textSize = when {
            value < 100 -> cellSize * 0.44f * scale
            value < 1000 -> cellSize * 0.36f * scale
            value < 10000 -> cellSize * 0.28f * scale
            else -> cellSize * 0.22f * scale
        }
        paint.textAlign = Paint.Align.CENTER
        val text = value.toString()
        val textRect = Rect()
        paint.getTextBounds(text, 0, text.length, textRect)
        canvas.drawText(text, cx, cy + textRect.height() / 2f, paint)
    }

    private fun drawOverlay(canvas: Canvas, w: Float, h: Float) {
        paint.color = GamePalette.OVERLAY
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = w / 14f
        paint.color = if (isWin) Color.parseColor("#00E676") else GamePalette.WARNING
        canvas.drawText(if (isWin) context.getString(R.string.win_4096_label) else context.getString(R.string.game_over), w / 2f, h / 2f - 40f, paint)

        paint.textSize = w / 34f
        paint.color = GamePalette.TEXT_PRIMARY
        canvas.drawText("${context.getString(R.string.score_label)}: $score  •  ${context.getString(R.string.best_label)}: $highScore", w / 2f, h / 2f + 20f, paint)

        paint.textSize = w / 38f
        paint.color = Color.parseColor("#00E5FF")
        val restartHint = "Press [ENTER / DPAD-CENTER] to Restart"
        canvas.drawText(restartHint, w / 2f, h / 2f + 70f, paint)
    }

    private fun getTileColor(value: Int): Int {
        return when (value) {
            0 -> Color.parseColor("#0E1626")
            2 -> Color.parseColor("#E0E6ED")
            4 -> Color.parseColor("#D0DBE5")
            8 -> Color.parseColor("#FFA726")
            16 -> Color.parseColor("#FB8C00")
            32 -> Color.parseColor("#FF7043")
            64 -> Color.parseColor("#F4511E")
            128 -> Color.parseColor("#FFD54F")
            256 -> Color.parseColor("#FFCA28")
            512 -> Color.parseColor("#FFC107")
            1024 -> Color.parseColor("#00E5FF")
            2048 -> Color.parseColor("#00B0FF")
            4096 -> Color.parseColor("#D500F9")
            8192 -> Color.parseColor("#FF1744")
            else -> Color.parseColor("#304FFE")
        }
    }
}
