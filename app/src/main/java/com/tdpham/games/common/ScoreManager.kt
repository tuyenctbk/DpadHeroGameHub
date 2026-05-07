package com.tdpham.games.common

import android.content.Context

object ScoreManager {
    private const val PREFS_NAME = "game_scores"

    fun getHighScore(context: Context, gameKey: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("high_score_$gameKey", 0)
    }

    fun updateHighScore(context: Context, gameKey: String, newScore: Int): Boolean {
        val currentHigh = getHighScore(context, gameKey)
        if (newScore > currentHigh) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt("high_score_$gameKey", newScore).apply()
            return true
        }
        return false
    }
}
