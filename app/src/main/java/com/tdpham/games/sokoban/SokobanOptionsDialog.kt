package com.tdpham.games.sokoban

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.tdpham.games.R

object SokobanOptionsDialog {
    private const val PREFS_NAME = "sokoban_settings"
    private const val KEY_START_LEVEL = "start_level"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_sokoban_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val levelLayout = dialog.findViewById<LinearLayout>(R.id.opt_level_layout)
        val levelValue = dialog.findViewById<TextView>(R.id.opt_level_value)

        fun updateLevelText() {
            val level = prefs.getInt(KEY_START_LEVEL, 1)
            levelValue.text = level.toString()
        }

        updateLevelText()

        levelLayout.setOnClickListener {
            val level = prefs.getInt(KEY_START_LEVEL, 1)
            val nextLevel = if (level >= 5) 1 else level + 1
            prefs.edit { putInt(KEY_START_LEVEL, nextLevel) }
            updateLevelText()
        }

        setupFocusEffect(levelLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        levelLayout.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200).start()
                v.setBackgroundColor("#33FFFFFF".toColorInt())
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.setBackgroundColor(Color.TRANSPARENT)
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
