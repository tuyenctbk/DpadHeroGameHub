package com.tdpham.games.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.tdpham.games.BuildConfig
import com.tdpham.games.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Smart manager for calculating and displaying:
 * 1. Rate App dialog (at peak engagement moments: after high scores, 3+ sessions, 4+ games).
 * 2. Share App dialog (after victories and milestone achievements).
 * 3. Update App dialog (via Firebase Remote Config version validation with Play Store direct update).
 */
object AppEngagementManager {

    private const val PREFS_NAME = "app_engagement_prefs"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_GAMES_PLAYED = "games_played"
    private const val KEY_GAMES_WON = "games_won"
    private const val KEY_HAS_RATED = "has_rated"
    private const val KEY_RATE_DECLINED = "rate_declined"
    private const val KEY_LAST_RATE_PROMPT = "last_rate_prompt_time"
    private const val KEY_LAST_SHARE_PROMPT = "last_share_prompt_time"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check_time"

    private const val RATE_COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
    private const val SHARE_COOLDOWN_MS = 5 * 24 * 60 * 60 * 1000L // 5 days

    private var isDialogOpen = false

    /**
     * Call when main hub or game activity is resumed.
     */
    fun onAppForegrounded(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessions = prefs.getInt(KEY_SESSION_COUNT, 0) + 1
        prefs.edit().putInt(KEY_SESSION_COUNT, sessions).apply()

        // 1. Check for App Updates first
        checkForAppUpdate(activity) { hasUpdate ->
            if (!hasUpdate) {
                // 2. If no update pending, evaluate smart rating prompt
                maybeShowRatePrompt(activity)
            }
        }
    }

    /**
     * Call when player finishes a game session.
     */
    fun onGameCompleted(activity: AppCompatActivity, isWin: Boolean, isNewHighScore: Boolean) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val games = prefs.getInt(KEY_GAMES_PLAYED, 0) + 1
        var wins = prefs.getInt(KEY_GAMES_WON, 0)
        if (isWin || isNewHighScore) {
            wins++
        }
        prefs.edit()
            .putInt(KEY_GAMES_PLAYED, games)
            .putInt(KEY_GAMES_WON, wins)
            .apply()

