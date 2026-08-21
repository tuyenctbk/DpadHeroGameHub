package com.tdpham.games.common

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.tdpham.games.common.profile.ProfileManager

import com.google.firebase.firestore.persistentCacheSettings

object LeaderboardManager {

    private const val TAG = "LeaderboardManager"
    private const val COLLECTION_SCORES = "global_arcade_leaderboards"

    data class ScoreEntry(
        val profileName: String,
        val score: Int,
        val avatarColor: Int,
        val avatarId: Int = 0,
        val isGlobal: Boolean = false
    )

    init {
        try {
            val db = Firebase.firestore
            val settings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings {})
            }
            db.firestoreSettings = settings
        } catch (e: Throwable) {
            Log.e(TAG, "Firestore initialization warning: ${e.message}")
        }
    }

    /**
     * Retrieve local high scores across all on-device profiles.
     */
    fun getLocalTopScores(context: Context, gameKey: String, level: Int = -1, limit: Int = 10): List<ScoreEntry> {
        val allProfiles = ProfileManager.getProfiles(context)
        val entries = mutableListOf<ScoreEntry>()
        val prefs = context.getSharedPreferences("game_scores", Context.MODE_PRIVATE)

        for (profile in allProfiles) {
            val key = if (level >= 0) "${profile.id}_high_score_${gameKey}_l$level" else "${profile.id}_high_score_$gameKey"
            val score = prefs.getInt(key, 0)
            if (score > 0) {
                entries.add(ScoreEntry(profile.name, score, profile.avatarColor, profile.avatarId, isGlobal = false))
            }
        }

        return entries.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Submits a player's high score to the Global Worldwide Firebase Firestore leaderboard.
     */
    fun submitGlobalScore(context: Context, gameKey: String, level: Int, score: Int) {
        if (score <= 0) return

        val profile = ProfileManager.getActiveProfile(context)
        val profileName = profile?.name ?: "Arcade Player"
        val avatarId = profile?.avatarId ?: 0
        val avatarColor = profile?.avatarColor ?: -16728065 // Teal default
        val activeId = ProfileManager.getActiveProfileId(context) ?: "anon_device"

        val compositeGameKey = if (level >= 0) "${gameKey}_l$level" else gameKey
        val docId = "${activeId}_$compositeGameKey"

        try {
            val db = Firebase.firestore
            val docRef = db.collection(COLLECTION_SCORES).document(docId)

            val scoreData = hashMapOf(
                "gameKey" to gameKey,
                "level" to level,
                "compositeGameKey" to compositeGameKey,
                "profileName" to profileName,
                "avatarId" to avatarId,
                "avatarColor" to avatarColor,
                "score" to score,
                "timestamp" to System.currentTimeMillis()
            )

            // Write or update if higher
            docRef.get().addOnSuccessListener { snapshot ->
                val existingScore = snapshot.getLong("score") ?: 0L
                if (score >= existingScore) {
                    docRef.set(scoreData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Global score submitted successfully for $compositeGameKey: $score")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Failed to submit global score: ${e.message}")
                        }
                }
            }.addOnFailureListener {
                // If read failed, attempt direct set
                docRef.set(scoreData)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception while submitting score to Firestore: ${e.message}")
        }
    }

    /**
     * Retrieves the Global Top High Scores from Firebase Firestore.
     */
    fun getGlobalTopScores(
        context: Context,
        gameKey: String,
        level: Int = -1,
        limit: Int = 10,
        onResult: (List<ScoreEntry>) -> Unit
    ) {
        val compositeGameKey = if (level >= 0) "${gameKey}_l$level" else gameKey

        try {
            val db = Firebase.firestore
            db.collection(COLLECTION_SCORES)
                .whereEqualTo("compositeGameKey", compositeGameKey)
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .addOnSuccessListener { snapshot ->
                    val globalEntries = snapshot.documents.mapNotNull { doc ->
                        val name = doc.getString("profileName") ?: "Player"
                        val score = doc.getLong("score")?.toInt() ?: 0
                        val color = doc.getLong("avatarColor")?.toInt() ?: -16728065
                        val avatarId = doc.getLong("avatarId")?.toInt() ?: 0
                        if (score > 0) {
                            ScoreEntry(name, score, color, avatarId, isGlobal = true)
                        } else null
                    }

                    if (globalEntries.isNotEmpty()) {
                        onResult(globalEntries)
                    } else {
                        // Fallback to local scores if global collection is empty
                        onResult(getLocalTopScores(context, gameKey, level, limit))
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error fetching global scores: ${e.message}. Falling back to local.")
                    onResult(getLocalTopScores(context, gameKey, level, limit))
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception fetching global scores: ${e.message}")
            onResult(getLocalTopScores(context, gameKey, level, limit))
        }
    }

    /**
     * Real-time Snapshot Listener for Global Top High Scores from Firebase Firestore.
     * Ideal for live leaderboard widgets on the main game selection screen.
     */
    fun listenGlobalTopScores(
        context: Context,
        gameKey: String,
        level: Int = -1,
        limit: Int = 3,
        onUpdate: (List<ScoreEntry>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val compositeGameKey = if (level >= 0) "${gameKey}_l$level" else gameKey

        return try {
            val db = Firebase.firestore
            db.collection(COLLECTION_SCORES)
                .whereEqualTo("compositeGameKey", compositeGameKey)
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Snapshot error for $compositeGameKey: ${error.message}")
                        onUpdate(getLocalTopScores(context, gameKey, level, limit))
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val globalEntries = snapshot.documents.mapNotNull { doc ->
                            val name = doc.getString("profileName") ?: "Player"
                            val score = doc.getLong("score")?.toInt() ?: 0
                            val color = doc.getLong("avatarColor")?.toInt() ?: -16728065
                            val avatarId = doc.getLong("avatarId")?.toInt() ?: 0
                            if (score > 0) {
                                ScoreEntry(name, score, color, avatarId, isGlobal = true)
                            } else null
                        }
                        if (globalEntries.isNotEmpty()) {
                            onUpdate(globalEntries)
                        } else {
                            onUpdate(getLocalTopScores(context, gameKey, level, limit))
                        }
                    } else {
                        onUpdate(getLocalTopScores(context, gameKey, level, limit))
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception starting snapshot listener: ${e.message}")
            onUpdate(getLocalTopScores(context, gameKey, level, limit))
            null
        }
    }
}
