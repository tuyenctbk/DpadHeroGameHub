package com.tdpham.games.sudoku

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
import com.tdpham.games.R

object SudokuOptionsDialog {
    private const val PREFS_NAME = "sudoku_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_sudoku_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val levelLayout = dialog.findViewById<LinearLayout>(R.id.opt_level_layout)
        val levelValue = dialog.findViewById<TextView>(R.id.opt_level_value)
        val levelDesc = dialog.findViewById<TextView>(R.id.opt_level_desc)

        fun updateLevelText() {
            val index = prefs.getInt(KEY_DIFFICULTY, 1)
            val (valRes, descRes) = when(index) {
                0 -> R.string.sudoku_level_1 to R.string.sudoku_level_1_desc
                2 -> R.string.sudoku_level_3 to R.string.sudoku_level_3_desc
                else -> R.string.sudoku_level_2 to R.string.sudoku_level_2_desc
            }
            levelValue.text = context.getString(valRes)
            levelDesc.text = context.getString(descRes)
        }

        updateLevelText()

        levelLayout.setOnClickListener {
            val index = prefs.getInt(KEY_DIFFICULTY, 1)
            val nextIndex = (index + 1) % 3
            prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
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
                v.setBackgroundColor(Color.parseColor("#33FFFFFF"))
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