        if (isWin || isNewHighScore) {
            // High positive sentiment: attempt Rate dialog, or milestone Share dialog
            if (!hasUserRated(activity) && shouldPromptRate(activity)) {
                showRateDialog(activity)
            } else if (wins > 0 && wins % 5 == 0 && shouldPromptShare(activity)) {
                showShareDialog(activity)
            }
        }
    }

    private fun hasUserRated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_RATED, false) || prefs.getBoolean(KEY_RATE_DECLINED, false)
    }

    private fun shouldPromptRate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_RATED, false) || prefs.getBoolean(KEY_RATE_DECLINED, false)) {
            return false
        }
        val sessions = prefs.getInt(KEY_SESSION_COUNT, 0)
        val games = prefs.getInt(KEY_GAMES_PLAYED, 0)
        val wins = prefs.getInt(KEY_GAMES_WON, 0)
        val lastPrompt = prefs.getLong(KEY_LAST_RATE_PROMPT, 0L)
        val now = System.currentTimeMillis()

        if (now - lastPrompt < RATE_COOLDOWN_MS) return false

        // User is engaged if they've played at least 3 sessions, or 4 games, or scored a win
        return sessions >= 3 || games >= 4 || wins >= 1
    }

    private fun shouldPromptShare(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPrompt = prefs.getLong(KEY_LAST_SHARE_PROMPT, 0L)
        val now = System.currentTimeMillis()
        return (now - lastPrompt >= SHARE_COOLDOWN_MS)
    }

    fun maybeShowRatePrompt(activity: AppCompatActivity) {
        if (isDialogOpen || activity.isFinishing || activity.isDestroyed) return
        if (shouldPromptRate(activity)) {
            showRateDialog(activity)
        }
    }

    /**
     * Shows the 5-Star Rating Dialog with D-Pad focus & audio feedback.
     */
    fun showRateDialog(activity: AppCompatActivity, onDismiss: (() -> Unit)? = null) {
        if (isDialogOpen || activity.isFinishing || activity.isDestroyed) return
        isDialogOpen = true

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_RATE_PROMPT, System.currentTimeMillis()).apply()

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_rate_app, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnRate = view.findViewById<Button>(R.id.btn_rate_5_stars)
        val btnLater = view.findViewById<Button>(R.id.btn_rate_later)
        val btnNever = view.findViewById<Button>(R.id.btn_rate_never)

        setupFocusAnimation(btnRate)
        setupFocusAnimation(btnLater)
        setupFocusAnimation(btnNever)

        btnRate.setOnClickListener {
            prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
            dialog.dismiss()
            rateApp(activity)
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        btnNever.setOnClickListener {
            prefs.edit().putBoolean(KEY_RATE_DECLINED, true).apply()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            isDialogOpen = false
            onDismiss?.invoke()
        }

        dialog.show()
        btnRate.requestFocus()
    }

    /**
     * Shows the Share Dialog with D-Pad focus & audio feedback.
     */
    fun showShareDialog(activity: AppCompatActivity, onDismiss: (() -> Unit)? = null) {
        if (isDialogOpen || activity.isFinishing || activity.isDestroyed) return
        isDialogOpen = true

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SHARE_PROMPT, System.currentTimeMillis()).apply()

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_share_app, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnShare = view.findViewById<Button>(R.id.btn_share_now)
        val btnLater = view.findViewById<Button>(R.id.btn_share_later)

        setupFocusAnimation(btnShare)
        setupFocusAnimation(btnLater)

        btnShare.setOnClickListener {
            dialog.dismiss()
            shareApp(activity)
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            isDialogOpen = false
            onDismiss?.invoke()
        }

        dialog.show()
        btnShare.requestFocus()
    }

    /**
     * Shows the Update Available Dialog.
     */
    fun showUpdateDialog(
        activity: AppCompatActivity,
        isForce: Boolean = false,
        updateNotes: String = "",
        onDismiss: (() -> Unit)? = null
    ) {
        if (isDialogOpen || activity.isFinishing || activity.isDestroyed) return
        isDialogOpen = true

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_app, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(!isForce)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvMessage = view.findViewById<TextView>(R.id.tv_update_message)
        if (updateNotes.isNotEmpty()) {
            tvMessage.text = updateNotes
        }

        val btnUpdate = view.findViewById<Button>(R.id.btn_update_now)
        val btnLater = view.findViewById<Button>(R.id.btn_update_later)

        setupFocusAnimation(btnUpdate)
        setupFocusAnimation(btnLater)

        if (isForce) {
            btnLater.visibility = View.GONE
        }

        btnUpdate.setOnClickListener {
            openPlayStore(activity)
            if (!isForce) dialog.dismiss()
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            isDialogOpen = false
            onDismiss?.invoke()
        }

        dialog.show()
        btnUpdate.requestFocus()
    }

    /**
     * Checks Firebase Remote Config for newer version releases.
     */
    fun checkForAppUpdate(activity: AppCompatActivity, callback: ((Boolean) -> Unit)? = null) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val remoteConfig = Firebase.remoteConfig
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600 // Cache 1 hour
                }
                remoteConfig.setConfigSettingsAsync(configSettings).await()
                remoteConfig.fetchAndActivate().await()

                val latestVersionCode = remoteConfig.getLong("latest_version_code")
                val minRequiredCode = remoteConfig.getLong("min_required_version_code")
                val updateMessage = remoteConfig.getString("update_message")
                val isForce = BuildConfig.VERSION_CODE < minRequiredCode

                withContext(Dispatchers.Main) {
                    if (latestVersionCode > BuildConfig.VERSION_CODE) {
                        showUpdateDialog(
                            activity = activity,
                            isForce = isForce,
                            updateNotes = if (updateMessage.isNotEmpty()) updateMessage else activity.getString(R.string.update_content)
                        )
                        callback?.invoke(true)
                    } else {
                        callback?.invoke(false)
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    callback?.invoke(false)
                }
            }
        }
    }

    /**
     * Opens Play Store page for rating or updating.
     */
    fun rateApp(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            context.startActivity(webIntent)
        }
    }

    fun openPlayStore(context: Context) {
        rateApp(context)
    }

    /**
     * Shares the app with friends via standard Android Share Chooser, with safe clipboard fallback for Android TV.
     */
    fun shareApp(context: Context) {
        val shareText = context.getString(R.string.share_message)
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        } catch (e: Throwable) {
            // Fallback for Android TV or devices without standard share intent handlers: copy to clipboard!
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Share Link", shareText)
                clipboard.setPrimaryClip(clip)
                val msg = context.getString(R.string.share_copied)
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            } catch (ignored: Throwable) {}
        }
    }

    private fun setupFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }
}
