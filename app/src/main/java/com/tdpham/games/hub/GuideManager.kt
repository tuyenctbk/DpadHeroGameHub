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
import com.tdpham.games.common.IdleAdManager
import com.tdpham.games.trex.TRexOptionsDialog

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

    fun showGuide(
        context: Context,
        gameKey: String,
        title: String,
        content: String,
        buttonText: String? = null,
        showCheckbox: Boolean = true,
        onOptionsClick: (() -> Unit)? = null,
        onDismiss: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_guide)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.guide_title).text = title
        dialog.findViewById<TextView>(R.id.guide_content).text = content
        val checkBox = dialog.findViewById<CheckBox>(R.id.cb_dont_show_again)
        val btnClose = dialog.findViewById<Button>(R.id.btn_close_guide)
        val btnOptions = dialog.findViewById<Button>(R.id.btn_guide_options)

        checkBox.visibility = if (showCheckbox) View.VISIBLE else View.GONE
        buttonText?.let { btnClose.text = it }

        if (onOptionsClick != null) {
            btnOptions.visibility = View.VISIBLE
            btnOptions.setOnClickListener {
                IdleAdManager.notifyInteraction()
                dialog.dismiss()
                onOptionsClick()
            }
            setupFocusEffect(btnOptions)
        } else {
            btnOptions.visibility = View.GONE
        }

        if (showCheckbox) {
            // Auto-check the box after 3rd session (starting from 4th launch) to guide user towards dismissal
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val launchCount = prefs.getInt("launch_count_$gameKey", 0)
            if (launchCount >= 3) {
                checkBox.isChecked = true
            }
        }

        btnClose.setOnClickListener {
            IdleAdManager.notifyInteraction()
            if (showCheckbox && checkBox.isChecked) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("show_$gameKey", false).apply()
            }
            dialog.dismiss()
            onDismiss()
        }

        setupFocusEffect(btnClose)
        setupFocusEffect(checkBox)

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                IdleAdManager.notifyInteraction()
                if (keyCode == android.view.KeyEvent.KEYCODE_M || keyCode == android.view.KeyEvent.KEYCODE_O ||
                    keyCode == android.view.KeyEvent.KEYCODE_MENU || keyCode == android.view.KeyEvent.KEYCODE_SETTINGS) {
                    if (onOptionsClick != null) {
                        dialog.dismiss()
                        onOptionsClick()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        dialog.show()
        btnClose.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                IdleAdManager.notifyInteraction()
                com.tdpham.games.common.SoundManager.playClick()
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
            }
        }
        view.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                IdleAdManager.notifyInteraction()
                v.requestFocus()
            }
            false
        }
    }
}
