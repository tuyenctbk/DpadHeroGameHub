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

    enum class GuidePhase { DISCOVERY, FAMILIARITY, MASTERY }

    fun getGuidePhase(context: Context, gameKey: String): GuidePhase {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check legacy "don't show again" boolean first
        if (!prefs.getBoolean("show_$gameKey", true)) return GuidePhase.MASTERY
        
        val launchCount = prefs.getInt("launch_count_$gameKey", 0)
        return when {
            launchCount == 0 -> GuidePhase.DISCOVERY
            launchCount < 5 -> GuidePhase.FAMILIARITY
            else -> GuidePhase.MASTERY
        }
    }

    fun incrementLaunchCount(context: Context, gameKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt("launch_count_$gameKey", 0)
        prefs.edit().putInt("launch_count_$gameKey", current + 1).apply()
    }

    fun markGuideAsRead(context: Context, gameKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Ensure it moves past DISCOVERY at minimum
        val current = prefs.getInt("launch_count_$gameKey", 0)
        if (current == 0) prefs.edit().putInt("launch_count_$gameKey", 1).apply()
    }

    fun shouldShowGuide(context: Context, gameKey: String): Boolean {
        return getGuidePhase(context, gameKey) != GuidePhase.MASTERY
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

        btnClose.setOnClickListener {
            if (checkBox.isChecked) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
