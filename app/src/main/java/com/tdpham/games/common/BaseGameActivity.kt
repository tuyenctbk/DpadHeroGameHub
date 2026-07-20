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
            gameView.onGameOver = { score ->
                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
                bundle.putInt(FirebaseAnalytics.Param.SCORE, score)
                firebaseAnalytics?.logEvent("level_end", bundle)

                // Record event for rating algorithm:
                // Consider it a 'win' if score > 0 (engaged)
                // We check if score >= currentBest because the High Score is often updated
                // right before onGameOver is called.
                val currentBest = ScoreManager.getHighScore(this, gameKey)
                val isHighScore = score > 0 && score >= currentBest
                
                RatingGuideManager.recordGameEvent(this, isWin = score > 0, isHighScore = isHighScore)
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
            // Do not auto-start game here to allow user to see lobby/options
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

    protected fun showGameGuide() {
        removeActiveOverlay()
        isGuideShowing = true
        gameView.pause()
        val btnText = if (hasStarted) getString(R.string.resume) else getString(R.string.start_game)
        
        // Only show "Don't show again" checkbox if shown at the very beginning (not started yet)
        val showCheckbox = !hasStarted
        
        GuideManager.showGuide(
            this, gameKey, gameTitle, gameInstructions, btnText,
            showCheckbox = showCheckbox,
            onDismiss = {
                isGuideShowing = false
                if (!hasStarted) {
                    startGameWithAnalytics()
                } else {
                    gameView.resume()
                }
                (gameView as View).requestFocus()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (!isGuideShowing) {
            gameView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
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
        // Intercept M and O keys globally for T-Rex Run and Snake
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_M || keyCode == KeyEvent.KEYCODE_O || 
                keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                
                if (gameKey == "trex") {
                    removeActiveOverlay()
                    TRexOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.trex.TRexView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "snake") {
                    removeActiveOverlay()
                    com.tdpham.games.snake.SnakeOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.snake.SnakeGameView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "minesweeper") {
                    removeActiveOverlay()
                    com.tdpham.games.minesweeper.MinesweeperOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.minesweeper.MinesweeperView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "sudoku") {
                    removeActiveOverlay()
                    com.tdpham.games.sudoku.SudokuOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.sudoku.SudokuView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "memory") {
                    removeActiveOverlay()
                    com.tdpham.games.memory.MemoryOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.memory.MemoryView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "slide_puzzle") {
                    removeActiveOverlay()
                    com.tdpham.games.slidepuzzle.SlidePuzzleOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.slidepuzzle.SlidePuzzleView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "tic_tac_toe") {
                    removeActiveOverlay()
                    com.tdpham.games.tictactoe.TicTacToeOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.tictactoe.TicTacToeView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "hangman") {
                    removeActiveOverlay()
                    com.tdpham.games.hangman.HangmanOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.hangman.HangmanView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "solitaire") {
                    removeActiveOverlay()
                    com.tdpham.games.solitaire.SolitaireOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.solitaire.SolitaireView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "4096") {
                    removeActiveOverlay()
                    com.tdpham.games.twentyfortyeight.TwentyFortyEightOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.twentyfortyeight.TwentyFortyEightView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "tetris") {
                    removeActiveOverlay()
                    com.tdpham.games.tetris.TetrisOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.tetris.TetrisView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "mental_math") {
                    removeActiveOverlay()
                    com.tdpham.games.mentalmath.MentalMathOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.mentalmath.MentalMathView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "flappy_hero") {
                    removeActiveOverlay()
                    com.tdpham.games.flappy.FlappyHeroOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.flappy.FlappyHeroView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "brick_break") {
                    removeActiveOverlay()
                    com.tdpham.games.brickbreak.BrickBreakOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.brickbreak.BrickBreakView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "lines98") {
                    removeActiveOverlay()
                    com.tdpham.games.lines98.Lines98OptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.lines98.Lines98View)?.resetGame()
                    }
                    return true
                } else if (gameKey == "word_quest") {
                    removeActiveOverlay()
                    com.tdpham.games.wordquest.WordQuestOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.wordquest.WordQuestView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "road_racer") {
                    removeActiveOverlay()
                    com.tdpham.games.roadracer.RoadRacerOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.roadracer.RoadRacerView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "sokoban") {
                    removeActiveOverlay()
                    com.tdpham.games.sokoban.SokobanOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.sokoban.SokobanView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "battle_tanks") {
                    removeActiveOverlay()
                    com.tdpham.games.tanks.BattleTanksOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.tanks.BattleTanksView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "starfighter") {
                    removeActiveOverlay()
                    com.tdpham.games.starfighter.StarFighterOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.starfighter.StarFighterView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "dungeon_escape") {
                    removeActiveOverlay()
                    com.tdpham.games.dungeon.DungeonOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.dungeon.DungeonEscapeView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "froggy_cross") {
                    removeActiveOverlay()
                    com.tdpham.games.froggy.FroggyOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.froggy.FroggyCrossView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "simon_says") {
                    removeActiveOverlay()
                    com.tdpham.games.simon.SimonOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.simon.SimonSaysView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "checkers") {
                    removeActiveOverlay()
                    com.tdpham.games.checkers.CheckersOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.checkers.CheckersView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "spinball") {
                    removeActiveOverlay()
                    com.tdpham.games.spinball.SpinballOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.spinball.SpinballView)?.resetGame()
                    }
                    return true
                } else if (gameKey == "syobon_action") {
                    removeActiveOverlay()
                    com.tdpham.games.syobon.SyobonOptionsDialog.show(this) {
                        (gameView as? com.tdpham.games.syobon.SyobonView)?.resetGame()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AdManager.showInterstitial(this) {
                finish()
            }
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
}
