package com.tdpham.games.hub

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.common.FocusHighlightDrawable
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
import com.tdpham.games.monkey.MonkeyActivity
import com.tdpham.games.retrodriver.RetroDriverActivity
import com.tdpham.games.frenzy.FrenzyActivity
import com.tdpham.games.fruit.FruitActivity
import com.tdpham.games.connectfour.ConnectFourActivity
import com.tdpham.games.blackjack.BlackjackActivity
import com.tdpham.games.trivia.TriviaActivity
import com.tdpham.games.common.DailyRewardDialog
import com.tdpham.games.common.DailyRewardManager
import com.tdpham.games.common.DailyRewardNotificationScheduler
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.LeaderboardManager
import com.tdpham.games.common.SettingsManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.profile.ProfileManager
import com.tdpham.games.common.profile.UserProfile
import com.tdpham.games.hub.profile.ProfileSelectionActivity
import com.tdpham.games.hub.profile.ProfileCreationActivity
import com.tdpham.games.common.IdleAdManager
import com.tdpham.games.common.IdleAdOverlayHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var returnedFromGame = false
    private lateinit var adOverlayHelper: IdleAdOverlayHelper
    private var hasAutoPromptedReward = false

    private var activeLeaderboardListener: ListenerRegistration? = null
    private var currentFocusedGameKey: String = "snake"
    private var currentFocusedGameTitle: String = "SNAKE"

    private val gameMetadata = mapOf(
        R.id.btn_snake to Pair("snake", "SNAKE"),
        R.id.btn_tetris to Pair("tetris", "TETRIS"),
        R.id.btn_minesweeper to Pair("minesweeper", "MINESWEEPER"),
        R.id.btn_trex to Pair("trex", "T-REX RUNNER"),
        R.id.btn_4096 to Pair("twentyfortyeight", "2048"),
        R.id.btn_memory to Pair("memory", "MEMORY MATCH"),
        R.id.btn_brick_break to Pair("brickbreak", "BRICK BREAK"),
        R.id.btn_syobon to Pair("syobon", "CAT MARIO"),
        R.id.btn_solitaire to Pair("solitaire", "SOLITAIRE"),
        R.id.btn_lines98 to Pair("lines98", "LINES 98"),
        R.id.btn_mental_math to Pair("mentalmath", "MENTAL MATH"),
        R.id.btn_sudoku to Pair("sudoku", "SUDOKU"),
        R.id.btn_tictactoe to Pair("tictactoe", "TIC TAC TOE"),
        R.id.btn_word_quest to Pair("wordquest", "WORD QUEST"),
        R.id.btn_sokoban to Pair("sokoban", "SOKOBAN"),
        R.id.btn_tanks to Pair("tanks", "BATTLE TANKS"),
        R.id.btn_starfighter to Pair("starfighter", "STAR FIGHTER"),
        R.id.btn_dungeon to Pair("dungeon", "DUNGEON ESCAPE"),
        R.id.btn_slide_puzzle to Pair("slidepuzzle", "SLIDE PUZZLE"),
        R.id.btn_hangman to Pair("hangman", "HANGMAN"),
        R.id.btn_simon to Pair("simon", "SIMON SAYS"),
        R.id.btn_flappy to Pair("flappy", "FLAPPY HERO"),
        R.id.btn_checkers to Pair("checkers", "CHECKERS"),
        R.id.btn_spinball to Pair("spinball", "SPINBALL"),
        R.id.btn_froggy to Pair("froggy", "FROGGY CROSS"),
        R.id.btn_monkey to Pair("monkey", "MONKEY ADVENTURE"),
        R.id.btn_retrodriver to Pair("retrodriver", "RETRO DRIVER"),
        R.id.btn_frenzy to Pair("frenzy", "FEEDING FRENZY"),
        R.id.btn_road_racer to Pair("roadracer", "ROAD RACER"),
        R.id.btn_fruit to Pair("fruit", "FRUIT NINJA"),
        R.id.btn_connect_four to Pair("connect_four", "CONNECT FOUR"),
        R.id.btn_blackjack to Pair("blackjack", "BLACKJACK 21"),
        R.id.btn_trivia to Pair("trivia", "TRIVIA QUIZ")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize notification scheduler for daily retention reminders
        DailyRewardNotificationScheduler.init(this)

        adOverlayHelper = IdleAdOverlayHelper(this).apply { init() }
        IdleAdManager.isGameMode = false
        IdleAdManager.init { state, remaining ->
            adOverlayHelper.showState(state, remaining)
        }

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

        findViewById<View>(R.id.btn_daily_reward)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                showDailyRewardDialog()
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_rate)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                com.tdpham.games.common.AppEngagementManager.showRateDialog(this@MainActivity)
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_share)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                com.tdpham.games.common.AppEngagementManager.showShareDialog(this@MainActivity)
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_leaderboard)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                val intent = Intent(this@MainActivity, LeaderboardActivity::class.java).apply {
                    putExtra("EXTRA_GAME_KEY", currentFocusedGameKey)
                }
                startActivity(intent)
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        findViewById<Button>(R.id.btn_settings)?.apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@MainActivity)
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@MainActivity)
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        setupLeaderboardWidget()
        setupGameButtons()
        updateProfileDisplay()
        updateCoinsDisplay()

        if (intent.getBooleanExtra("AUTO_LOGGED_IN", false)) {
            animateProfilePulse()
        }

        if (intent.getBooleanExtra("OPEN_DAILY_REWARDS", false)) {
            showDailyRewardDialog()
        }

        com.tdpham.games.common.AppEngagementManager.onAppForegrounded(this)
        focusLastPlayed()
    }

    private fun setupLeaderboardWidget() {
        val widget = findViewById<View>(R.id.live_leaderboard_widget) ?: return
        widget.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            val intent = Intent(this, LeaderboardActivity::class.java).apply {
                putExtra("EXTRA_GAME_KEY", currentFocusedGameKey)
            }
            startActivity(intent)
        }
        widget.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                view.animate().scaleX(1.02f).scaleY(1.02f).translationZ(12f).setDuration(200).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(200).start()
            }
        }

        // Initialize with default game (snake)
        bindLeaderboardToGame("snake", "SNAKE")
    }

    private fun bindLeaderboardToGame(gameKey: String, gameTitle: String) {
        currentFocusedGameKey = gameKey
        currentFocusedGameTitle = gameTitle

        findViewById<TextView>(R.id.tv_widget_game_title)?.text = "GLOBAL TOP 3 • $gameTitle"

        // Cancel previous listener to save bandwidth and keep listeners clean
        activeLeaderboardListener?.remove()

        activeLeaderboardListener = LeaderboardManager.listenGlobalTopScores(
            context = this,
            gameKey = gameKey,
            level = -1,
            limit = 3
        ) { entries ->
            runOnUiThread {
                renderLeaderboardPodium(entries)
            }
        }
    }

    private fun renderLeaderboardPodium(entries: List<LeaderboardManager.ScoreEntry>) {
        val avatars = listOf(
            R.drawable.ic_hero_knight, R.drawable.ic_hero_wizard,
            R.drawable.ic_hero_archer, R.drawable.ic_hero_ninja,
            R.drawable.ic_hero_viking, R.drawable.ic_hero_dragon,
            R.drawable.ic_hero_phoenix, R.drawable.ic_hero_shield,
            R.drawable.ic_hero_sword, R.drawable.ic_hero_crown
        )

        // Slot 1
        val e1 = entries.getOrNull(0)
        val name1 = findViewById<TextView>(R.id.widget_name_1)
        val score1 = findViewById<TextView>(R.id.widget_score_1)
        val avatar1 = findViewById<ImageView>(R.id.widget_avatar_1)
        if (e1 != null) {
            name1?.text = e1.profileName
            score1?.text = "${e1.score} pts"
            if (e1.avatarId in avatars.indices) {
                avatar1?.setImageResource(avatars[e1.avatarId])
                avatar1?.imageTintList = ColorStateList.valueOf(e1.avatarColor)
            }
        } else {
            name1?.text = "No Record"
            score1?.text = "0 pts"
        }

        // Slot 2
        val e2 = entries.getOrNull(1)
        val name2 = findViewById<TextView>(R.id.widget_name_2)
        val score2 = findViewById<TextView>(R.id.widget_score_2)
        val avatar2 = findViewById<ImageView>(R.id.widget_avatar_2)
        if (e2 != null) {
            name2?.text = e2.profileName
            score2?.text = "${e2.score} pts"
            if (e2.avatarId in avatars.indices) {
                avatar2?.setImageResource(avatars[e2.avatarId])
                avatar2?.imageTintList = ColorStateList.valueOf(e2.avatarColor)
            }
        } else {
            name2?.text = "---"
            score2?.text = "0 pts"
        }

        // Slot 3
        val e3 = entries.getOrNull(2)
        val name3 = findViewById<TextView>(R.id.widget_name_3)
        val score3 = findViewById<TextView>(R.id.widget_score_3)
        val avatar3 = findViewById<ImageView>(R.id.widget_avatar_3)
        if (e3 != null) {
            name3?.text = e3.profileName
            score3?.text = "${e3.score} pts"
            if (e3.avatarId in avatars.indices) {
                avatar3?.setImageResource(avatars[e3.avatarId])
                avatar3?.imageTintList = ColorStateList.valueOf(e3.avatarColor)
            }
        } else {
            name3?.text = "---"
            score3?.text = "0 pts"
        }
    }

    private fun updateCoinsDisplay() {
        val balance = DailyRewardManager.getCoinBalance(this)
        findViewById<TextView>(R.id.tv_main_coins)?.text = balance.toString()

        val canClaim = DailyRewardManager.canClaimReward(this)
        val rewardBtn = findViewById<View>(R.id.btn_daily_reward)
        if (canClaim) {
            rewardBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
            rewardBtn?.findViewById<TextView>(R.id.tv_main_coins)?.setTextColor(Color.parseColor("#0A0E17"))
        } else {
            rewardBtn?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            rewardBtn?.findViewById<TextView>(R.id.tv_main_coins)?.setTextColor(Color.WHITE)
        }
    }

    private fun showDailyRewardDialog() {
        DailyRewardDialog(this) {
            updateCoinsDisplay()
        }.show()
    }

    private fun updateProfileDisplay() {
        val activeProfile = ProfileManager.getActiveProfile(this) ?: return
        val nameView = findViewById<TextView>(R.id.active_profile_name)
        val iconView = findViewById<ImageView>(R.id.active_profile_icon)
        val layout = findViewById<View>(R.id.active_profile_layout)
        val customizationHint = findViewById<TextView>(R.id.hero_customization_hint)

        nameView.text = activeProfile.name
        
        // Show/Hide customization hint for default guest profile
        customizationHint.visibility = if (ProfileManager.isDefaultProfile(activeProfile)) View.VISIBLE else View.GONE

        // Transparent background for the layout
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
            // Color applies to the icon itself
            iconView.imageTintList = android.content.res.ColorStateList.valueOf(activeProfile.avatarColor)
        }

        layout.setOnClickListener {
            showProfileMenu()
        }
        layout.isFocusable = true
        layout.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
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
                    1 -> { // Switch
                        startActivity(Intent(this, ProfileSelectionActivity::class.java))
                    }
                }
            }
            .show()
    }

    private fun showPinDialog(profile: UserProfile, onSuccess: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin_entry, null)
        val editPin = dialogView.findViewById<EditText>(R.id.edit_pin)
        val errorView = dialogView.findViewById<TextView>(R.id.pin_error)
        val titleView = dialogView.findViewById<TextView>(R.id.pin_title)
        titleView.text = getString(R.string.edit_profile)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

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
            R.id.btn_froggy to FroggyCrossActivity::class.java,
            R.id.btn_monkey to MonkeyActivity::class.java,
            R.id.btn_retrodriver to RetroDriverActivity::class.java,
            R.id.btn_frenzy to FrenzyActivity::class.java,
            R.id.btn_road_racer to RoadRacerActivity::class.java,
            R.id.btn_fruit to FruitActivity::class.java,
            R.id.btn_connect_four to ConnectFourActivity::class.java,
            R.id.btn_blackjack to BlackjackActivity::class.java,
            R.id.btn_trivia to TriviaActivity::class.java
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
        
        FocusHighlightDrawable.attach(button, scaleFactor = 1.18f, elevationZ = 36f) { hasFocus ->
            if (hasFocus) {
                gameMetadata[button.id]?.let { (key, title) ->
                    bindLeaderboardToGame(key, title)
                }

                // Smoothly center the focused button in the HorizontalScrollView
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
        IdleAdManager.startTracking()
        updateProfileDisplay()
        updateCoinsDisplay()

        val isScanlineOn = SettingsManager.isScanlineEnabled(this)
        findViewById<View>(R.id.main_scanline_overlay)?.visibility = if (isScanlineOn) View.VISIBLE else View.GONE

        // Refresh active leaderboard listener
        bindLeaderboardToGame(currentFocusedGameKey, currentFocusedGameTitle)

        if (!hasAutoPromptedReward && DailyRewardManager.canClaimReward(this)) {
            hasAutoPromptedReward = true
            showDailyRewardDialog()
        }

        if (returnedFromGame) {
            returnedFromGame = false
            com.tdpham.games.common.AppEngagementManager.maybeShowRatePrompt(this)
        }
    }

    override fun onPause() {
        super.onPause()
        IdleAdManager.stopTracking()
        activeLeaderboardListener?.remove()
        activeLeaderboardListener = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        IdleAdManager.isWaitingMode = !hasFocus
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        IdleAdManager.notifyInteraction()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeLeaderboardListener?.remove()
        activeLeaderboardListener = null
        adOverlayHelper.destroy()
        SoundManager.release()
    }
}
