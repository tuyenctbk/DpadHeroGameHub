package com.tdpham.games.blackjack

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object BlackjackOptionsDialog {
    private const val PREFS_NAME = "blackjack_settings"
    const val KEY_DECKS = "deck_count" // 1, 2, 6
    const val KEY_STAND_SOFT_17 = "stand_soft_17" // true / false
    const val KEY_TABLE_THEME = "table_theme" // 0: Emerald, 1: Sapphire, 2: Crimson, 3: Gold

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.blackjack_settings_title))
            .addOption(
                label = "SHOE SIZE",
                valueProvider = {
                    val count = prefs.getInt(KEY_DECKS, 6)
                    "$count DECKS"
                },
                descProvider = {
                    "Number of standard 52-card decks shuffled into shoe."
                },
                onClick = {
                    val current = prefs.getInt(KEY_DECKS, 6)
                    val next = when (current) {
                        1 -> 2
                        2 -> 6
                        else -> 1
                    }
                    prefs.edit { putInt(KEY_DECKS, next) }
                }
            )
            .addOption(
                label = "DEALER SOFT 17",
                valueProvider = {
                    val stand = prefs.getBoolean(KEY_STAND_SOFT_17, true)
                    if (stand) "DEALER STANDS (H17)" else "DEALER HITS (S17)"
                },
                descProvider = {
                    "Rules when dealer holds an Ace counting as 11 and total is 17."
                },
                onClick = {
                    val stand = prefs.getBoolean(KEY_STAND_SOFT_17, true)
                    prefs.edit { putBoolean(KEY_STAND_SOFT_17, !stand) }
                }
            )
            .addOption(
                label = "FELT TABLE THEME",
                valueProvider = {
                    when (prefs.getInt(KEY_TABLE_THEME, 0)) {
                        0 -> "EMERALD GREEN"
                        1 -> "SAPPHIRE ROYAL"
                        2 -> "VELVET CRIMSON"
                        else -> "MIDNIGHT GOLD"
                    }
                },
                descProvider = {
                    "Casino felt fabric color."
                },
                onClick = {
                    val next = (prefs.getInt(KEY_TABLE_THEME, 0) + 1) % 4
                    prefs.edit { putInt(KEY_TABLE_THEME, next) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
