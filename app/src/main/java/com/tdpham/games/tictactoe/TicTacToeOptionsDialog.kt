package com.tdpham.games.tictactoe

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

object TicTacToeOptionsDialog {
    private const val PREFS_NAME = "tictactoe_settings"
    private const val KEY_BOARD_SIZE = "board_size"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_tictactoe_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val sizeLayout = dialog.findViewById<LinearLayout>(R.id.opt_size_layout)
        val sizeValue = dialog.findViewById<TextView>(R.id.opt_size_value)
        val sizeDesc = dialog.findViewById<TextView>(R.id.opt_size_desc)

        fun updateSizeText() {
            val size = prefs.getInt(KEY_BOARD_SIZE, 3)
            val (valRes, descRes) = when(size) {
                4 -> R.string.tictactoe_size_4 to R.string.tictactoe_size_4_desc
                5 -> R.string.tictactoe_size_5 to R.string.tictactoe_size_5_desc
                else -> R.string.tictactoe_size_3 to R.string.tictactoe_size_3_desc
            }
            sizeValue.text = context.getString(valRes)
            sizeDesc.text = context.getString(descRes)
        }

        updateSizeText()

        sizeLayout.setOnClickListener {
            val size = prefs.getInt(KEY_BOARD_SIZE, 3)
            val nextSize = when(size) {
                3 -> 4
                4 -> 5
                else -> 3
            }
            prefs.edit { putInt(KEY_BOARD_SIZE, nextSize) }
            updateSizeText()
        }

        setupFocusEffect(sizeLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        sizeLayout.requestFocus()
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
