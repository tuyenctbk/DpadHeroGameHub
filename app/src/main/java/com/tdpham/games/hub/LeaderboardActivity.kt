package com.tdpham.games.hub

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.LeaderboardManager
import com.tdpham.games.common.SoundManager
import java.text.NumberFormat
import java.util.Locale

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var tabsContainer: LinearLayout
    private lateinit var scoresContainer: LinearLayout
    private lateinit var levelTabsContainer: LinearLayout
    private lateinit var btnModeLocal: Button
    private lateinit var btnModeGlobal: Button
    private var isGlobalMode = false
    private var currentSelectedGame = "snake"
    private var currentSelectedLevel = -1
    private var currentSelectedLevelTabIdx = 0

    private val avatars = listOf(
        R.drawable.ic_hero_knight, R.drawable.ic_hero_wizard,
        R.drawable.ic_hero_archer, R.drawable.ic_hero_ninja,
        R.drawable.ic_hero_viking, R.drawable.ic_hero_dragon,
        R.drawable.ic_hero_phoenix, R.drawable.ic_hero_shield,
        R.drawable.ic_hero_sword, R.drawable.ic_hero_crown
    )

    private val rowInterpolator = DecelerateInterpolator()

    data class GameTab(
        val key: String,
        val title: String,
        val levels: List<Pair<String, Int>>? = null
    )

    private val games by lazy {
        val lvlLabel = getString(R.string.level_label)
        listOf(
            GameTab("snake", getString(R.string.snake), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("tetris", getString(R.string.game_tetris), listOf("LVL 1" to 1, "LVL 3" to 3, "LVL 5" to 5, "LVL 8" to 8, "LVL 10" to 10)),
            GameTab("minesweeper", getString(R.string.minesweeper), listOf("$lvlLabel 1" to 0, "$lvlLabel 2" to 1, "$lvlLabel 3" to 2)),
            GameTab("sudoku", getString(R.string.game_sudoku), listOf(
                getString(R.string.sudoku_level_1).split(":")[0] to 0,
                getString(R.string.sudoku_level_2).split(":")[0] to 1,
                getString(R.string.sudoku_level_3).split(":")[0] to 2
            )),
            GameTab("trex", getString(R.string.game_trex), listOf("DADDY" to 0, "NINJA" to 1, "ASTRO" to 2, "BABY" to 3, "KING" to 14)),
            GameTab("4096", getString(R.string.game_4096), listOf("4x4" to 4, "5x5" to 5)),
            GameTab("flappy_hero", getString(R.string.game_flappy), listOf("LVL 1" to 0, "LVL 2" to 1, "LVL 3" to 2, "LVL 4" to 3, "LVL 5" to 4)),
            GameTab("starfighter", getString(R.string.game_starfighter), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("syobon_action", getString(R.string.game_syobon), listOf("CLASSIC" to 0)),
            GameTab("road_racer", getString(R.string.game_road_racer), listOf("LOW" to 0, "NORMAL" to 1, "HIGH" to 2)),
            GameTab("brick_break", getString(R.string.game_brick_break), listOf("LARGE" to 0, "MEDIUM" to 1, "SMALL" to 2)),
            GameTab("froggy_cross", getString(R.string.game_froggy)),
            GameTab("battle_tanks", getString(R.string.game_tanks), listOf("STAGE 1" to 1, "STAGE 5" to 5, "STAGE 10" to 10)),
            GameTab("solitaire", getString(R.string.game_solitaire), listOf("DRAW 1" to 1, "DRAW 3" to 3)),
            GameTab("retrodriver", getString(R.string.game_retrodriver)),
            GameTab("lines98", getString(R.string.game_lines98), listOf("5 COLORS" to 5, "7 COLORS" to 7, "9 COLORS" to 9)),
            GameTab("spinball", getString(R.string.game_spinball)),
            GameTab("simon_says", getString(R.string.game_simon), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("checkers", getString(R.string.game_checkers), listOf("EASY" to 0, "HARD" to 1)),
            GameTab("memory", getString(R.string.game_memory), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("fruit", getString(R.string.game_fruit)),
            GameTab("sokoban", getString(R.string.game_sokoban), listOf("$lvlLabel 1" to 0, "$lvlLabel 2" to 1, "$lvlLabel 3" to 2)),
            GameTab("slide_puzzle", getString(R.string.game_slide_puzzle), listOf("3x3" to 0, "4x4" to 1, "5x5" to 2)),
            GameTab("mental_math", getString(R.string.game_mental_math), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("tic_tac_toe", getString(R.string.game_tictactoe)),
            GameTab("word_quest", getString(R.string.game_word_quest), listOf("EASY" to 0, "NORMAL" to 1, "HARD" to 2)),
            GameTab("dungeon_escape", getString(R.string.game_dungeon), listOf("LVL 1" to 1, "LVL 5" to 5, "LVL 10" to 10)),
            GameTab("hangman", getString(R.string.game_hangman)),
            GameTab("monkey", getString(R.string.game_monkey)),
            GameTab("frenzy", getString(R.string.game_frenzy)),
            GameTab("connect_four", getString(R.string.game_connect_four)),
            GameTab("blackjack", getString(R.string.game_blackjack)),
            GameTab("trivia", getString(R.string.game_trivia), listOf("ALL" to 0, "SCIENCE" to 1, "HISTORY" to 2, "POP" to 3, "SPORTS" to 4))
        )
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

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
            ).apply { setMargins(0, 0, 0, dpToPx(16)) }
            gravity = android.view.Gravity.CENTER
        }
        rootLayout.addView(levelTabsContainer, rootLayout.indexOfChild(tabsContainer.parent as View) + 1)

        setupModeSwitcher()

        val btnBack = findViewById<Button>(R.id.btn_back)
        btnBack.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            finish()
        }
        setupFocusEffect(btnBack)
        setupFocusEffect(btnModeLocal)
        setupFocusEffect(btnModeGlobal)

        setupGameTabs()

        val lastGame = getSharedPreferences("game_settings", MODE_PRIVATE).getString("last_played", "snake")
        val startIndex = games.indexOfFirst { it.key == lastGame }.coerceAtLeast(0)

        selectGame(games[startIndex])
        if (tabsContainer.childCount > startIndex) {
            tabsContainer.getChildAt(startIndex).requestFocus()
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
        val marginH = dpToPx(8)
        val padH = dpToPx(18)
        val padV = dpToPx(10)
        games.forEach { game ->
            val btn = Button(this).apply {
                text = game.title
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(marginH, 0, marginH, 0) }

                background = getDrawable(R.drawable.game_item_background)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
                setTextColor(Color.WHITE)
                setPadding(padH, padV, padH, padV)
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
                        if (currentSelectedGame != game.key) {
                            selectGame(game)
                        }
                        view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(180).start()
                    } else {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
                    }
                }
            }
            tabsContainer.addView(btn)
        }
    }

    private fun selectGame(game: GameTab) {
        currentSelectedGame = game.key

        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) as Button
            val isSelected = games[i].key == game.key
            child.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isSelected) "#4CAF50" else "#333333"))
            child.alpha = if (isSelected) 1.0f else 0.72f
        }

        setupLevelTabs(game)
        if (!game.levels.isNullOrEmpty()) {
            selectLevelTab(0, game.levels[0].second)
        } else {
            currentSelectedLevel = -1
            currentSelectedLevelTabIdx = 0
            refreshCurrentScores()
        }
    }

    private fun setupLevelTabs(game: GameTab) {
        levelTabsContainer.removeAllViews()
        val levels = game.levels
        if (levels.isNullOrEmpty()) {
            levelTabsContainer.visibility = View.GONE
            return
        }
        levelTabsContainer.visibility = View.VISIBLE
        val m = dpToPx(4)

        levels.forEachIndexed { index, (levelName, scoreLevelValue) ->
            val btn = Button(this).apply {
                text = levelName
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { setMargins(m, 0, m, 0) }

                background = getDrawable(R.drawable.game_item_background)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
                setTextColor(Color.LTGRAY)
                isFocusable = true

                setOnClickListener {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(this@LeaderboardActivity)
                    selectLevelTab(index, scoreLevelValue)
                }

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && currentSelectedLevelTabIdx != index) {
                        SoundManager.playClick()
                        selectLevelTab(index, scoreLevelValue)
                    }
                }
            }
            levelTabsContainer.addView(btn)
        }
    }

    private fun selectLevelTab(tabIndex: Int, scoreLevelValue: Int) {
        currentSelectedLevelTabIdx = tabIndex
        currentSelectedLevel = scoreLevelValue
        for (i in 0 until levelTabsContainer.childCount) {
            val child = levelTabsContainer.getChildAt(i) as Button
            val isSelected = i == tabIndex
            child.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isSelected) "#00E5FF" else "#222222"))
            child.setTextColor(if (isSelected) Color.BLACK else Color.LTGRAY)
        }
        refreshCurrentScores()
    }

    private fun refreshCurrentScores() {
        scoresContainer.removeAllViews()
        val reqGame = currentSelectedGame
        val reqLevel = currentSelectedLevel
        val reqGlobal = isGlobalMode

        if (reqGlobal) {
            val loadingView = TextView(this).apply {
                text = "⚡ Fetching Worldwide Scores..."
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = android.view.Gravity.CENTER
                setPadding(0, dpToPx(36), 0, 0)
            }
            scoresContainer.addView(loadingView)

            LeaderboardManager.getGlobalTopScores(this, reqGame, reqLevel) { globalScores ->
                if (!isFinishing && !isDestroyed &&
                    currentSelectedGame == reqGame &&
                    currentSelectedLevel == reqLevel &&
                    isGlobalMode == reqGlobal
                ) {
                    renderScores(globalScores)
                }
            }
        } else {
            val topScores = LeaderboardManager.getLocalTopScores(this, reqGame, reqLevel)
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
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = android.view.Gravity.CENTER
                setPadding(0, dpToPx(48), 0, 0)
            }
            scoresContainer.addView(emptyView)
            return
        }

        val numFmt = NumberFormat.getIntegerInstance(Locale.US)
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
            row.findViewById<TextView>(R.id.score_value).text = numFmt.format(entry.score)
            row.findViewById<View>(R.id.score_avatar).backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A3348"))

            val iconView = row.findViewById<ImageView>(R.id.score_icon)
            if (entry.avatarId in avatars.indices) {
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(avatars[entry.avatarId])
                iconView.imageTintList = ColorStateList.valueOf(entry.avatarColor)
            } else {
                iconView.visibility = View.GONE
            }

            if (index == 0) {
                row.findViewById<ImageView>(R.id.score_trophy).visibility = View.VISIBLE
            }

            row.alpha = 0f
            row.translationY = 14f
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 35L).coerceAtMost(240L))
                .setDuration(220)
                .setInterpolator(rowInterpolator)
                .start()

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
            animatePodiumSlot(p1, 0L)
        }
        if (scores.size >= 2) {
            p2.visibility = View.VISIBLE
            bindPodium(scores[1], R.id.name_2, R.id.avatar_2, R.id.icon_2, R.id.score_2)
            animatePodiumSlot(p2, 60L)
        }
        if (scores.size >= 3) {
            p3.visibility = View.VISIBLE
            bindPodium(scores[2], R.id.name_3, R.id.avatar_3, R.id.icon_3, R.id.score_3)
            animatePodiumSlot(p3, 120L)
        }
    }

    private fun animatePodiumSlot(view: View, delayMs: Long) {
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(260)
            .setInterpolator(rowInterpolator)
            .start()
    }

    private fun bindPodium(entry: LeaderboardManager.ScoreEntry, nameId: Int, bgId: Int, iconId: Int, scoreId: Int) {
        val prefix = if (entry.isGlobal) "🌐 " else ""
        findViewById<TextView>(nameId).text = "$prefix${entry.profileName}"
        findViewById<TextView>(scoreId).text = NumberFormat.getIntegerInstance(Locale.US).format(entry.score)
        findViewById<View>(bgId).backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A3348"))
        val iconView = findViewById<ImageView>(iconId)
        if (entry.avatarId in avatars.indices) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(avatars[entry.avatarId])
            iconView.imageTintList = ColorStateList.valueOf(entry.avatarColor)
        } else {
            iconView.visibility = View.GONE
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.tdpham.games.common.ControllerNavigationHelper.handleUiKeyEvent(this, event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                v.animate().scaleX(1.06f).scaleY(1.06f).translationZ(6f).setDuration(180).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(180).start()
            }
        }
    }
}
