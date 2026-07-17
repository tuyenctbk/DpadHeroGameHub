package com.tdpham.games.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.brickbreak.BrickBreakActivity
import com.tdpham.games.snake.SnakeActivity
import com.tdpham.games.minesweeper.MinesweeperActivity
import com.tdpham.games.sokoban.SokobanActivity
import com.tdpham.games.sudoku.SudokuActivity
import com.tdpham.games.tetris.TetrisActivity
import com.tdpham.games.starfighter.StarFighterActivity
import com.tdpham.games.memory.MemoryActivity
import com.tdpham.games.slidepuzzle.SlidePuzzleActivity
import com.tdpham.games.mentalmath.MentalMathActivity
import com.tdpham.games.simon.SimonSaysActivity
import com.tdpham.games.tanks.BattleTanksActivity
import com.tdpham.games.wordquest.WordQuestActivity
import com.tdpham.games.lines98.Lines98Activity
import com.tdpham.games.solitaire.SolitaireActivity
import com.tdpham.games.dungeon.DungeonEscapeActivity
import com.tdpham.games.twentyfortyeight.TwentyFortyEightActivity
import com.tdpham.games.trex.TRexActivity
import com.tdpham.games.tictactoe.TicTacToeActivity
import com.tdpham.games.hangman.HangmanActivity
import com.tdpham.games.roadracer.RoadRacerActivity
import com.tdpham.games.flappy.FlappyHeroActivity
import com.tdpham.games.checkers.CheckersActivity
import com.tdpham.games.spinball.SpinballActivity
import com.tdpham.games.froggy.FroggyCrossActivity
import com.tdpham.games.syobon.SyobonActivity
import com.tdpham.games.common.SoundManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var returnedFromGame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Analytics in background to avoid blocking main thread during Binder contention
        lifecycleScope.launch(Dispatchers.Default) {
            val analytics = try {
                Firebase.analytics
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Failed to initialize Firebase Analytics: ${e.message}", e)
                null
            }
            withContext(Dispatchers.Main) {
                firebaseAnalytics = analytics
            }
        }

        val title = findViewById<android.view.View>(R.id.main_title)
        title.alpha = 0f
        title.translationY = -50f
        title.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(300).start()

        findViewById<ImageButton>(R.id.btn_settings).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
            setOnHoverListener { view, event ->
                if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                    view.requestFocus()
                }
                false
            }
        }

        setupGameButtons()

        RatingGuideManager.incrementPlayCount(this)
        UpdateManager.checkForUpdates(this) { hasUpdate ->
            if (isFinishing || isDestroyed) return@checkForUpdates
            
            if (!hasUpdate) {
                if (RatingGuideManager.shouldShowRating(this)) {
                    RatingGuideManager.showRatingDialog(this) {
                        if (!isFinishing && !isDestroyed) focusLastPlayed()
                    }
                } else {
                    focusLastPlayed()
                }
            } else {
                focusLastPlayed()
            }
        }
    }

    private fun setupGameButtons() {
        val games = mapOf(
            R.id.btn_snake to SnakeActivity::class.java,
            R.id.btn_tetris to TetrisActivity::class.java,
            R.id.btn_minesweeper to MinesweeperActivity::class.java,
            R.id.btn_trex to TRexActivity::class.java,
            R.id.btn_4096 to TwentyFortyEightActivity::class.java,
            R.id.btn_memory to MemoryActivity::class.java,
            R.id.btn_brick_break to BrickBreakActivity::class.java,
            R.id.btn_syobon to SyobonActivity::class.java,
            R.id.btn_solitaire to SolitaireActivity::class.java,
            R.id.btn_lines98 to Lines98Activity::class.java,
            R.id.btn_mental_math to MentalMathActivity::class.java,
            R.id.btn_sudoku to SudokuActivity::class.java,
            R.id.btn_tictactoe to TicTacToeActivity::class.java,
            R.id.btn_word_quest to WordQuestActivity::class.java,
            R.id.btn_road_racer to RoadRacerActivity::class.java,
            R.id.btn_sokoban to SokobanActivity::class.java,
            R.id.btn_tanks to BattleTanksActivity::class.java,
            R.id.btn_starfighter to StarFighterActivity::class.java,
            R.id.btn_dungeon to DungeonEscapeActivity::class.java,
            R.id.btn_slide_puzzle to SlidePuzzleActivity::class.java,
            R.id.btn_hangman to HangmanActivity::class.java,
            R.id.btn_simon to SimonSaysActivity::class.java,
            R.id.btn_flappy to FlappyHeroActivity::class.java,
            R.id.btn_checkers to CheckersActivity::class.java,
            R.id.btn_spinball to SpinballActivity::class.java,
            R.id.btn_froggy to FroggyCrossActivity::class.java
        )

        for ((id, activityClass) in games) {
            val button = findViewById<Button>(id) ?: continue
            setupGameButton(button) {
                startActivity(Intent(this, activityClass))
            }
        }
    }

    private fun focusLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        val lastPlayed = prefs.getString("last_played", "snake")
        
        when (lastPlayed) {
            "snake" -> findViewById<Button>(R.id.btn_snake).requestFocus()
            "tetris" -> findViewById<Button>(R.id.btn_tetris).requestFocus()
            "minesweeper" -> findViewById<Button>(R.id.btn_minesweeper).requestFocus()
            "trex" -> findViewById<Button>(R.id.btn_trex).requestFocus()
            "4096" -> findViewById<Button>(R.id.btn_4096).requestFocus()
            "memory" -> findViewById<Button>(R.id.btn_memory).requestFocus()
            "brick_break" -> findViewById<Button>(R.id.btn_brick_break).requestFocus()
            "solitaire" -> findViewById<Button>(R.id.btn_solitaire).requestFocus()
            "lines98" -> findViewById<Button>(R.id.btn_lines98).requestFocus()
            "mental_math" -> findViewById<Button>(R.id.btn_mental_math).requestFocus()
            "sudoku" -> findViewById<Button>(R.id.btn_sudoku).requestFocus()
            "tic_tac_toe" -> findViewById<Button>(R.id.btn_tictactoe).requestFocus()
            "word_quest" -> findViewById<Button>(R.id.btn_word_quest).requestFocus()
            "road_racer" -> findViewById<Button>(R.id.btn_road_racer).requestFocus()
            "sokoban" -> findViewById<Button>(R.id.btn_sokoban).requestFocus()
            "battle_tanks" -> findViewById<Button>(R.id.btn_tanks).requestFocus()
            "star_fighter" -> findViewById<Button>(R.id.btn_starfighter).requestFocus()
            "dungeon_escape" -> findViewById<Button>(R.id.btn_dungeon).requestFocus()
            "slide_puzzle" -> findViewById<Button>(R.id.btn_slide_puzzle).requestFocus()
            "hangman" -> findViewById<Button>(R.id.btn_hangman).requestFocus()
            "simon_says" -> findViewById<Button>(R.id.btn_simon).requestFocus()
            "flappy_hero" -> findViewById<Button>(R.id.btn_flappy).requestFocus()
            "froggy_cross" -> findViewById<Button>(R.id.btn_froggy).requestFocus()
            "syobon_action" -> findViewById<Button>(R.id.btn_syobon).requestFocus()
            "checkers" -> findViewById<Button>(R.id.btn_checkers).requestFocus()
            "spinball" -> findViewById<Button>(R.id.btn_spinball).requestFocus()
            else -> findViewById<Button>(R.id.btn_snake).requestFocus()
        }
    }

    private fun setupGameButton(button: Button, action: () -> Unit) {
        button.isFocusableInTouchMode = true
        button.setOnClickListener { 
            returnedFromGame = true
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, button.text.toString())
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "game")
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
            action() 
        }
        
        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // "Light Up" Animation: Scale up + Elevation + Overshoot for punchy feel
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .translationZ(24f)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .setDuration(350)
                    .start()
            } else {
                // Return to normal
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(250)
                    .start()
            }
        }

        button.setOnHoverListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    view.requestFocus()
                }
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (returnedFromGame) {
            returnedFromGame = false
            // Check for rating prompt when returning from a game, 
            // as this is a high-engagement moment ("Having Fun").
            if (RatingGuideManager.shouldShowRating(this)) {
                RatingGuideManager.showRatingDialog(this) {
                    if (!isFinishing && !isDestroyed) focusLastPlayed()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
