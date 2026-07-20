package com.tdpham.games.hub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.tdpham.games.common.profile.ProfileManager
import com.tdpham.games.hub.profile.ProfileSelectionActivity
import com.tdpham.games.hub.profile.ProfileCreationActivity
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

        lifecycleScope.launch(Dispatchers.Default) {
            val analytics = try {
                Firebase.analytics
            } catch (e: Throwable) {
                null
            }
            withContext(Dispatchers.Main) {
                firebaseAnalytics = analytics
            }
        }

        val title = findViewById<View>(R.id.main_title)
        title.alpha = 0f
        title.translationY = -50f
        title.animate().alpha(1f).translationY(0f).setDuration(800).setStartDelay(300).start()

        findViewById<Button>(R.id.btn_leaderboard).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LeaderboardActivity::class.java))
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        setupGameButtons()
        updateProfileDisplay()

        if (intent.getBooleanExtra("AUTO_LOGGED_IN", false)) {
            animateProfilePulse()
        }

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

    private fun updateProfileDisplay() {
        val activeProfile = ProfileManager.getActiveProfile(this) ?: return
        val nameView = findViewById<TextView>(R.id.active_profile_name)
        val iconView = findViewById<ImageView>(R.id.active_profile_icon)
        val layout = findViewById<View>(R.id.active_profile_layout)

        nameView.text = activeProfile.name
        
        // Neutral background for the layout
        layout.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
        
        // Color applies to the icon tint
        val avatars = listOf(
            R.drawable.ic_avatar_smile, R.drawable.ic_avatar_alien,
            R.drawable.ic_avatar_cat, R.drawable.ic_avatar_star,
            R.drawable.ic_avatar_heart, R.drawable.ic_avatar_robot,
            R.drawable.ic_avatar_rocket, R.drawable.ic_avatar_ghost,
            R.drawable.ic_avatar_gamepad, R.drawable.ic_avatar_bolt
        )

        if (activeProfile.avatarId in avatars.indices) {
            iconView.setImageResource(avatars[activeProfile.avatarId])
            iconView.imageTintList = android.content.res.ColorStateList.valueOf(activeProfile.avatarColor)
        }

        layout.setOnClickListener {
            showProfileMenu()
        }
        layout.isFocusable = true
        layout.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }
    }

    private fun animateProfilePulse() {
        val layout = findViewById<View>(R.id.active_profile_layout)
        layout.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(400)
            .withEndAction {
                layout.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).start()
            }
            .start()
    }

    private fun showProfileMenu() {
        val activeProfile = ProfileManager.getActiveProfile(this) ?: return
        val options = arrayOf(
            getString(R.string.edit_profile),
            getString(R.string.switch_profile)
        )
        
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(activeProfile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Edit
                        val intent = Intent(this, ProfileCreationActivity::class.java)
                        intent.putExtra("EDIT_PROFILE_ID", activeProfile.id)
                        startActivity(intent)
                    }
                    1 -> { // Switch
                        startActivity(Intent(this, ProfileSelectionActivity::class.java))
                    }
                }
            }
            .show()
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
        val viewId = when (lastPlayed) {
            "snake" -> R.id.btn_snake
            "tetris" -> R.id.btn_tetris
            "minesweeper" -> R.id.btn_minesweeper
            "trex" -> R.id.btn_trex
            "4096" -> R.id.btn_4096
            "memory" -> R.id.btn_memory
            "brick_break" -> R.id.btn_brick_break
            "solitaire" -> R.id.btn_solitaire
            "lines98" -> R.id.btn_lines98
            "mental_math" -> R.id.btn_mental_math
            "sudoku" -> R.id.btn_sudoku
            "tic_tac_toe" -> R.id.btn_tictactoe
            "word_quest" -> R.id.btn_word_quest
            "road_racer" -> R.id.btn_road_racer
            "sokoban" -> R.id.btn_sokoban
            "battle_tanks" -> R.id.btn_tanks
            "starfighter" -> R.id.btn_starfighter
            "dungeon_escape" -> R.id.btn_dungeon
            "slide_puzzle" -> R.id.btn_slide_puzzle
            "hangman" -> R.id.btn_hangman
            "simon_says" -> R.id.btn_simon
            "flappy_hero" -> R.id.btn_flappy
            "froggy_cross" -> R.id.btn_froggy
            "syobon_action" -> R.id.btn_syobon
            "checkers" -> R.id.btn_checkers
            "spinball" -> R.id.btn_spinball
            else -> R.id.btn_snake
        }
        findViewById<Button>(viewId)?.requestFocus()
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
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .translationZ(24f)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .setDuration(350)
                    .start()
            } else {
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
        updateProfileDisplay()
        if (returnedFromGame) {
            returnedFromGame = false
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
