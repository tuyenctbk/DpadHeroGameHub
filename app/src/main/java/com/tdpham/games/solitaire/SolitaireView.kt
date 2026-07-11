package com.tdpham.games.solitaire

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.tdpham.games.common.GamePalette
import com.tdpham.games.common.GameView
import com.tdpham.games.common.GameEnvironment
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.CelebrationManager
import com.tdpham.games.R
import java.util.*

class SolitaireView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "solitaire"
    override var onGameOver: ((Int) -> Unit)? = null
    
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
    private var sourceCardIndex: Int = -1

    private var score = 0
    private var isGameOver = false
    private var isPaused = false
    private var currentVictoryWord = ""
    private val celebrationManager = CelebrationManager()

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
        celebrationManager.start(0f, 0f)
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
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
        sourceCardIndex = -1
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            performClick()
            if (isGameOver) {
                resetGame()
                return true
            }

            // Calculate card bounds (must match onDraw)
            val w = width.toFloat()
            val h = height.toFloat()
            val cardW = w * cardWidthRatio
            val cardH = h * cardHeightRatio
            val spacing = (w - 7 * cardW) / 8f
            
            val x = event.x
            val y = event.y

            // Top Row
            if (y >= spacing && y <= spacing + cardH) {
                // Stock
                if (x >= spacing && x <= spacing + cardW) {
                    cursorX = 0; cursorY = 0; handleSelection(); invalidate(); return true
                }
                // Waste
                if (x >= 2 * spacing + cardW && x <= 2 * spacing + 2 * cardW) {
                    cursorX = 1; cursorY = 0; handleSelection(); invalidate(); return true
                }
                // Foundations
                for (i in 0 until 4) {
                    val fx = 4 * spacing + 3 * cardW + i * (spacing + cardW)
                    if (x >= fx && x <= fx + cardW) {
                        cursorX = 3 + i; cursorY = 0; handleSelection(); invalidate(); return true
                    }
                }
            }
            
            // Tableaus
            val ty = 2 * spacing + cardH
            if (y >= ty) {
                for (i in 0 until 7) {
                    val tx = spacing + i * (spacing + cardW)
                    if (x >= tx && x <= tx + cardW) {
                        cursorX = i; cursorY = 1; handleSelection(); invalidate(); return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveCursor(dx: Int, dy: Int) {
        if (dx != 0) {
            cursorX = (cursorX + dx + 7) % 7
            // Skip the gap at index 2 in top row
            if (cursorY == 0 && cursorX == 2) {
                cursorX = (cursorX + dx + 7) % 7
            }
        }
        if (dy != 0) {
            cursorY = if (dy > 0) 1 else 0
            // If moving to top row, ensure we don't land on the gap
            if (cursorY == 0 && cursorX == 2) {
                cursorX = 3 // Move to first foundation
            }
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
                            sourceCardIndex = foundations[fIdx].size - 1
                        }
                    }
                }
            } else { // Tableau
                val tIdx = cursorX
                val tableau = tableaus[tIdx]
                if (tableau.isNotEmpty()) {
                    // Smart: Try Auto-move to foundation first
                    val lastCard = tableau.last()
                    for (i in 0 until 4) {
                        if (canMoveToFoundation(lastCard, i)) {
                            selectedCards.add(lastCard)
                            sourcePile = PileType.TABLEAU
                            sourceIndex = tIdx
                            sourceCardIndex = tableau.size - 1
                            moveCardsToFoundation(i)
                            finalizeMove()
                            SoundManager.playSuccess()
                            checkWin()
                            return
                        }
                    }

                    // Else pick up the entire face-up stack
                    val firstFaceUpIdx = tableau.indexOfFirst { it.isFaceUp }
                    if (firstFaceUpIdx != -1) {
                        selectedCards.addAll(tableau.subList(firstFaceUpIdx, tableau.size))
                        sourcePile = PileType.TABLEAU
                        sourceIndex = tIdx
                        sourceCardIndex = firstFaceUpIdx
                        SoundManager.playClick()
                    }
                }
            }
            if (selectedCards.isNotEmpty()) SoundManager.playClick()
        } else {
            // Placing
            var moveSuccessful = false
            if (cursorY == 0) {
                if (cursorX in 3..6) {
                    val fIdx = cursorX - 3
                    val lastCard = selectedCards.last()
                    if (canMoveToFoundation(lastCard, fIdx)) {
                        // Move ONLY the last card of our stack
                        val cardToMove = lastCard
                        selectedCards.removeAt(selectedCards.size - 1)
                        val rest = selectedCards.toList()
                        
                        selectedCards.add(cardToMove) // For removeCardsFromSource
                        removeCardsFromSource()
                        returnCardsToSource(rest)
                        
                        foundations[fIdx].add(cardToMove)
                        score += 10
                        moveSuccessful = true
                    }
                }
            } else {
                val tIdx = cursorX
                val movableIdx = findMovableStackIndex(selectedCards, tIdx)
                if (movableIdx != -1) {
                    val toMove = selectedCards.subList(movableIdx, selectedCards.size).toList()
                    val toReturn = selectedCards.subList(0, movableIdx).toList()
                    
                    removeCardsFromSource()
                    returnCardsToSource(toReturn)
                    
                    tableaus[tIdx].addAll(toMove)
                    score += 5
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
                sourceCardIndex = -1
                SoundManager.playError()
            }
        }
    }

    private fun findMovableStackIndex(cards: List<Card>, tIdx: Int): Int {
        val tableau = tableaus[tIdx]
        if (tableau.isEmpty()) {
            return cards.indexOfFirst { it.rank == Rank.KING }
        }
        val topCard = tableau.last()
        return cards.indexOfFirst { it.isRed != topCard.isRed && it.rank.value == topCard.rank.value - 1 }
    }

    private fun returnCardsToSource(cards: List<Card>) {
        if (cards.isEmpty()) return
        when (sourcePile) {
            PileType.WASTE -> waste.addAll(cards)
            PileType.FOUNDATION -> foundations[sourceIndex].addAll(cards)
            PileType.TABLEAU -> tableaus[sourceIndex].addAll(cards)
            else -> {}
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

    private fun removeCardsFromSource() {
        when (sourcePile) {
            PileType.WASTE -> if (waste.isNotEmpty()) waste.removeAt(waste.size - 1)
            PileType.FOUNDATION -> if (foundations[sourceIndex].isNotEmpty()) foundations[sourceIndex].removeAt(foundations[sourceIndex].size - 1)
            PileType.TABLEAU -> {
                val tableau = tableaus[sourceIndex]
                repeat(selectedCards.size) { if (tableau.isNotEmpty()) tableau.removeAt(tableau.size - 1) }
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
        sourceCardIndex = -1
        ScoreManager.updateHighScore(context, gameKey, score)
    }

    private fun checkWin() {
        if (foundations.all { it.size == 13 }) {
            isGameOver = true
            currentVictoryWord = celebrationManager.getRandomVictoryWord(context, gameKey)
            celebrationManager.startOutcome(width.toFloat(), height.toFloat(), true, score, score)
            SoundManager.playSuccess()
            onGameOver?.invoke(score)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Green Felt Background
        GameEnvironment.draw(canvas, GameEnvironment.BackgroundType.FELT, paint = paint)

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
                if (cursorX == i && cursorY == 1) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f * pulseFactor
                    paint.color = Color.YELLOW
                    canvas.drawRoundRect(tx, ty, tx + cardW, ty + cardH, 12f, 12f, paint)
                }
            } else {
                for (j in tableaus[i].indices) {
                    val card = tableaus[i][j]
                    val cardY = ty + j * cardH * 0.3f
                    val isSelected = sourcePile == PileType.TABLEAU && sourceIndex == i && j >= sourceCardIndex
                    
                    val isCursorOnColumn = cursorX == i && cursorY == 1
                    val isCursor = isCursorOnColumn && card.isFaceUp
                    
                    drawCard(canvas, card, tx, cardY, cardW, cardH, isSelected = isSelected, isCursor = isCursor)
                    
                    if (isCursorOnColumn && j == tableaus[i].size - 1) {
                        drawMagnifiedCard(canvas, card, tx, cardY, cardW, cardH)
                    }
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
                
                val currentCard = when(cursorX) {
                    0 -> stock.lastOrNull()
                    1 -> waste.lastOrNull()
                    in 3..6 -> foundations[cursorX - 3].lastOrNull()
                    else -> null
                }
                if (currentCard != null) {
                    drawMagnifiedCard(canvas, currentCard, cx, spacing, cardW, cardH)
                }
            }
        } else {
            // No individual card focus box needed anymore, handled by column highlight
        }

        // Draw Score
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 30f
        val hudY = Math.round(h - 20).toFloat()
        canvas.drawText("${context.getString(R.string.score_label)}: $score", 20f, hudY, paint)

        if (isGameOver) {
            celebrationManager.update()
            celebrationManager.draw(canvas)
            invalidate()
            drawOverlay(canvas, currentVictoryWord, "${context.getString(R.string.score_label)}: $score\n${context.getString(R.string.restart_hint)}")
        }
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

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float, forceFaceUp: Boolean = false, isSelected: Boolean = false, isCursor: Boolean = false) {
        val isFaceUp = card.isFaceUp || forceFaceUp
        
        // Shadow/Elevation
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.alpha = if (isSelected) 80 else 40
        val elev = if (isSelected) 8f else 4f
        canvas.drawRoundRect(x + elev, y + elev, x + w + elev, y + h + elev, 12f, 12f, paint)
        paint.alpha = 255

        paint.color = if (isFaceUp) {
            if (isSelected) Color.parseColor("#FFFDE7") else Color.WHITE
        } else {
            if (isSelected) Color.parseColor("#5C6BC0") else Color.parseColor("#3F51B5")
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 12f, 12f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.color = if (isSelected) Color.YELLOW else Color.parseColor("#E0E0E0")
        paint.strokeWidth = if (isSelected) 4f else 2f
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
            
            // If cursor is on this card, show a larger suit in center
            if (isCursor) {
                paint.alpha = 40
                paint.textSize = w * 0.8f
                canvas.drawText(suitSymbol, x + w/2 - paint.measureText(suitSymbol)/2, y + h/2 + paint.textSize/3, paint)
                paint.alpha = 255
            }
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

    private fun drawMagnifiedCard(canvas: Canvas, card: Card, x: Float, y: Float, w: Float, h: Float) {
        // Draw a slightly larger version over the current card for visibility
        // Only if it's face up
        if (!card.isFaceUp) return
        
        val mag = 1.1f
        val mw = w * mag
        val mh = h * mag
        val mx = x - (mw - w) / 2
        val my = y - (mh - h) / 2
        
        drawCard(canvas, card, mx, my, mw, mh, forceFaceUp = true)
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        paint.color = GamePalette.OVERLAY
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
