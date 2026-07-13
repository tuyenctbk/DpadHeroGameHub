package com.tdpham.games.lines98

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

object Lines98OptionsDialog {
    private const val PREFS_NAME = "lines98_settings"
    private const val KEY_COLOR_COUNT = "color_count"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_lines98_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val levelLayout = dialog.findViewById<LinearLayout>(R.id.opt_level_layout)
        val levelValue = dialog.findViewById<TextView>(R.id.opt_level_value)

        fun updateLevelText() {
            val count = prefs.getInt(KEY_COLOR_COUNT, 7)
            val valRes = when(count) {
                5 -> R.string.lines98_difficulty_easy
                9 -> R.string.lines98_difficulty_hard
                else -> R.string.lines98_difficulty_normal
            }
            levelValue.text = context.getString(valRes)
        }

        updateLevelText()

        levelLayout.setOnClickListener {
            val count = prefs.getInt(KEY_COLOR_COUNT, 7)
            val nextCount = when(count) {
                5 -> 7
                7 -> 9
                else -> 5
            }
            prefs.edit { putInt(KEY_COLOR_COUNT, nextCount) }
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
