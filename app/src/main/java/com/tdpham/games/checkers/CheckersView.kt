package com.tdpham.games.checkers

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

/**
 * English draughts on 8×8. You (red) move up; CPU (white) moves down.
 * Men: 1 / 2. Kings: 3 / 4. Kings move and capture on all four diagonals.
 * Captures are mandatory when available; multi-jumps finish on the same piece before the turn ends.
 */
class CheckersView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {
    override var gameKey: String = "checkers"
    override var onGameOver: ((Int) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val board = Array(8) { IntArray(8) }
    private var cursorR = 5
    private var cursorC = 0
    private var selected: Pair<Int, Int>? = null
    private var mustContinueFrom: Pair<Int, Int>? = null
    private var gameOver = false
    private var isCpuTurn = false
    private var status = ""
    private var wins = 0
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()
    private val PREFS_NAME = "checkers_settings"
    private val KEY_DIFFICULTY = "difficulty_index"
    private var currentDifficultyIndex = 1
    private var hintShowFrames = 0
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
    private val handler = Handler(Looper.getMainLooper())
    private val cpuRunnable = Runnable { cpuTurnLogic() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
        animHandler.post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(cpuRunnable)
        animHandler.removeCallbacks(animRunnable)
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {}
    override fun resume() {}
    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        celebrationManager.start(0f, 0f)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficultyIndex = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 1)

        for (r in 0..7) for (c in 0..7) board[r][c] = EMPTY
        for (r in 0..2) for (c in 0..7) {
            if (darkSquare(r, c)) board[r][c] = CPU_MAN
        }
        for (r in 5..7) for (c in 0..7) {
            if (darkSquare(r, c)) board[r][c] = PLAYER_MAN
        }
        cursorR = 5
        cursorC = 0
        selected = null
        mustContinueFrom = null
        gameOver = false
        isCpuTurn = false
        status = context.getString(R.string.your_turn_label)
        wins = ScoreManager.getHighScore(context, gameKey, currentDifficultyIndex)
        hintShowFrames = 100
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            resetGame()
            return true
        }
        if (isCpuTurn) {
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_O) {
                showOptions()
                return true
            }
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorR = (cursorR + 7) % 8
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorR = (cursorR + 1) % 8
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorC = (cursorC + 7) % 8
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorC = (cursorC + 1) % 8
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> onSelect()
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
        CheckersOptionsDialog.show(context) {
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
            if (isCpuTurn) return true

            // Calculate grid bounds (must match onDraw)
            val size = width.coerceAtMost(height) * 0.8f
            val left = (width - size) / 2f
            val top = (height - size) / 2f + 28f
            val cell = size / 8f

            if (event.x in left..(left + size) && event.y in top..(top + size)) {
                val c = ((event.x - left) / cell).toInt().coerceIn(0, 7)
                val r = ((event.y - top) / cell).toInt().coerceIn(0, 7)
                
                cursorR = r
                cursorC = c
                onSelect()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onSelect() {
        if (gameOver) return
        val cont = mustContinueFrom
        val sel = selected
        
        // Case 1: Continuing a jump sequence
        if (cont != null) {
            if (sel != null && sel == cont) {
                // Already selected the continuing piece, try to move to cursor
                if (tryPlayerMove(sel.first, sel.second, cursorR, cursorC)) {
                    if (countCpuPieces() == 0) {
                        endPlayerWin()
                        return
                    }
                    if (mustContinueFrom != null) {
                        selected = mustContinueFrom
                        status = context.getString(R.string.jump_again_label)
                        invalidate()
                        return
                    }
                    selected = null
                    cpuTurn()
                } else if (cursorR == sel.first && cursorC == sel.second) {
                    // Re-selecting same piece is fine (click feedback)
                    SoundManager.playClick()
                } else {
                    SoundManager.playError()
                }
            } else {
                // Must select the piece that needs to continue
                if (cursorR == cont.first && cursorC == cont.second) {
                    selected = cont
                    SoundManager.playClick()
                } else {
                    SoundManager.playError()
                }
            }
            invalidate()
            return
        }

        // Case 2: Standard selection or move
        if (!playerHasAnyMove()) {
            gameOver = true
            status = "${context.getString(R.string.no_moves_label)} - ${context.getString(R.string.cpu_wins_label)}"
            SoundManager.playError()
            onGameOver?.invoke(wins)
            invalidate()
            return
        }
        val piece = board[cursorR][cursorC]
        if (sel == null) {
            if (isPlayerPiece(piece) && squareHasLegalPlayerMove(cursorR, cursorC)) {
                selected = cursorR to cursorC
                SoundManager.playClick()
            } else if (isPlayerPiece(piece)) {
                SoundManager.playError()
            }
        } else {
            if (tryPlayerMove(sel.first, sel.second, cursorR, cursorC)) {
                if (countCpuPieces() == 0) {
                    endPlayerWin()
                    return
                }
                if (mustContinueFrom != null) {
                    selected = mustContinueFrom
                    status = context.getString(R.string.jump_again_label)
                    invalidate()
                    return
                }
                selected = null
                cpuTurn()
            } else {
                // Deselect or switch to another valid piece
                if (isPlayerPiece(piece) && squareHasLegalPlayerMove(cursorR, cursorC)) {
                    selected = cursorR to cursorC
                    SoundManager.playClick()
                } else {
                    selected = null
                }
            }
        }
        invalidate()
    }

    private fun endPlayerWin() {
        gameOver = true
        currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
        val oldWins = wins
        val newWins = wins + 1
        if (ScoreManager.updateHighScore(context, gameKey, newWins, currentDifficultyIndex)) wins = newWins
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = true,
            score = wins,
            highScore = oldWins
        )
        status = currentVictoryWord
        SoundManager.playSuccess()
        onGameOver?.invoke(wins)
    }

    private fun endCpuWin() {
        gameOver = true
        status = context.getString(R.string.cpu_wins_label)
        celebrationManager.startOutcome(
            width = width.toFloat(),
            height = height.toFloat(),
            isWin = false,
            score = 0,
            highScore = 100
        )
        SoundManager.playError()
    }

    private fun tryPlayerMove(fr: Int, fc: Int, tr: Int, tc: Int): Boolean {
        val cont = mustContinueFrom
        val jumpsOnly = if (cont != null) {
            fr == cont.first && fc == cont.second
        } else {
            playerMustJump()
        }
        val moves = legalMovesForSquare(fr, fc, PLAYER, jumpsOnly)
        val target = moves.find { it.tr == tr && it.tc == tc } ?: return false
        applyMove(target)
        val promoted = promoteIfNeeded(tr, tc, PLAYER)
        if (target.isJump) {
            SoundManager.playScore()
            val more = if (promoted) emptyList() else jumpDestinationsFrom(tr, tc, PLAYER)
            mustContinueFrom = if (more.isNotEmpty()) tr to tc else null
        } else {
            mustContinueFrom = null
            SoundManager.playClick()
        }
        return true
    }

    private fun applyMove(m: Move) {
        val p = board[m.fr][m.fc]
        board[m.fr][m.fc] = EMPTY
        if (m.capturedR >= 0) {
            board[m.capturedR][m.capturedC] = EMPTY
        }
        board[m.tr][m.tc] = p
    }

    private fun promoteIfNeeded(r: Int, c: Int, side: Int): Boolean {
        val p = board[r][c]
        if (side == PLAYER && r == 0 && p == PLAYER_MAN) {
            board[r][c] = PLAYER_KING
            return true
        }
        if (side == CPU && r == 7 && p == CPU_MAN) {
            board[r][c] = CPU_KING
            return true
        }
        return false
    }

    private fun cpuTurn() {
        mustContinueFrom = null
        selected = null
        isCpuTurn = true
        status = context.getString(R.string.cpu_thinking_label)
        invalidate()
        handler.postDelayed(cpuRunnable, 600)
    }

    private fun isThreatened(r: Int, c: Int, side: Int): Boolean {
        val enemySide = if (side == PLAYER) CPU else PLAYER
        val opponentDirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((dr, dc) in opponentDirs) {
            val oppR = r + dr
            val oppC = c + dc
            if (oppR in 0..7 && oppC in 0..7) {
                val oppPiece = board[oppR][oppC]
                if (isEnemy(oppPiece, side)) {
                    val oppAllowedDirs = directionsFor(oppPiece, enemySide)
                    if (oppAllowedDirs.contains(-dr to -dc)) {
                        val landR = r - dr
                        val landC = c - dc
                        if (landR in 0..7 && landC in 0..7 && board[landR][landC] == EMPTY) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun cpuTurnLogic() {
        if (gameOver) {
            isCpuTurn = false
            return
        }
        val jumpsOnly = cpuMustJump()
        val allMoves = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            if (isCpuPiece(board[r][c])) {
                allMoves.addAll(legalMovesForSquare(r, c, CPU, jumpsOnly))
            }
        }
        if (allMoves.isEmpty()) {
            endPlayerWin()
            isCpuTurn = false
            invalidate()
            return
        }
        val captures = allMoves.filter { it.isJump }
        val pickFrom = if (captures.isNotEmpty()) captures else allMoves
        
        // Smart Move Selection for CPU on Normal difficulty
        val m = if (currentDifficultyIndex == 1) {
            pickFrom.maxByOrNull { move ->
                var moveScore = 0
                if (move.isJump) {
                    moveScore += 15
                }
                if (isThreatened(move.tr, move.tc, CPU)) {
                    moveScore -= 10
                }
                if (move.tc == 0 || move.tc == 7) {
                    moveScore += 4
                }
                val piece = board[move.fr][move.fc]
                if (piece == CPU_MAN && move.tr == 7) {
                    moveScore += 10
                }
                if (piece == CPU_KING) {
                    moveScore += 2
                }
                moveScore
            } ?: pickFrom[Random.nextInt(pickFrom.size)]
        } else {
            pickFrom[Random.nextInt(pickFrom.size)]
        }

        applyMove(m)
        var promoted = promoteIfNeeded(m.tr, m.tc, CPU)
        if (m.isJump) SoundManager.playScore() else SoundManager.playClick()
        
        var cr = m.tr
        var cc = m.tc
        if (m.isJump && !promoted) {
            while (true) {
                val nextJumps = legalMovesForSquare(cr, cc, CPU, true).filter { it.isJump }
                if (nextJumps.isEmpty()) break
                
                val j = if (currentDifficultyIndex == 1) {
                    nextJumps.maxByOrNull { jumpMove ->
                        var jumpScore = 0
                        if (isThreatened(jumpMove.tr, jumpMove.tc, CPU)) jumpScore -= 10
                        if (promoteIfNeeded(jumpMove.tr, jumpMove.tc, CPU)) jumpScore += 10
                        jumpScore
                    } ?: nextJumps[Random.nextInt(nextJumps.size)]
                } else {
                    nextJumps[Random.nextInt(nextJumps.size)]
                }

                applyMove(j)
                promoted = promoteIfNeeded(j.tr, j.tc, CPU)
                SoundManager.playScore()
                cr = j.tr
                cc = j.tc
                if (promoted) break
            }
        }
        when {
            countPlayerPieces() == 0 -> {
                endCpuWin()
            }
            !playerHasAnyMove() -> {
                gameOver = true
                status = "${context.getString(R.string.no_moves_label)} - ${context.getString(R.string.cpu_wins_label)}"
                SoundManager.playError()
            }
            else -> status = context.getString(R.string.your_turn_label)
        }
        isCpuTurn = false
        if (gameOver) onGameOver?.invoke(wins)
        invalidate()
    }

    private fun playerMustJump(): Boolean {
        for (r in 0..7) for (c in 0..7) {
            if (isPlayerPiece(board[r][c])) {
                if (legalMovesForSquare(r, c, PLAYER, true).any { it.isJump }) return true
            }
        }
        return false
    }

    private fun cpuMustJump(): Boolean {
        for (r in 0..7) for (c in 0..7) {
            if (isCpuPiece(board[r][c])) {
                if (legalMovesForSquare(r, c, CPU, true).any { it.isJump }) return true
            }
        }
        return false
    }

    private fun playerHasAnyMove(): Boolean {
        val jumps = playerMustJump()
        for (r in 0..7) for (c in 0..7) {
            if (isPlayerPiece(board[r][c]) && legalMovesForSquare(r, c, PLAYER, jumps).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun squareHasLegalPlayerMove(r: Int, c: Int): Boolean {
        val jumps = playerMustJump()
        return legalMovesForSquare(r, c, PLAYER, jumps).isNotEmpty()
    }

    private fun legalMovesForSquare(
        r: Int,
        c: Int,
        side: Int,
        jumpsOnly: Boolean
    ): List<Move> {
        val piece = board[r][c]
        if (side == PLAYER && !isPlayerPiece(piece)) return emptyList()
        if (side == CPU && !isCpuPiece(piece)) return emptyList()
        val out = mutableListOf<Move>()
        val jumpTargets = jumpDestinationsFrom(r, c, side)
        for (j in jumpTargets) {
            val tr = j.first
            val tc = j.second
            val mr = (r + tr) / 2
            val mc = (c + tc) / 2
            out.add(Move(r, c, tr, tc, true, mr, mc))
        }
        if (jumpsOnly || jumpTargets.isNotEmpty()) return out
        val dirs = directionsFor(piece, side)
        for ((dr, dc) in dirs) {
            val tr = r + dr
            val tc = c + dc
            if (tr in 0..7 && tc in 0..7 && board[tr][tc] == EMPTY) {
                out.add(Move(r, c, tr, tc, false, -1, -1))
            }
        }
        return out
    }

    private fun jumpDestinationsFrom(r: Int, c: Int, side: Int): List<Pair<Int, Int>> {
        val piece = board[r][c]
        val dirs = directionsFor(piece, side)
        val res = mutableListOf<Pair<Int, Int>>()
        for ((dr, dc) in dirs) {
            val mr = r + dr
            val mc = c + dc
            val tr = r + 2 * dr
            val tc = c + 2 * dc
            if (tr !in 0..7 || tc !in 0..7) continue
            if (board[tr][tc] != EMPTY) continue
            val mid = board[mr][mc]
            if (mid == EMPTY) continue
            val enemy = isEnemy(mid, side)
            if (enemy) res.add(tr to tc)
        }
        return res
    }

    private fun isEnemy(cell: Int, side: Int): Boolean = when (side) {
        PLAYER -> isCpuPiece(cell)
        CPU -> isPlayerPiece(cell)
        else -> false
    }

    private fun directionsFor(piece: Int, side: Int): List<Pair<Int, Int>> {
        val king = piece == PLAYER_KING || piece == CPU_KING
        return when {
            king -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
            side == PLAYER -> listOf(-1 to -1, -1 to 1)
            else -> listOf(1 to -1, 1 to 1)
        }
    }

    private fun countCpuPieces(): Int {
        var n = 0
        for (r in 0..7) for (c in 0..7) if (isCpuPiece(board[r][c])) n++
        return n
    }

    private fun countPlayerPieces(): Int {
        var n = 0
        for (r in 0..7) for (c in 0..7) if (isPlayerPiece(board[r][c])) n++
        return n
    }

    private fun darkSquare(r: Int, c: Int) = (r + c) % 2 == 1

    private fun isPlayerPiece(v: Int) = v == PLAYER_MAN || v == PLAYER_KING
    private fun isCpuPiece(v: Int) = v == CPU_MAN || v == CPU_KING

    override fun onDraw(canvas: Canvas) {
        if (hintShowFrames > 0) {
            hintShowFrames--
            invalidate()
        }
        canvas.drawColor(GamePalette.BACKGROUND)
        val size = width.coerceAtMost(height) * 0.8f
        val left = (width - size) / 2f
        val top = (height - size) / 2f + 28f
        val cell = size / 8f
        for (r in 0..7) for (c in 0..7) {
            val x = left + c * cell
            val y = top + r * cell
            paint.style = Paint.Style.FILL
            paint.color = if (darkSquare(r, c)) Color.parseColor("#6D4C41") else Color.parseColor("#BCAAA4")
            canvas.drawRect(x, y, x + cell, y + cell, paint)
            when (board[r][c]) {
                PLAYER_MAN -> {
                    paint.color = Color.parseColor("#EF5350")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                    // Bevel
                    paint.color = Color.WHITE
                    paint.alpha = 60
                    canvas.drawCircle(x + cell * 0.4f, y + cell * 0.4f, cell * 0.1f, paint)
                    paint.alpha = 255
                }
                CPU_MAN -> {
                    paint.color = Color.parseColor("#EEEEEE")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                    // Bevel
                    paint.color = Color.WHITE
                    paint.alpha = 150
                    canvas.drawCircle(x + cell * 0.4f, y + cell * 0.4f, cell * 0.1f, paint)
                    paint.alpha = 255
                }
                PLAYER_KING -> {
                    paint.color = Color.parseColor("#EF5350")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                    // Bevel
                    paint.color = Color.WHITE
                    paint.alpha = 60
                    canvas.drawCircle(x + cell * 0.4f, y + cell * 0.4f, cell * 0.1f, paint)
                    paint.alpha = 255
                    
                    paint.color = Color.parseColor("#FFD54F")
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = cell * 0.45f
                    canvas.drawText("K", x + cell / 2, y + cell * 0.62f, paint)
                }
                CPU_KING -> {
                    paint.color = Color.parseColor("#EEEEEE")
                    canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.35f, paint)
                    // Bevel
                    paint.color = Color.WHITE
                    paint.alpha = 150
                    canvas.drawCircle(x + cell * 0.4f, y + cell * 0.4f, cell * 0.1f, paint)
                    paint.alpha = 255
                    
                    paint.color = Color.parseColor("#424242")
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = cell * 0.45f
                    canvas.drawText("K", x + cell / 2, y + cell * 0.62f, paint)
                }
            }
        }
        selected?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.CYAN
            val x = left + it.second * cell
            val y = top + it.first * cell
            canvas.drawRect(x + 3, y + 3, x + cell - 3, y + cell - 3, paint)
        }
        mustContinueFrom?.let {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.MAGENTA
            val x = left + it.second * cell
            val y = top + it.first * cell
            canvas.drawRect(x + 8, y + 8, x + cell - 8, y + cell - 8, paint)
        }
        if (!gameOver) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.YELLOW
            val x = left + cursorC * cell
            val y = top + cursorR * cell
            canvas.drawRect(x + 6, y + 6, x + cell - 6, y + cell - 6, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.textAlign = Paint.Align.LEFT
        val hudY = 52f
        canvas.drawText("${context.getString(R.string.wins_label)}: $wins", 30f, hudY, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.LTGRAY
        val modeStr = context.getString(if (currentDifficultyIndex == 0) R.string.checkers_difficulty_1 else R.string.checkers_difficulty_2)
        canvas.drawText("${context.getString(R.string.mode_label)}: $modeStr", width - 30f, hudY, paint)
        paint.textAlign = Paint.Align.LEFT

        // Quick Hint (Top/Left)
        if (hintShowFrames > 0) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            paint.color = Color.WHITE
            paint.alpha = (hintShowFrames * 3).coerceAtMost(255)
            canvas.drawText(context.getString(R.string.trex_press_menu_options), 30f, hudY + 45f, paint)
            paint.alpha = 255
        }
        
        if (gameOver) {
            celebrationManager.draw(canvas)
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 32f
        canvas.drawText(status, width / 2f, top - 16f, paint)

        if (gameOver) {
            val isWin = !board.any { r -> r.any { it == 2 || it == 4 } }
            drawOverlay(canvas, if (isWin) currentVictoryWord else context.getString(R.string.cpu_wins_label), context.getString(R.string.restart_hint))
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
            canvas.drawText(s, width / 2f, height / 2f + 70f + i * 60f, paint)
        }
    }

    data class Move(
        val fr: Int,
        val fc: Int,
        val tr: Int,
        val tc: Int,
        val isJump: Boolean,
        val capturedR: Int,
        val capturedC: Int
    )

    companion object {
        private const val EMPTY = 0
        private const val PLAYER_MAN = 1
        private const val CPU_MAN = 2
        private const val PLAYER_KING = 3
        private const val CPU_KING = 4
        private const val PLAYER = 1
        private const val CPU = 2
    }
}
