package com.tdpham.games.blackjack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.tdpham.games.common.GameView
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BlackjackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GameView {

    override var gameKey: String = "blackjack"
    override var onGameOver: ((Int) -> Unit)? = null

    enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
    enum class Rank(val value: Int, val symbol: String) {
        TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
        SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"),
        TEN(10, "10"), JACK(10, "J"), QUEEN(10, "Q"), KING(10, "K"),
        ACE(11, "A")
    }

    data class Card(val suit: Suit, val rank: Rank, var isFaceUp: Boolean = true) {
        val isRed: Boolean get() = suit == Suit.HEARTS || suit == Suit.DIAMONDS
    }

    enum class GameState {
        BETTING,
        DEALING,
        PLAYER_TURN,
        DEALER_TURN,
        ROUND_OVER
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val deck = mutableListOf<Card>()
    private val playerHand = mutableListOf<Card>()
    private val dealerHand = mutableListOf<Card>()

    private var currentBet = 50
    private var chipBalance = 500
    private var winStreak = 0
    private var highestBalance = 500

    private var gameState = GameState.BETTING
    private var statusMessage = "PLACE YOUR BET TO DEAL"
    private var subStatus = ""

    // Betting controls index: 0: +10, 1: +25, 2: +100, 3: +500, 4: Clear, 5: DEAL
    private var betFocusIndex = 5

    // Player action controls index: 0: HIT, 1: STAND, 2: DOUBLE DOWN
    private var actionFocusIndex = 0

    // Settings
    private var deckCount = 6
    private var standSoft17 = true
    private var tableTheme = 0

    private val celebrationManager = CelebrationManager()
    private var isEndCelebration = false

    private val gameHandler = Handler(Looper.getMainLooper())
    private val dealerStepRunnable = Runnable { stepDealer() }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (isEndCelebration) celebrationManager.update()
            invalidate()
            gameHandler.postDelayed(this, 30)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        loadSettings()
        syncCoins()
        resetGame()
        gameHandler.post(loopRunnable)
    }

    private fun loadSettings() {
        val prefs = context.getSharedPreferences("blackjack_settings", Context.MODE_PRIVATE)
        deckCount = prefs.getInt(BlackjackOptionsDialog.KEY_DECKS, 6)
        standSoft17 = prefs.getBoolean(BlackjackOptionsDialog.KEY_STAND_SOFT_17, true)
        tableTheme = prefs.getInt(BlackjackOptionsDialog.KEY_TABLE_THEME, 0)
    }

    private fun syncCoins() {
        chipBalance = DailyRewardManager.getCoinBalance(context)
        if (chipBalance < 25) {
            DailyRewardManager.addCoins(context, 100)
            chipBalance = DailyRewardManager.getCoinBalance(context)
        }
        highestBalance = max(chipBalance, ScoreManager.getHighScore(context, gameKey))
    }

    override fun startGame() {
        requestFocus()
    }

    override fun pause() {
        gameHandler.removeCallbacks(dealerStepRunnable)
    }

    override fun resume() {
        requestFocus()
        loadSettings()
        syncCoins()
    }

    override fun toggleSound(): Boolean = SoundManager.toggleSound()

    override fun resetGame() {
        loadSettings()
        syncCoins()
        buildShoe()
        playerHand.clear()
        dealerHand.clear()
        gameState = GameState.BETTING
        currentBet = min(50, chipBalance)
        if (currentBet <= 0 && chipBalance > 0) currentBet = min(10, chipBalance)
        statusMessage = "PLACE YOUR BET TO DEAL"
        subStatus = "Use D-Pad to select chips, press DEAL"
        isEndCelebration = false
        invalidate()
    }

    private fun buildShoe() {
        deck.clear()
        for (d in 0 until deckCount) {
            for (s in Suit.values()) {
                for (r in Rank.values()) {
                    deck.add(Card(s, r))
                }
            }
        }
        deck.shuffle()
    }

    private fun drawCard(faceUp: Boolean = true): Card {
        if (deck.size < 15) {
            buildShoe()
        }
        val card = deck.removeAt(deck.size - 1)
        card.isFaceUp = faceUp
        return card
    }

    private fun calculateHandValue(hand: List<Card>): Int {
        var sum = 0
        var aces = 0
        for (c in hand) {
            if (!c.isFaceUp) continue
            if (c.rank == Rank.ACE) {
                aces++
                sum += 11
            } else {
                sum += c.rank.value
            }
        }
        while (sum > 21 && aces > 0) {
            sum -= 10
            aces--
        }
        return sum
    }

    private fun isSoft17(hand: List<Card>): Boolean {
        var sum = 0
        var aces = 0
        for (c in hand) {
            if (!c.isFaceUp) continue
            if (c.rank == Rank.ACE) {
                aces++
                sum += 11
            } else {
                sum += c.rank.value
            }
        }
        return sum == 17 && aces > 0
    }

    private fun startDeal() {
        if (currentBet <= 0 || currentBet > chipBalance) {
            SoundManager.playError()
            return
        }

        // Deduct bet from balance
        DailyRewardManager.addCoins(context, -currentBet)
        chipBalance = DailyRewardManager.getCoinBalance(context)

        playerHand.clear()
        dealerHand.clear()
        gameState = GameState.DEALING
        isEndCelebration = false

        SoundManager.playClick()
        HapticManager.vibrateClick(context)

        // Deal 2 cards to player, 2 to dealer (1 face down)
        playerHand.add(drawCard(true))
        dealerHand.add(drawCard(true))
        playerHand.add(drawCard(true))
        dealerHand.add(drawCard(false)) // Hole card

        val playerVal = calculateHandValue(playerHand)
        val isPlayerBlackjack = (playerHand.size == 2 && playerVal == 21)

        if (isPlayerBlackjack) {
            // Reveal dealer hole card
            dealerHand[1].isFaceUp = true
            val dealerVal = calculateHandValue(dealerHand)
            val isDealerBlackjack = (dealerHand.size == 2 && dealerVal == 21)

            if (isDealerBlackjack) {
                // Push
                endRound("PUSH! Both have Blackjack 🤝", 0, isPush = true)
            } else {
                // 3:2 payout!
                val winnings = (currentBet * 2.5f).toInt()
                endRound("NATURAL BLACKJACK! 👑 (Pays 3:2)", winnings, isBlackjack = true)
            }
        } else {
            gameState = GameState.PLAYER_TURN
            actionFocusIndex = 0
            statusMessage = "YOUR TURN: HIT OR STAND?"
            subStatus = "Hand Total: $playerVal"
        }
        invalidate()
    }

    private fun hitPlayer() {
        if (gameState != GameState.PLAYER_TURN) return
        val card = drawCard(true)
        playerHand.add(card)
        SoundManager.playClick()
        HapticManager.vibrateClick(context)

        val total = calculateHandValue(playerHand)
        if (total > 21) {
            // Bust
            dealerHand[1].isFaceUp = true
            endRound("BUST! Over 21 (${total}) 💥", 0, isBust = true)
        } else if (total == 21) {
            // Auto-stand on 21
            standPlayer()
        } else {
            subStatus = "Hand Total: $total"
        }
        invalidate()
    }

    private fun standPlayer() {
        if (gameState != GameState.PLAYER_TURN) return
        gameState = GameState.DEALER_TURN
        dealerHand[1].isFaceUp = true
        statusMessage = "DEALER TURN..."
        subStatus = "Dealer Hand: ${calculateHandValue(dealerHand)}"
        SoundManager.playClick()
        HapticManager.vibrateClick(context)

        gameHandler.postDelayed(dealerStepRunnable, 600)
        invalidate()
    }

    private fun doubleDownPlayer() {
        if (gameState != GameState.PLAYER_TURN || playerHand.size != 2) return
        if (chipBalance < currentBet) {
            SoundManager.playError()
            subStatus = "Not enough coins to Double Down!"
            invalidate()
            return
        }

        // Deduct extra bet
        DailyRewardManager.addCoins(context, -currentBet)
        chipBalance = DailyRewardManager.getCoinBalance(context)
        currentBet *= 2

        // Player gets exactly 1 card then auto-stands
        val card = drawCard(true)
        playerHand.add(card)
        SoundManager.playClick()
        HapticManager.vibrateClick(context)

        val total = calculateHandValue(playerHand)
        if (total > 21) {
            dealerHand[1].isFaceUp = true
            endRound("BUST ON DOUBLE! (${total}) 💥", 0, isBust = true)
        } else {
            standPlayer()
        }
    }

    private fun stepDealer() {
        if (gameState != GameState.DEALER_TURN) return
        val dealerVal = calculateHandValue(dealerHand)

        val mustHit = if (standSoft17) {
            dealerVal < 17
        } else {
            dealerVal < 17 || isSoft17(dealerHand)
        }

        if (mustHit) {
            dealerHand.add(drawCard(true))
            SoundManager.playClick()
            HapticManager.vibrateClick(context)
            subStatus = "Dealer draws... (${calculateHandValue(dealerHand)})"
            invalidate()
            gameHandler.postDelayed(dealerStepRunnable, 700)
        } else {
            // Dealer stops, resolve winner
            val finalDealer = calculateHandValue(dealerHand)
            val finalPlayer = calculateHandValue(playerHand)

            when {
                finalDealer > 21 -> {
                    val winAmount = currentBet * 2
                    endRound("DEALER BUSTS (${finalDealer})! YOU WIN! 🎉", winAmount)
                }
                finalPlayer > finalDealer -> {
                    val winAmount = currentBet * 2
                    endRound("YOU WIN! ($finalPlayer vs $finalDealer) 🏆", winAmount)
                }
                finalDealer > finalPlayer -> {
                    endRound("DEALER WINS ($finalDealer vs $finalPlayer) 💀", 0)
                }
                else -> {
                    endRound("PUSH! ($finalPlayer vs $finalDealer) 🤝", currentBet, isPush = true)
                }
            }
        }
    }

    private fun endRound(msg: String, payout: Int, isBlackjack: Boolean = false, isBust: Boolean = false, isPush: Boolean = false) {
        gameState = GameState.ROUND_OVER
        statusMessage = msg

        if (payout > 0) {
            DailyRewardManager.addCoins(context, payout)
            chipBalance = DailyRewardManager.getCoinBalance(context)
        }

        if (isBlackjack || (payout > currentBet && !isPush)) {
            winStreak++
            SoundManager.playSuccess()
            HapticManager.vibrateSuccess(context)
            if (isBlackjack || winStreak >= 3) {
                isEndCelebration = true
                val w = width.toFloat()
                val h = height.toFloat()
                celebrationManager.startOutcome(w, h, isWin = true, isNewHigh = chipBalance > highestBalance, score = chipBalance, highScore = highestBalance)
            }
        } else if (isPush) {
            SoundManager.playClick()
        } else {
            winStreak = 0
            SoundManager.playError()
            HapticManager.vibrateExplosion(context)
        }

        // Update high score
        if (chipBalance > highestBalance) {
            highestBalance = chipBalance
            ScoreManager.updateHighScore(context, gameKey, highestBalance)
        }

        subStatus = context.getString(R.string.press_enter_next_round)
        onGameOver?.invoke(chipBalance)
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (gameState) {
            GameState.BETTING -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (betFocusIndex > 0) {
                            betFocusIndex--
                            SoundManager.playClick()
                            HapticManager.vibrateClick(context)
                            invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (betFocusIndex < 5) {
                            betFocusIndex++
                            SoundManager.playClick()
                            HapticManager.vibrateClick(context)
                            invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Rapid bet increase
                        adjustBet(50)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Rapid bet decrease
                        adjustBet(-50)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                        triggerBetAction()
                        return true
                    }
                }
            }
            GameState.PLAYER_TURN -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (actionFocusIndex > 0) {
                            actionFocusIndex--
                            SoundManager.playClick()
                            HapticManager.vibrateClick(context)
                            invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val maxAction = if (playerHand.size == 2 && chipBalance >= currentBet) 2 else 1
                        if (actionFocusIndex < maxAction) {
                            actionFocusIndex++
                            SoundManager.playClick()
                            HapticManager.vibrateClick(context)
                            invalidate()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                        when (actionFocusIndex) {
                            0 -> hitPlayer()
                            1 -> standPlayer()
                            2 -> doubleDownPlayer()
                        }
                        return true
                    }
                }
            }
            GameState.ROUND_OVER -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
                    resetGame()
                    return true
                }
            }
            else -> {}
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun adjustBet(delta: Int) {
        val newBet = (currentBet + delta).coerceIn(10, chipBalance)
        if (newBet != currentBet) {
            currentBet = newBet
            SoundManager.playClick()
            HapticManager.vibrateClick(context)
            invalidate()
        }
    }

    private fun triggerBetAction() {
        when (betFocusIndex) {
            0 -> adjustBet(10)
            1 -> adjustBet(25)
            2 -> adjustBet(100)
            3 -> adjustBet(500)
            4 -> {
                currentBet = min(10, chipBalance)
                SoundManager.playClick()
                invalidate()
            }
            5 -> startDeal()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Casino Felt Background
        val (feltColor, feltDark) = when (tableTheme) {
            1 -> Pair(Color.parseColor("#0D254C"), Color.parseColor("#06132A")) // Sapphire
            2 -> Pair(Color.parseColor("#4A0E17"), Color.parseColor("#26050A")) // Crimson
            3 -> Pair(Color.parseColor("#2B210B"), Color.parseColor("#140F04")) // Gold/Black
            else -> Pair(Color.parseColor("#0E472A"), Color.parseColor("#062415")) // Emerald
        }

        paint.color = feltColor
        canvas.drawRect(0f, 0f, w, h, paint)

        // Oval Table Border Ring
        paint.color = Color.parseColor("#FFD700")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        val tableRect = RectF(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.88f)
        canvas.drawRoundRect(tableRect, 40f, 40f, paint)

        paint.style = Paint.Style.FILL

        // 2. Top Header Bar (Balance, Streak, High Bankroll)
        drawCasinoHeader(canvas, w, h)

        // 3. Dealer Area
        drawDealerArea(canvas, w, h)

        // 4. Player Area
        drawPlayerArea(canvas, w, h)

        // 5. Center Status Banner
        drawCenterStatus(canvas, w, h)

        // 6. Action Control Bar (Betting or Hit/Stand)
        drawBottomActionControls(canvas, w, h)

        // 7. Confetti
        if (isEndCelebration) {
            celebrationManager.draw(canvas)
        }
    }

    private fun drawCasinoHeader(canvas: Canvas, w: Float, h: Float) {
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 20f
        paint.isFakeBoldText = true

        // Balance & Chips
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("CHIPS: $$chipBalance 🪙", w * 0.06f, 36f, paint)

        paint.textSize = 15f
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawText("STREAK: $winStreak WINS 🔥", w * 0.06f, 60f, paint)

        // Title Center
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 22f
        paint.color = Color.WHITE
        canvas.drawText("VEGAS BLACKJACK 21", w * 0.5f, 38f, paint)

        paint.textSize = 14f
        paint.color = Color.parseColor("#B0BEC5")
        canvas.drawText("BLACKJACK PAYS 3 TO 2 • DEALER STANDS ON 17", w * 0.5f, 58f, paint)

        // High Score Right
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 18f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("BEST: $$highestBalance", w * 0.94f, 36f, paint)

        paint.textSize = 14f
        paint.color = Color.WHITE
        canvas.drawText("SHOE: ${deck.size} CARDS", w * 0.94f, 58f, paint)
    }

    private fun drawDealerArea(canvas: Canvas, w: Float, h: Float) {
        val areaY = h * 0.14f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        paint.color = Color.WHITE
        paint.isFakeBoldText = true

        val dealerTotal = calculateHandValue(dealerHand)
        val dealerLabel = if (dealerHand.any { !it.isFaceUp }) "DEALER" else "DEALER: $dealerTotal"
        canvas.drawText(dealerLabel, w * 0.5f, areaY, paint)

        // Draw Dealer Cards
        drawCardRow(canvas, dealerHand, w * 0.5f, areaY + 16f, w, h)
    }

    private fun drawPlayerArea(canvas: Canvas, w: Float, h: Float) {
        val areaY = h * 0.44f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        paint.color = Color.parseColor("#00E5FF")
        paint.isFakeBoldText = true

        val playerTotal = calculateHandValue(playerHand)
        val playerLabel = if (playerHand.isEmpty()) "YOUR HAND" else "YOUR HAND: $playerTotal (BET: $$currentBet)"
        canvas.drawText(playerLabel, w * 0.5f, areaY, paint)

        // Draw Player Cards
        drawCardRow(canvas, playerHand, w * 0.5f, areaY + 16f, w, h)
    }

    private fun drawCardRow(canvas: Canvas, hand: List<Card>, centerX: Float, topY: Float, w: Float, h: Float) {
        if (hand.isEmpty()) return
        val cardW = min(w * 0.10f, 90f)
        val cardH = cardW * 1.45f
        val spacing = cardW * 0.38f
        val totalRowW = cardW + (hand.size - 1) * spacing
        var startX = centerX - totalRowW / 2f

        for (card in hand) {
            drawSingleCard(canvas, card, startX, topY, cardW, cardH)
            startX += spacing
        }
    }

    private fun drawSingleCard(canvas: Canvas, card: Card, x: Float, y: Float, cardW: Float, cardH: Float) {
        val cardRect = RectF(x, y, x + cardW, y + cardH)

        // Card Drop Shadow
        paint.color = Color.parseColor("#55000000")
        canvas.drawRoundRect(RectF(x + 4f, y + 4f, x + cardW + 4f, y + cardH + 4f), 10f, 10f, paint)

        if (!card.isFaceUp) {
            // Face-down card back
            paint.color = Color.parseColor("#B71C1C") // Crimson card back
            canvas.drawRoundRect(cardRect, 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(x + 5f, y + 5f, x + cardW - 5f, y + cardH - 5f), 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            // Diamond pattern center
            paint.color = Color.parseColor("#D32F2F")
            canvas.drawCircle(x + cardW / 2f, y + cardH / 2f, cardW * 0.22f, paint)
            return
        }

        // Face-up card
        paint.color = Color.WHITE
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#CFD8DC")
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        val suitColor = if (card.isRed) Color.parseColor("#D50000") else Color.parseColor("#212121")
        val suitSymbol = when (card.suit) {
            Suit.SPADES -> "♠"
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
        }

        // Top-left Rank & Suit
        paint.color = suitColor
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = cardW * 0.28f
        paint.isFakeBoldText = true
        canvas.drawText(card.rank.symbol, x + 6f, y + cardW * 0.28f + 2f, paint)

        paint.textSize = cardW * 0.22f
        canvas.drawText(suitSymbol, x + 6f, y + cardW * 0.54f, paint)

        // Center Large Suit
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = cardW * 0.46f
        canvas.drawText(suitSymbol, x + cardW / 2f, y + cardH / 2f + cardW * 0.16f, paint)
    }

    private fun drawCenterStatus(canvas: Canvas, w: Float, h: Float) {
        val bannerY = h * 0.72f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        paint.textSize = 22f
        paint.color = when {
            statusMessage.contains("WIN") || statusMessage.contains("BLACKJACK") -> Color.parseColor("#00E676")
            statusMessage.contains("BUST") || statusMessage.contains("DEALER WINS") -> Color.parseColor("#FF5252")
            statusMessage.contains("PUSH") -> Color.parseColor("#FFD700")
            else -> Color.WHITE
        }
        canvas.drawText(statusMessage, w * 0.5f, bannerY, paint)

        if (subStatus.isNotEmpty()) {
            paint.textSize = 15f
            paint.color = Color.parseColor("#E0E0E0")
            canvas.drawText(subStatus, w * 0.5f, bannerY + 26f, paint)
        }
    }

    private fun drawBottomActionControls(canvas: Canvas, w: Float, h: Float) {
        val bottomY = h * 0.84f

        if (gameState == GameState.BETTING) {
            val buttons = listOf("+$10", "+$25", "+$100", "+$500", "CLEAR", "DEAL ($$currentBet)")
            val btnW = w * 0.13f
            val btnH = 46f
            val spacing = w * 0.016f
            val totalW = buttons.size * btnW + (buttons.size - 1) * spacing
            var startX = (w - totalW) / 2f

            for (i in buttons.indices) {
                val isFocused = (i == betFocusIndex)
                val rect = RectF(startX, bottomY, startX + btnW, bottomY + btnH)

                // Background
                paint.color = when {
                    i == 5 -> Color.parseColor("#00C853") // Green DEAL button
                    i == 4 -> Color.parseColor("#D32F2F") // Red CLEAR
                    else -> Color.parseColor("#1E293B")
                }
                canvas.drawRoundRect(rect, 14f, 14f, paint)

                if (isFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f
                    paint.color = Color.parseColor("#FFD700")
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.style = Paint.Style.FILL
                }

                // Text
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = if (i == 5) 15f else 14f
                paint.color = Color.WHITE
                paint.isFakeBoldText = true
                canvas.drawText(buttons[i], startX + btnW / 2f, bottomY + 28f, paint)

                startX += btnW + spacing
            }
        } else if (gameState == GameState.PLAYER_TURN) {
            val canDouble = (playerHand.size == 2 && chipBalance >= currentBet)
            val buttons = if (canDouble) listOf("HIT 🃏", "STAND ✋", "DOUBLE 2X 💰") else listOf("HIT 🃏", "STAND ✋")

            val btnW = w * 0.22f
            val btnH = 50f
            val spacing = w * 0.04f
            val totalW = buttons.size * btnW + (buttons.size - 1) * spacing
            var startX = (w - totalW) / 2f

            for (i in buttons.indices) {
                val isFocused = (i == actionFocusIndex)
                val rect = RectF(startX, bottomY, startX + btnW, bottomY + btnH)

                paint.color = when (i) {
                    0 -> Color.parseColor("#0288D1") // HIT Blue
                    1 -> Color.parseColor("#E65100") // STAND Orange
                    else -> Color.parseColor("#7B1FA2") // DOUBLE Purple
                }
                canvas.drawRoundRect(rect, 16f, 16f, paint)

                if (isFocused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 5f
                    paint.color = Color.parseColor("#FFD700")
                    canvas.drawRoundRect(rect, 16f, 16f, paint)
                    paint.style = Paint.Style.FILL
                }

                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 17f
                paint.color = Color.WHITE
                paint.isFakeBoldText = true
                canvas.drawText(buttons[i], startX + btnW / 2f, bottomY + 31f, paint)

                startX += btnW + spacing
            }
        } else if (gameState == GameState.ROUND_OVER) {
            // Next Round prompt
            val btnW = 280f
            val btnH = 50f
            val btnL = (w - btnW) / 2f
            val rect = RectF(btnL, bottomY, btnL + btnW, bottomY + btnH)

            paint.color = Color.parseColor("#00E676")
            canvas.drawRoundRect(rect, 25f, 25f, paint)

            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 19f
            paint.isFakeBoldText = true
            canvas.drawText("NEXT ROUND [ENTER]", w / 2f, bottomY + 32f, paint)
        }
    }
}
