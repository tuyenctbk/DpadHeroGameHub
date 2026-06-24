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
import com.tdpham.games.checkers.CheckersActivity
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
import com.tdpham.games.froggy.FroggyCrossActivity
import com.tdpham.games.tanks.BattleTanksActivity
import com.tdpham.games.wordquest.WordQuestActivity
import com.tdpham.games.lines98.Lines98Activity
import com.tdpham.games.solitaire.SolitaireActivity
import com.tdpham.games.dungeon.DungeonEscapeActivity
import com.tdpham.games.flappy.FlappyHeroActivity
import com.tdpham.games.twentyfortyeight.TwentyFortyEightActivity
import com.tdpham.games.trex.TRexActivity
import com.tdpham.games.tictactoe.TicTacToeActivity
import com.tdpham.games.hangman.HangmanActivity
import com.tdpham.games.roadracer.RoadRacerActivity
import com.tdpham.games.common.SoundManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase

class MainActivity : AppCompatActivity() {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAnalytics = try {
            Firebase.analytics
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize Firebase Analytics: ${e.message}", e)
            null
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

        val btnSnake = findViewById<Button>(R.id.btn_snake)
        setupGameButton(btnSnake) {
            startActivity(Intent(this, SnakeActivity::class.java))
        }

        val btnMinesweeper = findViewById<Button>(R.id.btn_minesweeper)
        setupGameButton(btnMinesweeper) {
            startActivity(Intent(this, MinesweeperActivity::class.java))
        }

        val btn4096 = findViewById<Button>(R.id.btn_4096)
        setupGameButton(btn4096) {
            startActivity(Intent(this, TwentyFortyEightActivity::class.java))
        }

        val btnTRex = findViewById<Button>(R.id.btn_trex)
        setupGameButton(btnTRex) {
            startActivity(Intent(this, TRexActivity::class.java))
        }

        val btnBrickBreak = findViewById<Button>(R.id.btn_brick_break)
        setupGameButton(btnBrickBreak) {
            startActivity(Intent(this, BrickBreakActivity::class.java))
        }

        val btnTicTacToe = findViewById<Button>(R.id.btn_tictactoe)
        setupGameButton(btnTicTacToe) {
            startActivity(Intent(this, TicTacToeActivity::class.java))
        }

        val btnCheckers = findViewById<Button>(R.id.btn_checkers)
        setupGameButton(btnCheckers) {
            startActivity(Intent(this, CheckersActivity::class.java))
        }

        val btnSokoban = findViewById<Button>(R.id.btn_sokoban)
        setupGameButton(btnSokoban) {
            startActivity(Intent(this, SokobanActivity::class.java))
        }

        val btnSudoku = findViewById<Button>(R.id.btn_sudoku)
        setupGameButton(btnSudoku) {
            startActivity(Intent(this, SudokuActivity::class.java))
        }

        val btnTetris = findViewById<Button>(R.id.btn_tetris)
        setupGameButton(btnTetris) {
            startActivity(Intent(this, TetrisActivity::class.java))
        }

        val btnStarFighter = findViewById<Button>(R.id.btn_starfighter)
        setupGameButton(btnStarFighter) {
            startActivity(Intent(this, StarFighterActivity::class.java))
        }

        val btnMemory = findViewById<Button>(R.id.btn_memory)
        setupGameButton(btnMemory) {
            startActivity(Intent(this, MemoryActivity::class.java))
        }

        val btnSlidePuzzle = findViewById<Button>(R.id.btn_slide_puzzle)
        setupGameButton(btnSlidePuzzle) {
            startActivity(Intent(this, SlidePuzzleActivity::class.java))
        }


        val btnMentalMath = findViewById<Button>(R.id.btn_mental_math)
        setupGameButton(btnMentalMath) {
            startActivity(Intent(this, MentalMathActivity::class.java))
        }

        val btnFroggy = findViewById<Button>(R.id.btn_froggy)
        setupGameButton(btnFroggy) {
            startActivity(Intent(this, FroggyCrossActivity::class.java))
        }

        val btnSimon = findViewById<Button>(R.id.btn_simon)
        setupGameButton(btnSimon) {
            startActivity(Intent(this, SimonSaysActivity::class.java))
        }

        val btnTanks = findViewById<Button>(R.id.btn_tanks)
        setupGameButton(btnTanks) {
            startActivity(Intent(this, BattleTanksActivity::class.java))
        }

        val btnWordQuest = findViewById<Button>(R.id.btn_word_quest)
        setupGameButton(btnWordQuest) {
            startActivity(Intent(this, WordQuestActivity::class.java))
        }

