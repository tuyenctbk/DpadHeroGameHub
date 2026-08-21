package com.tdpham.games.connectfour

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object ConnectFourOptionsDialog {
    private const val PREFS_NAME = "connect_four_settings"
    const val KEY_MODE = "game_mode" // 0: 1P vs CPU, 1: 2P Local
    const val KEY_DIFFICULTY = "ai_difficulty" // 0: Easy, 1: Normal, 2: Master
    const val KEY_THEME = "disc_theme" // 0: Classic, 1: Cyber, 2: Retro Gold

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.connect_four_settings_title))
            .addOption(
                label = "GAME MODE",
                valueProvider = {
                    val mode = prefs.getInt(KEY_MODE, 0)
                    if (mode == 0) "1P vs CPU AI" else "2-PLAYER PASS & PLAY"
                },
                descProvider = {
                    val mode = prefs.getInt(KEY_MODE, 0)
                    if (mode == 0) "Play solo against tactical AI" else "Couch battle with a friend"
                },
                onClick = {
                    val mode = prefs.getInt(KEY_MODE, 0)
                    prefs.edit { putInt(KEY_MODE, if (mode == 0) 1 else 0) }
                }
            )
            .addOption(
                label = "AI DIFFICULTY",
                valueProvider = {
                    when (prefs.getInt(KEY_DIFFICULTY, 1)) {
                        0 -> "EASY (Casual)"
                        1 -> "NORMAL (Strategic)"
                        else -> "MASTER (Minimax 4-in-a-Row)"
                    }
                },
                descProvider = {
                    "Higher difficulty calculates more moves ahead."
                },
                onClick = {
                    val diff = (prefs.getInt(KEY_DIFFICULTY, 1) + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, diff) }
                }
            )
            .addOption(
                label = "COLOR THEME",
                valueProvider = {
                    when (prefs.getInt(KEY_THEME, 0)) {
                        0 -> "CLASSIC (Yellow & Red)"
                        1 -> "CYBER (Cyan & Neon Magenta)"
                        else -> "RETRO (Gold & Platinum)"
                    }
                },
                descProvider = {
                    "Visual color palette for player discs and board."
                },
                onClick = {
                    val theme = (prefs.getInt(KEY_THEME, 0) + 1) % 3
                    prefs.edit { putInt(KEY_THEME, theme) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
