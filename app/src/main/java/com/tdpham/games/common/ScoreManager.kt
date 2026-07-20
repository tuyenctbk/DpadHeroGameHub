package com.tdpham.games.common

import android.content.Context
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.tdpham.games.common.profile.ProfileManager

object ScoreManager {
    private const val PREFS_NAME = "game_scores"

    private fun getProfileScopedKey(context: Context, gameKey: String, level: Int): String {
        val activeId = ProfileManager.getActiveProfileId(context) ?: "global"
        val baseKey = if (level >= 0) "high_score_${gameKey}_l$level" else "high_score_$gameKey"
        return "${activeId}_$baseKey"
    }

    fun getHighScore(context: Context, gameKey: String, level: Int = -1): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getProfileScopedKey(context, gameKey, level)
        return prefs.getInt(key, 0)
    }

    fun updateHighScore(context: Context, gameKey: String, newScore: Int, level: Int = -1): Boolean {
        val currentHigh = getHighScore(context, gameKey, level)
        if (newScore > currentHigh) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = getProfileScopedKey(context, gameKey, level)
            prefs.edit().putInt(key, newScore).apply()

            // Also update global if active id is not null (for backward compatibility)
            val activeId = ProfileManager.getActiveProfileId(context)
            if (activeId != null) {
                val globalKey = if (level >= 0) "high_score_${gameKey}_l$level" else "high_score_$gameKey"
                prefs.edit().putInt(globalKey, newScore).apply()
            }

            // Log high score to Firebase
            try {
                val bundle = Bundle()
                val firebaseKey = if (level >= 0) "${gameKey}_l$level" else gameKey
                bundle.putString(FirebaseAnalytics.Param.LEVEL_NAME, firebaseKey)
                bundle.putLong(FirebaseAnalytics.Param.SCORE, newScore.toLong())
                Firebase.analytics.logEvent(FirebaseAnalytics.Event.POST_SCORE, bundle)
            } catch (e: Exception) {
                android.util.Log.e("ScoreManager", "Failed to log high score to Firebase: ${e.message}", e)
            }

            return true
        }
        return false
    }

    fun migrateGlobalToProfile(context: Context, profileId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val editor = prefs.edit()
        
        for ((key, value) in allEntries) {
            if (key.startsWith("high_score_") && value is Int) {
                val profileKey = "${profileId}_$key"
                if (prefs.getInt(profileKey, 0) < value) {
                    editor.putInt(profileKey, value)
                }
            }
        }
        editor.apply()
    }
}