        val btnDungeon = findViewById<Button>(R.id.btn_dungeon)
        setupGameButton(btnDungeon) {
            startActivity(Intent(this, DungeonEscapeActivity::class.java))
        }

        val btnFlappy = findViewById<Button>(R.id.btn_flappy)
        setupGameButton(btnFlappy) {
            startActivity(Intent(this, FlappyHeroActivity::class.java))
        }

        val btnLines98 = findViewById<Button>(R.id.btn_lines98)
        setupGameButton(btnLines98) {
            startActivity(Intent(this, Lines98Activity::class.java))
        }

        val btnSolitaire = findViewById<Button>(R.id.btn_solitaire)
        setupGameButton(btnSolitaire) {
            startActivity(Intent(this, SolitaireActivity::class.java))
        }

        val btnRoadRacer = findViewById<Button>(R.id.btn_road_racer)
        setupGameButton(btnRoadRacer) {
            startActivity(Intent(this, RoadRacerActivity::class.java))
        }

        val btnHangman = findViewById<Button>(R.id.btn_hangman)
        setupGameButton(btnHangman) {
            startActivity(Intent(this, HangmanActivity::class.java))
        }

        RatingGuideManager.incrementPlayCount(this)
        UpdateManager.checkForUpdates(this) { hasUpdate ->
            if (!hasUpdate) {
                if (RatingGuideManager.shouldShowRating(this)) {
                    RatingGuideManager.showRatingDialog(this) {
                        focusLastPlayed()
                    }
                } else {
                    focusLastPlayed()
                }
            }
        }
    }

    private fun focusLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        val lastPlayed = prefs.getString("last_played", "snake")
        
        when (lastPlayed) {
            "snake" -> findViewById<Button>(R.id.btn_snake).requestFocus()
            "minesweeper" -> findViewById<Button>(R.id.btn_minesweeper).requestFocus()
            "4096" -> findViewById<Button>(R.id.btn_4096).requestFocus()
            "trex" -> findViewById<Button>(R.id.btn_trex).requestFocus()
            "brick_break" -> findViewById<Button>(R.id.btn_brick_break).requestFocus()
            "tic_tac_toe" -> findViewById<Button>(R.id.btn_tictactoe).requestFocus()
            "checkers" -> findViewById<Button>(R.id.btn_checkers).requestFocus()
            "sokoban" -> findViewById<Button>(R.id.btn_sokoban).requestFocus()
            "sudoku" -> findViewById<Button>(R.id.btn_sudoku).requestFocus()
            "tetris" -> findViewById<Button>(R.id.btn_tetris).requestFocus()
            "star_fighter" -> findViewById<Button>(R.id.btn_starfighter).requestFocus()
            "memory" -> findViewById<Button>(R.id.btn_memory).requestFocus()
            "slide_puzzle" -> findViewById<Button>(R.id.btn_slide_puzzle).requestFocus()
            "mental_math" -> findViewById<Button>(R.id.btn_mental_math).requestFocus()
            "froggy_cross" -> findViewById<Button>(R.id.btn_froggy).requestFocus()
            "simon_says" -> findViewById<Button>(R.id.btn_simon).requestFocus()
            "battle_tanks" -> findViewById<Button>(R.id.btn_tanks).requestFocus()
            "word_quest" -> findViewById<Button>(R.id.btn_word_quest).requestFocus()
            "dungeon_escape" -> findViewById<Button>(R.id.btn_dungeon).requestFocus()
            "flappy_hero" -> findViewById<Button>(R.id.btn_flappy).requestFocus()
            "hangman" -> findViewById<Button>(R.id.btn_hangman).requestFocus()
            "road_racer" -> findViewById<Button>(R.id.btn_road_racer).requestFocus()
            "lines98" -> findViewById<Button>(R.id.btn_lines98).requestFocus()
            "solitaire" -> findViewById<Button>(R.id.btn_solitaire).requestFocus()
            else -> findViewById<Button>(R.id.btn_snake).requestFocus()
        }
    }

    private fun setupGameButton(button: Button, action: () -> Unit) {
        button.isFocusableInTouchMode = true
        button.setOnClickListener { 
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, button.text.toString())
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "game")
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
            action() 
        }
        
        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Brighter (handled by selector) + Scale Up
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .translationZ(12f)
                    .setDuration(200)
                    .start()
                view.elevation = 20f
            } else {
                // Back to normal
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .start()
                view.elevation = 8f
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

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.release()
    }
}
