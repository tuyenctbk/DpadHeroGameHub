package com.tdpham.games.hub

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.tdpham.games.R
import com.tdpham.games.blackjack.BlackjackActivity
import com.tdpham.games.brickbreak.BrickBreakActivity
import com.tdpham.games.checkers.CheckersActivity
import com.tdpham.games.connectfour.ConnectFourActivity
import com.tdpham.games.common.FocusHighlightDrawable
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.IdleAdManager
import com.tdpham.games.common.IdleAdOverlayHelper
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SettingsManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.profile.ProfileManager
import com.tdpham.games.common.profile.UserProfile
import com.tdpham.games.dungeon.DungeonEscapeActivity
import com.tdpham.games.flappy.FlappyHeroActivity
import com.tdpham.games.frenzy.FrenzyActivity
import com.tdpham.games.froggy.FroggyCrossActivity
import com.tdpham.games.fruit.FruitActivity
import com.tdpham.games.hangman.HangmanActivity
import com.tdpham.games.hub.profile.ProfileCreationActivity
import com.tdpham.games.hub.profile.ProfileSelectionActivity
import com.tdpham.games.lines98.Lines98Activity
import com.tdpham.games.memory.MemoryActivity
import com.tdpham.games.mentalmath.MentalMathActivity
import com.tdpham.games.minesweeper.MinesweeperActivity
import com.tdpham.games.monkey.MonkeyActivity
import com.tdpham.games.retrodriver.RetroDriverActivity
import com.tdpham.games.roadracer.RoadRacerActivity
import com.tdpham.games.simon.SimonSaysActivity
import com.tdpham.games.slidepuzzle.SlidePuzzleActivity
import com.tdpham.games.snake.SnakeActivity
import com.tdpham.games.sokoban.SokobanActivity
import com.tdpham.games.solitaire.SolitaireActivity
import com.tdpham.games.spinball.SpinballActivity
import com.tdpham.games.starfighter.StarFighterActivity
import com.tdpham.games.sudoku.SudokuActivity
import com.tdpham.games.syobon.SyobonActivity
import com.tdpham.games.tanks.BattleTanksActivity
import com.tdpham.games.tetris.TetrisActivity
import com.tdpham.games.tictactoe.TicTacToeActivity
import com.tdpham.games.trex.TRexActivity
import com.tdpham.games.trivia.TriviaActivity
import com.tdpham.games.twentyfortyeight.TwentyFortyEightActivity
import com.tdpham.games.wordquest.WordQuestActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adOverlayHelper: IdleAdOverlayHelper
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var currentFocusedButton: View? = null
    private var returnedFromGame = false

    private var currentFocusedMeta: GameCardMeta? = null

    private data class GameCardMeta(
        val key: String,
        val defaultTitleRes: Int,
        val genreRes: Int,
        val index: Int
    )

    // Matches exact left-to-right order in activity_main.xml & exact gameKey values across all 33 games
    private val gameMetadata = mapOf(
        R.id.btn_snake to GameCardMeta("snake", R.string.snake, R.string.genre_arcade, 1),
        R.id.btn_tetris to GameCardMeta("tetris", R.string.game_tetris, R.string.genre_puzzle, 2),
        R.id.btn_minesweeper to GameCardMeta("minesweeper", R.string.minesweeper, R.string.genre_strategy, 3),
        R.id.btn_monkey to GameCardMeta("monkey", R.string.game_monkey, R.string.genre_adventure, 4),
        R.id.btn_frenzy to GameCardMeta("frenzy", R.string.game_frenzy, R.string.genre_action, 5),
        R.id.btn_trex to GameCardMeta("trex", R.string.game_trex, R.string.genre_runner, 6),
        R.id.btn_4096 to GameCardMeta("4096", R.string.game_4096, R.string.genre_puzzle, 7),
        R.id.btn_flappy to GameCardMeta("flappy_hero", R.string.game_flappy, R.string.genre_arcade, 8),
        R.id.btn_memory to GameCardMeta("memory", R.string.game_memory, R.string.genre_brain, 9),
        R.id.btn_brick_break to GameCardMeta("brick_break", R.string.game_brick_break, R.string.genre_arcade, 10),
        R.id.btn_solitaire to GameCardMeta("solitaire", R.string.game_solitaire, R.string.genre_cards, 11),
        R.id.btn_retrodriver to GameCardMeta("retrodriver", R.string.game_retrodriver, R.string.genre_racing, 12),
        R.id.btn_lines98 to GameCardMeta("lines98", R.string.game_lines98, R.string.genre_puzzle, 13),
        R.id.btn_sudoku to GameCardMeta("sudoku", R.string.game_sudoku, R.string.genre_logic, 14),
        R.id.btn_tictactoe to GameCardMeta("tic_tac_toe", R.string.game_tictactoe, R.string.genre_classic, 15),
        R.id.btn_word_quest to GameCardMeta("word_quest", R.string.game_word_quest, R.string.genre_word, 16),
        R.id.btn_sokoban to GameCardMeta("sokoban", R.string.game_sokoban, R.string.genre_puzzle, 17),
        R.id.btn_tanks to GameCardMeta("battle_tanks", R.string.game_tanks, R.string.genre_action, 18),
        R.id.btn_starfighter to GameCardMeta("starfighter", R.string.game_starfighter, R.string.genre_shooter, 19),
        R.id.btn_dungeon to GameCardMeta("dungeon_escape", R.string.game_dungeon, R.string.genre_adventure, 20),
        R.id.btn_froggy to GameCardMeta("froggy_cross", R.string.game_froggy, R.string.genre_arcade, 21),
        R.id.btn_syobon to GameCardMeta("syobon_action", R.string.game_syobon, R.string.genre_platform, 22),
        R.id.btn_slide_puzzle to GameCardMeta("slide_puzzle", R.string.game_slide_puzzle, R.string.genre_puzzle, 23),
        R.id.btn_hangman to GameCardMeta("hangman", R.string.game_hangman, R.string.genre_word, 24),
        R.id.btn_simon to GameCardMeta("simon_says", R.string.game_simon, R.string.genre_memory, 25),
        R.id.btn_checkers to GameCardMeta("checkers", R.string.game_checkers, R.string.genre_board, 26),
        R.id.btn_spinball to GameCardMeta("spinball", R.string.game_spinball, R.string.genre_arcade, 27),
        R.id.btn_mental_math to GameCardMeta("mental_math", R.string.game_mental_math, R.string.genre_brain, 28),
        R.id.btn_road_racer to GameCardMeta("road_racer", R.string.game_road_racer, R.string.genre_racing, 29),
        R.id.btn_fruit to GameCardMeta("fruit", R.string.game_fruit, R.string.genre_arcade, 30),
        R.id.btn_connect_four to GameCardMeta("connect_four", R.string.game_connect_four, R.string.genre_board, 31),
        R.id.btn_blackjack to GameCardMeta("blackjack", R.string.game_blackjack, R.string.genre_cards, 32),
        R.id.btn_trivia to GameCardMeta("trivia", R.string.game_trivia, R.string.genre_brain, 33)
    )

    private val spotlightInterpolator = DecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adOverlayHelper = IdleAdOverlayHelper(this).apply { init() }
        IdleAdManager.isGameMode = false

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
        title.translationY = -40f
        title.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(120).setInterpolator(spotlightInterpolator).start()

        val spotlight = findViewById<View>(R.id.spotlight_container)
        spotlight?.apply {
            alpha = 0f
            translationY = 24f
            animate().alpha(1f).translationY(0f).setDuration(550).setStartDelay(220).setInterpolator(spotlightInterpolator).start()
        }

        val onDownToGameList = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                currentFocusedButton?.requestFocus() ?: findViewById<View>(R.id.btn_snake)?.requestFocus()
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.active_profile_layout)?.setOnKeyListener(onDownToGameList)

        // Setup GamePadMonitor Indicator in Header if present in layout
        val layoutControllerId = resources.getIdentifier("layout_controller_indicator", "id", packageName)
        if (layoutControllerId != 0) {
            val layoutController = findViewById<View?>(layoutControllerId)
            val ivControllerIcon = findViewById<ImageView?>(resources.getIdentifier("iv_controller_status_icon", "id", packageName))
            val tvControllerText = findViewById<TextView?>(resources.getIdentifier("tv_controller_status_text", "id", packageName))
            val dotController = findViewById<View?>(resources.getIdentifier("view_controller_status_dot", "id", packageName))
            if (layoutController != null && ivControllerIcon != null && tvControllerText != null) {
                com.tdpham.games.common.GamePadMonitor.bindHeaderIndicator(
                    activity = this,
                    container = layoutController,
                    iconView = ivControllerIcon,
                    textView = tvControllerText,
                    statusDot = dotController
                )
            }
        }

        findViewById<Button>(R.id.btn_main_leaderboard)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                startActivity(Intent(this@MainActivity, LeaderboardActivity::class.java))
            }
            setOnKeyListener(onDownToGameList)
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_settings)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            setOnKeyListener(onDownToGameList)
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_main_exit)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                showExitConfirmationDialog()
            }
            setOnKeyListener(onDownToGameList)
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        setupGameButtons()
        updateProfileDisplay()

        if (intent.getBooleanExtra("AUTO_LOGGED_IN", false)) {
            animateProfilePulse()
        }

        com.tdpham.games.common.AppEngagementManager.onAppForegrounded(this)
        focusLastPlayed()
    }

    private fun updateSpotlightBar(meta: GameCardMeta, displayTitle: String, animate: Boolean) {
        currentFocusedMeta = meta
        val container = findViewById<View>(R.id.spotlight_container) ?: return
        val genreTv = findViewById<TextView>(R.id.tv_spotlight_genre)
        val titleTv = findViewById<TextView>(R.id.tv_spotlight_title)
        val bestTv = findViewById<TextView>(R.id.tv_spotlight_best)
        val indexTv = findViewById<TextView>(R.id.tv_spotlight_index)

        val bestScore = ScoreManager.getBestScoreAcrossAllLevels(this, meta.key)
        val bestLabel = if (bestScore > 0) {
            val formatted = NumberFormat.getIntegerInstance(Locale.US).format(bestScore)
            "★ ${getString(R.string.best_label)} $formatted"
        } else {
            getString(R.string.spotlight_play_to_set_record)
        }

        genreTv?.text = getString(meta.genreRes)
        titleTv?.text = displayTitle.ifBlank { getString(meta.defaultTitleRes) }
        bestTv?.text = bestLabel
        bestTv?.setTextColor(if (bestScore > 0) Color.parseColor("#FFD700") else Color.parseColor("#38BDF8"))
        indexTv?.text = String.format(Locale.US, "%02d / %02d", meta.index, gameMetadata.size)

        if (animate) {
            container.animate().cancel()
            container.alpha = 0.72f
            container.translationY = 6f
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .setInterpolator(spotlightInterpolator)
                .start()
        }
    }

    private fun updateProfileDisplay() {
        val activeProfile = ProfileManager.getActiveProfile(this) ?: return
        val nameView = findViewById<TextView>(R.id.active_profile_name)
        val iconView = findViewById<ImageView>(R.id.active_profile_icon)
        val layout = findViewById<View>(R.id.active_profile_layout)
        val customizationHint = findViewById<TextView>(R.id.hero_customization_hint)

        nameView.text = activeProfile.name
        customizationHint.visibility = if (ProfileManager.isDefaultProfile(activeProfile)) View.VISIBLE else View.GONE
        layout.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)

        val avatars = listOf(
            R.drawable.ic_hero_knight, R.drawable.ic_hero_wizard,
            R.drawable.ic_hero_archer, R.drawable.ic_hero_ninja,
            R.drawable.ic_hero_viking, R.drawable.ic_hero_dragon,
            R.drawable.ic_hero_phoenix, R.drawable.ic_hero_shield,
            R.drawable.ic_hero_sword, R.drawable.ic_hero_crown
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
                SoundManager.playClick()
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(180).start()
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }
    }

    private fun animateProfilePulse() {
        val layout = findViewById<View>(R.id.active_profile_layout)
        layout.animate()
            .scaleX(1.18f).scaleY(1.18f)
            .setDuration(350)
            .withEndAction {
                layout.animate().scaleX(1.0f).scaleY(1.0f).setDuration(350).start()
            }
            .start()
    }

    private fun showProfileMenu() {
        val activeProfile = ProfileManager.getActiveProfile(this) ?: return

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_hero_profile_menu)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val nameView = dialog.findViewById<TextView>(R.id.tv_hero_menu_name)
        val iconView = dialog.findViewById<ImageView>(R.id.hero_menu_avatar_icon)
        val btnEdit = dialog.findViewById<Button>(R.id.btn_hero_menu_edit)
        val btnSwitch = dialog.findViewById<Button>(R.id.btn_hero_menu_switch)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_hero_menu_cancel)

        nameView.text = activeProfile.name

        val avatars = listOf(
            R.drawable.ic_hero_knight, R.drawable.ic_hero_wizard,
            R.drawable.ic_hero_archer, R.drawable.ic_hero_ninja,
            R.drawable.ic_hero_viking, R.drawable.ic_hero_dragon,
            R.drawable.ic_hero_phoenix, R.drawable.ic_hero_shield,
            R.drawable.ic_hero_sword, R.drawable.ic_hero_crown
        )

        if (activeProfile.avatarId in avatars.indices) {
            iconView.setImageResource(avatars[activeProfile.avatarId])
            iconView.imageTintList = android.content.res.ColorStateList.valueOf(activeProfile.avatarColor)
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            if (activeProfile.pin != null) {
                showPinDialog(activeProfile) {
                    val intent = Intent(this, ProfileCreationActivity::class.java)
                    intent.putExtra("EDIT_PROFILE_ID", activeProfile.id)
                    startActivity(intent)
                }
            } else {
                val intent = Intent(this, ProfileCreationActivity::class.java)
                intent.putExtra("EDIT_PROFILE_ID", activeProfile.id)
                startActivity(intent)
            }
        }

        btnSwitch.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnKeyListener { _, _, event ->
            com.tdpham.games.common.ControllerNavigationHelper.handleDialogKeyEvent(dialog, event)
        }

        dialog.show()
        btnEdit.requestFocus()
    }

    private fun showPinDialog(profile: UserProfile, onSuccess: () -> Unit) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_pin_entry)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        dialog.setOnKeyListener { _, _, event ->
            com.tdpham.games.common.ControllerNavigationHelper.handleDialogKeyEvent(dialog, event)
        }

        val editPin = dialog.findViewById<EditText>(R.id.edit_pin)
        val errorView = dialog.findViewById<TextView>(R.id.pin_error)
        val titleView = dialog.findViewById<TextView>(R.id.pin_title)
        titleView.text = getString(R.string.edit_profile)

        editPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 4) {
                    if (s.toString() == profile.pin) {
                        dialog.dismiss()
                        onSuccess()
                    } else {
                        errorView.visibility = View.VISIBLE
                        s.clear()
                        SoundManager.playError()
                    }
                } else {
                    errorView.visibility = View.INVISIBLE
                }
            }
        })
        dialog.show()
        editPin.requestFocus()
    }

    private val initializedGameButtons = mutableSetOf<Int>()

    private fun setupGameButtons() {
        val games = linkedMapOf(
            R.id.btn_snake to SnakeActivity::class.java,
            R.id.btn_tetris to TetrisActivity::class.java,
            R.id.btn_minesweeper to MinesweeperActivity::class.java,
            R.id.btn_monkey to MonkeyActivity::class.java,
            R.id.btn_frenzy to FrenzyActivity::class.java,
            R.id.btn_trex to TRexActivity::class.java,
            R.id.btn_4096 to TwentyFortyEightActivity::class.java,
            R.id.btn_flappy to FlappyHeroActivity::class.java,
            R.id.btn_memory to MemoryActivity::class.java,
            R.id.btn_brick_break to BrickBreakActivity::class.java,
            R.id.btn_solitaire to SolitaireActivity::class.java,
            R.id.btn_retrodriver to RetroDriverActivity::class.java,
            R.id.btn_lines98 to Lines98Activity::class.java,
            R.id.btn_sudoku to SudokuActivity::class.java,
            R.id.btn_tictactoe to TicTacToeActivity::class.java,
            R.id.btn_word_quest to WordQuestActivity::class.java,
            R.id.btn_sokoban to SokobanActivity::class.java,
            R.id.btn_tanks to BattleTanksActivity::class.java,
            R.id.btn_starfighter to StarFighterActivity::class.java,
            R.id.btn_dungeon to DungeonEscapeActivity::class.java,
            R.id.btn_froggy to FroggyCrossActivity::class.java,
            R.id.btn_syobon to SyobonActivity::class.java,
            R.id.btn_slide_puzzle to SlidePuzzleActivity::class.java,
            R.id.btn_hangman to HangmanActivity::class.java,
            R.id.btn_simon to SimonSaysActivity::class.java,
            R.id.btn_checkers to CheckersActivity::class.java,
            R.id.btn_spinball to SpinballActivity::class.java,
            R.id.btn_mental_math to MentalMathActivity::class.java,
            R.id.btn_road_racer to RoadRacerActivity::class.java,
            R.id.btn_fruit to FruitActivity::class.java,
            R.id.btn_connect_four to ConnectFourActivity::class.java,
            R.id.btn_blackjack to BlackjackActivity::class.java,
            R.id.btn_trivia to TriviaActivity::class.java
        )

        val iconMap = mapOf(
            R.id.btn_snake to R.drawable.ic_game_snake,
            R.id.btn_tetris to R.drawable.ic_game_tetris,
            R.id.btn_minesweeper to R.drawable.ic_game_minesweeper,
            R.id.btn_trex to R.drawable.ic_game_trex,
            R.id.btn_4096 to R.drawable.ic_game_4096,
            R.id.btn_memory to R.drawable.ic_game_memory,
            R.id.btn_brick_break to R.drawable.ic_game_brick_break,
            R.id.btn_syobon to R.drawable.ic_game_syobon,
            R.id.btn_solitaire to R.drawable.ic_game_solitaire,
            R.id.btn_lines98 to R.drawable.ic_game_lines98,
            R.id.btn_mental_math to R.drawable.ic_game_mental_math,
            R.id.btn_sudoku to R.drawable.ic_game_sudoku,
            R.id.btn_tictactoe to R.drawable.ic_game_tictactoe,
            R.id.btn_word_quest to R.drawable.ic_game_word_quest,
            R.id.btn_sokoban to R.drawable.ic_game_sokoban,
            R.id.btn_tanks to R.drawable.ic_game_tanks,
            R.id.btn_starfighter to R.drawable.ic_game_starfighter,
            R.id.btn_dungeon to R.drawable.ic_game_dungeon,
            R.id.btn_slide_puzzle to R.drawable.ic_game_slide_puzzle,
            R.id.btn_hangman to R.drawable.ic_game_hangman,
            R.id.btn_simon to R.drawable.ic_game_simon,
            R.id.btn_flappy to R.drawable.ic_game_flappy,
            R.id.btn_checkers to R.drawable.ic_game_checkers,
            R.id.btn_spinball to R.drawable.ic_game_spinball,
            R.id.btn_froggy to R.drawable.ic_game_froggy,
            R.id.btn_monkey to R.drawable.ic_game_monkey,
            R.id.btn_retrodriver to R.drawable.ic_game_retrodriver,
            R.id.btn_frenzy to R.drawable.ic_game_frenzy,
            R.id.btn_road_racer to R.drawable.ic_game_road_racer,
            R.id.btn_fruit to R.drawable.ic_game_fruit,
            R.id.btn_connect_four to R.drawable.ic_game_connect_four,
            R.id.btn_blackjack to R.drawable.ic_game_blackjack,
            R.id.btn_trivia to R.drawable.ic_game_trivia
        )

        fun bindSingleGameCard(id: Int, activityClass: Class<*>) {
            if (initializedGameButtons.contains(id)) return
            val button = findViewById<Button>(id) ?: return
            initializedGameButtons.add(id)

            val iconRes = iconMap[id]
            if (iconRes != null) {
                com.tdpham.games.common.GlideGameIconLoader.loadButtonTopIcon(this, button, iconRes)
            }
            setupGameButton(button) {
                startActivity(Intent(this, activityClass))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        val lastPlayedKey = prefs.getString("last_played", "snake")
        val lastPlayedId = resolveButtonIdForGameKey(lastPlayedKey)

        // Immediately bind priority viewport cards (< 200ms initial screen display)
        val initialIds = listOf(R.id.btn_snake, R.id.btn_tetris, R.id.btn_minesweeper, R.id.btn_monkey, R.id.btn_frenzy, lastPlayedId)
        for (id in initialIds) {
            val act = games[id]
            if (act != null) {
                bindSingleGameCard(id, act)
            }
        }

        // Preload icons off the UI thread
        lifecycleScope.launch(Dispatchers.Default) {
            com.tdpham.games.common.GlideGameIconLoader.preloadIcons(applicationContext, iconMap.values)
        }

        // Staggered lazy binding for remaining cards
        val remainingGames = games.filterKeys { !initializedGameButtons.contains(it) }.toList()
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            for (chunk in remainingGames.chunked(4)) {
                kotlinx.coroutines.delay(16)
                for ((id, act) in chunk) {
                    bindSingleGameCard(id, act)
                }
            }
        }
    }

    private fun resolveButtonIdForGameKey(gameKey: String?): Int = when (gameKey) {
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
        "monkey" -> R.id.btn_monkey
        "retrodriver" -> R.id.btn_retrodriver
        "frenzy" -> R.id.btn_frenzy
        "road_racer" -> R.id.btn_road_racer
        "fruit" -> R.id.btn_fruit
        "connect_four" -> R.id.btn_connect_four
        "blackjack" -> R.id.btn_blackjack
        "trivia" -> R.id.btn_trivia
        else -> R.id.btn_snake
    }

    private fun focusLastPlayed() {
        val prefs = getSharedPreferences("game_settings", Context.MODE_PRIVATE)
        val lastPlayed = prefs.getString("last_played", "snake")
        val viewId = resolveButtonIdForGameKey(lastPlayed)
        val btn = findViewById<Button>(viewId)
        btn?.requestFocus()
        gameMetadata[viewId]?.let { meta ->
            updateSpotlightBar(meta, btn?.text?.toString() ?: getString(meta.defaultTitleRes), animate = false)
        }
    }

    private fun setupGameButton(button: Button, action: () -> Unit) {
        button.isFocusableInTouchMode = true
        button.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            returnedFromGame = true
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, button.text.toString())
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "game")
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
            action()
        }

        button.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val hsv = findViewById<HorizontalScrollView>(R.id.hsv_games)
                val leaderboardBtn = findViewById<View>(R.id.btn_main_leaderboard)
                val settingsBtn = findViewById<View>(R.id.btn_settings)
                val exitBtn = findViewById<View>(R.id.btn_main_exit)
                val profileBtn = findViewById<View>(R.id.active_profile_layout)
                val relPos = if (hsv != null && hsv.width > 0) {
                    (button.left + button.width / 2f - hsv.scrollX) / hsv.width.toFloat()
                } else {
                    0.5f
                }
                when {
                    relPos < 0.42f -> profileBtn?.requestFocus()
                    relPos < 0.65f -> leaderboardBtn?.requestFocus() ?: settingsBtn?.requestFocus()
                    relPos < 0.82f -> settingsBtn?.requestFocus()
                    else -> exitBtn?.requestFocus() ?: settingsBtn?.requestFocus()
                }
                true
            } else {
                false
            }
        }

        FocusHighlightDrawable.attach(button, scaleFactor = 1.18f, elevationZ = 36f) { hasFocus ->
            if (hasFocus) {
                currentFocusedButton = button
                gameMetadata[button.id]?.let { meta ->
                    updateSpotlightBar(meta, button.text.toString(), animate = true)
                }

                val hsv = findViewById<HorizontalScrollView>(R.id.hsv_games)
                hsv?.post {
                    val targetScrollX = button.left - (hsv.width - button.width) / 2
                    hsv.smoothScrollTo(targetScrollX.coerceAtLeast(0), 0)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IdleAdManager.isGameMode = false
        IdleAdManager.registerListener(this) { state, remaining ->
            adOverlayHelper.showState(state, remaining)
        }
        IdleAdManager.startTracking()
        updateProfileDisplay()

        // Refresh focused game spotlight score (picks up new records immediately on return)
        currentFocusedMeta?.let { meta ->
            val title = (currentFocusedButton as? Button)?.text?.toString() ?: getString(meta.defaultTitleRes)
            updateSpotlightBar(meta, title, animate = false)
        }

        val isScanlineOn = SettingsManager.isScanlineEnabled(this)
        findViewById<View>(R.id.main_scanline_overlay)?.visibility = if (isScanlineOn) View.VISIBLE else View.GONE

        if (returnedFromGame) {
            returnedFromGame = false
            com.tdpham.games.common.AppEngagementManager.maybeShowRatePrompt(this)
        }
    }

    override fun onPause() {
        super.onPause()
        IdleAdManager.stopTracking()
        IdleAdManager.unregisterListener(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        IdleAdManager.isWaitingMode = !hasFocus
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        IdleAdManager.notifyInteraction()
    }

    private var exitDialog: Dialog? = null

    fun showExitConfirmationDialog() {
        if (isFinishing || isDestroyed) return
        if (exitDialog?.isShowing == true) return

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_exit_app)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val btnExit = dialog.findViewById<Button>(R.id.btn_dialog_exit)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_dialog_cancel)

        btnExit.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            dialog.dismiss()
            finishAffinity()
        }

        btnCancel.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            dialog.dismiss()
            currentFocusedButton?.requestFocus()
        }

        setupDialogButtonFocus(btnExit)
        setupDialogButtonFocus(btnCancel)

        dialog.setOnKeyListener { _, _, event ->
            if (com.tdpham.games.common.ControllerNavigationHelper.handleDialogKeyEvent(dialog, event) {
                dialog.dismiss()
                currentFocusedButton?.requestFocus()
                true
            }) {
                return@setOnKeyListener true
            }
            false
        }

        dialog.setOnDismissListener {
            exitDialog = null
        }

        exitDialog = dialog
        dialog.show()
        btnExit.requestFocus()
    }

    private fun setupDialogButtonFocus(button: Button) {
        button.isFocusable = true
        button.isFocusableInTouchMode = true
        button.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (com.tdpham.games.common.ControllerNavigationHelper.handleUiKeyEvent(this, event) {
            showExitConfirmationDialog()
            true
        }) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        exitDialog?.dismiss()
        exitDialog = null
        IdleAdManager.unregisterListener(this)
        adOverlayHelper.destroy()
        if (isFinishing) {
            SoundManager.release()
        }
    }
}
