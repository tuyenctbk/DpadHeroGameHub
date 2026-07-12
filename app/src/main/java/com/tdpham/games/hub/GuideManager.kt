package com.tdpham.games.hub

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.tdpham.games.R

object GuideManager {
    private const val PREFS_NAME = "game_guides"

    fun incrementLaunchCount(context: Context, gameKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt("launch_count_$gameKey", 0)
        prefs.edit().putInt("launch_count_$gameKey", current + 1).apply()
    }

    fun shouldShowGuide(context: Context, gameKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("show_$gameKey", true)
    }

    fun shouldShowMasteryHint(context: Context, gameKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_$gameKey", true)) {
            val launchCount = prefs.getInt("launch_count_$gameKey", 0)
            if (launchCount <= 10) return true
            
            // Reduced rate after 10 sessions: 10/launchCount probability
            val chance = 10.0 / launchCount
            return Math.random() < chance
        }
        return false
    }

    fun showGuide(context: Context, gameKey: String, title: String, content: String, buttonText: String? = null, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_guide)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.guide_title).text = title
        dialog.findViewById<TextView>(R.id.guide_content).text = content
        val checkBox = dialog.findViewById<CheckBox>(R.id.cb_dont_show_again)
        val btnClose = dialog.findViewById<Button>(R.id.btn_close_guide)

        buttonText?.let { btnClose.text = it }

        // Auto-check the box after 3rd session (starting from 4th launch) to guide user towards dismissal
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val launchCount = prefs.getInt("launch_count_$gameKey", 0)
        if (launchCount >= 3) {
            checkBox.isChecked = true
        }

        btnClose.setOnClickListener {
            if (checkBox.isChecked) {
                prefs.edit().putBoolean("show_$gameKey", false).apply()
            }
            dialog.dismiss()
            onDismiss()
        }

        setupFocusEffect(btnClose)
        setupFocusEffect(checkBox)

        dialog.show()
        btnClose.requestFocus()
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
