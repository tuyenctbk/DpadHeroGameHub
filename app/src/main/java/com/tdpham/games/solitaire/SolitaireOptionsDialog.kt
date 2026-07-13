package com.tdpham.games.solitaire

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object SolitaireOptionsDialog {
    private const val PREFS_NAME = "solitaire_settings"
    private const val KEY_DRAW_COUNT = "draw_count"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.solitaire_settings_title))
            .addOption(
                label = context.getString(R.string.solitaire_draw_label),
                valueProvider = {
                    val count = prefs.getInt(KEY_DRAW_COUNT, 1)
                    context.getString(if (count == 3) R.string.solitaire_draw_3 else R.string.solitaire_draw_1)
                },
                descProvider = {
                    val count = prefs.getInt(KEY_DRAW_COUNT, 1)
                    context.getString(if (count == 3) R.string.solitaire_draw_3_desc else R.string.solitaire_draw_1_desc)
                },
                onClick = {
                    val count = prefs.getInt(KEY_DRAW_COUNT, 1)
                    val nextCount = if (count == 1) 3 else 1
                    prefs.edit { putInt(KEY_DRAW_COUNT, nextCount) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
