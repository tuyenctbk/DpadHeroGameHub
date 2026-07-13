package com.tdpham.games.checkers

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object CheckersOptionsDialog {
    private const val PREFS_NAME = "checkers_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.checkers_settings_title))
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(if (index == 0) R.string.checkers_difficulty_1 else R.string.checkers_difficulty_2)
                },
                descProvider = {
                    context.getString(R.string.checkers_difficulty_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = if (index == 0) 1 else 0
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
