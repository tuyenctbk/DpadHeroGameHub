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
import com.tdpham.games.R

object RatingGuideManager {
    private const val PREFS_NAME = "game_ratings"
    private const val KEY_PLAY_COUNT = "play_count"
    private const val KEY_SHOULD_SHOW = "should_show_rating"

    fun incrementPlayCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_PLAY_COUNT, 0)
        prefs.edit().putInt(KEY_PLAY_COUNT, currentCount + 1).apply()
    }

    fun shouldShowRating(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shouldShow = prefs.getBoolean(KEY_SHOULD_SHOW, true)
        if (!shouldShow) return false
        
        val count = prefs.getInt(KEY_PLAY_COUNT, 0)
        // Show rating prompt starting after 3 launches, then every 5 launches
        return count >= 3 && (count - 3) % 5 == 0
    }

    fun showRatingDialog(context: Context, onDismiss: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_rating)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val btnRate = dialog.findViewById<Button>(R.id.btn_rate_now)
        val btnLater = dialog.findViewById<Button>(R.id.btn_rate_later)
        val btnNever = dialog.findViewById<Button>(R.id.btn_rate_never)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        btnRate.setOnClickListener {
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
                } catch (ex: Exception) {
                    Log.e("RatingGuideManager", "Could not launch Play Store link", ex)
                }
            }
            onDismiss()
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }

        btnNever.setOnClickListener {
            prefs.edit().putBoolean(KEY_SHOULD_SHOW, false).apply()
            dialog.dismiss()
            onDismiss()
        }

        setupFocusEffect(btnRate)
        setupFocusEffect(btnLater)
        setupFocusEffect(btnNever)

        dialog.show()
        btnRate.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
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
