package com.tdpham.games.hub

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.tdpham.games.R

object RatingGuideManager {
    private const val PREFS_NAME = "game_ratings"
    private const val KEY_PLAY_COUNT = "play_count"
    private const val KEY_SHOULD_SHOW = "should_show_rating"
    private const val KEY_INSTALL_DATE = "install_date"
    private const val KEY_WIN_COUNT = "win_count"
    private const val KEY_HIGHSCORE_COUNT = "highscore_count"
    private const val KEY_TOTAL_GAMES = "total_games_played"
    private const val KEY_LAST_SHOW_TIME = "last_show_time"

    fun incrementPlayCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_PLAY_COUNT, 0)
        
        val editor = prefs.edit()
        editor.putInt(KEY_PLAY_COUNT, currentCount + 1)
        
        // Initialize install date if first time
        if (prefs.getLong(KEY_INSTALL_DATE, 0L) == 0L) {
            editor.putLong(KEY_INSTALL_DATE, System.currentTimeMillis())
        }
        editor.apply()
    }

    fun recordGameEvent(context: Context, isWin: Boolean, isHighScore: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        editor.putInt(KEY_TOTAL_GAMES, prefs.getInt(KEY_TOTAL_GAMES, 0) + 1)
        if (isWin) {
            editor.putInt(KEY_WIN_COUNT, prefs.getInt(KEY_WIN_COUNT, 0) + 1)
        }
        if (isHighScore) {
            editor.putInt(KEY_HIGHSCORE_COUNT, prefs.getInt(KEY_HIGHSCORE_COUNT, 0) + 1)
        }
        editor.apply()
    }

    fun shouldShowRating(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Don't show if already rated or opted out
        val shouldShow = prefs.getBoolean(KEY_SHOULD_SHOW, true)
        if (!shouldShow) return false

        // 2. Early days check: at least 3 days since first install
        val firstLaunch = prefs.getLong(KEY_INSTALL_DATE, 0L)
        if (firstLaunch == 0L) return false
        
        val daysPassed = (System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)
        if (daysPassed < 3) return false

        // 3. Nag frequency check: max once every 3 days to avoid annoyance
        val lastShow = prefs.getLong(KEY_LAST_SHOW_TIME, 0L)
        if (System.currentTimeMillis() - lastShow < 3 * 24 * 60 * 60 * 1000) return false

        // 4. "Having Fun" Algorithm
        // User is considered having fun if they played at least 5 games 
        // AND have achieved some success (at least 1 win or 2 high scores)
        val totalGames = prefs.getInt(KEY_TOTAL_GAMES, 0)
        val wins = prefs.getInt(KEY_WIN_COUNT, 0)
        val highScores = prefs.getInt(KEY_HIGHSCORE_COUNT, 0)
        
        val isHavingFun = totalGames >= 5 && (wins >= 1 || highScores >= 2)
        if (!isHavingFun) return false

        // 5. Engagement threshold: Show every 10 sessions or 15 games played
        val playCount = prefs.getInt(KEY_PLAY_COUNT, 0)
        return (playCount >= 5 && playCount % 10 == 0) || (totalGames >= 10 && totalGames % 15 == 0)
    }

    fun showRatingDialog(context: Context, onDismiss: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_rating)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        // Mark as shown to prevent immediate re-prompt
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SHOW_TIME, System.currentTimeMillis()).apply()

        val btnRate = dialog.findViewById<Button>(R.id.btn_rate_now)
        val btnLater = dialog.findViewById<Button>(R.id.btn_rate_later)
        val contentText = dialog.findViewById<TextView>(R.id.rating_content)

        // Ensure the content text explicitly asks for 5 stars as requested
        contentText.text = context.getString(R.string.rating_content)

        btnRate.setOnClickListener {
            // If they clicked Rate, don't show again
            prefs.edit().putBoolean(KEY_SHOULD_SHOW, false).apply()
            dialog.dismiss()
            
            val packageName = context.packageName
            val uri = Uri.parse("market://details?id=$packageName")
            val goToMarket = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            try {
                context.startActivity(goToMarket)
            } catch (e: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                } catch (ex: Throwable) {
                    Log.e("RatingGuideManager", "Could not launch Play Store link", ex)
                }
            }
            onDismiss()
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }

        setupFocusEffect(btnRate)
        setupFocusEffect(btnLater)

        try {
            dialog.show()
            btnRate.requestFocus()
        } catch (t: Throwable) {
            Log.e("RatingGuideManager", "Failed to show rating dialog: ${t.message}")
            onDismiss()
        }
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }
        view.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                v.requestFocus()
            }
            false
        }
    }
}
