package com.tdpham.games.common

import android.content.Context
import com.tdpham.games.common.profile.ProfileManager

object LeaderboardManager {
    
    data class ScoreEntry(
        val profileName: String,
        val score: Int,
        val avatarColor: Int,
        val avatarId: Int = 0
    )

    fun getLocalTopScores(context: Context, gameKey: String, level: Int = -1, limit: Int = 10): List<ScoreEntry> {
        val allProfiles = ProfileManager.getProfiles(context)
        val entries = mutableListOf<ScoreEntry>()
        
        val prefs = context.getSharedPreferences("game_scores", Context.MODE_PRIVATE)
        
        for (profile in allProfiles) {
            val key = if (level >= 0) "${profile.id}_high_score_${gameKey}_l$level" else "${profile.id}_high_score_$gameKey"
            val score = prefs.getInt(key, 0)
            if (score > 0) {
                entries.add(ScoreEntry(profile.name, score, profile.avatarColor, profile.avatarId))
            }
        }
        
        return entries.sortedByDescending { it.score }.take(limit)
    }
}
