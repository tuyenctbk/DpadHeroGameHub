package com.tdpham.games.hangman

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

object HangmanOptionsDialog {
    private const val PREFS_NAME = "hangman_settings"
    private const val KEY_CATEGORY = "selected_category_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_hangman_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val catLayout = dialog.findViewById<LinearLayout>(R.id.opt_cat_layout)
        val catValue = dialog.findViewById<TextView>(R.id.opt_cat_value)
        val catDesc = dialog.findViewById<TextView>(R.id.opt_cat_desc)

        fun updateCatText() {
            val index = prefs.getInt(KEY_CATEGORY, -1)
            val valRes = when(index) {
                0 -> R.string.cat_animals
                1 -> R.string.cat_fruits
                2 -> R.string.cat_countries
                3 -> R.string.cat_sports
                else -> R.string.hangman_mode_random
            }
            catValue.text = context.getString(valRes)
            catDesc.text = if (index == -1) context.getString(R.string.hangman_mode_random_desc) else ""
        }

        updateCatText()

        catLayout.setOnClickListener {
            val index = prefs.getInt(KEY_CATEGORY, -1)
            val nextIndex = if (index >= 3) -1 else index + 1
            prefs.edit { putInt(KEY_CATEGORY, nextIndex) }
            updateCatText()
        }

        setupFocusEffect(catLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        catLayout.requestFocus()
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
