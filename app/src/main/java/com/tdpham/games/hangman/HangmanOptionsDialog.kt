package com.tdpham.games.hangman

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object HangmanOptionsDialog {
    private const val PREFS_NAME = "hangman_settings"
    private const val KEY_CATEGORY = "selected_category_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.hangman_settings_title))
            .addOption(
                label = context.getString(R.string.hangman_category_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CATEGORY, -1)
                    context.getString(when(index) {
                        0 -> R.string.cat_animals
                        1 -> R.string.cat_fruits
                        2 -> R.string.cat_countries
                        3 -> R.string.cat_sports
                        else -> R.string.hangman_mode_random
                    })
                },
                descProvider = {
                    val index = prefs.getInt(KEY_CATEGORY, -1)
                    if (index == -1) context.getString(R.string.hangman_mode_random_desc) else ""
                },
                onClick = {
                    val index = prefs.getInt(KEY_CATEGORY, -1)
                    val nextIndex = if (index >= 3) -1 else index + 1
                    prefs.edit { putInt(KEY_CATEGORY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
