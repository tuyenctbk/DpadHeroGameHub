package com.tdpham.games.starfighter

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

object StarFighterOptionsDialog {
    private const val PREFS_NAME = "starfighter_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_starfighter_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val modeLayout = dialog.findViewById<LinearLayout>(R.id.opt_level_layout)
        val modeValue = dialog.findViewById<TextView>(R.id.opt_level_value)

        fun updateModeText() {
            val index = prefs.getInt(KEY_DIFFICULTY, 1)
            val valRes = when(index) {
                0 -> R.string.starfighter_difficulty_1
                2 -> R.string.starfighter_difficulty_3
                else -> R.string.starfighter_difficulty_2
            }
            modeValue.text = context.getString(valRes)
        }

        updateModeText()

        modeLayout.setOnClickListener {
            val index = prefs.getInt(KEY_DIFFICULTY, 1)
            val nextIndex = (index + 1) % 3
            prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
            updateModeText()
        }

        setupFocusEffect(modeLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        modeLayout.requestFocus()
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
