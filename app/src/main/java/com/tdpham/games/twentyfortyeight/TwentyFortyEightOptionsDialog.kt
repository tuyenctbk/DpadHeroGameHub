package com.tdpham.games.twentyfortyeight

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

object TwentyFortyEightOptionsDialog {
    private const val PREFS_NAME = "twentyfortyeight_settings"
    private const val KEY_GRID_SIZE = "grid_size"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_twentyfortyeight_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val sizeLayout = dialog.findViewById<LinearLayout>(R.id.opt_size_layout)
        val sizeValue = dialog.findViewById<TextView>(R.id.opt_size_value)
        val sizeDesc = dialog.findViewById<TextView>(R.id.opt_size_desc)

        fun updateSizeText() {
            val size = prefs.getInt(KEY_GRID_SIZE, 4)
            val valRes = when(size) {
                5 -> R.string.game_4096_size_5
                else -> R.string.game_4096_size_4
            }
            sizeValue.text = context.getString(valRes)
            sizeDesc.text = context.getString(R.string.game_4096_size_desc)
        }

        updateSizeText()

        sizeLayout.setOnClickListener {
            val size = prefs.getInt(KEY_GRID_SIZE, 4)
            val nextSize = if (size == 4) 5 else 4
            prefs.edit { putInt(KEY_GRID_SIZE, nextSize) }
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
