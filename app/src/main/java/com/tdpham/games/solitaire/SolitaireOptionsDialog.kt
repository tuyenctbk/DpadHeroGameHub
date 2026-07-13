package com.tdpham.games.solitaire

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

object SolitaireOptionsDialog {
    private const val PREFS_NAME = "solitaire_settings"
    private const val KEY_DRAW_COUNT = "draw_count"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_solitaire_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val drawLayout = dialog.findViewById<LinearLayout>(R.id.opt_draw_layout)
        val drawValue = dialog.findViewById<TextView>(R.id.opt_draw_value)
        val drawDesc = dialog.findViewById<TextView>(R.id.opt_draw_desc)

        fun updateDrawText() {
            val count = prefs.getInt(KEY_DRAW_COUNT, 1)
            val (valRes, descRes) = when(count) {
                3 -> R.string.solitaire_draw_3 to R.string.solitaire_draw_3_desc
                else -> R.string.solitaire_draw_1 to R.string.solitaire_draw_1_desc
            }
            drawValue.text = context.getString(valRes)
            drawDesc.text = context.getString(descRes)
        }

        updateDrawText()

        drawLayout.setOnClickListener {
            val count = prefs.getInt(KEY_DRAW_COUNT, 1)
            val nextCount = if (count == 1) 3 else 1
            prefs.edit { putInt(KEY_DRAW_COUNT, nextCount) }
            updateDrawText()
        }

        setupFocusEffect(drawLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        drawLayout.requestFocus()
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
