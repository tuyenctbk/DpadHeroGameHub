package com.tdpham.games.hub

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.tdpham.games.R

object GuideManager {
    private const val PREFS_NAME = "game_guides"

    fun shouldShowGuide(context: Context, gameKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("show_$gameKey", true)
    }

    fun showGuide(context: Context, gameKey: String, title: String, content: String, buttonText: String? = null, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_guide)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        dialog.findViewById<TextView>(R.id.guide_title).text = title
        dialog.findViewById<TextView>(R.id.guide_content).text = content
        val checkBox = dialog.findViewById<CheckBox>(R.id.cb_dont_show_again)
        val btnClose = dialog.findViewById<Button>(R.id.btn_close_guide)

        buttonText?.let { btnClose.text = it }

        btnClose.setOnClickListener {
            if (checkBox.isChecked) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("show_$gameKey", false).apply()
            }
            dialog.dismiss()
            onDismiss()
        }

        dialog.show()
        btnClose.requestFocus()
    }
}
