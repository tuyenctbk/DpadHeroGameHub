package com.tdpham.games.wordquest

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

object WordQuestOptionsDialog {
    private const val PREFS_NAME = "wordquest_settings"
    private const val KEY_CATEGORY = "selected_category_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_word_quest_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val catLayout = dialog.findViewById<LinearLayout>(R.id.opt_cat_layout)
        val catValue = dialog.findViewById<TextView>(R.id.opt_cat_value)

        fun updateCatText() {
            val index = prefs.getInt(KEY_CATEGORY, 0)
            val valRes = when(index) {
                1 -> R.string.word_quest_category_nature
                2 -> R.string.word_quest_category_tech
                else -> R.string.word_quest_category_all
            }
            catValue.text = context.getString(valRes)
        }

        updateCatText()

        catLayout.setOnClickListener {
            val index = prefs.getInt(KEY_CATEGORY, 0)
            val nextIndex = (index + 1) % 3
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
