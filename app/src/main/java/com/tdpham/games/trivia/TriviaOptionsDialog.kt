package com.tdpham.games.trivia

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object TriviaOptionsDialog {
    private const val PREFS_NAME = "trivia_settings"
    const val KEY_CATEGORY = "category_pack" // 0: ALL, 1: Gaming, 2: Science, 3: Pop Culture, 4: History
    const val KEY_TIMER_MODE = "timer_mode" // 0: 15s (Standard), 1: 30s (Relaxed), 2: 8s (Blitz), 3: Infinite
    const val KEY_GAUNTLET_LENGTH = "gauntlet_length" // 5, 10, 15

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.trivia_settings_title))
            .addOption(
                label = "CATEGORY PACK",
                valueProvider = {
                    when (prefs.getInt(KEY_CATEGORY, 0)) {
                        0 -> "ALL MIXED (Grand Arena)"
                        1 -> "GAMING & RETRO ARCADE"
                        2 -> "SCIENCE & TECHNOLOGY"
                        3 -> "POP CULTURE & CINEMA"
                        else -> "WORLD HISTORY & GEOGRAPHY"
                    }
                },
                descProvider = {
                    "Topic focus for the quiz question gauntlet."
                },
                onClick = {
                    val next = (prefs.getInt(KEY_CATEGORY, 0) + 1) % 5
                    prefs.edit { putInt(KEY_CATEGORY, next) }
                }
            )
            .addOption(
                label = "QUESTION TIMER",
                valueProvider = {
                    when (prefs.getInt(KEY_TIMER_MODE, 0)) {
                        0 -> "15 SECONDS (Standard)"
                        1 -> "30 SECONDS (Relaxed)"
                        2 -> "8 SECONDS (Speed Blitz)"
                        else -> "INFINITE (Untimed Casual)"
                    }
                },
                descProvider = {
                    "Faster answers earn higher time multiplier bonuses."
                },
                onClick = {
                    val next = (prefs.getInt(KEY_TIMER_MODE, 0) + 1) % 4
                    prefs.edit { putInt(KEY_TIMER_MODE, next) }
                }
            )
            .addOption(
                label = "GAUNTLET LENGTH",
                valueProvider = {
                    val length = prefs.getInt(KEY_GAUNTLET_LENGTH, 10)
                    "$length QUESTIONS"
                },
                descProvider = {
                    "Total questions required to complete the run."
                },
                onClick = {
                    val current = prefs.getInt(KEY_GAUNTLET_LENGTH, 10)
                    val next = when (current) {
                        5 -> 10
                        10 -> 15
                        else -> 5
                    }
                    prefs.edit { putInt(KEY_GAUNTLET_LENGTH, next) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
