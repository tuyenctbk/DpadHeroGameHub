package com.tdpham.games.brickbreak

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

object BrickBreakOptionsDialog {
    private const val PREFS_NAME = "brick_break_settings"
    private const val KEY_PADDLE_SIZE = "paddle_size_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_brick_break_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val paddleLayout = dialog.findViewById<LinearLayout>(R.id.opt_paddle_layout)
        val paddleValue = dialog.findViewById<TextView>(R.id.opt_paddle_value)

        fun updatePaddleText() {
            val index = prefs.getInt(KEY_PADDLE_SIZE, 1) // Default Medium
            val valRes = when(index) {
                0 -> R.string.brick_break_paddle_large
                2 -> R.string.brick_break_paddle_small
                else -> R.string.brick_break_paddle_medium
            }
            paddleValue.text = context.getString(valRes)
        }

        updatePaddleText()

        paddleLayout.setOnClickListener {
            val index = prefs.getInt(KEY_PADDLE_SIZE, 1)
            val nextIndex = (index + 1) % 3
            prefs.edit { putInt(KEY_PADDLE_SIZE, nextIndex) }
            updatePaddleText()
        }

        setupFocusEffect(paddleLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        paddleLayout.requestFocus()
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
