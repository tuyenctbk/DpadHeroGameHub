package com.tdpham.games.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import com.tdpham.games.R
import com.tdpham.games.hub.GuideManager
import com.tdpham.games.hub.RatingGuideManager
import com.tdpham.games.common.IdleAdManager
import com.tdpham.games.common.IdleAdOverlayHelper

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseGameActivity : AppCompatActivity() {
    
    protected abstract val gameKey: String
    protected abstract val gameTitle: String
    protected abstract val gameInstructions: String
    
    protected lateinit var gameView: GameView
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private lateinit var btnHelp: View
    private var isGuideShowing = false
    private var hasStarted = false
    private var activeOverlay: View? = null
    private lateinit var adOverlayHelper: IdleAdOverlayHelper
    private var revivePromptShowing = false
    private var revivedLastRound = false
    private var pauseDialog: InGamePauseDialog? = null
    private var inGamePauseOverlay: PauseOverlay? = null
    private var reviveOverlay: RevivePromptOverlay? = null
    private var pendingReviveRunnable: Runnable? = null
    private var pendingInterstitialRunnable: Runnable? = null
    protected var isGameOverState = false

    private val pauseActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePauseActivityResult(result.resultCode)
    }

    protected open fun shouldShowHelpButton(): Boolean = false

    private fun cancelPendingPostGamePrompts() {
        val decor = window?.decorView
        pendingReviveRunnable?.let { decor?.removeCallbacks(it) }
        pendingReviveRunnable = null
        pendingInterstitialRunnable?.let { decor?.removeCallbacks(it) }
        pendingInterstitialRunnable = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())

        val view = findViewById<View>(getGameViewId())

        lifecycleScope.launch(Dispatchers.Default) {
            val analytics = try {
                Firebase.analytics
            } catch (e: Exception) {
                android.util.Log.e("BaseGameActivity", "Failed to initialize Firebase Analytics: ${e.message}", e)
                null
            }
            withContext(Dispatchers.Main) {
                firebaseAnalytics = analytics
            }
        }
        
        if (view is GameView) {
            gameView = view
            gameView.gameKey = gameKey
            
            adOverlayHelper = IdleAdOverlayHelper(this).apply { init() }

            // Warm a rewarded ad only for games that support revive (avoids wasted requests)
            if (gameView.supportsRevive) AdManager.preloadRewarded(this)

            gameView.onGameOver = { score ->
                cancelPendingPostGamePrompts()
                isGameOverState = true
                HapticManager.vibrateExplosion(this)
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
                bundle.putInt(FirebaseAnalytics.Param.SCORE, score)
                firebaseAnalytics?.logEvent("level_end", bundle)

                val currentBest = ScoreManager.getHighScore(this, gameKey)
                val isHighScore = score > 0 && score >= currentBest

                // Priority 1: Rewarded "continue" offer (only on true loss, never stacked with ads or rate dialogs)
                if (shouldOfferRevive()) {
                    AppEngagementManager.onGameCompleted(
                        this,
                        isWin = score > 0,
                        isNewHighScore = isHighScore,
                        skipPrompt = true
                    )
                    val runnable = Runnable {
                        pendingReviveRunnable = null
                        if (!isFinishing && !isDestroyed && isGameOverState) {
                            showRevivePrompt()
                        }
                    }
                    pendingReviveRunnable = runnable
                    window?.decorView?.postDelayed(runnable, 1100L)
                } else {
                    revivedLastRound = false
                    // Priority 2: Natural Game-Over Interstitial Ad Trigger (Respects cooldown & frequency)
                    val interstitialDue = AdManager.registerGameOver() && AdManager.canShowInterstitial(this)
                    AppEngagementManager.onGameCompleted(
                        this,
                        isWin = score > 0,
                        isNewHighScore = isHighScore,
                        skipPrompt = interstitialDue
                    )
                    if (interstitialDue) {
                        AdManager.resetGameOverCounter()
                        val runnable = Runnable {
                            pendingInterstitialRunnable = null
                            if (!isFinishing && !isDestroyed && isGameOverState) {
                                AdManager.showInterstitial(this)
                            }
                        }
                        pendingInterstitialRunnable = runnable
                        window?.decorView?.postDelayed(runnable, 1400L)
                    }
                }
            }
        } else {
            throw IllegalStateException("View must implement GameView interface")
        }

        btnHelp = findViewById(R.id.btn_show_guide)
        btnHelp.setOnClickListener { showGameGuide() }
        btnHelp.isFocusable = true
        btnHelp.isFocusableInTouchMode = true
        btnHelp.setOnHoverListener { view, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                view.requestFocus()
            }
            false
        }
        
        // Hide or show the help UI container based on game preference
        val helpContainer = (btnHelp.parent as? View)
        if (shouldShowHelpButton()) {
            helpContainer?.visibility = View.VISIBLE
        } else {
            helpContainer?.visibility = View.GONE
        }

        handleGuideProgression()
        saveLastPlayed()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    /** Offer only when: game supports revive, a rewarded ad is ready, and the player didn't just use a revive. */
    private fun shouldOfferRevive(): Boolean {
        if (!::gameView.isInitialized || !gameView.canRevive) return false
        if (revivePromptShowing || revivedLastRound) return false
        if (!ConfigManager.isAdsEnabled()) return false
        return AdManager.isRewardedReady()
    }

    private fun showRevivePrompt() {
        if (revivePromptShowing || isFinishing || isDestroyed) return
        if (!AdManager.isRewardedReady()) return
        val root = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        revivePromptShowing = true
        var earned = false

        reviveOverlay = RevivePromptOverlay.show(
            container = root,
            onWatch = {
                revivePromptShowing = false
                reviveOverlay = null
                AdManager.showRewarded(
                    activity = this,
                    onUserEarnedReward = { earned = true },
                    onAdDismissed = {
                        if (earned) AdManager.resetGameOverCounter()
                        if (earned && gameView.reviveGame()) {
                            revivedLastRound = true
                            isGameOverState = false
                        }
                        (gameView as View).requestFocus()
                        gameView.resume()
                    }
                )
            },
            onDecline = {
                revivePromptShowing = false
                reviveOverlay = null
                revivedLastRound = false
                (gameView as View).requestFocus()
                if (isGameOverState && AdManager.registerGameOver() && AdManager.canShowInterstitial(this)) {
                    AdManager.resetGameOverCounter()
                    val runnable = Runnable {
                        pendingInterstitialRunnable = null
                        if (!isFinishing && !isDestroyed && isGameOverState) {
                            AdManager.showInterstitial(this)
                        }
                    }
                    pendingInterstitialRunnable = runnable
                    window?.decorView?.postDelayed(runnable, 500L)
                }
            }
        )
    }

    private fun handleGuideProgression() {
        if (GuideManager.shouldShowGuide(this, gameKey)) {
            showGameGuide()
        } else {
            if (GuideManager.shouldShowMasteryHint(this, gameKey)) {
                showMasteryHint()
            }
            // Auto-start game if guide is skipped for smoother UX
            startGameWithAnalytics()
            focusGame()
        }
        GuideManager.incrementLaunchCount(this, gameKey)
    }

    private fun focusGame() {
        val view = findViewById<View>(getGameViewId())
        view?.alpha = 1f
        view?.requestFocus()
    }

    private fun showMasteryHint() {
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val hintText = if (gameKey == "trex" || gameKey == "syobon_action") {
            getString(R.string.trex_press_menu_options)
        } else {
            getString(R.string.guide_hint_keys)
        }
        
        val hint = android.widget.TextView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.START
            ).apply { setMargins(32, 32, 0, 0) }
            text = hintText
            setTextColor(android.graphics.Color.WHITE)
            alpha = 0f
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            // Subtle shadow for readability on any background
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }

        root.addView(hint)
        activeOverlay = hint
        hint.animate().alpha(0.8f).setDuration(500).withEndAction {
            hint.animate()
                .alpha(0f)
                .setStartDelay(4000)
                .setDuration(1000)
                .withEndAction { 
                    root.removeView(hint)
                    if (activeOverlay == hint) activeOverlay = null
                }
                .start()
        }.start()
    }

    private fun removeActiveOverlay() {
        activeOverlay?.let {
            it.animate().cancel()
            val root = findViewById<android.view.ViewGroup>(android.R.id.content)
            root.removeView(it)
            activeOverlay = null
        }
    }

    private fun startGameWithAnalytics() {
        hasStarted = true
        isGameOverState = false
        IdleAdManager.isWaitingMode = isGuideShowing
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.LEVEL_START, bundle)
        gameView.startGame()
    }

    private fun saveLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("last_played", gameKey).apply()
    }

    abstract fun getLayoutId(): Int
    abstract fun getGameViewId(): Int

    open fun showGameGuide() {
        removeActiveOverlay()
        isGuideShowing = true
        IdleAdManager.isWaitingMode = true
        gameView.pause()
        val btnText = if (hasStarted) getString(R.string.resume) else getString(R.string.start_game)
        
        // Only show "Don't show again" checkbox if shown at the very beginning (not started yet)
        val showCheckbox = !hasStarted
        
        GuideManager.showGuide(
            context = this,
            gameKey = gameKey,
            title = gameTitle,
            content = gameInstructions,
            buttonText = btnText,
            showCheckbox = showCheckbox,
            onOptionsClick = {
                isGuideShowing = false
                IdleAdManager.isWaitingMode = false
                showGameOptions {
                    if (!hasStarted) {
                        showGameGuide()
                    } else {
                        (gameView as View).requestFocus()
                        gameView.resume()
                    }
                }
            },
            onBackPress = {
                isGuideShowing = false
                IdleAdManager.isWaitingMode = false
                if (!hasStarted) {
                    finish()
                } else {
                    (gameView as View).requestFocus()
                    gameView.resume()
                }
            },
            onDismiss = {
                isGuideShowing = false
                IdleAdManager.isWaitingMode = false
                if (!hasStarted) {
                    startGameWithAnalytics()
                } else {
                    gameView.resume()
                }
                (gameView as View).requestFocus()
            }
        )
    }

    protected open fun handleBackPress() {
        if (isFinishing || isDestroyed) return
        if (activeOverlay != null) {
            removeActiveOverlay()
            return
        }
        if (inGamePauseOverlay?.isShowing == true) {
            inGamePauseOverlay?.hide {
                (gameView as View).requestFocus()
                gameView.resume()
            }
            return
        }
        if (pauseDialog?.isShowing == true) {
            pauseDialog?.dismiss()
            pauseDialog = null
            (gameView as View).requestFocus()
            gameView.resume()
            return
        }
        if (reviveOverlay != null || revivePromptShowing) {
            reviveOverlay?.decline()
            return
        }
        if (isGameOverState) {
            cancelPendingPostGamePrompts()
            exitToHub()
            return
        }
        showPauseDialog()
    }

    protected fun exitToHub() {
        if (AdManager.canShowInterstitial(this)) {
            AdManager.showInterstitial(this) {
                finish()
            }
        } else {
            finish()
        }
    }

    protected open fun showPauseDialog() {
        if (isGuideShowing || isFinishing || isDestroyed) return
        removeActiveOverlay()
        IdleAdManager.notifyInteraction()
        gameView.pause()

        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root != null) {
            if (inGamePauseOverlay == null) {
                inGamePauseOverlay = PauseOverlay.attach(
                    container = root,
                    gameTitle = gameTitle,
                    hasOptions = true,
                    hasGuide = true,
                    onResume = {
                        (gameView as View).requestFocus()
                        gameView.resume()
                    },
                    onOptions = {
                        showGameOptions {
                            (gameView as View).requestFocus()
                            gameView.resume()
                        }
                    },
                    onGuide = {
                        showGameGuide()
                    },
                    onRestart = {
                        restartCurrentGame()
                        gameView.resume()
                    },
                    onExit = {
                        exitToHub()
                    }
                )
            }
            inGamePauseOverlay?.show()
        } else {
            val intent = PauseActivity.createIntent(this, gameKey, gameTitle)
            pauseActivityLauncher.launch(intent)
        }
    }

    private fun handlePauseActivityResult(resultCode: Int) {
        when (resultCode) {
            PauseActivity.RESULT_RESUME -> {
                (gameView as View).requestFocus()
                gameView.resume()
            }
            PauseActivity.RESULT_OPTIONS -> {
                showGameOptions {
                    (gameView as View).requestFocus()
                    gameView.resume()
                }
            }
            PauseActivity.RESULT_RESTART -> {
                restartCurrentGame()
                gameView.resume()
            }
            PauseActivity.RESULT_EXIT -> {
                exitToHub()
            }
        }
    }

    private fun restartCurrentGame() {
        cancelPendingPostGamePrompts()
        isGameOverState = false
        revivedLastRound = false
        (gameView as View).requestFocus()
        gameView.resetGame()
    }

    override fun onResume() {
        super.onResume()
        ScreenShake.syncSettings(this)
        IdleAdManager.isGameMode = true
        IdleAdManager.isWaitingMode = !hasStarted || isGuideShowing
        if (::adOverlayHelper.isInitialized) {
            IdleAdManager.registerListener(this) { state, remaining ->
                adOverlayHelper.showState(state, remaining)
            }
        }
        if (!isGuideShowing && inGamePauseOverlay?.isShowing != true && !revivePromptShowing) {
            gameView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelPendingPostGamePrompts()
        IdleAdManager.unregisterListener(this)
        gameView.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        IdleAdManager.isWaitingMode = !hasFocus || isGuideShowing || !hasStarted
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        IdleAdManager.notifyInteraction()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = gameView as View
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_HOVER_ENTER || event.action == MotionEvent.ACTION_HOVER_MOVE) {
                view.requestFocus()
            }
            if (event.action == MotionEvent.ACTION_BUTTON_PRESS || event.action == MotionEvent.ACTION_BUTTON_RELEASE) {
                if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                    val action = if (event.action == MotionEvent.ACTION_BUTTON_PRESS) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP
                    val pointerEvent = MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        action,
                        event.x,
                        event.y,
                        event.metaState
                    )
                    val handled = view.dispatchTouchEvent(pointerEvent)
                    pointerEvent.recycle()
                    return handled
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN

        val isPauseShowing = inGamePauseOverlay?.isShowing == true || pauseDialog?.isShowing == true
        val isReviveShowing = revivePromptShowing || reviveOverlay != null
        val isModalShowing = isPauseShowing || isReviveShowing || isGuideShowing

        if (isModalShowing) {
            if (isDown) {
                IdleAdManager.notifyInteraction()
                if (ControllerNavigationHelper.isBackKey(keyCode)) {
                    handleBackPress()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    val focused = currentFocus ?: inGamePauseOverlay?.findFocus()
                    if (focused != null && focused.isClickable) {
                        focused.performClick()
                        return true
                    }
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    val focused = currentFocus ?: inGamePauseOverlay?.findFocus()
                    if (focused != null && focused.isClickable) {
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }

        if (isDown) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                handleBackPress()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (::gameView.isInitialized && (gameView as View).onKeyDown(keyCode, event)) {
                    return true
                }
                handleBackPress()
                return true
            }
            if (ControllerNavigationHelper.isMenuKey(keyCode) || keyCode == KeyEvent.KEYCODE_M || keyCode == KeyEvent.KEYCODE_O) {
                if (showGameOptions()) {
                    return true
                }
                showPauseDialog()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_H || keyCode == KeyEvent.KEYCODE_INFO) {
                removeActiveOverlay()
                showGameGuide()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_L || keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
                showInGameLeaderboard()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                gameView.toggleSound()
                return true
            }
            // Remove overlay on D-pad input
            if (ControllerNavigationHelper.isDpadKey(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                removeActiveOverlay()
            }
            // If the player restarts directly from the game-over screen via confirm key, clear post-game state & pending overlays
            if (isGameOverState && ControllerNavigationHelper.isConfirmKey(keyCode)) {
                cancelPendingPostGamePrompts()
                isGameOverState = false
                revivedLastRound = false
            }
        }

        // Direct D-Pad and Gamepad pass-through to active gameView (single delivery, no double onKeyDown)
        if (::gameView.isInitialized) {
            val v = gameView as View
            if (v.visibility == View.VISIBLE) {
                if (isDown) {
                    if (v.onKeyDown(keyCode, event)) {
                        return true
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    if (v.onKeyUp(keyCode, event)) {
                        return true
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    open fun showGameOptions(onComplete: () -> Unit = {}): Boolean {
        removeActiveOverlay()
        gameView.pause()
        val callback: () -> Unit = {
            (gameView as View).requestFocus()
            gameView.resume()
            onComplete()
        }
        if (gameView.showOptionsDialog(this, callback)) {
            return true
        }
        return showFallbackGameOptions(callback)
    }

    private fun showFallbackGameOptions(callback: () -> Unit): Boolean {
        when (gameKey) {
            "trex" -> com.tdpham.games.trex.TRexOptionsDialog.show(this) { (gameView as? com.tdpham.games.trex.TRexView)?.resetGame(); callback() }
            "snake" -> com.tdpham.games.snake.SnakeOptionsDialog.show(this) { (gameView as? com.tdpham.games.snake.SnakeGameView)?.resetGame(); callback() }
            "minesweeper" -> com.tdpham.games.minesweeper.MinesweeperOptionsDialog.show(this) { (gameView as? com.tdpham.games.minesweeper.MinesweeperView)?.resetGame(); callback() }
            "sudoku" -> com.tdpham.games.sudoku.SudokuOptionsDialog.show(this) { (gameView as? com.tdpham.games.sudoku.SudokuView)?.resetGame(); callback() }
            "memory" -> com.tdpham.games.memory.MemoryOptionsDialog.show(this) { (gameView as? com.tdpham.games.memory.MemoryView)?.resetGame(); callback() }
            "slide_puzzle" -> com.tdpham.games.slidepuzzle.SlidePuzzleOptionsDialog.show(this) { (gameView as? com.tdpham.games.slidepuzzle.SlidePuzzleView)?.resetGame(); callback() }
            "tic_tac_toe" -> com.tdpham.games.tictactoe.TicTacToeOptionsDialog.show(this) { (gameView as? com.tdpham.games.tictactoe.TicTacToeView)?.resetGame(); callback() }
            "hangman" -> com.tdpham.games.hangman.HangmanOptionsDialog.show(this) { (gameView as? com.tdpham.games.hangman.HangmanView)?.resetGame(); callback() }
            "solitaire" -> com.tdpham.games.solitaire.SolitaireOptionsDialog.show(this) { (gameView as? com.tdpham.games.solitaire.SolitaireView)?.resetGame(); callback() }
            "4096" -> com.tdpham.games.twentyfortyeight.TwentyFortyEightOptionsDialog.show(this) { (gameView as? com.tdpham.games.twentyfortyeight.TwentyFortyEightView)?.resetGame(); callback() }
            "tetris" -> com.tdpham.games.tetris.TetrisOptionsDialog.show(this) { (gameView as? com.tdpham.games.tetris.TetrisView)?.resetGame(); callback() }
            "mental_math" -> com.tdpham.games.mentalmath.MentalMathOptionsDialog.show(this) { (gameView as? com.tdpham.games.mentalmath.MentalMathView)?.resetGame(); callback() }
            "flappy_hero" -> com.tdpham.games.flappy.FlappyHeroOptionsDialog.show(this) { (gameView as? com.tdpham.games.flappy.FlappyHeroView)?.resetGame(); callback() }
            "brick_break" -> com.tdpham.games.brickbreak.BrickBreakOptionsDialog.show(this) { (gameView as? com.tdpham.games.brickbreak.BrickBreakView)?.resetGame(); callback() }
            "lines98" -> com.tdpham.games.lines98.Lines98OptionsDialog.show(this) { (gameView as? com.tdpham.games.lines98.Lines98View)?.resetGame(); callback() }
            "word_quest" -> com.tdpham.games.wordquest.WordQuestOptionsDialog.show(this) { (gameView as? com.tdpham.games.wordquest.WordQuestView)?.resetGame(); callback() }
            "road_racer" -> com.tdpham.games.roadracer.RoadRacerOptionsDialog.show(this) { (gameView as? com.tdpham.games.roadracer.RoadRacerView)?.resetGame(); callback() }
            "sokoban" -> com.tdpham.games.sokoban.SokobanOptionsDialog.show(this) { (gameView as? com.tdpham.games.sokoban.SokobanView)?.resetGame(); callback() }
            "battle_tanks" -> com.tdpham.games.tanks.BattleTanksOptionsDialog.show(this) { (gameView as? com.tdpham.games.tanks.BattleTanksView)?.resetGame(); callback() }
            "starfighter", "star_fighter" -> com.tdpham.games.starfighter.StarFighterOptionsDialog.show(this) { (gameView as? com.tdpham.games.starfighter.StarFighterView)?.resetGame(); callback() }
            "dungeon_escape" -> com.tdpham.games.dungeon.DungeonOptionsDialog.show(this) { (gameView as? com.tdpham.games.dungeon.DungeonEscapeView)?.resetGame(); callback() }
            "froggy_cross" -> com.tdpham.games.froggy.FroggyOptionsDialog.show(this) { (gameView as? com.tdpham.games.froggy.FroggyCrossView)?.resetGame(); callback() }
            "simon_says" -> com.tdpham.games.simon.SimonOptionsDialog.show(this) { (gameView as? com.tdpham.games.simon.SimonSaysView)?.resetGame(); callback() }
            "checkers" -> com.tdpham.games.checkers.CheckersOptionsDialog.show(this) { (gameView as? com.tdpham.games.checkers.CheckersView)?.resetGame(); callback() }
            "spinball" -> com.tdpham.games.spinball.SpinballOptionsDialog.show(this) { (gameView as? com.tdpham.games.spinball.SpinballView)?.resetGame(); callback() }
            "syobon_action" -> com.tdpham.games.syobon.SyobonOptionsDialog.show(this) { (gameView as? com.tdpham.games.syobon.SyobonView)?.resetGame(); callback() }
            "monkey" -> com.tdpham.games.monkey.MonkeyOptionsDialog.show(this) { (gameView as? com.tdpham.games.monkey.MonkeyView)?.resetGame(); callback() }
            "frenzy" -> com.tdpham.games.frenzy.FrenzyOptionsDialog.show(this) { (gameView as? com.tdpham.games.frenzy.FrenzyView)?.resetGame(); callback() }
            "retrodriver" -> com.tdpham.games.retrodriver.RetroDriverOptionsDialog.show(this) { (gameView as? com.tdpham.games.retrodriver.RetroDriverView)?.resetGame(); callback() }
            "fruit" -> com.tdpham.games.fruit.FruitOptionsDialog.show(this) { (gameView as? com.tdpham.games.fruit.FruitView)?.resetGame(); callback() }
            "connect_four" -> com.tdpham.games.connectfour.ConnectFourOptionsDialog.show(this) { (gameView as? com.tdpham.games.connectfour.ConnectFourView)?.resetGame(); callback() }
            "blackjack" -> com.tdpham.games.blackjack.BlackjackOptionsDialog.show(this) { (gameView as? com.tdpham.games.blackjack.BlackjackView)?.resetGame(); callback() }
            "trivia" -> com.tdpham.games.trivia.TriviaOptionsDialog.show(this) { (gameView as? com.tdpham.games.trivia.TriviaView)?.resetGame(); callback() }
            else -> {
                callback()
                return false
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            handleBackPress()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showInGameLeaderboard() {
        val intent = Intent(this, com.tdpham.games.hub.LeaderboardActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingPostGamePrompts()
        IdleAdManager.unregisterListener(this)
        inGamePauseOverlay?.hide()
        inGamePauseOverlay = null
        pauseDialog?.dismiss()
        pauseDialog = null
        reviveOverlay = null
        removeActiveOverlay()
        if (::adOverlayHelper.isInitialized) {
            adOverlayHelper.destroy()
        }
    }
}
