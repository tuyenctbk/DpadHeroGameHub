package com.tdpham.games.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import com.tdpham.games.R
import com.tdpham.games.hub.GuideManager
import com.tdpham.games.hub.RatingGuideManager
import com.tdpham.games.trex.TRexOptionsDialog
import com.tdpham.games.trex.TRexView
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
    private var gameOverCount = 0
    private var pauseDialog: InGamePauseDialog? = null

    protected open fun shouldShowHelpButton(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())

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
        
        val view = findViewById<View>(getGameViewId())
        if (view is GameView) {
            gameView = view
            gameView.gameKey = gameKey
            
            adOverlayHelper = IdleAdOverlayHelper(this).apply { init() }
            IdleAdManager.init { state, remaining ->
                adOverlayHelper.showState(state, remaining)
            }

            gameView.onGameOver = { score ->
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
                bundle.putInt(FirebaseAnalytics.Param.SCORE, score)
                firebaseAnalytics?.logEvent("level_end", bundle)

                // Record event for smart rating and sharing engagement:
                val currentBest = ScoreManager.getHighScore(this, gameKey)
                val isHighScore = score > 0 && score >= currentBest
                
                AppEngagementManager.onGameCompleted(this, isWin = score > 0, isNewHighScore = isHighScore)

                // Natural Game-Over Interstitial Ad Trigger (Respects cooldown & frequency)
                gameOverCount++
                val freq = ConfigManager.getAdsGameOverFrequency()
                if (gameOverCount >= freq && AdManager.canShowInterstitial(this)) {
                    gameOverCount = 0
                    AdManager.showInterstitial(this)
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
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(400).start()
        view.requestFocus()
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

    protected open fun showPauseDialog() {
        if (isGuideShowing || isFinishing || isDestroyed) return
        IdleAdManager.notifyInteraction()
        gameView.pause()

        pauseDialog?.dismiss()
        pauseDialog = InGamePauseDialog.show(
            context = this,
            title = "$gameTitle - ${getString(R.string.pause_menu_title)}",
            hasOptions = true,
            onResume = {
                pauseDialog = null
                (gameView as View).requestFocus()
                gameView.resume()
            },
            onOptions = {
                pauseDialog = null
                showGameOptions {
                    (gameView as View).requestFocus()
                    gameView.resume()
                }
            },
            onGuide = {
                pauseDialog = null
                showGameGuide()
            },
            onRestart = {
                pauseDialog = null
                (gameView as View).requestFocus()
                when (gameKey) {
                    "trex" -> (gameView as? com.tdpham.games.trex.TRexView)?.resetGame()
                    "snake" -> (gameView as? com.tdpham.games.snake.SnakeGameView)?.resetGame()
                    "minesweeper" -> (gameView as? com.tdpham.games.minesweeper.MinesweeperView)?.resetGame()
                    "sudoku" -> (gameView as? com.tdpham.games.sudoku.SudokuView)?.resetGame()
                    "memory" -> (gameView as? com.tdpham.games.memory.MemoryView)?.resetGame()
                    "slide_puzzle" -> (gameView as? com.tdpham.games.slidepuzzle.SlidePuzzleView)?.resetGame()
                    "tic_tac_toe" -> (gameView as? com.tdpham.games.tictactoe.TicTacToeView)?.resetGame()
                    "hangman" -> (gameView as? com.tdpham.games.hangman.HangmanView)?.resetGame()
                    "solitaire" -> (gameView as? com.tdpham.games.solitaire.SolitaireView)?.resetGame()
                    "4096" -> (gameView as? com.tdpham.games.twentyfortyeight.TwentyFortyEightView)?.resetGame()
                    "tetris" -> (gameView as? com.tdpham.games.tetris.TetrisView)?.resetGame()
                    "mental_math" -> (gameView as? com.tdpham.games.mentalmath.MentalMathView)?.resetGame()
                    "flappy_hero" -> (gameView as? com.tdpham.games.flappy.FlappyHeroView)?.resetGame()
                    "brick_break" -> (gameView as? com.tdpham.games.brickbreak.BrickBreakView)?.resetGame()
                    "lines98" -> (gameView as? com.tdpham.games.lines98.Lines98View)?.resetGame()
                    "word_quest" -> (gameView as? com.tdpham.games.wordquest.WordQuestView)?.resetGame()
                    "road_racer" -> (gameView as? com.tdpham.games.roadracer.RoadRacerView)?.resetGame()
                    "sokoban" -> (gameView as? com.tdpham.games.sokoban.SokobanView)?.resetGame()
                    "battle_tanks" -> (gameView as? com.tdpham.games.tanks.BattleTanksView)?.resetGame()
                    "starfighter", "star_fighter" -> (gameView as? com.tdpham.games.starfighter.StarFighterView)?.resetGame()
                    "dungeon_escape" -> (gameView as? com.tdpham.games.dungeon.DungeonEscapeView)?.resetGame()
                    "froggy_cross" -> (gameView as? com.tdpham.games.froggy.FroggyCrossView)?.resetGame()
                    "simon_says" -> (gameView as? com.tdpham.games.simon.SimonSaysView)?.resetGame()
                    "checkers" -> (gameView as? com.tdpham.games.checkers.CheckersView)?.resetGame()
                    "spinball" -> (gameView as? com.tdpham.games.spinball.SpinballView)?.resetGame()
                    "syobon_action" -> (gameView as? com.tdpham.games.syobon.SyobonView)?.resetGame()
                    "monkey" -> (gameView as? com.tdpham.games.monkey.MonkeyView)?.resetGame()
                    "frenzy" -> (gameView as? com.tdpham.games.frenzy.FrenzyView)?.resetGame()
                    "retrodriver" -> (gameView as? com.tdpham.games.retrodriver.RetroDriverView)?.resetGame()
                    "fruit" -> (gameView as? com.tdpham.games.fruit.FruitView)?.resetGame()
                }
                gameView.resume()
            },
            onExit = {
                pauseDialog = null
                if (AdManager.canShowInterstitial(this)) {
                    AdManager.showInterstitial(this) {
                        finish()
                    }
                } else {
                    finish()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        IdleAdManager.isGameMode = true
        IdleAdManager.isWaitingMode = !hasStarted || isGuideShowing
        IdleAdManager.startTracking()
        if (!isGuideShowing) {
            gameView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        IdleAdManager.stopTracking()
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
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_M || keyCode == KeyEvent.KEYCODE_O || 
                keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                if (showGameOptions()) {
                    return true
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
        when (gameKey) {
            "trex" -> TRexOptionsDialog.show(this) { (gameView as? com.tdpham.games.trex.TRexView)?.resetGame(); callback() }
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
            else -> {
                callback()
                return false
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showPauseDialog()
            return true
        }
        
        // Pass specialized keys (M, O, etc.) to the game view even if activity handles some
        if (keyCode == KeyEvent.KEYCODE_M || keyCode == KeyEvent.KEYCODE_O || 
            keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if ((gameView as View).onKeyDown(keyCode, event)) return true
        }

        // Hide overlay on any D-pad input
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            removeActiveOverlay()
        }

        if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            gameView.toggleSound()
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
        return (gameView as View).onKeyDown(keyCode, event)
    }

    private fun showInGameLeaderboard() {
        val intent = Intent(this, com.tdpham.games.hub.LeaderboardActivity::class.java)
        startActivity(intent)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return (gameView as View).onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseDialog?.dismiss()
        pauseDialog = null
        removeActiveOverlay()
        adOverlayHelper.destroy()
    }
}
