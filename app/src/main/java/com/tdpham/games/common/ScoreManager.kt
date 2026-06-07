package com.tdpham.games.common

import android.content.Context
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

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

            // Log high score to Firebase
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, gameKey)
            bundle.putLong(FirebaseAnalytics.Param.SCORE, newScore.toLong())
            Firebase.analytics.logEvent(FirebaseAnalytics.Event.POST_SCORE, bundle)

            return true
        }
        return false
    }
}
