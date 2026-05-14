package com.tdpham.games.solitaire

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GameView
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import java.util.*

class SolitaireView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "solitaire"
    
    private val deck = mutableListOf<Card>()
    private val stock = mutableListOf<Card>()
    private val waste = mutableListOf<Card>()
    private val foundations = Array(4) { mutableListOf<Card>() }
    private val tableaus = Array(7) { mutableListOf<Card>() }

    private var cursorX = 0 // 0-6 for tableaus, foundations, etc.
    private var cursorY = 0 // 0 for stock/waste/foundations, 1+ for tableaus
    private var selectedCards = mutableListOf<Card>()
    private var sourcePile: PileType? = null
    private var sourceIndex: Int = -1

    private var score = 0
    private var isGameOver = false
    private var isPaused = false

    private var pulseFactor = 1.0f
    private var pulseDirection = 1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardWidthRatio = 0.12f
    private val cardHeightRatio = 0.18f

    enum class Suit { HEARTS, DIAMONDS, CLUBS, SPADES }
    enum class Rank(val value: Int) { 
        ACE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), 
        EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13)
    }
    enum class PileType { STOCK, WASTE, FOUNDATION, TABLEAU }

    data class Card(val suit: Suit, val rank: Rank, var isFaceUp: Boolean = false) {
        val isRed = suit == Suit.HEARTS || suit == Suit.DIAMONDS
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        resetGame()
    }

    override fun startGame() {
        isPaused = false
        invalidate()
    }

    override fun pause() {
        isPaused = true
        invalidate()
    }

    override fun resume() {
        isPaused = false
        invalidate()
    }

    override fun resetGame() {
        deck.clear()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                deck.add(Card(suit, rank))
            }
        }
        deck.shuffle()

        stock.clear()
        waste.clear()
        for (i in 0 until 4) foundations[i].clear()
        for (i in 0 until 7) {
            tableaus[i].clear()
            repeat(i + 1) {
                val card = deck.removeAt(0)
                card.isFaceUp = (it == i)
                tableaus[i].add(card)
            }
        }
        stock.addAll(deck)
        
        score = 0
        isGameOver = false
        cursorX = 0
        cursorY = 0
        selectedCards.clear()
        sourcePile = null
        invalidate()
    }

    override fun toggleSound(): Boolean {
        return SoundManager.toggleSound()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGameOver) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                resetGame()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursor(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(0, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursor(1, 0)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> handleSelection()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    private fun moveCursor(dx: Int, dy: Int) {
        cursorX = (cursorX + dx + 7) % 7
        if (dy != 0) {
            if (cursorY == 0 && dy > 0) {
                cursorY = 1
            } else if (cursorY >= 1 && dy < 0) {
                cursorY = 0
            } else if (cursorY >= 1 && dy > 0) {
                val tableauSize = tableaus[cursorX].size
                if (cursorY < tableauSize) cursorY++
            } else if (cursorY > 1 && dy < 0) {
                cursorY--
            }
        }
        
        // Snap cursorY to valid range for current cursorX
        if (cursorY >= 1) {
            val tableauSize = tableaus[cursorX].size
            if (tableauSize == 0) cursorY = 1
            else if (cursorY > tableauSize) cursorY = tableauSize
        }
    }

    private fun handleSelection() {
        if (selectedCards.isEmpty()) {
            // Picking up
            if (cursorY == 0) {
                when (cursorX) {
                    0 -> { // Stock
                        if (stock.isEmpty()) {
                            stock.addAll(waste.reversed())
                            waste.clear()
                            stock.forEach { it.isFaceUp = false }
                        } else {
                            val card = stock.removeAt(stock.size - 1)
                            card.isFaceUp = true
                            waste.add(card)
                        }
                    }
                    1 -> { // Waste
                        if (waste.isNotEmpty()) {
                            val card = waste.last()
                            selectedCards.add(card)
                            sourcePile = PileType.WASTE
                        }
                    }
                    in 3..6 -> { // Foundations
                        val fIdx = cursorX - 3
                        if (foundations[fIdx].isNotEmpty()) {
                            selectedCards.add(foundations[fIdx].last())
                            sourcePile = PileType.FOUNDATION
                            sourceIndex = fIdx
                        }
                    }
                }
            } else { // Tableau
                val tIdx = cursorX
                val tableau = tableaus[tIdx]
                if (tableau.isNotEmpty()) {
                    val cardIdx = cursorY - 1
                    if (tableau[cardIdx].isFaceUp) {
                        selectedCards.addAll(tableau.subList(cardIdx, tableau.size))
                        sourcePile = PileType.TABLEAU
                        sourceIndex = tIdx
                    }
                }
            }
            if (selectedCards.isNotEmpty()) SoundManager.playClick()
        } else {
            // Placing
            var moveSuccessful = false
            if (cursorY == 0) {
                if (cursorX in 3..6 && selectedCards.size == 1) {
                    val fIdx = cursorX - 3
                    if (canMoveToFoundation(selectedCards[0], fIdx)) {
                        moveCardsToFoundation(fIdx)
                        moveSuccessful = true
                    }
                }
            } else {
                val tIdx = cursorX
                if (canMoveToTableau(selectedCards, tIdx)) {
                    moveCardsToTableau(tIdx)
                    moveSuccessful = true
                }
            }

            if (moveSuccessful) {
                finalizeMove()
                SoundManager.playSuccess()
                checkWin()
            } else {
                selectedCards.clear()
                sourcePile = null
                SoundManager.playError()
            }
        }
    }

    private fun canMoveToFoundation(card: Card, fIdx: Int): Boolean {
        val foundation = foundations[fIdx]
        if (foundation.isEmpty()) {
            return card.rank == Rank.ACE
        }
        val topCard = foundation.last()
        return card.suit == topCard.suit && card.rank.value == topCard.rank.value + 1
    }

    private fun moveCardsToFoundation(fIdx: Int) {
        val card = selectedCards[0]
        removeCardsFromSource()
        foundations[fIdx].add(card)
        score += 10
    }

    private fun canMoveToTableau(cards: List<Card>, tIdx: Int): Boolean {
        val tableau = tableaus[tIdx]
        val firstCard = cards[0]
        if (tableau.isEmpty()) {
            return firstCard.rank == Rank.KING
        }
        val topCard = tableau.last()
        return firstCard.isRed != topCard.isRed && firstCard.rank.value == topCard.rank.value - 1
    }

    private fun moveCardsToTableau(tIdx: Int) {
        removeCardsFromSource()
        tableaus[tIdx].addAll(selectedCards)
        score += 5
    }

    private fun removeCardsFromSource() {
        when (sourcePile) {
            PileType.WASTE -> waste.removeAt(waste.size - 1)
            PileType.FOUNDATION -> foundations[sourceIndex].removeAt(foundations[sourceIndex].size - 1)
            PileType.TABLEAU -> {
                val tableau = tableaus[sourceIndex]
                repeat(selectedCards.size) { tableau.removeAt(tableau.size - 1) }
                if (tableau.isNotEmpty() && !tableau.last().isFaceUp) {
                    tableau.last().isFaceUp = true
                    score += 5
                }
            }
            else -> {}
        }
    }

    private fun finalizeMove() {
        selectedCards.clear()
        sourcePile = null
        ScoreManager.updateHighScore(context, gameKey, score)
    }

    private fun checkWin() {
        if (foundations.all { it.size == 13 }) {
            isGameOver = true
            SoundManager.playSuccess()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Update pulse animation
        if (!isGameOver) {
            pulseFactor += 0.015f * pulseDirection
            if (pulseFactor > 1.1f) {
                pulseFactor = 1.1f
                pulseDirection = -1
            } else if (pulseFactor < 0.9f) {
                pulseFactor = 0.9f
                pulseDirection = 1
            }
            invalidate()
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val cardW = w * cardWidthRatio
        val cardH = h * cardHeightRatio
        val spacing = (w - 7 * cardW) / 8f

        // Draw Foundations & Stock/Waste
        drawCardPile(canvas, stock.lastOrNull(), spacing, spacing, cardW, cardH, false)
        drawCardPile(canvas, waste.lastOrNull(), 2 * spacing + cardW, spacing, cardW, cardH, true)
        
        for (i in 0 until 4) {
            drawCardPile(canvas, foundations[i].lastOrNull(), 4 * spacing + 3 * cardW + i * (spacing + cardW), spacing, cardW, cardH, true)
        }

        // Draw Tableaus
        for (i in 0 until 7) {
            val tx = spacing + i * (spacing + cardW)
            val ty = 2 * spacing + cardH
            if (tableaus[i].isEmpty()) {
                drawEmptySlot(canvas, tx, ty, cardW, cardH)
            } else {
                for (j in tableaus[i].indices) {
                    val card = tableaus[i][j]
                    val cardY = ty + j * cardH * 0.2f
                    drawCard(canvas, card, tx, cardY, cardW, cardH)
                }
            }
        }

        // Draw Cursor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f * pulseFactor
        paint.color = Color.YELLOW
        if (cursorY == 0) {
            val cx = if (cursorX < 2) spacing + cursorX * (spacing + cardW) 
                     else 4 * spacing + 3 * cardW + (cursorX - 3) * (spacing + cardW)
            if (cursorX != 2) {
                val pad = 5f * pulseFactor
                canvas.drawRoundRect(cx - pad, spacing - pad, cx + cardW + pad, spacing + cardH + pad, 12f, 12f, paint)
            }
        } else {
            val cx = spacing + cursorX * (spacing + cardW)
            val ty = 2 * spacing + cardH
            val cy = ty + (cursorY - 1) * cardH * 0.2f
            val pad = 5f * pulseFactor
            canvas.drawRoundRect(cx - pad, cy - pad, cx + cardW + pad, cy + cardH + pad, 12f, 12f, paint)
        }

        // Draw Score
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 30f
        canvas.drawText("Score: $score", 20f, h - 20, paint)

        if (isGameOver) drawOverlay(canvas, "YOU WIN!", "Score: $score\nPress CENTER to Restart")
    }

    private fun drawCardPile(canvas: Canvas, card: Card?, x: Float, y: Float, w: Float, h: Float, faceUp: Boolean) {
        if (card == null) {
            drawEmptySlot(canvas, x, y, w, h)
        } else {
            drawCard(canvas, card, x, y, w, h, faceUp)
        }
    }

    private fun drawEmptySlot(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.alpha = 100
        canvas.drawRoundRect(x, y, x + w, y + h, 10f, 10f, paint)
        paint.alpha = 255
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float, forceFaceUp: Boolean = false) {
        val isFaceUp = card.isFaceUp || forceFaceUp
        
        // Shadow/Elevation
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.alpha = 40
        canvas.drawRoundRect(x + 4, y + 4, x + w + 4, y + h + 4, 12f, 12f, paint)
        paint.alpha = 255

        paint.color = if (isFaceUp) Color.WHITE else Color.parseColor("#3F51B5")
        canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#E0E0E0")
        paint.strokeWidth = 2f
        canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, paint)

        if (isFaceUp) {
            paint.style = Paint.Style.FILL
            paint.color = if (card.isRed) Color.parseColor("#D32F2F") else Color.parseColor("#212121")
            paint.textSize = w * 0.35f
            paint.isFakeBoldText = true
            val rankText = when(card.rank) {
                Rank.ACE -> "A"
                Rank.JACK -> "J"
                Rank.QUEEN -> "Q"
                Rank.KING -> "K"
                else -> card.rank.value.toString()
            }
            canvas.drawText(rankText, x + 12, y + paint.textSize + 8, paint)
            
            paint.textSize = w * 0.45f
            val suitSymbol = when(card.suit) {
                Suit.HEARTS -> "♥"
                Suit.DIAMONDS -> "♦"
                Suit.CLUBS -> "♣"
                Suit.SPADES -> "♠"
            }
            canvas.drawText(suitSymbol, x + w - paint.measureText(suitSymbol) - 10, y + h - 12, paint)
            paint.isFakeBoldText = false
        } else {
            // Card back design
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.alpha = 60
            paint.strokeWidth = 3f
            canvas.drawCircle(x + w/2, y + h/2, w * 0.25f, paint)
            canvas.drawRect(x + 10, y + 10, x + w - 10, y + h - 10, paint)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.color = Color.parseColor("#AA000000")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 80f
        canvas.drawText(title, width / 2f, height / 2f - 40, paint)
        paint.textSize = 40f
        val lines = subtitle.split("\n")
        var yOffset = height / 2f + 40
        for (line in lines) {
            canvas.drawText(line, width / 2f, yOffset, paint)
            yOffset += 50
        }
        paint.textAlign = Paint.Align.LEFT
    }
}
