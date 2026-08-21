package com.tdpham.games.hub

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.LeaderboardManager
import com.tdpham.games.common.SoundManager

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var tabsContainer: LinearLayout
    private lateinit var scoresContainer: LinearLayout
    private lateinit var levelTabsContainer: LinearLayout
    private lateinit var btnModeLocal: Button
    private lateinit var btnModeGlobal: Button
    private var isGlobalMode = false
    private var currentSelectedGame = "snake"
    private var currentSelectedLevel = -1
    
    private val avatars = listOf(
        R.drawable.ic_avatar_smile, R.drawable.ic_avatar_alien,
        R.drawable.ic_avatar_cat, R.drawable.ic_avatar_star,
        R.drawable.ic_avatar_heart, R.drawable.ic_avatar_robot,
        R.drawable.ic_avatar_rocket, R.drawable.ic_avatar_ghost,
        R.drawable.ic_avatar_gamepad, R.drawable.ic_avatar_bolt
    )

    private val games by lazy {
        listOf(
            GameTab("snake", getString(R.string.snake)),
            GameTab("tetris", getString(R.string.game_tetris), listOf("LVL 1", "LVL 5", "LVL 10")),
            GameTab("minesweeper", getString(R.string.minesweeper), listOf(getString(R.string.level_label) + " 1", getString(R.string.level_label) + " 2", getString(R.string.level_label) + " 3")),
            GameTab("sudoku", getString(R.string.game_sudoku), listOf(getString(R.string.sudoku_level_1).split(":")[0], getString(R.string.sudoku_level_2).split(":")[0], getString(R.string.sudoku_level_3).split(":")[0])),
            GameTab("trex", getString(R.string.game_trex)),
            GameTab("4096", getString(R.string.game_4096), listOf("4x4", "5x5")),
            GameTab("flappy_hero", getString(R.string.game_flappy), listOf("LVL 1", "LVL 2", "LVL 3", "LVL 4", "LVL 5")),
            GameTab("starfighter", getString(R.string.game_starfighter), listOf(getString(R.string.level_label) + " 1", getString(R.string.level_label) + " 2", getString(R.string.level_label) + " 3")),
            GameTab("syobon_action", getString(R.string.game_syobon)),
            GameTab("road_racer", getString(R.string.game_road_racer)),
            GameTab("brick_break", getString(R.string.game_brick_break)),
            GameTab("froggy_cross", getString(R.string.game_froggy)),
            GameTab("battle_tanks", getString(R.string.game_tanks)),
            GameTab("solitaire", getString(R.string.game_solitaire)),
            GameTab("retrodriver", getString(R.string.game_retrodriver)),
            GameTab("lines98", getString(R.string.game_lines98)),
            GameTab("spinball", getString(R.string.game_spinball)),
            GameTab("simon", getString(R.string.game_simon)),
            GameTab("checkers", getString(R.string.game_checkers)),
            GameTab("memory", getString(R.string.game_memory)),
            GameTab("fruit", getString(R.string.game_fruit)),
            GameTab("sokoban", getString(R.string.game_sokoban)),
            GameTab("slide_puzzle", getString(R.string.game_slide_puzzle)),
            GameTab("mental_math", getString(R.string.game_mental_math)),
            GameTab("tictactoe", getString(R.string.game_tictactoe)),
            GameTab("word_quest", getString(R.string.game_word_quest)),
            GameTab("dungeon", getString(R.string.game_dungeon)),
            GameTab("hangman", getString(R.string.game_hangman)),
            GameTab("monkey", getString(R.string.game_monkey)),
            GameTab("frenzy", getString(R.string.game_frenzy))
        )
    }

    data class GameTab(val key: String, val title: String, val levels: List<String>? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        val headerTitle = findViewById<View>(R.id.leaderboard_title) ?: findViewById<View>(R.id.game_tabs_container)
        headerTitle?.let { androidx.core.view.ViewCompat.setTransitionName(it, "hub_leaderboard_transition") }

        tabsContainer = findViewById(R.id.game_tabs_container)
        scoresContainer = findViewById(R.id.scores_container)
        btnModeLocal = findViewById(R.id.btn_mode_local)
        btnModeGlobal = findViewById(R.id.btn_mode_global)
        
        val rootLayout = tabsContainer.parent.parent as LinearLayout
        levelTabsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            gravity = android.view.Gravity.CENTER
        }
        rootLayout.addView(levelTabsContainer, rootLayout.indexOfChild(tabsContainer.parent as View) + 1)

        setupModeSwitcher()

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            finish()
        }

        setupGameTabs()
        
        val lastGame = getSharedPreferences("game_settings", MODE_PRIVATE).getString("last_played", "snake")
        val startIndex = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)
        
        if (tabsContainer.childCount > startIndex) {
            tabsContainer.getChildAt(startIndex).requestFocus()
        } else {
            selectGame(games[0])
        }
    }

    private fun setupModeSwitcher() {
        btnModeLocal.setOnClickListener {
            if (isGlobalMode) {
                isGlobalMode = false
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                updateModeButtons()
                refreshCurrentScores()
            }
        }

        btnModeGlobal.setOnClickListener {
            if (!isGlobalMode) {
                isGlobalMode = true
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                updateModeButtons()
                refreshCurrentScores()
            }
        }

        updateModeButtons()
    }

    private fun updateModeButtons() {
        if (isGlobalMode) {
            btnModeGlobal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
            btnModeGlobal.setTextColor(Color.parseColor("#0A0E17"))
            btnModeLocal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
            btnModeLocal.setTextColor(Color.WHITE)
        } else {
            btnModeLocal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            btnModeLocal.setTextColor(Color.parseColor("#0A0E17"))
            btnModeGlobal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
            btnModeGlobal.setTextColor(Color.WHITE)
        }
    }

    private fun setupGameTabs() {
        games.forEach { game ->
            val btn = Button(this).apply {
                text = game.title
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 0, 12, 0) }
                
                background = getDrawable(R.drawable.game_item_background)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
                setTextColor(Color.WHITE)
                setPadding(32, 16, 32, 16)
                isFocusable = true
                isFocusableInTouchMode = true
                
                setOnClickListener {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@LeaderboardActivity)
                    selectGame(game)
                }
                
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        SoundManager.playClick()
                        HapticManager.vibrateClick(this@LeaderboardActivity)
                        selectGame(game)
                        view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                    } else {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    }
                }
            }
            tabsContainer.addView(btn)
        }
    }

    private fun selectGame(game: GameTab) {
        currentSelectedGame = game.key
        currentSelectedLevel = -1
        
        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) as Button
            val isSelected = games[i].key == game.key
            child.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isSelected) "#4CAF50" else "#333333"))
            child.alpha = if (isSelected) 1.0f else 0.7f
        }

        setupLevelTabs(game)
        refreshCurrentScores()
    }

    private fun setupLevelTabs(game: GameTab) {
        levelTabsContainer.removeAllViews()
        if (game.levels == null) {
            levelTabsContainer.visibility = View.GONE
            return
        }
        levelTabsContainer.visibility = View.VISIBLE
        
        game.levels.forEachIndexed { index, levelName ->
            val btn = Button(this).apply {
                text = levelName
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { setMargins(4, 0, 4, 0) }
                
                background = getDrawable(R.drawable.game_item_background)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
                setTextColor(Color.LTGRAY)
                isFocusable = true
                
                setOnClickListener {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@LeaderboardActivity)
                    selectLevel(index, game.key)
                }
                
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        selectLevel(index, game.key)
                    }
                }
            }
            levelTabsContainer.addView(btn)
        }
    }

    private fun selectLevel(level: Int, gameKey: String) {
        currentSelectedLevel = level
        for (i in 0 until levelTabsContainer.childCount) {
            val child = levelTabsContainer.getChildAt(i) as Button
            val isSelected = i == level
            child.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isSelected) "#00E5FF" else "#222222"))
            child.setTextColor(if (isSelected) Color.BLACK else Color.LTGRAY)
        }
        refreshCurrentScores()
    }

    private fun refreshCurrentScores() {
        scoresContainer.removeAllViews()
        if (isGlobalMode) {
            // Show loading placeholder
            val loadingView = TextView(this).apply {
                text = "⚡ Fetching Worldwide Firestore Scores..."
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 60, 0, 0)
            }
            scoresContainer.addView(loadingView)

            LeaderboardManager.getGlobalTopScores(this, currentSelectedGame, currentSelectedLevel) { globalScores ->
                if (!isFinishing && !isDestroyed) {
                    renderScores(globalScores)
                }
            }
        } else {
            val topScores = LeaderboardManager.getLocalTopScores(this, currentSelectedGame, currentSelectedLevel)
            renderScores(topScores)
        }
    }

    private fun renderScores(scores: List<LeaderboardManager.ScoreEntry>) {
        scoresContainer.removeAllViews()
        updatePodium(scores)

        if (scores.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = if (isGlobalMode) "🌐 No online records found yet. Set a high score!" else getString(R.string.no_records)
                setTextColor(Color.GRAY)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 100, 0, 0)
            }
            scoresContainer.addView(emptyView)
            return
        }

        scores.forEachIndexed { index, entry ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_leaderboard_row, scoresContainer, false)
            val medalView = row.findViewById<ImageView>(R.id.score_medal)
            val rankView = row.findViewById<TextView>(R.id.score_rank)
            
            when (index) {
                0 -> medalView.setImageResource(R.drawable.ic_medal_gold)
                1 -> medalView.setImageResource(R.drawable.ic_medal_silver)
                2 -> medalView.setImageResource(R.drawable.ic_medal_bronze)
                else -> {
                    medalView.visibility = View.GONE
                    rankView.visibility = View.VISIBLE
                    rankView.text = (index + 1).toString()
                    rankView.setTextColor(Color.LTGRAY)
                }
            }

            val prefix = if (entry.isGlobal) "🌐 " else ""
            row.findViewById<TextView>(R.id.score_name).text = "$prefix${entry.profileName}"
            row.findViewById<TextView>(R.id.score_value).text = entry.score.toString()
            row.findViewById<View>(R.id.score_avatar).backgroundTintList = ColorStateList.valueOf(entry.avatarColor)
            
            val iconView = row.findViewById<ImageView>(R.id.score_icon)
            if (entry.avatarId in avatars.indices) {
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(avatars[entry.avatarId])
            } else {
                iconView.visibility = View.GONE
            }

            if (index == 0) {
                row.findViewById<ImageView>(R.id.score_trophy).visibility = View.VISIBLE
            }

            scoresContainer.addView(row)
        }
    }

    private fun updatePodium(scores: List<LeaderboardManager.ScoreEntry>) {
        val p1 = findViewById<View>(R.id.podium_1)
        val p2 = findViewById<View>(R.id.podium_2)
        val p3 = findViewById<View>(R.id.podium_3)

        listOf(p1, p2, p3).forEach { it.visibility = View.INVISIBLE }

        if (scores.isNotEmpty()) {
            p1.visibility = View.VISIBLE
            bindPodium(scores[0], R.id.name_1, R.id.avatar_1, R.id.icon_1, R.id.score_1)
        }
        if (scores.size >= 2) {
            p2.visibility = View.VISIBLE
            bindPodium(scores[1], R.id.name_2, R.id.avatar_2, R.id.icon_2, R.id.score_2)
        }
        if (scores.size >= 3) {
            p3.visibility = View.VISIBLE
            bindPodium(scores[2], R.id.name_3, R.id.avatar_3, R.id.icon_3, R.id.score_3)
        }
    }

    private fun bindPodium(entry: LeaderboardManager.ScoreEntry, nameId: Int, bgId: Int, iconId: Int, scoreId: Int) {
        val prefix = if (entry.isGlobal) "🌐 " else ""
        findViewById<TextView>(nameId).text = "$prefix${entry.profileName}"
        findViewById<TextView>(scoreId).text = entry.score.toString()
        findViewById<View>(bgId).backgroundTintList = ColorStateList.valueOf(entry.avatarColor)
        val iconView = findViewById<ImageView>(iconId)
        if (entry.avatarId in avatars.indices) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(avatars[entry.avatarId])
        } else {
            iconView.visibility = View.GONE
        }
    }
}
